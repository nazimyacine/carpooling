package fr.esilv.poolup.auth;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import fr.esilv.poolup.users.UserRepository;
import fr.esilv.poolup.users.UserStatus;

/**
 * Stateless API: every request carries its JWT in {@code Authorization: Bearer ...},
 * checked by Spring Security (signature + expiry) without any database access.
 */
@Configuration
public class SecurityConfig {

    /** HMAC-SHA256 needs a key of at least 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey jwtKey;
    private final ObjectMapper objectMapper;

    public SecurityConfig(@Value("${poolup.jwt.secret}") String secret, ObjectMapper objectMapper) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("poolup.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        this.jwtKey = new SecretKeySpec(bytes, "HmacSHA256");
        this.objectMapper = objectMapper;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
        http
                // No cookie or session: the token is sent explicitly, so CSRF protection is not needed
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, ex) -> {
                            bearerEntryPoint.commence(request, response, ex); // WWW-Authenticate header
                            writeProblem(request, response, HttpStatus.UNAUTHORIZED, "Authentification requise.");
                        })
                        .accessDeniedHandler((request, response, ex) ->
                                writeProblem(request, response, HttpStatus.FORBIDDEN, "Accès refusé.")))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> {
                            bearerEntryPoint.commence(request, response, ex);
                            writeProblem(request, response, HttpStatus.UNAUTHORIZED, "Authentification requise.");
                        })
                        .accessDeniedHandler((request, response, ex) ->
                                writeProblem(request, response, HttpStatus.FORBIDDEN, "Accès refusé.")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    /**
     * Signature and expiry, then the account must still be ACTIVE: a token issued before a suspension
     * is refused (401) at the next request instead of staying valid until it expires.
     */
    @Bean
    JwtDecoder jwtDecoder(UserRepository userRepository) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(), activeAccountValidator(userRepository)));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> activeAccountValidator(UserRepository userRepository) {
        OAuth2Error inactive = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Compte suspendu ou supprimé.", null);
        return jwt -> {
            boolean active;
            try {
                active = userRepository.existsByIdAndStatus(Long.valueOf(jwt.getSubject()), UserStatus.ACTIVE);
            } catch (NumberFormatException ex) {
                active = false;
            }
            return active ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(inactive);
        };
    }

    /** The "role" claim (USER or ADMIN) becomes the authority ROLE_USER or ROLE_ADMIN. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtService.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /** Same error format as {@link fr.esilv.poolup.common.GlobalExceptionHandler}, for errors raised by the filters. */
    private void writeProblem(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
