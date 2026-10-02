package fr.esilv.poolup.bookings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Number of seats to book on the trip given in the URL. */
public record BookingRequest(@NotNull @Min(1) @Max(8) Integer seats) {
}
