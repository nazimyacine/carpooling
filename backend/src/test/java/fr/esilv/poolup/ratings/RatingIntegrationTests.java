package fr.esilv.poolup.ratings;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import fr.esilv.poolup.TestcontainersConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RatingIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void ratingCreationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/trips/1/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":5,\"ratedUserId\":2}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownTripReturnsNotFoundWhenCreatingRating() throws Exception {
        String token = registerAndLogin("rating-unknown-" + UUID.randomUUID() + "@test.poolup.fr");

        mockMvc.perform(post("/api/trips/999999/ratings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\":4,\"ratedUserId\":2}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void userRatingsSummaryReturnsZeroesForUnknownUser() throws Exception {
        String token = registerAndLogin("rating-summary-" + UUID.randomUUID() + "@test.poolup.fr");

        mockMvc.perform(get("/api/users/999999/ratings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(999999))
                .andExpect(jsonPath("$.totalRatings").value(0))
                .andExpect(jsonPath("$.averageScore").value(0.0));
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"motdepasse123","firstName":"Rating","lastName":"User"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        ResultActions login = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"motdepasse123"}
                        """.formatted(email)));

        login.andExpect(status().isOk());
        return JsonPath.read(login.andReturn().getResponse().getContentAsString(), "$.token");
    }
}
