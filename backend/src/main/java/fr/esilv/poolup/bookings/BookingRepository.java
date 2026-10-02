package fr.esilv.poolup.bookings;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Seat counters are changed by single SQL statements that check and update at the same time:
 * PostgreSQL locks the trip row during the update, so two bookings at the same second cannot
 * both take the last seat. The updates clear the persistence context: reload entities afterwards.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Booking with its trip, driver and cities loaded, to build a {@link BookingResponse}. */
    @EntityGraph(attributePaths = {"trip", "trip.driver", "trip.departureCity", "trip.arrivalCity"})
    Optional<Booking> findWithTripById(Long id);

    /** Passenger history, most recent first, cancelled bookings included (rule 6). */
    @EntityGraph(attributePaths = {"trip", "trip.driver", "trip.departureCity", "trip.arrivalCity"})
    List<Booking> findByPassengerIdOrderByCreatedAtDescIdDesc(Long passengerId);

    boolean existsByTripIdAndPassengerIdAndStatus(Long tripId, Long passengerId, BookingStatus status);

    /**
     * Rules 1 and 2: takes the seats only if the trip is open, has enough free seats, has not left
     * and is not driven by the passenger. Returns 0 when the trip cannot be booked.
     * The CASE sees the values before the update: FULL when the last seat is taken.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE trips
            SET seats_available = seats_available - :seats,
                status = CASE WHEN seats_available - :seats = 0 THEN 'FULL' ELSE status END
            WHERE id = :tripId
              AND status = 'OPEN'
              AND seats_available >= :seats
              AND departure_at > CURRENT_TIMESTAMP
              AND driver_id <> :passengerId
            """, nativeQuery = true)
    int takeSeats(@Param("tripId") Long tripId, @Param("passengerId") Long passengerId, @Param("seats") int seats);

    /** Returns 0 if the booking was already cancelled (e.g. two cancellations at the same time). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE bookings SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP
            WHERE id = :bookingId AND status = 'CONFIRMED'
            """, nativeQuery = true)
    int cancelIfConfirmed(@Param("bookingId") Long bookingId);

    /** Gives the seats back: a FULL trip is OPEN again. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE trips
            SET seats_available = seats_available + :seats,
                status = CASE WHEN status = 'FULL' THEN 'OPEN' ELSE status END
            WHERE id = :tripId AND status IN ('OPEN', 'FULL')
            """, nativeQuery = true)
    int releaseSeats(@Param("tripId") Long tripId, @Param("seats") int seats);
}
