package fr.esilv.poolup.trips;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TripRepository extends JpaRepository<Trip, Long>, JpaSpecificationExecutor<Trip> {

    /** Trip with its driver and cities loaded, to build a {@link TripResponse} without extra queries. */
    @EntityGraph(attributePaths = {"driver", "departureCity", "arrivalCity"})
    Optional<Trip> findWithDetailsById(Long id);

    /**
     * Locks the row ({@code SELECT ... FOR UPDATE}) until the end of the transaction: a booking made
     * at the same moment waits, then sees the new seat counters. Without the lock, a change of
     * {@code seats_total} could overwrite a seat taken in between.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trip t WHERE t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"driver", "departureCity", "arrivalCity"})
    List<Trip> findByDriverIdOrderByDepartureAtAscIdAsc(Long driverId);

    /**
     * Search with the filters built by {@link TripSearch}; driver and cities are loaded in the same query.
     */
    @Override
    @EntityGraph(attributePaths = {"driver", "departureCity", "arrivalCity"})
    List<Trip> findAll(Specification<Trip> spec, Sort sort);

    /** Rule 6: the bookings of a cancelled trip are cancelled in the same transaction. */
    @Modifying
    @Query(value = """
            UPDATE bookings SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP
            WHERE trip_id = :tripId AND status = 'CONFIRMED'
            """, nativeQuery = true)
    int cancelConfirmedBookings(@Param("tripId") Long tripId);

    /** Nightly job: trips whose departure time has passed are done. */
    @Modifying
    @Query("""
            UPDATE Trip t SET t.status = fr.esilv.poolup.trips.TripStatus.COMPLETED
            WHERE t.status IN (fr.esilv.poolup.trips.TripStatus.OPEN, fr.esilv.poolup.trips.TripStatus.FULL)
              AND t.departureAt <= :now
            """)
    int completeDepartedTrips(@Param("now") Instant now);
}
