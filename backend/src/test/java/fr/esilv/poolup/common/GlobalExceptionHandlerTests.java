package fr.esilv.poolup.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every error leaves the API with the same format and the expected HTTP status.
 * Plain MockMvc without Spring context: no database or Docker needed.
 */
class GlobalExceptionHandlerTests {

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void apiExceptionKeepsItsStatusAndMessage() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Plus assez de places."))
                .andExpect(jsonPath("$.instance").value("/test/conflict"));
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Trajet introuvable."));
    }

    @Test
    void invalidFieldsGive400WithTheListOfFields() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\", \"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void unreadableJsonGives400() throws Exception {
        mockMvc.perform(post("/test/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void databaseConstraintViolationGives409() throws Exception {
        mockMvc.perform(get("/test/constraint"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void accessDeniedGives403() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void unexpectedErrorGives500WithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/test/crash"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Erreur interne du serveur."))
                .andExpect(content().string(not(containsString("secret"))));
    }

    record ValidatedBody(@Email @NotBlank String email, @NotBlank String name) {
    }

    // Nested in a test class: Spring Boot's TestTypeExcludeFilter keeps it out of the other tests' component scan.
    @RestController
    @RequestMapping("/test")
    static class FailingController {

        @GetMapping("/conflict")
        void conflict() {
            throw ApiException.conflict("Plus assez de places.");
        }

        @GetMapping("/not-found")
        void notFound() {
            throw ApiException.notFound("Trajet introuvable.");
        }

        @PostMapping("/validated")
        void validated(@Valid @RequestBody ValidatedBody body) {
        }

        @GetMapping("/constraint")
        void constraint() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("not the owner");
        }

        @GetMapping("/crash")
        void crash() {
            throw new IllegalStateException("secret internal detail");
        }
    }
}
