package fr.esilv.poolup.trips;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import fr.esilv.poolup.cities.City;
import fr.esilv.poolup.users.User;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Table {@code trips} (V1__schema.sql). Never returned by a controller: map it to {@link TripResponse}.
 * Seat counters are only changed through the methods below or atomic SQL updates (bookings),
 * always on a locked row: see {@link TripRepository#findByIdForUpdate}.
 */
@Entity
@Table(name = "trips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false, updatable = false)
    private User driver;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "departure_city_id", nullable = false)
    private City departureCity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "arrival_city_id", nullable = false)
    private City arrivalCity;

    @Column(name = "meeting_point", nullable = false, length = 500)
    private String meetingPoint;

    @Column(name = "departure_at", nullable = false)
    private Instant departureAt;

    @Column(name = "seats_total", nullable = false)
    private int seatsTotal;

    @Column(name = "seats_available", nullable = false)
    private int seatsAvailable;

    @Column(name = "price_per_seat", nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerSeat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status = TripStatus.OPEN;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Trip(User driver, City departureCity, City arrivalCity, String meetingPoint, Instant departureAt,
            int seatsTotal, BigDecimal pricePerSeat, String description) {
        this.driver = driver;
        this.departureCity = departureCity;
        this.arrivalCity = arrivalCity;
        this.meetingPoint = meetingPoint;
        this.departureAt = departureAt;
        this.seatsTotal = seatsTotal;
        this.seatsAvailable = seatsTotal;
        this.pricePerSeat = pricePerSeat;
        this.description = description;
    }

    /** Seats held by confirmed bookings. */
    public int getSeatsBooked() {
        return seatsTotal - seatsAvailable;
    }

    /**
     * Replaces the trip details. The caller has checked that {@code seatsTotal} is not below
     * {@link #getSeatsBooked()} (rule 3); the free seats follow the new total.
     */
    void update(City departureCity, City arrivalCity, String meetingPoint, Instant departureAt,
            int seatsTotal, BigDecimal pricePerSeat, String description) {
        int seatsBooked = getSeatsBooked();
        this.departureCity = departureCity;
        this.arrivalCity = arrivalCity;
        this.meetingPoint = meetingPoint;
        this.departureAt = departureAt;
        this.seatsTotal = seatsTotal;
        this.seatsAvailable = seatsTotal - seatsBooked;
        this.status = seatsAvailable == 0 ? TripStatus.FULL : TripStatus.OPEN;
        this.pricePerSeat = pricePerSeat;
        this.description = description;
    }

    /** All bookings are cancelled with the trip (rule 6), so every seat is free again. */
    void cancel() {
        this.status = TripStatus.CANCELLED;
        this.seatsAvailable = seatsTotal;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
