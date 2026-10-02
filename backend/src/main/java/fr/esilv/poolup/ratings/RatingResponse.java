package fr.esilv.poolup.ratings;

import java.time.Instant;

public record RatingResponse(
        Long id,
        Long tripId,
        Long raterId,
        Long ratedId,
        Integer score,
        String comment,
        Instant createdAt) {

    public static RatingResponse from(Rating rating) {
        return new RatingResponse(
                rating.getId(),
                rating.getTrip().getId(),
                rating.getRater().getId(),
                rating.getRated().getId(),
                rating.getScore(),
                rating.getComment(),
                rating.getCreatedAt());
    }
}
