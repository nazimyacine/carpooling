package fr.esilv.poolup.users;

/**
 * Account role, stored as text in {@code users.role}. Passenger / driver is not a role:
 * it is a display mode chosen in the front end.
 */
public enum Role {
    USER,
    ADMIN
}
