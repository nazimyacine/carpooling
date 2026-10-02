package fr.esilv.poolup.bookings;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.esilv.poolup.common.ApiException;
import fr.esilv.poolup.trips.Trip;
import fr.esilv.poolup.trips.TripRepository;
import fr.esilv.poolup.trips.TripStatus;
import fr.esilv.poolup.users.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;

    /**
     * Rules 1 and 2. The checks below only give a clear error message; the real guard is the atomic
     * {@link BookingRepository#takeSeats} (0 row = 409), plus the unique index "one confirmed booking per
     * passenger and trip" (409 too). Any error rolls back the whole transaction, seats included.
     */
    @Transactional
    public BookingResponse book(Long tripId, Long passengerId, int seats) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> ApiException.notFound("Trajet introuvable."));
        if (Objects.equals(trip.getDriver().getId(), passengerId)) {
            throw ApiException.forbidden("Vous ne pouvez pas réserver votre propre trajet.");
        }
        checkBookable(trip, seats);
        if (bookingRepository.existsByTripIdAndPassengerIdAndStatus(tripId, passengerId, BookingStatus.CONFIRMED)) {
            throw ApiException.conflict("Vous avez déjà une réservation sur ce trajet.");
        }

        if (bookingRepository.takeSeats(tripId, passengerId, seats) == 0) {
            // The trip changed between the checks and the update (another booking took the seats...)
            throw ApiException.conflict("Plus assez de places disponibles sur ce trajet.");
        }
        Booking booking = new Booking(tripRepository.getReferenceById(tripId),
                userRepository.getReferenceById(passengerId), seats);
        bookingRepository.saveAndFlush(booking);
        return loadResponse(booking.getId());
    }

    /** Passenger history: confirmed and cancelled bookings, with the trip and its status. */
    @Transactional(readOnly = true)
    public List<BookingResponse> listPassengerBookings(Long passengerId) {
        return bookingRepository.findByPassengerIdOrderByCreatedAtDescIdDesc(passengerId).stream()
                .map(BookingResponse::from)
                .toList();
    }

    /**
     * The passenger cancels before departure: the seats are given back and a FULL trip is OPEN again.
     * The trip row is locked first, in the same order as the driver's trip cancellation (trip, then
     * bookings), so the two can never block each other.
     */
    @Transactional
    public BookingResponse cancel(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Réservation introuvable."));
        // Rule 4: only the passenger who booked
        if (!Objects.equals(booking.getPassenger().getId(), userId)) {
            throw ApiException.forbidden("Seul le passager peut annuler sa réservation.");
        }
        Trip trip = tripRepository.findByIdForUpdate(booking.getTrip().getId())
                .orElseThrow(() -> ApiException.notFound("Trajet introuvable."));
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.conflict("Cette réservation est déjà annulée.");
        }
        if (!trip.getStatus().isActive() || !trip.getDepartureAt().isAfter(Instant.now())) {
            throw ApiException.conflict("Ce trajet est déjà parti ou terminé : la réservation ne peut plus être annulée.");
        }
        if (bookingRepository.cancelIfConfirmed(bookingId) == 0) {
            throw ApiException.conflict("Cette réservation est déjà annulée.");
        }
        bookingRepository.releaseSeats(trip.getId(), booking.getSeats());
        return loadResponse(bookingId);
    }

    private static void checkBookable(Trip trip, int seats) {
        if (trip.getStatus() == TripStatus.CANCELLED) {
            throw ApiException.conflict("Ce trajet a été annulé.");
        }
        if (trip.getStatus() == TripStatus.COMPLETED || !trip.getDepartureAt().isAfter(Instant.now())) {
            throw ApiException.conflict("Ce trajet est déjà parti.");
        }
        if (trip.getStatus() == TripStatus.FULL) {
            throw ApiException.conflict("Ce trajet est complet.");
        }
        if (trip.getSeatsAvailable() < seats) {
            throw ApiException.conflict("Il ne reste que " + trip.getSeatsAvailable() + " place(s) sur ce trajet.");
        }
    }

    private BookingResponse loadResponse(Long bookingId) {
        return bookingRepository.findWithTripById(bookingId)
                .map(BookingResponse::from)
                .orElseThrow(() -> ApiException.notFound("Réservation introuvable."));
    }
}
