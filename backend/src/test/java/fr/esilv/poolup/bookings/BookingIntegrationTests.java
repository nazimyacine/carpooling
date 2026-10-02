package fr.esilv.poolup.bookings;

import java.time.Instant;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Book and cancel (rules 1, 2, 4 and 6), through the HTTP layer on PostgreSQL 16. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BookingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------------------------------------------------------------- book

    @Test
    void passengerBooksSeatsAndTheTripCountersFollow() throws Exception {
        Account driver = register();
        Account passenger = register();
        long tripId = publishTrip(driver, 3);

        book(passenger, tripId, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.seats").value(2))
                .andExpect(jsonPath("$.totalPrice").value(25.00))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.trip.id").value(tripId))
                .andExpect(jsonPath("$.trip.seatsAvailable").value(1))
                .andExpect(jsonPath("$.trip.seatsBooked").value(2))
                .andExpect(jsonPath("$.trip.status").value("OPEN"));

        assertThat(tripState(tripId)).isEqualTo(List.of("OPEN", "1"));
    }

    @Test
    void bookingTheLastSeatMakesTheTripFullAndRefusesTheNextOne() throws Exception {
        Account driver = register();
        long tripId = publishTrip(driver, 2);

        book(register(), tripId, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trip.status").value("FULL"))
                .andExpect(jsonPath("$.trip.seatsAvailable").value(0));

        book(register(), tripId, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        assertThat(tripState(tripId)).isEqualTo(List.of("FULL", "0"));
    }

    @Test
    void bookingMoreSeatsThanAvailableIs409AndTakesNothing() throws Exception {
        long tripId = publishTrip(register(), 2);

        book(register(), tripId, 3).andExpect(status().isConflict());

        assertThat(tripState(tripId)).isEqualTo(List.of("OPEN", "2"));
        assertThat(confirmedBookings(tripId)).isZero();
    }

    @Test
    void driverCannotBookTheirOwnTrip() throws Exception {
        Account driver = register();
        long tripId = publishTrip(driver, 3);

        book(driver, tripId, 1).andExpect(status().isForbidden());
        assertThat(tripState(tripId)).isEqualTo(List.of("OPEN", "3"));
    }

    @Test
    void cancelledCompletedOrDepartedTripsCannotBeBooked() throws Exception {
        Account driver = register();
        Account passenger = register();

        long cancelled = publishTrip(driver, 3);
        mockMvc.perform(post("/api/trips/" + cancelled + "/cancel").header(HttpHeaders.AUTHORIZATION, driver.bearer()))
                .andExpect(status().isOk());
        book(passenger, cancelled, 1).andExpect(status().isConflict());

        long completed = publishTrip(driver, 3);
        jdbcTemplate.update("UPDATE trips SET status = 'COMPLETED', departure_at = now() - interval '1 day' WHERE id = ?",
                completed);
        book(passenger, completed, 1).andExpect(status().isConflict());

        // Departure passed but the nightly job has not marked it COMPLETED yet: still refused
        long departed = publishTrip(driver, 3);
        jdbcTemplate.update("UPDATE trips SET departure_at = now() - interval '1 hour' WHERE id = ?", departed);
        book(passenger, departed, 1).andExpect(status().isConflict());

        assertThat(tripState(departed)).isEqualTo(List.of("OPEN", "3"));
    }

    @Test
    void aPassengerHasAtMostOneConfirmedBookingPerTrip() throws Exception {
        Account passenger = register();
        long tripId = publishTrip(register(), 4);

        book(passenger, tripId, 1).andExpect(status().isCreated());
        book(passenger, tripId, 1).andExpect(status().isConflict());

        assertThat(tripState(tripId)).isEqualTo(List.of("OPEN", "3"));
        assertThat(confirmedBookings(tripId)).isEqualTo(1);
    }

    @Test
    void invalidSeatCountUnknownTripAndAnonymousAreRefused() throws Exception {
        Account passenger = register();
        long tripId = publishTrip(register(), 3);

        book(passenger, tripId, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.seats").exists());
        book(passenger, tripId, 9).andExpect(status().isBadRequest());
        book(passenger, -1L, 1).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/trips/" + tripId + "/bookings")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seats\": 1}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- cancel

    @Test
    void cancellingGivesTheSeatsBackAndReopensAFullTrip() throws Exception {
        Account passenger = register();
        long tripId = publishTrip(register(), 2);
        long bookingId = createdId(book(passenger, tripId, 2));
        assertThat(tripState(tripId)).isEqualTo(List.of("FULL", "0"));

        cancelBooking(passenger, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.trip.status").value("OPEN"))
                .andExpect(jsonPath("$.trip.seatsAvailable").value(2));

        // The cancelled booking stays in history and does not prevent booking again
        book(passenger, tripId, 1).andExpect(status().isCreated());
        assertThat(tripState(tripId)).isEqualTo(List.of("OPEN", "1"));
    }

    @Test
    void onlyThePassengerCanCancelAndOnlyOnce() throws Exception {
        Account passenger = register();
        long tripId = publishTrip(register(), 3);
        long bookingId = createdId(book(passenger, tripId, 1));

        cancelBooking(register(), bookingId).andExpect(status().isForbidden());
        cancelBooking(passenger, bookingId).andExpect(status().isOk());
        cancelBooking(passenger, bookingId).andExpect(status().isConflict());
        cancelBooking(passenger, -1L).andExpect(status().isNotFound());

        // Seats given back exactly once
        assertThat(tripState(tripId)).isEqualTo(List.of("OPEN", "3"));
    }

    @Test
    void aBookingCannotBeCancelledAfterDeparture() throws Exception {
        Account passenger = register();
        long tripId = publishTrip(register(), 3);
        long bookingId = createdId(book(passenger, tripId, 1));
        jdbcTemplate.update("UPDATE trips SET departure_at = now() - interval '1 hour' WHERE id = ?", tripId);

        cancelBooking(passenger, bookingId).andExpect(status().isConflict());
        assertThat(tripState(tripId)).isEqualTo(List.of("OPEN", "2"));
    }

    @Test
    void passengerHistoryShowsTripsCancelledByTheDriver() throws Exception {
        Account driver = register();
        Account passenger = register();
        long kept = publishTrip(driver, 3);
        long cancelled = publishTrip(driver, 3);
        book(passenger, kept, 1).andExpect(status().isCreated());
        book(passenger, cancelled, 2).andExpect(status().isCreated());

        // Rule 6: the driver cancels, the booking is cancelled with the trip
        mockMvc.perform(post("/api/trips/" + cancelled + "/cancel").header(HttpHeaders.AUTHORIZATION, driver.bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bookings/mine").header(HttpHeaders.AUTHORIZATION, passenger.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].trip.id").value(cancelled))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$[0].trip.status").value("CANCELLED"))
                .andExpect(jsonPath("$[1].trip.id").value(kept))
                .andExpect(jsonPath("$[1].status").value("CONFIRMED"));

        // Other users' bookings are not listed
        mockMvc.perform(get("/api/bookings/mine").header(HttpHeaders.AUTHORIZATION, driver.bearer()))
                .andExpect(jsonPath("$.length()").value(0));
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
                                {"email": "bookings-%s@test.poolup.fr", "password": "motdepasse123",
                                 "firstName": "Léa", "lastName": "Martin"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Account(((Number) JsonPath.read(body, "$.user.id")).longValue(), JsonPath.read(body, "$.token"));
    }

    /** Paris -> Lille in three days, 12.50 per seat. */
    private long publishTrip(Account driver, int seats) throws Exception {
        return createdId(mockMvc.perform(post("/api/trips")
                .header(HttpHeaders.AUTHORIZATION, driver.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"departureCityId": %d, "arrivalCityId": %d, "meetingPoint": "Gare",
                         "departureAt": "%s", "seatsTotal": %d, "pricePerSeat": 12.50}
                        """.formatted(city("Paris"), city("Lille"),
                        Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES), seats))));
    }

    private ResultActions book(Account passenger, long tripId, int seats) throws Exception {
        return mockMvc.perform(post("/api/trips/" + tripId + "/bookings")
                .header(HttpHeaders.AUTHORIZATION, passenger.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"seats\": " + seats + "}"));
    }

    private ResultActions cancelBooking(Account user, long bookingId) throws Exception {
        return mockMvc.perform(post("/api/bookings/" + bookingId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, user.bearer()));
    }

    private static long createdId(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long city(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM cities WHERE name = ?", Long.class, name);
    }

    /** [status, seats_available] read directly in the database. */
    private List<String> tripState(long tripId) {
        return jdbcTemplate.queryForObject("SELECT status, seats_available FROM trips WHERE id = ?",
                (row, index) -> List.of(row.getString(1), String.valueOf(row.getInt(2))), tripId);
    }

    private int confirmedBookings(long tripId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookings WHERE trip_id = ? AND status = 'CONFIRMED'", Integer.class, tripId);
    }
}
