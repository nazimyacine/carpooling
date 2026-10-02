package fr.esilv.poolup.bookings;

import java.math.BigDecimal;
import java.time.Instant;

import fr.esilv.poolup.trips.TripResponse;

/** A booking with its trip: the trip status shows the passenger that the driver cancelled (rule 6). */
public record BookingResponse(
        Long id,
        int seats,
        BigDecimal totalPrice,
        BookingStatus status,
        Instant createdAt,
        Instant cancelledAt,
        TripResponse trip) {

    public static BookingResponse from(Booking booking) {
        TripResponse trip = TripResponse.from(booking.getTrip());
        return new BookingResponse(booking.getId(), booking.getSeats(),
                trip.pricePerSeat().multiply(BigDecimal.valueOf(booking.getSeats())),
                booking.getStatus(), booking.getCreatedAt(), booking.getCancelledAt(), trip);
    }
}
