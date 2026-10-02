package fr.esilv.poolup.admin;

import java.time.Instant;

import fr.esilv.poolup.users.Role;
import fr.esilv.poolup.users.User;
import fr.esilv.poolup.users.UserStatus;

/** Account as seen by an administrator: includes the status and creation date, never the password hash. */
public record AdminUserResponse(Long id, String email, String firstName, String lastName, String carModel,
        Role role, UserStatus status, Instant createdAt) {

    static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getCarModel(), user.getRole(), user.getStatus(), user.getCreatedAt());
    }
}
