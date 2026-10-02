package fr.esilv.poolup.users;

/** Account status, stored as text in {@code users.status}. A suspended account can no longer log in. */
public enum UserStatus {
    ACTIVE,
    SUSPENDED
}
