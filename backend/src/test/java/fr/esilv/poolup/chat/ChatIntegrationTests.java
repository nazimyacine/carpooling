package fr.esilv.poolup.chat;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChatIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void chatMessagesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/trips/1/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownTripReturnsNotFoundForChatMessages() throws Exception {
        String token = registerAndLogin("chat-not-found-" + UUID.randomUUID() + "@test.poolup.fr");

        mockMvc.perform(get("/api/trips/999999/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void sendingChatMessageOnUnknownTripFailsWithNotFound() throws Exception {
        String token = registerAndLogin("chat-send-" + UUID.randomUUID() + "@test.poolup.fr");

        mockMvc.perform(post("/api/trips/999999/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Bonjour\"}"))
                .andExpect(status().isNotFound());
    }

    private String registerAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"motdepasse123","firstName":"Chat","lastName":"User"}
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
