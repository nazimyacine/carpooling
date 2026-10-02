package fr.esilv.poolup.bookings;

/** Stored as text in {@code bookings.status}. A cancelled booking stays in the passenger's history. */
public enum BookingStatus {
    CONFIRMED,
    CANCELLED
}
