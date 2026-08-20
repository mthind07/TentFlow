package com.mthind.tentflow.api;

import com.jayway.jsonpath.JsonPath;
import com.mthind.tentflow.api.dto.CreateUserAccountRequest;
import com.mthind.tentflow.security.Role;
import com.mthind.tentflow.service.AccountService;
import com.mthind.tentflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TentFlowSecurityIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "Correct-Horse-42";
    private static final String TEST_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final String EXPECTED_ISSUER = "tentflow-test";
    private static final String EXPECTED_AUDIENCE = "tentflow-test-api";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Test
    void registrationHashesPasswordAndLoginIssuesCompleteSignedJwt()
            throws Exception {
        Registration registration = register(
                "Avery Stone",
                "  AVERY.Security@Example.com  ",
                PASSWORD
        );

        MvcResult login = login("avery.security@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.customerId").value(
                        registration.customerId()
                ))
                .andReturn();

        String token = JsonPath.read(
                login.getResponse().getContentAsString(),
                "$.accessToken"
        );
        Jwt jwt = jwtDecoder.decode(token);

        assertEquals("HS256", jwt.getHeaders().get("alg"));

        // Compare the raw claim because the issuer is a local identifier,
        // not necessarily a URL.
        assertEquals(EXPECTED_ISSUER, jwt.getClaimAsString("iss"));
        assertEquals("avery.security@example.com", jwt.getSubject());
        assertEquals(List.of(EXPECTED_AUDIENCE), jwt.getAudience());
        assertEquals(
                registration.userId(),
                ((Number) jwt.getClaim("user_id")).longValue()
        );
        assertEquals(
                registration.customerId(),
                ((Number) jwt.getClaim("customer_id")).longValue()
        );
        assertEquals(
                List.of("CUSTOMER"),
                jwt.getClaimAsStringList("roles")
        );
        assertNotNull(jwt.getId());
        assertFalse(jwt.getId().isBlank());
        assertEquals(
                Duration.ofMinutes(15),
                Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())
        );

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_accounts WHERE id = ?",
                String.class,
                registration.userId()
        );

        assertTrue(passwordHash.startsWith("{bcrypt}"));
        assertFalse(passwordHash.contains(PASSWORD));
        assertFalse(
                login.getResponse()
                        .getContentAsString()
                        .contains("passwordHash")
        );

        mockMvc.perform(get("/api/auth/me").with(bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(
                        "avery.security@example.com"
                ))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void loginFailuresAreGenericAndOversizedUtf8NeverReachesBcrypt()
            throws Exception {
        register("Known User", "known@example.com", PASSWORD);

        MvcResult wrongPassword = login(
                "known@example.com",
                "Wrong-Password-42"
        )
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andReturn();

        MvcResult unknownUser = login(
                "missing@example.com",
                "Wrong-Password-42"
        )
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult oversizedUtf8 = login(
                "known@example.com",
                "🛡️".repeat(19)
        )
                .andExpect(status().isUnauthorized())
                .andReturn();

        String expectedMessage = "Invalid email or password.";

        assertEquals(
                expectedMessage,
                JsonPath.read(
                        wrongPassword.getResponse().getContentAsString(),
                        "$.message"
                )
        );
        assertEquals(
                expectedMessage,
                JsonPath.read(
                        unknownUser.getResponse().getContentAsString(),
                        "$.message"
                )
        );
        assertEquals(
                expectedMessage,
                JsonPath.read(
                        oversizedUtf8.getResponse().getContentAsString(),
                        "$.message"
                )
        );

        Integer failedLogins = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM audit_events
                WHERE action = 'LOGIN' AND outcome = 'FAILED'
                """,
                Integer.class
        );

        assertEquals(3, failedLogins);
    }

    @Test
    void concurrentCaseInsensitiveRegistrationCreatesOneAtomicAccount()
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first = executor.submit(() -> {
                start.await();
                return registerStatus(
                        "Race One",
                        "race@example.com"
                );
            });

            Future<Integer> second = executor.submit(() -> {
                start.await();
                return registerStatus(
                        "Race Two",
                        "RACE@EXAMPLE.COM"
                );
            });

            start.countDown();

            assertEquals(
                    Set.of(201, 409),
                    new HashSet<>(List.of(
                            first.get(15, TimeUnit.SECONDS),
                            second.get(15, TimeUnit.SECONDS)
                    ))
            );

            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM user_accounts "
                            + "WHERE lower(email) = 'race@example.com'",
                    Integer.class
            ));

            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM customers "
                            + "WHERE lower(email) = 'race@example.com'",
                    Integer.class
            ));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void missingTamperedExpiredWrongIssuerAudienceClaimsAndKeyTokensAre401()
            throws Exception {
        Registration customer = register(
                "Token User",
                "token.user@example.com",
                PASSWORD
        );

        String validToken = tokenFor(
                "token.user@example.com",
                PASSWORD
        );
        UUID requestId = UUID.randomUUID();

        mockMvc.perform(get("/api/tents")
                        .header("X-Request-ID", requestId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andExpect(header().string(
                        "X-Request-ID",
                        requestId.toString()
                ))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.requestId").value(
                        requestId.toString()
                ));

        assertRejectedToken(tamper(validToken));

        Instant now = clock.instant();

        assertRejectedToken(customToken(
                decodedTestSecret(),
                "wrong-issuer",
                List.of(EXPECTED_AUDIENCE),
                now.minusSeconds(30),
                now.plusSeconds(300),
                customer
        ));

        assertRejectedToken(customToken(
                decodedTestSecret(),
                EXPECTED_ISSUER,
                List.of("wrong-audience"),
                now.minusSeconds(30),
                now.plusSeconds(300),
                customer
        ));

        // A valid signature is not sufficient. Audience, expiration,
        // and JWT ID are mandatory claims.
        assertRejectedToken(customToken(
                decodedTestSecret(),
                EXPECTED_ISSUER,
                null,
                now.minusSeconds(30),
                now.plusSeconds(300),
                customer,
                true
        ));

        assertRejectedToken(customToken(
                decodedTestSecret(),
                EXPECTED_ISSUER,
                List.of(EXPECTED_AUDIENCE),
                now.minusSeconds(30),
                null,
                customer,
                true
        ));

        assertRejectedToken(customToken(
                decodedTestSecret(),
                EXPECTED_ISSUER,
                List.of(EXPECTED_AUDIENCE),
                now.minusSeconds(30),
                now.plusSeconds(300),
                customer,
                false
        ));

        assertRejectedToken(customToken(
                decodedTestSecret(),
                EXPECTED_ISSUER,
                List.of(EXPECTED_AUDIENCE),
                now.minusSeconds(7_200),
                now.minusSeconds(3_600),
                customer
        ));

        assertRejectedToken(customToken(
                "different-signing-secret-32-bytes!".getBytes(
                        StandardCharsets.UTF_8
                ),
                EXPECTED_ISSUER,
                List.of(EXPECTED_AUDIENCE),
                now.minusSeconds(30),
                now.plusSeconds(300),
                customer
        ));
    }

    @Test
    void customerCanUseOwnApiButCannotCrossOwnershipOrUseStaffRoutes()
            throws Exception {
        Registration first = register(
                "First Customer",
                "first@example.com",
                PASSWORD
        );

        Registration second = register(
                "Second Customer",
                "second@example.com",
                PASSWORD
        );

        String firstToken = tokenFor("first@example.com", PASSWORD);
        String adminToken = createPrivilegedToken(
                "admin@example.com",
                Role.ADMIN
        );

        long tentId = createTent(adminToken, 20, 40, 2);

        long secondReservationId = createReservation(
                adminToken,
                second.customerId(),
                tentId,
                "Second booking"
        );

        mockMvc.perform(get("/api/customers/{id}", first.customerId())
                        .with(bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.customerId()));

        mockMvc.perform(get("/api/customers/{id}", second.customerId())
                        .with(bearer(firstToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/reservations")
                        .with(bearer(firstToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(
                                second.customerId(),
                                tentId,
                                "Forged customer booking"
                        )))
                .andExpect(status().isForbidden());

        long ownReservationId = createReservation(
                firstToken,
                first.customerId(),
                tentId,
                "Own booking"
        );

        mockMvc.perform(get("/api/reservations")
                        .with(bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(ownReservationId));

        mockMvc.perform(get(
                        "/api/reservations/{id}",
                        secondReservationId
                ).with(bearer(firstToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/api/reservations/{id}/cancel",
                        secondReservationId
                ).with(bearer(firstToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/api/reservations/{id}/confirm",
                        ownReservationId
                ).with(bearer(firstToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/tents/{id}/waitlist", tentId)
                        .with(bearer(firstToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/users")
                        .with(bearer(firstToken)))
                .andExpect(status().isForbidden());

        UUID deniedRequestId = UUID.randomUUID();

        mockMvc.perform(post("/api/tents")
                        .with(bearer(firstToken))
                        .header("X-Request-ID", deniedRequestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "widthFeet": 10,
                                  "lengthFeet": 10,
                                  "totalQuantity": 1
                                }
                                """))
                .andExpect(status().isForbidden());

        assertEquals(1, jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM audit_events
                WHERE action = 'ACCESS_DENIED'
                  AND actor_email = 'first@example.com'
                  AND request_id = ?
                  AND details = 'path=/api/tents'
                  AND outcome = 'FAILED'
                """,
                Integer.class,
                deniedRequestId
        ));

        mockMvc.perform(post(
                        "/api/reservations/{id}/cancel",
                        ownReservationId
                ).with(bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get(
                        "/api/reservations/{id}",
                        secondReservationId
                ).with(bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reservations",
                Integer.class
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tent_types",
                Integer.class
        ));
    }

    @Test
    void staffCanOperateInventoryButOnlyAdminCanManageAccountsAndAudit()
            throws Exception {
        String staffToken = createPrivilegedToken(
                "staff@example.com",
                Role.STAFF
        );
        String adminToken = createPrivilegedToken(
                "admin@example.com",
                Role.ADMIN
        );

        createTent(staffToken, 30, 40, 4);

        mockMvc.perform(get("/api/customers").with(bearer(staffToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/users")
                        .with(bearer(staffToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/audit-events")
                        .with(bearer(staffToken)))
                .andExpect(status().isForbidden());

        MvcResult created = mockMvc.perform(post("/api/admin/users")
                        .with(bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "second.staff@example.com",
                                  "password": "Second-Staff-Password-42",
                                  "role": "STAFF"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("STAFF"))
                .andReturn();

        Number accountId = JsonPath.read(
                created.getResponse().getContentAsString(),
                "$.id"
        );

        mockMvc.perform(get(
                        "/api/admin/users/{id}",
                        accountId.longValue()
                ).with(bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(
                        "second.staff@example.com"
                ));

        mockMvc.perform(get("/api/admin/audit-events")
                        .with(bearer(adminToken))
                        .queryParam("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void customerMustRequestFutureSetupWhileStaffRetainsTrustedOverride()
            throws Exception {
        Registration customer = register(
                "Time Policy Customer",
                "time.policy@example.com",
                PASSWORD
        );

        String customerToken = tokenFor(
                "time.policy@example.com",
                PASSWORD
        );
        String staffToken = createPrivilegedToken(
                "time.policy.staff@example.com",
                Role.STAFF
        );

        long tentId = createTent(staffToken, 15, 30, 2);

        String historicalRequest = reservationJsonWithTimes(
                customer.customerId(),
                tentId,
                "2026-01-14T10:00:00",
                "2026-01-14T16:00:00",
                "2026-01-14T08:00:00",
                "2026-01-14T18:00:00",
                "Historical request"
        );

        mockMvc.perform(post("/api/reservations")
                        .with(bearer(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(historicalRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Setup time must be in the future."
                ));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reservations",
                Integer.class
        ));

        mockMvc.perform(post("/api/reservations")
                        .with(bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(historicalRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reservations",
                Integer.class
        ));
    }

    @Test
    void staffLifecycleAuditCapturesActorRequestTimeAndStatuses()
            throws Exception {
        Registration customer = register(
                "Lifecycle Customer",
                "lifecycle.customer@example.com",
                PASSWORD
        );

        String staffToken = createPrivilegedToken(
                "lifecycle.staff@example.com",
                Role.STAFF
        );

        Jwt staffJwt = jwtDecoder.decode(staffToken);
        long staffUserId = ((Number) staffJwt.getClaim("user_id"))
                .longValue();

        long tentId = createTent(staffToken, 25, 40, 3);

        long completedReservationId = createReservation(
                staffToken,
                customer.customerId(),
                tentId,
                "Staff-completed booking"
        );

        long rejectedReservationId = createReservation(
                staffToken,
                customer.customerId(),
                tentId,
                "Staff-rejected booking"
        );

        UUID confirmRequestId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/api/reservations/{id}/confirm",
                        completedReservationId
                )
                        .with(bearer(staffToken))
                        .header("X-Request-ID", confirmRequestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        UUID completeRequestId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/api/reservations/{id}/complete",
                        completedReservationId
                )
                        .with(bearer(staffToken))
                        .header("X-Request-ID", completeRequestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        UUID rejectRequestId = UUID.randomUUID();

        mockMvc.perform(post(
                        "/api/reservations/{id}/reject",
                        rejectedReservationId
                )
                        .with(bearer(staffToken))
                        .header("X-Request-ID", rejectRequestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        assertLifecycleAudit(
                "CONFIRM_RESERVATION",
                completedReservationId,
                staffUserId,
                confirmRequestId,
                "PENDING",
                "CONFIRMED"
        );

        assertLifecycleAudit(
                "COMPLETE_RESERVATION",
                completedReservationId,
                staffUserId,
                completeRequestId,
                "CONFIRMED",
                "COMPLETED"
        );

        assertLifecycleAudit(
                "REJECT_RESERVATION",
                rejectedReservationId,
                staffUserId,
                rejectRequestId,
                "PENDING",
                "REJECTED"
        );
    }

    private void assertLifecycleAudit(
            String action,
            long reservationId,
            long staffUserId,
            UUID requestId,
            String oldStatus,
            String newStatus
    ) {
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                """
                SELECT actor_user_id, actor_email, request_id, occurred_at,
                       old_status, new_status, outcome
                FROM audit_events
                WHERE action = ?
                  AND resource_id = ?
                """,
                action,
                Long.toString(reservationId)
        );

        assertEquals(
                staffUserId,
                ((Number) audit.get("actor_user_id")).longValue()
        );
        assertEquals(
                "lifecycle.staff@example.com",
                audit.get("actor_email")
        );
        assertEquals(requestId, audit.get("request_id"));
        assertNotNull(audit.get("occurred_at"));
        assertEquals(oldStatus, audit.get("old_status"));
        assertEquals(newStatus, audit.get("new_status"));
        assertEquals("SUCCEEDED", audit.get("outcome"));
    }

    @Test
    void browserSessionCannotAuthenticateStatelessApiAndBearerIsNotUiLogin()
            throws Exception {
        register("Session User", "session@example.com", PASSWORD);
        String token = tokenFor("session@example.com", PASSWORD);

        MvcResult browserLogin = mockMvc.perform(formLogin("/login")
                        .userParameter("email")
                        .user("session@example.com")
                        .password(PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) browserLogin
                .getRequest()
                .getSession(false);

        assertNotNull(session);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/customer/reservations")
                        .with(bearer(token)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        containsString("/login")
                ));
    }

    private Registration register(
            String fullName,
            String email,
            String password
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(fullName, email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andReturn();

        Number userId = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );
        Number customerId = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.customerId"
        );

        return new Registration(
                userId.longValue(),
                customerId.longValue()
        );
    }

    private int registerStatus(String fullName, String email)
            throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationJson(
                                fullName,
                                email,
                                PASSWORD
                        )))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String email,
            String password
    ) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, password)));
    }

    private String tokenFor(String email, String password)
            throws Exception {
        MvcResult result = login(email, password)
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.accessToken"
        );
    }

    private String createPrivilegedToken(String email, Role role)
            throws Exception {
        accountService.createPrivilegedAccount(
                new CreateUserAccountRequest(email, PASSWORD, role)
        );

        return tokenFor(email, PASSWORD);
    }

    private long createTent(
            String token,
            int width,
            int length,
            int quantity
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tents")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "widthFeet": %d,
                                  "lengthFeet": %d,
                                  "totalQuantity": %d
                                }
                                """.formatted(
                                width,
                                length,
                                quantity
                        )))
                .andExpect(status().isCreated())
                .andReturn();

        Number id = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );

        return id.longValue();
    }

    private long createReservation(
            String token,
            long customerId,
            long tentId,
            String location
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/reservations")
                        .with(bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(
                                customerId,
                                tentId,
                                location
                        )))
                .andExpect(status().isCreated())
                .andReturn();

        Number id = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );

        return id.longValue();
    }

    private String reservationJson(
            long customerId,
            long tentId,
            String location
    ) {
        return reservationJsonWithTimes(
                customerId,
                tentId,
                "2027-06-12T10:00:00",
                "2027-06-12T16:00:00",
                "2027-06-12T08:00:00",
                "2027-06-12T18:00:00",
                location
        );
    }

    private String reservationJsonWithTimes(
            long customerId,
            long tentId,
            String eventStart,
            String eventEnd,
            String reservedFrom,
            String reservedUntil,
            String location
    ) {
        return """
                {
                  "customerId": %d,
                  "tentId": %d,
                  "quantity": 1,
                  "eventStart": "%s",
                  "eventEnd": "%s",
                  "reservedFrom": "%s",
                  "reservedUntil": "%s",
                  "location": "%s"
                }
                """.formatted(
                customerId,
                tentId,
                eventStart,
                eventEnd,
                reservedFrom,
                reservedUntil,
                location
        );
    }

    private void assertRejectedToken(String token) throws Exception {
        mockMvc.perform(get("/api/tents").with(bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer"
                ))
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.status").value(401));
    }

    private String customToken(
            byte[] secretBytes,
            String issuer,
            List<String> audience,
            Instant issuedAt,
            Instant expiresAt,
            Registration registration
    ) {
        return customToken(
                secretBytes,
                issuer,
                audience,
                issuedAt,
                expiresAt,
                registration,
                true
        );
    }

    private String customToken(
            byte[] secretBytes,
            String issuer,
            List<String> audience,
            Instant issuedAt,
            Instant expiresAt,
            Registration registration,
            boolean includeJwtId
    ) {
        SecretKey secretKey = new SecretKeySpec(
                secretBytes,
                "HmacSHA256"
        );

        JwtEncoder encoder = NimbusJwtEncoder
                .withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject("token.user@example.com")
                .issuedAt(issuedAt)
                .claim("user_id", registration.userId())
                .claim("customer_id", registration.customerId())
                .claim("roles", List.of("CUSTOMER"));

        if (audience != null) {
            claims.audience(audience);
        }

        if (expiresAt != null) {
            claims.expiresAt(expiresAt);
        }

        if (includeJwtId) {
            claims.id(UUID.randomUUID().toString());
        }

        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build()
        )).getTokenValue();
    }

    private byte[] decodedTestSecret() {
        return Base64.getDecoder().decode(TEST_SECRET);
    }

    private String tamper(String token) {
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);

        parts[2] = (first == 'A' ? 'B' : 'A')
                + parts[2].substring(1);

        return String.join(".", parts);
    }

    private RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader(
                    HttpHeaders.AUTHORIZATION,
                    "Bearer " + token
            );
            return request;
        };
    }

    private String registrationJson(
            String fullName,
            String email,
            String password
    ) {
        return """
                {
                  "fullName": "%s",
                  "email": "%s",
                  "phone": "416-555-0199",
                  "password": "%s"
                }
                """.formatted(fullName, email, password);
    }

    private record Registration(long userId, long customerId) {
    }
}