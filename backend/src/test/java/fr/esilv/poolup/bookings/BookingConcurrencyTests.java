package fr.esilv.poolup.bookings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import fr.esilv.poolup.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rule 1 under real concurrency: a real HTTP server, requests sent at the same instant from
 * several threads, and the final state checked in PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class BookingConcurrencyTests {

    /** Each scenario is repeated: a race that only fails once in a while must still be caught. */
    private static final int ROUNDS = 10;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    @AfterEach
    void stopThreads() {
        executor.shutdownNow();
    }

    @Test
    void oneSeatLeftTwoSimultaneousBookingsOnlyOneSucceeds() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long tripId = publishTrip(register(), 1);
            String first = register().token();
            String second = register().token();

            List<Integer> statuses = simultaneously(List.of(
                    () -> book(first, tripId, 1),
                    () -> book(second, tripId, 1)));

            assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(201, 409);
            assertThat(tripState(tripId)).isEqualTo(List.of("FULL", "0"));
            assertThat(confirmedSeats(tripId)).isEqualTo(1);
        }
    }

    @Test
    void tenPassengersForThreeSeatsExactlyThreeSucceed() throws Exception {
        long tripId = publishTrip(register(), 3);
        List<Callable<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String token = register().token();
            requests.add(() -> book(token, tripId, 1));
        }

        List<Integer> statuses = simultaneously(requests);

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(3);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(7);
        assertThat(tripState(tripId)).isEqualTo(List.of("FULL", "0"));
        assertThat(confirmedSeats(tripId)).isEqualTo(3);
    }

    @Test
    void doubleClickBySamePassengerCreatesOneBookingAndTakesSeatsOnce() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long tripId = publishTrip(register(), 4);
            String passenger = register().token();

            List<Integer> statuses = simultaneously(List.of(
                    () -> book(passenger, tripId, 1),
                    () -> book(passenger, tripId, 1)));

            // The loser's seat decrement is rolled back with its transaction
            assertThat(statuses).as("round %d", round).containsExactlyInAnyOrder(201, 409);
            assertThat(tripState(tripId)).isEqualTo(List.of("OPEN", "3"));
            assertThat(confirmedSeats(tripId)).isEqualTo(1);
        }
    }

    /** Starts every request at the same instant and returns the HTTP statuses. */
    private List<Integer> simultaneously(List<Callable<Integer>> requests) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (Callable<Integer> request : requests) {
            results.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return request.call();
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(30, TimeUnit.SECONDS));
        }
        return statuses;
    }

    // ---------------------------------------------------------------- helpers

    private record Account(long id, String token) {
    }

    private Account register() throws Exception {
        HttpResponse<String> response = post("/api/auth/register", null, """
                {"email": "concurrency-%s@test.poolup.fr", "password": "motdepasse123",
                 "firstName": "Théo", "lastName": "Petit"}
                """.formatted(UUID.randomUUID()));
        assertThat(response.statusCode()).isEqualTo(201);
        return new Account(((Number) JsonPath.read(response.body(), "$.user.id")).longValue(),
                JsonPath.read(response.body(), "$.token"));
    }

    private long publishTrip(Account driver, int seats) throws Exception {
        HttpResponse<String> response = post("/api/trips", driver.token(), """
                {"departureCityId": %d, "arrivalCityId": %d, "meetingPoint": "Gare",
                 "departureAt": "%s", "seatsTotal": %d, "pricePerSeat": 10}
                """.formatted(city("Paris"), city("Lille"),
                Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES), seats));
        assertThat(response.statusCode()).isEqualTo(201);
        return ((Number) JsonPath.read(response.body(), "$.id")).longValue();
    }

    private int book(String token, long tripId, int seats) throws Exception {
        return post("/api/trips/" + tripId + "/bookings", token, "{\"seats\": " + seats + "}").statusCode();
    }

    private HttpResponse<String> post(String path, String token, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private long city(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM cities WHERE name = ?", Long.class, name);
    }

    private List<String> tripState(long tripId) {
        return jdbcTemplate.queryForObject("SELECT status, seats_available FROM trips WHERE id = ?",
                (row, index) -> List.of(row.getString(1), String.valueOf(row.getInt(2))), tripId);
    }

    private int confirmedSeats(long tripId) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(seats), 0) FROM bookings WHERE trip_id = ? AND status = 'CONFIRMED'",
                Integer.class, tripId);
    }
}
