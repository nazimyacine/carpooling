package fr.esilv.poolup.trips;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import fr.esilv.poolup.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Publish, search, edit (rule 3) and cancel (rule 6) trips, through the HTTP layer on PostgreSQL 16. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TripIntegrationTests {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------------------------------------------------------------- publish

    @Test
    void driverPublishesATrip() throws Exception {
        Account driver = register();
        Instant departure = inDays(3);

        publish(driver, tripJson(city("Paris"), city("Lille"), departure, 3, "18.50"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.seatsTotal").value(3))
                .andExpect(jsonPath("$.seatsAvailable").value(3))
                .andExpect(jsonPath("$.seatsBooked").value(0))
                .andExpect(jsonPath("$.pricePerSeat").value(18.50))
                .andExpect(jsonPath("$.departureAt").value(departure.toString()))
                .andExpect(jsonPath("$.departureCity.name").value("Paris"))
                .andExpect(jsonPath("$.departureCity.latitude").isNumber())
                .andExpect(jsonPath("$.arrivalCity.name").value("Lille"))
                .andExpect(jsonPath("$.driver.id").value(driver.id()))
                .andExpect(jsonPath("$.driver.firstName").value("Karim"))
                .andExpect(jsonPath("$.driver.lastNameInitial").value("B"))
                .andExpect(jsonPath("$.driver.email").doesNotExist());
    }

    @Test
    void publishRejectsADepartureInThePast() throws Exception {
        publish(register(), tripJson(city("Paris"), city("Lille"), Instant.now().minus(1, ChronoUnit.HOURS), 3, "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.departureAt").exists());
    }

    @Test
    void publishRejectsTheSameDepartureAndArrivalCity() throws Exception {
        publish(register(), tripJson(city("Paris"), city("Paris"), inDays(2), 3, "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void publishRejectsASeatCountOutsideOneToEight() throws Exception {
        Account driver = register();
        publish(driver, tripJson(city("Paris"), city("Lille"), inDays(2), 0, "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.seatsTotal").exists());
        publish(driver, tripJson(city("Paris"), city("Lille"), inDays(2), 9, "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.seatsTotal").exists());
    }

    @Test
    void publishRejectsANonPositivePrice() throws Exception {
        Account driver = register();
        publish(driver, tripJson(city("Paris"), city("Lille"), inDays(2), 3, "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pricePerSeat").exists());
        publish(driver, tripJson(city("Paris"), city("Lille"), inDays(2), 3, "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pricePerSeat").exists());
    }

    @Test
    void publishRejectsAnUnknownCity() throws Exception {
        publish(register(), tripJson(-1L, city("Lille"), inDays(2), 3, "10"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousUserCannotPublishOrSearch() throws Exception {
        mockMvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON)
                        .content(tripJson(city("Paris"), city("Lille"), inDays(2), 3, "10")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/trips")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- read

    @Test
    void searchFiltersByCitiesDayAndFreeSeats() throws Exception {
        Account driver = register();
        LocalDate day = LocalDate.now(PARIS).plusDays(20);
        Instant morning = day.atTime(8, 15).atZone(PARIS).toInstant();
        long matching = createdId(publish(driver, tripJson(city("Rennes"), city("Brest"), morning, 3, "12")));
        long full = createdId(publish(driver, tripJson(city("Rennes"), city("Brest"), morning.plusSeconds(3600), 1, "12")));
        book(full, register().id(), 1);
        long otherDay = createdId(publish(driver, tripJson(city("Rennes"), city("Brest"), morning.plus(1, ChronoUnit.DAYS), 3, "12")));
        long otherArrival = createdId(publish(driver, tripJson(city("Rennes"), city("Nantes"), morning, 3, "12")));
        long cancelled = createdId(publish(driver, tripJson(city("Rennes"), city("Brest"), morning, 3, "12")));
        cancel(driver, cancelled).andExpect(status().isOk());

        String search = "/api/trips?departureCityId=" + city("Rennes") + "&arrivalCityId=" + city("Brest") + "&date=" + day;
        String found = mockMvc.perform(get(search).header(HttpHeaders.AUTHORIZATION, driver.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(found, "$[*].id");
        assertThat(ids).containsExactly((int) matching, (int) full); // sorted by departure time
        assertThat(ids).doesNotContain((int) otherDay, (int) otherArrival, (int) cancelled);

        mockMvc.perform(get(search + "&seats=1").header(HttpHeaders.AUTHORIZATION, driver.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) matching)))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) full))));
    }

    @Test
    void searchHidesDepartedAndCompletedTrips() throws Exception {
        Account driver = register();
        long departed = createdId(publish(driver, tripJson(city("Caen"), city("Rouen"), inDays(2), 3, "9")));
        long completed = createdId(publish(driver, tripJson(city("Caen"), city("Rouen"), inDays(2), 3, "9")));
        jdbcTemplate.update("UPDATE trips SET departure_at = now() - interval '1 hour' WHERE id = ?", departed);
        jdbcTemplate.update("UPDATE trips SET status = 'COMPLETED' WHERE id = ?", completed);

        mockMvc.perform(get("/api/trips?departureCityId=" + city("Caen") + "&arrivalCityId=" + city("Rouen"))
                        .header(HttpHeaders.AUTHORIZATION, driver.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem((int) departed))))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) completed))));
    }

    @Test
    void searchRejectsASeatFilterOutsideOneToEight() throws Exception {
        mockMvc.perform(get("/api/trips?seats=9").header(HttpHeaders.AUTHORIZATION, register().bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anyUserReadsATripAndUnknownTripIs404() throws Exception {
        long tripId = createdId(publish(register(), tripJson(city("Paris"), city("Lyon"), inDays(5), 4, "30")));
        Account passenger = register();

        mockMvc.perform(get("/api/trips/" + tripId).header(HttpHeaders.AUTHORIZATION, passenger.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tripId))
                .andExpect(jsonPath("$.arrivalCity.name").value("Lyon"));
        mockMvc.perform(get("/api/trips/999999999").header(HttpHeaders.AUTHORIZATION, passenger.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void mineListsOnlyTheDriversOwnTrips() throws Exception {
        Account driver = register();
        long own = createdId(publish(driver, tripJson(city("Paris"), city("Lyon"), inDays(5), 4, "30")));
        long other = createdId(publish(register(), tripJson(city("Paris"), city("Lyon"), inDays(5), 4, "30")));

        String body = mockMvc.perform(get("/api/trips/mine").header(HttpHeaders.AUTHORIZATION, driver.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Integer> ids = JsonPath.read(body, "$[*].id");
        assertThat(ids).containsExactly((int) own).doesNotContain((int) other);
    }

    // ---------------------------------------------------------------- edit (rule 3)

    @Test
    void driverEditsTheTrip() throws Exception {
        Account driver = register();
        long tripId = createdId(publish(driver, tripJson(city("Paris"), city("Lille"), inDays(3), 3, "18")));
        Instant newDeparture = inDays(4);

        update(driver, tripId, tripJson(city("La Défense"), city("Lille"), newDeparture, 4, "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departureCity.name").value("La Défense"))
                .andExpect(jsonPath("$.departureAt").value(newDeparture.toString()))
                .andExpect(jsonPath("$.seatsTotal").value(4))
                .andExpect(jsonPath("$.seatsAvailable").value(4))
                .andExpect(jsonPath("$.pricePerSeat").value(20));
    }

    @Test
    void driverCannotRemoveSeatsAlreadyBooked() throws Exception {
        Account driver = register();
        long tripId = createdId(publish(driver, tripJson(city("Paris"), city("Lille"), inDays(3), 4, "18")));
        book(tripId, register().id(), 2);

        update(driver, tripId, tripJson(city("Paris"), city("Lille"), inDays(3), 1, "18"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertThat(seats(tripId)).containsExactly(4, 2);

        // Down to exactly the booked seats: allowed, and the trip becomes FULL
        update(driver, tripId, tripJson(city("Paris"), city("Lille"), inDays(3), 2, "18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatsTotal").value(2))
                .andExpect(jsonPath("$.seatsAvailable").value(0))
                .andExpect(jsonPath("$.seatsBooked").value(2))
                .andExpect(jsonPath("$.status").value("FULL"));

        // Adding a seat to a full trip opens it again
        update(driver, tripId, tripJson(city("Paris"), city("Lille"), inDays(3), 3, "18"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatsAvailable").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void onlyTheDriverCanEditOrCancel() throws Exception {
        Account driver = register();
        Account other = register();
        long tripId = createdId(publish(driver, tripJson(city("Paris"), city("Lille"), inDays(3), 3, "18")));

        update(other, tripId, tripJson(city("Paris"), city("Lille"), inDays(3), 8, "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        cancel(other, tripId).andExpect(status().isForbidden());
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM trips WHERE id = ?", String.class, tripId))
                .isEqualTo("OPEN");
    }

    @Test
    void editRejectsInvalidDataLikePublish() throws Exception {
        Account driver = register();
        long tripId = createdId(publish(driver, tripJson(city("Paris"), city("Lille"), inDays(3), 3, "18")));

        update(driver, tripId, tripJson(city("Lille"), city("Lille"), inDays(3), 3, "18"))
                .andExpect(status().isBadRequest());
        update(driver, tripId, tripJson(city("Paris"), city("Lille"), Instant.now().minusSeconds(60), 3, "18"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.departureAt").exists());
    }

    @Test
    void cancelledCompletedOrDepartedTripsCannotBeEdited() throws Exception {
        Account driver = register();
        long cancelled = createdId(publish(driver, tripJson(city("Paris"), city("Lille"), inDays(3), 3, "18")));
        cancel(driver, cancelled).andExpect(status().isOk());
        long completed = createdId(publish(driver, tripJson(city("Paris"), city("Lille"), inDays(3), 3, "18")));
        jdbcTemplate.update("UPDATE trips SET status = 'COMPLETED' WHERE id = ?", completed);
        long departed = createdId(publish(driver, tripJson(city("Paris"), city("Lille"), inDays(3), 3, "18")));
        jdbcTemplate.update("UPDATE trips SET departure_at = now() - interval '1 hour' WHERE id = ?", departed);

        for (long tripId : new long[] {cancelled, completed, departed}) {
            update(driver, tripId, tripJson(city("Paris"), city("Lille"), inDays(3), 3, "18"))
                    .andExpect(status().isConflict());
            cancel(driver, tripId).andExpect(status().isConflict());
        }
    }

    @Test
    void editOrCancelOfAnUnknownTripIs404() throws Exception {
        Account driver = register();
        update(driver, 999999999L, tripJson(city("Paris"), city("Lille"), inDays(3), 3, "18"))
                .andExpect(status().isNotFound());
        cancel(driver, 999999999L).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- cancel (rule 6)

    @Test
    void cancellingATripCancelsAllItsBookings() throws Exception {
        Account driver = register();
        long tripId = createdId(publish(driver, tripJson(city("Paris"), city("Lille"), inDays(3), 3, "18")));
        long first = register().id();
        long second = register().id();
        book(tripId, first, 1);
        book(tripId, second, 2);
        assertThat(seats(tripId)).containsExactly(3, 0);

        cancel(driver, tripId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.seatsAvailable").value(3));

        // Bookings stay in the passengers' history, as cancelled
        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM bookings WHERE trip_id = ? AND cancelled_at IS NOT NULL", String.class, tripId);
        assertThat(statuses).containsExactly("CANCELLED", "CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE trip_id = ? AND status = 'CONFIRMED'", Long.class, tripId))
                .isZero();
    }

    // ---------------------------------------------------------------- helpers

    private record Account(long id, String token) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    private Account register() throws Exception {
        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "trips-%s@test.poolup.fr", "password": "motdepasse123",
                                 "firstName": "Karim", "lastName": "Benali"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Account(((Number) JsonPath.read(body, "$.user.id")).longValue(), JsonPath.read(body, "$.token"));
    }

    private ResultActions publish(Account driver, String json) throws Exception {
        return mockMvc.perform(post("/api/trips")
                .header(HttpHeaders.AUTHORIZATION, driver.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions update(Account user, long tripId, String json) throws Exception {
        return mockMvc.perform(put("/api/trips/" + tripId)
                .header(HttpHeaders.AUTHORIZATION, user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions cancel(Account user, long tripId) throws Exception {
        return mockMvc.perform(post("/api/trips/" + tripId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, user.bearer()));
    }

    private static long createdId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private static String tripJson(long departureCityId, long arrivalCityId, Instant departureAt, int seats,
            String price) {
        return """
                {"departureCityId": %d, "arrivalCityId": %d, "meetingPoint": "Parvis, sortie 4 du métro",
                 "departureAt": "%s", "seatsTotal": %d, "pricePerSeat": %s,
                 "description": "Un bagage cabine par personne."}
                """.formatted(departureCityId, arrivalCityId, departureAt, seats, price);
    }

    private long city(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM cities WHERE name = ?", Long.class, name);
    }

    private static Instant inDays(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    }

    /**
     * Stands in for the bookings module (step 3.3): same effect as its atomic booking,
     * a confirmed booking plus the seats taken from the trip.
     */
    private void book(long tripId, long passengerId, int seats) {
        int updated = jdbcTemplate.update("""
                UPDATE trips SET seats_available = seats_available - ?,
                    status = CASE WHEN seats_available - ? = 0 THEN 'FULL' ELSE status END
                WHERE id = ? AND status = 'OPEN' AND seats_available >= ?
                """, seats, seats, tripId, seats);
        assertThat(updated).isEqualTo(1);
        jdbcTemplate.update("INSERT INTO bookings (trip_id, passenger_id, seats) VALUES (?, ?, ?)",
                tripId, passengerId, seats);
    }

    /** [seats_total, seats_available] */
    private List<Integer> seats(long tripId) {
        return jdbcTemplate.queryForObject("SELECT seats_total, seats_available FROM trips WHERE id = ?",
                (row, index) -> List.of(row.getInt(1), row.getInt(2)), tripId);
    }
}
