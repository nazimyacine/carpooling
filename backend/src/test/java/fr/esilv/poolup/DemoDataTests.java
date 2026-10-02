package fr.esilv.poolup;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The demo data must respect the business rules, otherwise the demo would show impossible states.
 */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class DemoDataTests {

    private static final String DEMO_USERS = "SELECT id FROM users WHERE email LIKE '%@demo.poolup.fr'";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void demoDataIsLoadedWithTheDemoProfile() {
        assertThat(count("SELECT COUNT(*) FROM users WHERE email LIKE '%@demo.poolup.fr'")).isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM trips")).isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM bookings")).isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM messages")).isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM ratings")).isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM reports")).isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM users WHERE role = 'ADMIN'")).isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM users WHERE status = 'SUSPENDED'")).isGreaterThan(0);
    }

    @Test
    void demoPasswordMatchesTheStoredHashes() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT password_hash FROM users WHERE id IN (" + DEMO_USERS + ")", String.class);
        assertThat(hashes).allSatisfy(hash -> assertThat(encoder.matches("demo1234", hash)).isTrue());
    }

    @Test
    void seatCountersMatchConfirmedBookings() {
        // Rule 1: seats_available = seats_total - confirmed seats, FULL exactly when no seat is left.
        assertThat(count("""
                SELECT COUNT(*) FROM trips t
                WHERE t.status <> 'CANCELLED'
                  AND t.seats_available <> t.seats_total - COALESCE((SELECT SUM(b.seats) FROM bookings b
                        WHERE b.trip_id = t.id AND b.status = 'CONFIRMED'), 0)
                """)).isZero();
        assertThat(count("SELECT COUNT(*) FROM trips WHERE status = 'OPEN' AND seats_available = 0")).isZero();
        assertThat(count("SELECT COUNT(*) FROM trips WHERE status = 'FULL' AND seats_available <> 0")).isZero();
    }

    @Test
    void tripStatusesMatchDepartureDates() {
        assertThat(count("""
                SELECT COUNT(*) FROM trips
                WHERE status IN ('OPEN', 'FULL') AND departure_at <= CURRENT_TIMESTAMP
                """)).isZero();
        assertThat(count("""
                SELECT COUNT(*) FROM trips
                WHERE status = 'COMPLETED' AND departure_at > CURRENT_TIMESTAMP
                """)).isZero();
    }

    @Test
    void bookingsRespectBookingRules() {
        // Rule 2: nobody books their own trip. Rule 6: a cancelled trip has no confirmed booking.
        assertThat(count("""
                SELECT COUNT(*) FROM bookings b JOIN trips t ON t.id = b.trip_id
                WHERE b.passenger_id = t.driver_id
                """)).isZero();
        assertThat(count("""
                SELECT COUNT(*) FROM bookings b JOIN trips t ON t.id = b.trip_id
                WHERE t.status = 'CANCELLED' AND b.status = 'CONFIRMED'
                """)).isZero();
        assertThat(count("""
                SELECT COUNT(*) FROM bookings
                WHERE (status = 'CANCELLED') <> (cancelled_at IS NOT NULL)
                """)).isZero();
    }

    @Test
    void messagesAreWrittenByTripParticipants() {
        // Rule 5: only the driver and confirmed passengers take part in a trip discussion.
        assertThat(count("""
                SELECT COUNT(*) FROM messages m JOIN trips t ON t.id = m.trip_id
                WHERE m.sender_id <> t.driver_id
                  AND NOT EXISTS (SELECT 1 FROM bookings b WHERE b.trip_id = m.trip_id
                        AND b.passenger_id = m.sender_id AND b.status = 'CONFIRMED')
                """)).isZero();
    }

    @Test
    void ratingsAreOnlyBetweenPeopleWhoTravelledTogether() {
        // Rule 7: completed trip, between the driver and a confirmed passenger, in either direction.
        assertThat(count("""
                SELECT COUNT(*) FROM ratings r JOIN trips t ON t.id = r.trip_id
                WHERE t.status <> 'COMPLETED'
                   OR NOT (
                        (r.rated_id = t.driver_id AND EXISTS (SELECT 1 FROM bookings b
                            WHERE b.trip_id = t.id AND b.passenger_id = r.rater_id AND b.status = 'CONFIRMED'))
                     OR (r.rater_id = t.driver_id AND EXISTS (SELECT 1 FROM bookings b
                            WHERE b.trip_id = t.id AND b.passenger_id = r.rated_id AND b.status = 'CONFIRMED')))
                """)).isZero();
    }

    @Test
    void reloadingTheScriptDoesNotDuplicateData() throws Exception {
        Map<String, Object> before = tableCounts();
        String sql = new ClassPathResource("db/demo/R__demo_data.sql").getContentAsString(StandardCharsets.UTF_8);
        // Same conditions as Flyway: the whole script in one transaction (its temp tables are dropped on commit).
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute(sql);
            connection.commit();
        }
        assertThat(tableCounts()).isEqualTo(before);
    }

    private Map<String, Object> tableCounts() {
        return jdbcTemplate.queryForMap("""
                SELECT (SELECT COUNT(*) FROM users) AS users, (SELECT COUNT(*) FROM trips) AS trips,
                       (SELECT COUNT(*) FROM bookings) AS bookings, (SELECT COUNT(*) FROM messages) AS messages,
                       (SELECT COUNT(*) FROM ratings) AS ratings, (SELECT COUNT(*) FROM reports) AS reports
                """);
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
}
