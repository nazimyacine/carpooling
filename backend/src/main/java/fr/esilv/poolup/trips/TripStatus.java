package fr.esilv.poolup.trips;

/**
 * OPEN -> FULL (last seat booked), FULL -> OPEN (a booking cancelled),
 * OPEN/FULL -> CANCELLED (driver cancels), OPEN/FULL -> COMPLETED (departure passed, nightly job).
 */
public enum TripStatus {
    OPEN,
    FULL,
    COMPLETED,
    CANCELLED;

    /** A trip can still be changed, cancelled or booked only in these states. */
    public boolean isActive() {
        return this == OPEN || this == FULL;
    }
}
