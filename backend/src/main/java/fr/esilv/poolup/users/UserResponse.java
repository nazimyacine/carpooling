package fr.esilv.poolup.users;

/** Public view of an account: never exposes the password hash. */
public record UserResponse(Long id, String email, String firstName, String lastName, String carModel, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getCarModel(), user.getRole());
    }
}
