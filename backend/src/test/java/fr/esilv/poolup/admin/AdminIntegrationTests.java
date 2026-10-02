package fr.esilv.poolup.admin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import fr.esilv.poolup.TestcontainersConfiguration;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reports (any user) and moderation (admin only): reports, suspensions, message deletion. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminIntegrationTests {

    private static final String PASSWORD = "motdepasse123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---------------------------------------------------------------- reporting (any user)

    @Test
    void participantReportsAMessageAndAdminSeesItWithItsContext() throws Exception {
        Discussion discussion = discussion("Profitez de -30 % sur nos cours de conduite !");
        Account admin = admin();

        long reportId = idOf(report(discussion.passenger(), "MESSAGE", discussion.messageId(), "Publicité")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.reason").value("Publicité"))
                .andExpect(jsonPath("$.target").doesNotExist()));

        String path = "$[?(@.id == " + reportId + ")]";
        perform(admin, get("/api/admin/reports?status=OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(path + ".targetType").value("MESSAGE"))
                .andExpect(jsonPath(path + ".reporter.id").value((int) discussion.passenger().id()))
                .andExpect(jsonPath(path + ".reporter.email").value(discussion.passenger().email()))
                .andExpect(jsonPath(path + ".target.exists").value(true))
                .andExpect(jsonPath(path + ".target.content").value("Profitez de -30 % sur nos cours de conduite !"))
                .andExpect(jsonPath(path + ".target.label").value("Message dans la discussion Paris → Lille"))
                .andExpect(jsonPath(path + ".target.tripId").value((int) discussion.tripId()))
                .andExpect(jsonPath(path + ".target.concernedUser.id").value((int) discussion.driver().id()));
    }

    @Test
    void aMessageCanOnlyBeReportedByAParticipantOfTheDiscussion() throws Exception {
        Discussion discussion = discussion("Bonjour");
        // Rule 5: an outsider gets the same 404 as for a missing message, nothing leaks
        report(register(), "MESSAGE", discussion.messageId(), "Spam").andExpect(status().isNotFound());
        report(discussion.passenger(), "MESSAGE", 999999999L, "Spam").andExpect(status().isNotFound());
        report(discussion.driver(), "MESSAGE", discussion.messageId(), "Le mien").andExpect(status().isBadRequest());
    }

    @Test
    void usersAndTripsCanBeReported() throws Exception {
        Account reporter = register();
        Account reported = register();
        long tripId = publishTrip(reported);

        report(reporter, "USER", reported.id(), "Propos insultants").andExpect(status().isCreated());
        report(reporter, "TRIP", tripId, "Prix anormalement élevé").andExpect(status().isCreated());

        report(reporter, "USER", reporter.id(), "Moi").andExpect(status().isBadRequest());
        report(reporter, "USER", 999999999L, "Inconnu").andExpect(status().isNotFound());
        report(reporter, "TRIP", 999999999L, "Inconnu").andExpect(status().isNotFound());
    }

    @Test
    void theSameOpenReportCannotBeSentTwice() throws Exception {
        Account reporter = register();
        Account reported = register();
        report(reporter, "USER", reported.id(), "Insultes").andExpect(status().isCreated());
        report(reporter, "USER", reported.id(), "Encore").andExpect(status().isConflict());
    }

    @Test
    void invalidReportIsRejectedWith400() throws Exception {
        Account reporter = register();
        perform(reporter, post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\": \"USER\", \"targetId\": 1, \"reason\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.reason").exists());
        perform(reporter, post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\": \"RATING\", \"targetId\": 1, \"reason\": \"x\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\": \"USER\", \"targetId\": 1, \"reason\": \"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- admin access

    @Test
    void adminRoutesAreForbiddenToUsersAndAnonymous() throws Exception {
        Account user = register();
        String[] reads = {"/api/admin/reports", "/api/admin/users", "/api/admin/trips"};
        for (String path : reads) {
            perform(user, get(path)).andExpect(status().isForbidden());
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        perform(user, post("/api/admin/users/" + register().id() + "/suspend")).andExpect(status().isForbidden());
        perform(user, post("/api/admin/reports/1/resolve")).andExpect(status().isForbidden());
        perform(user, delete("/api/admin/messages/1")).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- reports handling

    @Test
    void adminResolvesAReportOnce() throws Exception {
        Account admin = admin();
        long reportId = idOf(report(register(), "USER", register().id(), "Insultes"));

        perform(admin, post("/api/admin/reports/" + reportId + "/resolve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        perform(admin, get("/api/admin/reports?status=OPEN"))
                .andExpect(jsonPath("$[*].id", not(hasItem((int) reportId))));
        perform(admin, get("/api/admin/reports?status=RESOLVED"))
                .andExpect(jsonPath("$[*].id", hasItem((int) reportId)));

        perform(admin, post("/api/admin/reports/" + reportId + "/resolve")).andExpect(status().isConflict());
        perform(admin, post("/api/admin/reports/999999999/resolve")).andExpect(status().isNotFound());
    }

    @Test
    void adminDeletesAReportedMessage() throws Exception {
        Discussion discussion = discussion("Message abusif");
        Account admin = admin();
        long reportId = idOf(report(discussion.passenger(), "MESSAGE", discussion.messageId(), "Insulte"));

        perform(admin, delete("/api/admin/messages/" + discussion.messageId())).andExpect(status().isNoContent());

        perform(discussion.driver(), get("/api/trips/" + discussion.tripId() + "/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem((int) discussion.messageId()))));
        String path = "$[?(@.id == " + reportId + ")]";
        perform(admin, get("/api/admin/reports"))
                .andExpect(jsonPath(path + ".target.exists").value(false))
                .andExpect(jsonPath(path + ".target.label").value("Message supprimé"));
        perform(admin, delete("/api/admin/messages/" + discussion.messageId())).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- sanctions

    @Test
    void suspendedAccountLosesAccessAtOnceAndRegainsItWhenReactivated() throws Exception {
        Account admin = admin();
        Account user = register();
        perform(user, get("/api/auth/me")).andExpect(status().isOk());

        perform(admin, post("/api/admin/users/" + user.id() + "/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        // The token issued before the suspension is refused, and a new login is refused too
        perform(user, get("/api/auth/me")).andExpect(status().isUnauthorized());
        login(user.email()).andExpect(status().isForbidden());

        perform(admin, post("/api/admin/users/" + user.id() + "/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        perform(user, get("/api/auth/me")).andExpect(status().isOk());
        login(user.email()).andExpect(status().isOk());
    }

    @Test
    void anAdministratorCannotBeSuspended() throws Exception {
        Account admin = admin();
        Account otherAdmin = admin();
        perform(admin, post("/api/admin/users/" + otherAdmin.id() + "/suspend")).andExpect(status().isConflict());
        perform(admin, post("/api/admin/users/" + admin.id() + "/suspend")).andExpect(status().isConflict());
        perform(admin, post("/api/admin/users/999999999/suspend")).andExpect(status().isNotFound());
    }

    @Test
    void adminListsAndSearchesUsers() throws Exception {
        Account admin = admin();
        Account user = register();

        perform(admin, get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) user.id())))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
        String part = user.email().substring(6, 20).toUpperCase(); // case-insensitive search
        perform(admin, get("/api/admin/users").param("query", part))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(user.email()))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
        // "%" is matched literally, not as a wildcard that would return every account
        perform(admin, get("/api/admin/users").param("query", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void adminListsTripsOfEveryStatus() throws Exception {
        Account admin = admin();
        Account driver = register();
        long cancelled = publishTrip(driver);
        perform(driver, post("/api/trips/" + cancelled + "/cancel")).andExpect(status().isOk());

        perform(admin, get("/api/admin/trips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + cancelled + ")].status").value("CANCELLED"));
    }

    // ---------------------------------------------------------------- helpers

    private record Account(long id, String email, String token) {
    }

    /** A trip Paris -> Lille with one confirmed passenger and one message from the driver. */
    private record Discussion(Account driver, Account passenger, long tripId, long messageId) {
    }

    private Discussion discussion(String driverMessage) throws Exception {
        Account driver = register();
        Account passenger = register();
        long tripId = publishTrip(driver);
        perform(passenger, post("/api/trips/" + tripId + "/bookings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seats\": 1}"))
                .andExpect(status().isCreated());
        long messageId = idOf(perform(driver, post("/api/trips/" + tripId + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"" + driverMessage + "\"}")));
        return new Discussion(driver, passenger, tripId, messageId);
    }

    private long publishTrip(Account driver) throws Exception {
        Instant departure = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        return idOf(perform(driver, post("/api/trips").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"departureCityId": %d, "arrivalCityId": %d, "meetingPoint": "Gare du Nord",
                         "departureAt": "%s", "seatsTotal": 3, "pricePerSeat": 15}
                        """.formatted(city("Paris"), city("Lille"), departure))));
    }

    private ResultActions report(Account reporter, String type, long targetId, String reason) throws Exception {
        return perform(reporter, post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"targetType": "%s", "targetId": %d, "reason": "%s"}
                        """.formatted(type, targetId, reason)));
    }

    private Account register() throws Exception {
        String email = "admin-tests-" + UUID.randomUUID() + "@test.poolup.fr";
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "firstName": "Test", "lastName": "User"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Account(((Number) JsonPath.read(body, "$.user.id")).longValue(), email,
                JsonPath.read(body, "$.token"));
    }

    /** Admins are not created through the API: the role is set in the database, then a new token is issued. */
    private Account admin() throws Exception {
        Account account = register();
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", account.id());
        String body = login(account.email()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Account(account.id(), account.email(), JsonPath.read(body, "$.token"));
    }

    private ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, PASSWORD)));
    }

    private ResultActions perform(Account account, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + account.token()));
    }

    private static long idOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long city(String name) {
        return jdbcTemplate.queryForObject("SELECT id FROM cities WHERE name = ?", Long.class, name);
    }
}
