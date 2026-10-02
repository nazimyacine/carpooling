package fr.esilv.poolup.bookings;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /** 201, or 409 if the trip is full, cancelled, departed or already booked by this passenger. */
    @PostMapping("/api/trips/{tripId}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(@AuthenticationPrincipal Jwt jwt, @PathVariable Long tripId,
            @Valid @RequestBody BookingRequest request) {
        return bookingService.book(tripId, userId(jwt), request.seats());
    }

    /** "Mes réservations": confirmed and cancelled bookings of the signed-in passenger. */
    @GetMapping("/api/bookings/mine")
    public List<BookingResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.listPassengerBookings(userId(jwt));
    }

    @PostMapping("/api/bookings/{id}/cancel")
    public BookingResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return bookingService.cancel(id, userId(jwt));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
