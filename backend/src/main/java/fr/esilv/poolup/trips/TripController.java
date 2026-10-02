package fr.esilv.poolup.trips;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse publish(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TripRequest request) {
        return tripService.publish(userId(jwt), request);
    }

    /** Search: every filter is optional; {@code date} is a day in France, e.g. {@code 2026-10-03}. */
    @GetMapping
    public List<TripResponse> search(
            @RequestParam(required = false) Long departureCityId,
            @RequestParam(required = false) Long arrivalCityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @Min(1) @Max(8) Integer seats) {
        return tripService.search(departureCityId, arrivalCityId, date, seats);
    }

    /** Trips published by the signed-in user (driver dashboard). */
    @GetMapping("/mine")
    public List<TripResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return tripService.listDriverTrips(userId(jwt));
    }

    @GetMapping("/{id}")
    public TripResponse get(@PathVariable Long id) {
        return tripService.get(id);
    }

    @PutMapping("/{id}")
    public TripResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody TripRequest request) {
        return tripService.update(id, userId(jwt), request);
    }

    @PostMapping("/{id}/cancel")
    public TripResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return tripService.cancel(id, userId(jwt));
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
