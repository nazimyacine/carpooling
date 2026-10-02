package fr.esilv.poolup.ratings;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping("/trips/{tripId}/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public RatingResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long tripId,
            @Valid @RequestBody RatingRequest request) {
        return ratingService.createRating(tripId, userId(jwt), request);
    }

    @GetMapping("/trips/{tripId}/ratings")
    public List<RatingResponse> listForTrip(@PathVariable Long tripId) {
        return ratingService.listTripRatings(tripId);
    }

    @GetMapping("/users/{userId}/ratings")
    public UserRatingSummary summary(@PathVariable Long userId) {
        return ratingService.getUserSummary(userId);
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
