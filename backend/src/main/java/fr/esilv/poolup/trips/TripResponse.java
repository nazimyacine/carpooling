package fr.esilv.poolup.trips;

import java.math.BigDecimal;
import java.time.Instant;

import fr.esilv.poolup.cities.CityResponse;
import fr.esilv.poolup.users.User;

/** Trip as shown to any signed-in user. Cities carry their coordinates for the map. */
public record TripResponse(
        Long id,
        Driver driver,
        CityResponse departureCity,
        CityResponse arrivalCity,
        String meetingPoint,
        Instant departureAt,
        int seatsTotal,
        int seatsAvailable,
        int seatsBooked,
        BigDecimal pricePerSeat,
        TripStatus status,
        String description,
        Instant createdAt) {

    /** Only what passengers need to recognise the driver: no email, last name reduced to its initial. */
    public record Driver(Long id, String firstName, String lastNameInitial, String carModel) {

        static Driver from(User user) {
            String lastName = user.getLastName();
            return new Driver(user.getId(), user.getFirstName(),
                    lastName.isEmpty() ? "" : lastName.substring(0, 1), user.getCarModel());
        }
    }

    public static TripResponse from(Trip trip) {
        return new TripResponse(trip.getId(), Driver.from(trip.getDriver()),
                CityResponse.from(trip.getDepartureCity()), CityResponse.from(trip.getArrivalCity()),
                trip.getMeetingPoint(), trip.getDepartureAt(), trip.getSeatsTotal(), trip.getSeatsAvailable(),
                trip.getSeatsBooked(), trip.getPricePerSeat(), trip.getStatus(), trip.getDescription(),
                trip.getCreatedAt());
    }
}
