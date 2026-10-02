package fr.esilv.poolup.trips;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

/**
 * Search filters as a JPA {@link Specification}: a filter left empty is simply not added to the
 * WHERE clause (a "{@code :param IS NULL OR ...}" query fails on PostgreSQL, which cannot type a null parameter).
 */
final class TripSearch {

    private TripSearch() {
    }

    /**
     * OPEN or FULL trips departing in [from, to) with at least {@code minSeats} free seats;
     * {@code to}, {@code departureCityId} and {@code arrivalCityId} are optional.
     */
    static Specification<Trip> matching(Long departureCityId, Long arrivalCityId, Instant from, Instant to,
            int minSeats) {
        return (trip, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            filters.add(trip.get("status").in(TripStatus.OPEN, TripStatus.FULL));
            filters.add(cb.greaterThanOrEqualTo(trip.get("departureAt"), from));
            if (to != null) {
                filters.add(cb.lessThan(trip.get("departureAt"), to));
            }
            if (minSeats > 0) {
                filters.add(cb.greaterThanOrEqualTo(trip.get("seatsAvailable"), minSeats));
            }
            if (departureCityId != null) {
                filters.add(cb.equal(trip.get("departureCity").get("id"), departureCityId));
            }
            if (arrivalCityId != null) {
                filters.add(cb.equal(trip.get("arrivalCity").get("id"), arrivalCityId));
            }
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }
}
