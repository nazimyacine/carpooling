package fr.esilv.poolup.trips;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of the publish and edit requests. "Departure and arrival are different cities" is checked
 * by {@link TripService} (it involves two fields). {@code departureAt} is an ISO-8601 instant,
 * e.g. {@code 2026-10-03T06:15:00Z}.
 */
public record TripRequest(
        @NotNull Long departureCityId,
        @NotNull Long arrivalCityId,
        @NotBlank @Size(max = 500) String meetingPoint,
        @NotNull @Future Instant departureAt,
        @NotNull @Min(1) @Max(8) Integer seatsTotal,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal pricePerSeat,
        @Size(max = 2000) String description) {
}
