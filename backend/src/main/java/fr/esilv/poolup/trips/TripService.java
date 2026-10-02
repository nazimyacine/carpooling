package fr.esilv.poolup.trips;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.esilv.poolup.cities.City;
import fr.esilv.poolup.cities.CityRepository;
import fr.esilv.poolup.common.ApiException;
import fr.esilv.poolup.users.User;
import fr.esilv.poolup.users.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TripService {

    /** A search date is a calendar day in France, whatever the server time zone. */
    static final ZoneId SEARCH_ZONE = ZoneId.of("Europe/Paris");

    private final TripRepository tripRepository;
    private final CityRepository cityRepository;
    private final UserRepository userRepository;

    @Transactional
    public TripResponse publish(Long driverId, TripRequest request) {
        User driver = userRepository.findById(driverId)
                .orElseThrow(() -> ApiException.unauthorized("Compte introuvable."));
        checkDifferentCities(request);
        Trip trip = new Trip(driver, findCity(request.departureCityId()), findCity(request.arrivalCityId()),
                request.meetingPoint().trim(), request.departureAt(), request.seatsTotal(),
                request.pricePerSeat(), normalizeDescription(request.description()));
        return TripResponse.from(tripRepository.save(trip));
    }

    /**
     * Trips that can still be seen in a search: not cancelled, not completed, not yet departed.
     * Full trips are included unless {@code minSeats} asks for free seats.
     */
    @Transactional(readOnly = true)
    public List<TripResponse> search(Long departureCityId, Long arrivalCityId, LocalDate date, Integer minSeats) {
        Instant from = Instant.now();
        Instant to = null;
        if (date != null) {
            Instant startOfDay = date.atStartOfDay(SEARCH_ZONE).toInstant();
            from = startOfDay.isAfter(from) ? startOfDay : from;
            to = date.plusDays(1).atStartOfDay(SEARCH_ZONE).toInstant();
        }
        Specification<Trip> filters = TripSearch.matching(departureCityId, arrivalCityId, from, to,
                minSeats == null ? 0 : minSeats);
        return tripRepository.findAll(filters, Sort.by("departureAt", "id")).stream()
                .map(TripResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TripResponse get(Long tripId) {
        return tripRepository.findWithDetailsById(tripId)
                .map(TripResponse::from)
                .orElseThrow(TripService::tripNotFound);
    }

    /** Driver dashboard: all the driver's trips, whatever their status. */
    @Transactional(readOnly = true)
    public List<TripResponse> listDriverTrips(Long driverId) {
        return tripRepository.findByDriverIdOrderByDepartureAtAscIdAsc(driverId).stream()
                .map(TripResponse::from)
                .toList();
    }

    @Transactional
    public TripResponse update(Long tripId, Long userId, TripRequest request) {
        checkDifferentCities(request);
        Trip trip = lockEditableTrip(tripId, userId);
        // Rule 3: the driver cannot take back seats already booked
        if (request.seatsTotal() < trip.getSeatsBooked()) {
            throw ApiException.conflict("Impossible de proposer " + request.seatsTotal() + " place(s) : "
                    + trip.getSeatsBooked() + " place(s) sont déjà réservées.");
        }
        trip.update(findCity(request.departureCityId()), findCity(request.arrivalCityId()),
                request.meetingPoint().trim(), request.departureAt(), request.seatsTotal(),
                request.pricePerSeat(), normalizeDescription(request.description()));
        return TripResponse.from(trip);
    }

    /** Rule 6: cancelling a trip cancels all its bookings, in the same transaction. */
    @Transactional
    public TripResponse cancel(Long tripId, Long userId) {
        Trip trip = lockEditableTrip(tripId, userId);
        tripRepository.cancelConfirmedBookings(trip.getId());
        trip.cancel();
        return TripResponse.from(trip);
    }

    /** Called by {@link TripCompletionJob}; returns the number of trips marked COMPLETED. */
    @Transactional
    public int completeDepartedTrips() {
        return tripRepository.completeDepartedTrips(Instant.now());
    }

    /** Locked trip that the user drives and that has not left yet. */
    private Trip lockEditableTrip(Long tripId, Long userId) {
        Trip trip = tripRepository.findByIdForUpdate(tripId).orElseThrow(TripService::tripNotFound);
        // Rule 4: always checked by the server, even if the front hides the button
        if (!Objects.equals(trip.getDriver().getId(), userId)) {
            throw ApiException.forbidden("Seul le conducteur peut modifier ou annuler ce trajet.");
        }
        if (!trip.getStatus().isActive()) {
            String state = trip.getStatus() == TripStatus.CANCELLED ? "annulé" : "terminé";
            throw ApiException.conflict("Ce trajet est " + state + " : il ne peut plus être modifié.");
        }
        if (!trip.getDepartureAt().isAfter(Instant.now())) {
            throw ApiException.conflict("Ce trajet est déjà parti : il ne peut plus être modifié.");
        }
        return trip;
    }

    private static void checkDifferentCities(TripRequest request) {
        if (request.departureCityId().equals(request.arrivalCityId())) {
            throw ApiException.badRequest("Les villes de départ et d'arrivée doivent être différentes.");
        }
    }

    private City findCity(Long cityId) {
        return cityRepository.findById(cityId)
                .orElseThrow(() -> ApiException.badRequest("Ville inconnue : " + cityId + "."));
    }

    private static String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private static ApiException tripNotFound() {
        return ApiException.notFound("Trajet introuvable.");
    }
}
