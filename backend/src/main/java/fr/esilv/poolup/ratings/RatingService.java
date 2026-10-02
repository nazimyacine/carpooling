package fr.esilv.poolup.ratings;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.esilv.poolup.bookings.BookingRepository;
import fr.esilv.poolup.bookings.BookingStatus;
import fr.esilv.poolup.common.ApiException;
import fr.esilv.poolup.trips.Trip;
import fr.esilv.poolup.trips.TripRepository;
import fr.esilv.poolup.trips.TripStatus;
import fr.esilv.poolup.users.User;
import fr.esilv.poolup.users.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final TripRepository tripRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Transactional
    public RatingResponse createRating(Long tripId, Long raterId, RatingRequest request) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> ApiException.notFound("Trajet introuvable."));

        if (trip.getStatus() != TripStatus.COMPLETED) {
            throw ApiException.conflict("La note n'est possible que pour un trajet terminé.");
        }

        User rater = userRepository.findById(raterId)
                .orElseThrow(() -> ApiException.unauthorized("Compte introuvable."));
        User rated = userRepository.findById(request.ratedUserId())
                .orElseThrow(() -> ApiException.notFound("Utilisateur cible introuvable."));

        if (Objects.equals(rater.getId(), rated.getId())) {
            throw ApiException.conflict("On ne peut pas se noter soi-même.");
        }

        if (ratingRepository.existsByTripIdAndRaterIdAndRatedId(tripId, raterId, rated.getId())) {
            throw ApiException.conflict("Vous avez déjà noté cette personne pour ce trajet.");
        }

        validateCanRate(trip, raterId, rated.getId());

        Rating rating = new Rating(trip, rater, rated, request.score(), request.comment());
        return RatingResponse.from(ratingRepository.saveAndFlush(rating));
    }

    @Transactional(readOnly = true)
    public List<RatingResponse> listTripRatings(Long tripId) {
        return ratingRepository.findByTripIdOrderByCreatedAtDesc(tripId).stream()
                .map(RatingResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserRatingSummary getUserSummary(Long userId) {
        long total = ratingRepository.countByRatedId(userId);
        Double average = ratingRepository.averageByRatedId(userId);
        return new UserRatingSummary(userId, total, average == null ? 0.0 : average);
    }

    private void validateCanRate(Trip trip, Long raterId, Long ratedId) {
        boolean raterIsDriver = Objects.equals(trip.getDriver().getId(), raterId);
        boolean raterIsPassenger = bookingRepository.existsByTripIdAndPassengerIdAndStatus(
                trip.getId(), raterId, BookingStatus.CONFIRMED);

        if (raterIsDriver && bookingRepository.existsByTripIdAndPassengerIdAndStatus(
                trip.getId(), ratedId, BookingStatus.CONFIRMED)) {
            return;
        }

        if (raterIsPassenger && Objects.equals(trip.getDriver().getId(), ratedId)) {
            return;
        }

        throw ApiException.forbidden("Vous n'avez pas voyagé avec cette personne sur ce trajet.");
    }
}
