package fr.esilv.poolup.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Update the currently authenticated user's public profile. */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 150) String carModel) {
}
