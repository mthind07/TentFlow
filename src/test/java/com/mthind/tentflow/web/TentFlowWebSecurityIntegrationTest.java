package com.mthind.tentflow.web;

import com.jayway.jsonpath.JsonPath;
import com.mthind.tentflow.api.dto.CreateUserAccountRequest;
import com.mthind.tentflow.api.dto.RegisterRequest;
import com.mthind.tentflow.api.dto.UserAccountResponse;
import com.mthind.tentflow.model.ReservationStatus;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.model.TentType;
import com.mthind.tentflow.security.Role;
import com.mthind.tentflow.service.AccountService;
import com.mthind.tentflow.service.BookingService;
import com.mthind.tentflow.service.BookingWorkflowService;
import com.mthind.tentflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Proves that Milestone 4's browser UI uses database-backed sessions and
 * CSRF, while the REST API remains a separate stateless Bearer-token surface.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TentFlowWebSecurityIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "tentflow-password-42";

    private static final LocalDateTime RESERVED_FROM =
            LocalDateTime.of(2027, 6, 12, 8, 0);

    private static final LocalDateTime EVENT_START =
            LocalDateTime.of(2027, 6, 12, 10, 0);

    private static final LocalDateTime EVENT_END =
            LocalDateTime.of(2027, 6, 12, 16, 0);

    private static final LocalDateTime RESERVED_UNTIL =
            LocalDateTime.of(2027, 6, 12, 18, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accountService;

    @Autowired
    private BookingWorkflowService workflowService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void anonymousVisitorsSeePublicTemplatesButProtectedPagesRequireLogin()
            throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("TentFlow")))
                .andExpect(content().string(containsString(
                        "Create customer account"
                )));

        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("Sign in")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(content().string(containsString(
                        "Create your account"
                )))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mockMvc.perform(get("/css/tentflow.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".topbar")));

        mockMvc.perform(get("/customer/reservations"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(get("/staff/reservations"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void registrationFormRejectsMissingCsrfAndAcceptsAValidCsrfToken()
            throws Exception {
        mockMvc.perform(post("/register")
                        .param("fullName", "Web Registrant")
                        .param("email", "web.register@example.com")
                        .param("phone", "416-555-0100")
                        .param("password", PASSWORD))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/register")
                        .with(csrf())
                        // Browser and JSON registration both remove harmless
                        // surrounding whitespace before validating identity.
                        .param("fullName", " Web Registrant ")
                        .param("email", " web.register@example.com ")
                        .param("phone", " 416-555-0100 ")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));

        MockHttpSession customerSession = login(
                "web.register@example.com",
                PASSWORD
        );

        mockMvc.perform(get("/customer/reservations")
                        .session(customerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("customer-reservations"))
                .andExpect(content().string(containsString(
                        "Web Registrant&#39;s reservations"
                )))
                .andExpect(content().string(containsString(
                        "No reservations yet"
                )));
    }

    @Test
    void browserLoginAuditsSuccessAndGenericSecretFreeFailures()
            throws Exception {
        UserAccountResponse customer = registerCustomer(
                "Multibyte Customer",
                "multibyte.customer@example.com"
        );

        login(customer.email(), PASSWORD);

        String wrongPassword = "wrong-password-42";

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", customer.email())
                        .param("password", wrongPassword))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        String passwordOverBcryptByteLimit = "😀".repeat(19);

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", customer.email())
                        .param("password", passwordOverBcryptByteLimit))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        List<LoginAudit> loginAudits = jdbcTemplate.query(
                """
                SELECT occurred_at,
                       actor_user_id,
                       actor_email,
                       outcome,
                       details
                FROM audit_events
                WHERE action = 'LOGIN'
                ORDER BY id
                """,
                (resultSet, rowNumber) -> new LoginAudit(
                        resultSet.getObject("occurred_at"),
                        resultSet.getObject("actor_user_id", Long.class),
                        resultSet.getString("actor_email"),
                        resultSet.getString("outcome"),
                        resultSet.getString("details")
                )
        );

        assertEquals(3, loginAudits.size());

        LoginAudit successfulLogin = loginAudits.getFirst();

        assertNotNull(successfulLogin.occurredAt());
        assertEquals(customer.id(), successfulLogin.actorUserId());
        assertEquals(customer.email(), successfulLogin.actorEmail());
        assertEquals("SUCCEEDED", successfulLogin.outcome());
        assertNull(successfulLogin.details());

        for (LoginAudit failedLogin : loginAudits.subList(1, 3)) {
            assertNotNull(failedLogin.occurredAt());
            assertNull(failedLogin.actorUserId());
            assertEquals("anonymous", failedLogin.actorEmail());
            assertEquals("FAILED", failedLogin.outcome());

            assertNull(
                    failedLogin.details(),
                    "Login audits must not contain a password, request body, "
                            + "or token."
            );
        }
    }

    @Test
    void databaseBackedSessionsEnforceCustomerStaffAndAdminWorkspaces()
            throws Exception {
        registerCustomer(
                "Session Customer",
                "session.customer@example.com"
        );

        createPrivilegedAccount(
                "session.staff@example.com",
                Role.STAFF
        );

        createPrivilegedAccount(
                "session.admin@example.com",
                Role.ADMIN
        );

        MockHttpSession customerSession = login(
                "session.customer@example.com",
                PASSWORD
        );

        MockHttpSession staffSession = login(
                "session.staff@example.com",
                PASSWORD
        );

        MockHttpSession adminSession = login(
                "session.admin@example.com",
                PASSWORD
        );

        mockMvc.perform(get("/").session(customerSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/reservations"));

        mockMvc.perform(get("/customer/reservations")
                        .session(customerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("customer-reservations"));

        mockMvc.perform(get("/staff/reservations")
                        .session(customerSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/").session(staffSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/reservations"));

        mockMvc.perform(get("/staff/reservations")
                        .session(staffSession))
                .andExpect(status().isOk())
                .andExpect(view().name("staff-reservations"));

        mockMvc.perform(get("/customer/reservations")
                        .session(staffSession))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/staff/reservations")
                        .session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("staff-reservations"));
    }

    @Test
    void browserSessionAndApiBearerAuthenticationCannotSubstituteForEachOther()
            throws Exception {
        UserAccountResponse customer = registerCustomer(
                "Boundary Customer",
                "boundary.customer@example.com"
        );

        TentType tent = workflowService.addTentType(20, 30, 2);

        MockHttpSession browserSession = login(
                customer.email(),
                PASSWORD
        );

        String token = apiLogin(customer.email(), PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(browserSession))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isOk());

        MvcResult bearerOnBrowserPage = mockMvc.perform(
                        get("/customer/reservations")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"))
                .andReturn();

        assertNull(
                bearerOnBrowserPage.getRequest().getSession(false),
                "A Bearer token must not create a browser login session."
        );

        mockMvc.perform(post("/api/reservations")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(
                                customer.customerId(),
                                tent.getId(),
                                "Bearer API Event"
                        )))
                .andExpect(status().isCreated());
    }

    @Test
    void customerDashboardIsOwnershipScopedAndCancelIsCsrfProtected()
            throws Exception {
        UserAccountResponse firstCustomer = registerCustomer(
                "First Customer",
                "first.customer@example.com"
        );

        UserAccountResponse secondCustomer = registerCustomer(
                "Second Customer",
                "second.customer@example.com"
        );

        TentType tent = workflowService.addTentType(20, 30, 2);

        ReservationView firstReservation = createReservation(
                firstCustomer.customerId(),
                tent.getId(),
                "First Family Event"
        );

        ReservationView secondReservation = createReservation(
                secondCustomer.customerId(),
                tent.getId(),
                "Second Private Event"
        );

        MockHttpSession firstCustomerSession = login(
                firstCustomer.email(),
                PASSWORD
        );

        mockMvc.perform(get("/customer/reservations")
                        .session(firstCustomerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("customer-reservations"))
                .andExpect(content().string(containsString(
                        "First Family Event"
                )))
                .andExpect(content().string(not(containsString(
                        "Second Private Event"
                ))));

        mockMvc.perform(post(
                        "/customer/reservations/{id}/cancel",
                        secondReservation.id()
                )
                        .session(firstCustomerSession)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertEquals(
                ReservationStatus.PENDING,
                bookingService
                        .getReservation(secondReservation.id())
                        .status()
        );

        mockMvc.perform(post(
                        "/customer/reservations/{id}/cancel",
                        firstReservation.id()
                )
                        .session(firstCustomerSession))
                .andExpect(status().isForbidden());

        assertEquals(
                ReservationStatus.PENDING,
                bookingService
                        .getReservation(firstReservation.id())
                        .status()
        );

        mockMvc.perform(post(
                        "/customer/reservations/{id}/cancel",
                        firstReservation.id()
                )
                        .session(firstCustomerSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/reservations"));

        assertEquals(
                ReservationStatus.CANCELLED,
                bookingService
                        .getReservation(firstReservation.id())
                        .status()
        );
    }

    @Test
    void customerRequestFormRendersAndCreatesOnlyForLoggedInCustomer()
            throws Exception {
        UserAccountResponse requestingCustomer = registerCustomer(
                "Requesting Customer",
                "requesting.customer@example.com"
        );

        UserAccountResponse otherCustomer = registerCustomer(
                "Other Customer",
                "other.dashboard@example.com"
        );

        TentType tent = workflowService.addTentType(20, 30, 2);

        MockHttpSession requestingSession = login(
                requestingCustomer.email(),
                PASSWORD
        );

        MockHttpSession otherSession = login(
                otherCustomer.email(),
                PASSWORD
        );

        mockMvc.perform(get("/customer/reservations/new")
                        .session(requestingSession))
                .andExpect(status().isOk())
                .andExpect(view().name("customer-new-reservation"))
                .andExpect(content().string(containsString(
                        "Request tent inventory"
                )))
                .andExpect(content().string(containsString("20x30")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mockMvc.perform(customerReservationForm(tent.getId())
                        .session(requestingSession))
                .andExpect(status().isForbidden());

        assertEquals(0, bookingService.getAllReservations().size());

        mockMvc.perform(pastCustomerReservationForm(tent.getId())
                        .session(requestingSession)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("customer-new-reservation"))
                .andExpect(content().string(containsString(
                        "Setup time must be in the future."
                )))
                .andExpect(content().string(containsString(
                        "Customer Past Venue"
                )));

        assertEquals(
                0,
                bookingService.getAllReservations().size(),
                "A customer cannot create a reservation whose setup time "
                        + "is at or before the fixed application Clock."
        );

        mockMvc.perform(customerReservationForm(tent.getId())
                        .session(requestingSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/reservations"));

        assertEquals(1, bookingService.getAllReservations().size());

        mockMvc.perform(get("/customer/reservations")
                        .session(requestingSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Customer Form Venue"
                )));

        mockMvc.perform(get("/customer/reservations")
                        .session(otherSession))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(
                        "Customer Form Venue"
                ))));
    }

    @Test
    void staffReviewTemplateAndLifecycleActionsRequireStaffSessionAndCsrf()
            throws Exception {
        UserAccountResponse customer = registerCustomer(
                "Review Customer",
                "review.customer@example.com"
        );

        createPrivilegedAccount(
                "review.staff@example.com",
                Role.STAFF
        );

        TentType tent = workflowService.addTentType(20, 30, 1);

        ReservationView reservation = createReservation(
                customer.customerId(),
                tent.getId(),
                "Review Hall"
        );

        MockHttpSession staffSession = login(
                "review.staff@example.com",
                PASSWORD
        );

        mockMvc.perform(get("/staff/reservations")
                        .session(staffSession))
                .andExpect(status().isOk())
                .andExpect(view().name("staff-reservations"))
                .andExpect(content().string(containsString(
                        "Reservation review"
                )))
                .andExpect(content().string(containsString(
                        "Review Customer"
                )))
                .andExpect(content().string(containsString("Confirm")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));

        mockMvc.perform(post(
                        "/staff/reservations/{id}/confirm",
                        reservation.id()
                )
                        .session(staffSession))
                .andExpect(status().isForbidden());

        assertEquals(
                ReservationStatus.PENDING,
                bookingService
                        .getReservation(reservation.id())
                        .status()
        );

        mockMvc.perform(post(
                        "/staff/reservations/{id}/confirm",
                        reservation.id()
                )
                        .session(staffSession)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/reservations"));

        assertEquals(
                ReservationStatus.CONFIRMED,
                bookingService
                        .getReservation(reservation.id())
                        .status()
        );

        mockMvc.perform(get("/staff/reservations")
                        .session(staffSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("CONFIRMED")))
                .andExpect(content().string(containsString("Complete")));
    }

    private UserAccountResponse registerCustomer(
            String fullName,
            String email
    ) {
        return accountService.registerCustomer(new RegisterRequest(
                fullName,
                email,
                "416-555-0199",
                PASSWORD
        ));
    }

    private void createPrivilegedAccount(String email, Role role) {
        accountService.createPrivilegedAccount(
                new CreateUserAccountRequest(
                        email,
                        PASSWORD,
                        role
                )
        );
    }

    private ReservationView createReservation(
            long customerId,
            long tentId,
            String location
    ) {
        return workflowService.requestReservation(
                customerId,
                tentId,
                1,
                EVENT_START,
                EVENT_END,
                RESERVED_FROM,
                RESERVED_UNTIL,
                location
        );
    }

    private MockHttpSession login(
            String email,
            String password
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result
                .getRequest()
                .getSession(false);

        assertNotNull(
                session,
                "Form login must create a browser session."
        );

        return session;
    }

    private String apiLogin(
            String email,
            String password
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.accessToken"
        );
    }

    private MockHttpServletRequestBuilder customerReservationForm(
            long tentId
    ) {
        return post("/customer/reservations")
                .param("tentId", Long.toString(tentId))
                .param("quantity", "1")
                .param("eventStart", "2027-06-12T10:00")
                .param("eventEnd", "2027-06-12T16:00")
                .param("reservedFrom", "2027-06-12T08:00")
                .param("reservedUntil", "2027-06-12T18:00")
                .param("location", "Customer Form Venue");
    }

    private MockHttpServletRequestBuilder pastCustomerReservationForm(
            long tentId
    ) {
        return post("/customer/reservations")
                .param("tentId", Long.toString(tentId))
                .param("quantity", "1")
                .param("eventStart", "2026-01-15T10:30")
                .param("eventEnd", "2026-01-15T12:00")
                .param("reservedFrom", "2026-01-15T10:00")
                .param("reservedUntil", "2026-01-15T13:00")
                .param("location", "Customer Past Venue");
    }

    private String reservationJson(
            long customerId,
            long tentId,
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
                EVENT_START,
                EVENT_END,
                RESERVED_FROM,
                RESERVED_UNTIL,
                location
        );
    }

    private record LoginAudit(
            Object occurredAt,
            Long actorUserId,
            String actorEmail,
            String outcome,
            String details
    ) {
    }
}