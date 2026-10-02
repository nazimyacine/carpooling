package fr.esilv.poolup.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fr.esilv.poolup.auth.dto.AuthResponse;
import fr.esilv.poolup.auth.dto.LoginRequest;
import fr.esilv.poolup.auth.dto.RegisterRequest;
import fr.esilv.poolup.common.ApiException;
import fr.esilv.poolup.users.User;
import fr.esilv.poolup.users.UserRepository;
import fr.esilv.poolup.users.UserResponse;
import fr.esilv.poolup.users.UserStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** 409 if the email is taken. Two simultaneous sign-ups: the unique index rejects the second one (409 too). */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw ApiException.badRequest("Mot de passe trop long.");
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Un compte existe déjà avec cet email.");
        }
        User user = new User(email, passwordEncoder.encode(request.password()),
                request.firstName().trim(), request.lastName().trim());
        userRepository.saveAndFlush(user);
        return toResponse(user);
    }

    /**
     * Same 401 for an unknown email and a wrong password, so the answer does not reveal which accounts exist.
     * The suspension is only revealed once the password is correct.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Email ou mot de passe incorrect."));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw ApiException.forbidden("Ce compte est suspendu.");
        }
        return toResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.unauthorized("Compte introuvable."));
    }

    private AuthResponse toResponse(User user) {
        Jwt token = jwtService.issue(user);
        return new AuthResponse(token.getTokenValue(), token.getExpiresAt(), UserResponse.from(user));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
