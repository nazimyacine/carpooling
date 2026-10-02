package fr.esilv.poolup.trips;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import fr.esilv.poolup.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/** Step 3.2: the nightly job moves departed OPEN/FULL trips to COMPLETED and touches nothing else. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TripCompletionJobTests {

    @Autowired
    private TripCompletionJob job;

    @Autowired
    private ScheduledTaskHolder scheduledTasks;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void departedOpenAndFullTripsBecomeCompleted() {
        long driver = insertDriver();
        long departedOpen = insertTrip(driver, "OPEN", "-2 hours", 3);
        long departedFull = insertTrip(driver, "FULL", "-1 day", 0);
        long departedCancelled = insertTrip(driver, "CANCELLED", "-1 day", 3);
        long upcomingOpen = insertTrip(driver, "OPEN", "+2 hours", 3);
        long upcomingFull = insertTrip(driver, "FULL", "+1 day", 0);

        job.completeDepartedTrips();

        assertThat(status(departedOpen)).isEqualTo("COMPLETED");
        assertThat(status(departedFull)).isEqualTo("COMPLETED");
        assertThat(status(departedCancelled)).isEqualTo("CANCELLED");
        assertThat(status(upcomingOpen)).isEqualTo("OPEN");
        assertThat(status(upcomingFull)).isEqualTo("FULL");
    }

    @Test
    void runningTheJobTwiceChangesNothingMore() {
        long trip = insertTrip(insertDriver(), "OPEN", "-3 hours", 3);
        job.completeDepartedTrips();
        job.completeDepartedTrips();
        assertThat(status(trip)).isEqualTo("COMPLETED");
    }

    @Test
    void jobIsScheduledEveryNightAtThreeInParis() {
        assertThat(scheduledTasks.getScheduledTasks())
                .map(scheduled -> scheduled.getTask())
                .filteredOn(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .anySatisfy(task -> {
                    assertThat(task.getExpression()).isEqualTo("0 0 3 * * *");
                    assertThat(task.toString()).contains("TripCompletionJob.completeDepartedTrips");
                });
    }

    private long insertDriver() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (email, password_hash, first_name, last_name)
                VALUES (?, 'not-used', 'Job', 'Driver') RETURNING id
                """, Long.class, "job-" + UUID.randomUUID() + "@test.poolup.fr");
    }

    /** Inserted in SQL: the API refuses a departure in the past. */
    private long insertTrip(long driverId, String status, String departureOffset, int seatsAvailable) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO trips (driver_id, departure_city_id, arrival_city_id, meeting_point, departure_at,
                                   seats_total, seats_available, price_per_seat, status)
                VALUES (?, (SELECT id FROM cities WHERE name = 'Paris'), (SELECT id FROM cities WHERE name = 'Lille'),
                        'Gare du Nord', now() + CAST(? AS interval), 3, ?, 15, ?)
                RETURNING id
                """, Long.class, driverId, departureOffset, seatsAvailable, status);
    }

    private String status(long tripId) {
        return jdbcTemplate.queryForObject("SELECT status FROM trips WHERE id = ?", String.class, tripId);
    }
}
