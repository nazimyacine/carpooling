package fr.esilv.poolup.auth.dto;

import java.time.Instant;

import fr.esilv.poolup.users.UserResponse;

/** Returned after a register or login: the front stores the token and sends it with every call. */
public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
}
