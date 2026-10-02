package fr.esilv.poolup.ratings;

public record UserRatingSummary(Long userId, long totalRatings, double averageScore) {
}
