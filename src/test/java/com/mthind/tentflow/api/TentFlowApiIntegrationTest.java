package com.mthind.tentflow.api;

import com.jayway.jsonpath.JsonPath;
import com.mthind.tentflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the M2 HTTP contract against M3's real PostgreSQL service. */
@SpringBootTest
@AutoConfigureMockMvc
class TentFlowApiIntegrationTest extends PostgresIntegrationTest {

    private static final String FROM = "2027-06-12T08:00:00";
    private static final String UNTIL = "2027-06-12T18:00:00";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void customerEndpointsCreateReadListAndValidate() throws Exception {
        long customerId = createCustomer(
                "  Alex Rivera  ",
                "alex.api@example.com"
        );

        mockMvc.perform(get("/api/customers/{id}", customerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId))
                .andExpect(jsonPath("$.fullName").value("Alex Rivera"))
                .andExpect(jsonPath("$.email").value("alex.api@example.com"));

        mockMvc.perform(get("/api/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) customerId)));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": " ",
                                  "email": " @ ",
                                  "phone": "416-555-0101"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/customers"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists());

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Valid Name",
                                  "email": " @ ",
                                  "phone": "416-555-0102"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson(
                                "Duplicate Alex",
                                "ALEX.API@EXAMPLE.COM"
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Customer email ALEX.API@EXAMPLE.COM already exists."
                ));
    }

    @Test
    void tentEndpointsCreateReadListAndReportAvailability()
            throws Exception {
        long tentId = createTent(10, 20, 3);

        mockMvc.perform(get("/api/tents/{id}", tentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sizeLabel").value("10x20"))
                .andExpect(jsonPath("$.totalQuantity").value(3));

        mockMvc.perform(get("/api/tents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) tentId)));

        mockMvc.perform(get("/api/tents/{id}/availability", tentId)
                        .queryParam("from", FROM)
                        .queryParam("until", UNTIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tentId").value(tentId))
                .andExpect(jsonPath("$.tentSize").value("10x20"))
                .andExpect(jsonPath("$.availableQuantity").value(3))
                .andExpect(jsonPath("$.totalQuantity").value(3));

        mockMvc.perform(post("/api/tents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tentJson(10, 20, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Tent size 10x20 already exists."
                ));

        mockMvc.perform(post("/api/tents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lengthFeet": 30,
                                  "totalQuantity": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.widthFeet").value(
                        "Width is required."
                ));
    }

    @Test
    void reservationRequestAutomaticallyBecomesPendingOrWaitlisted()
            throws Exception {
        long customerId = createCustomer(
                "Morgan Lee",
                "morgan.waitlist@example.com"
        );
        long tentId = createTent(10, 30, 1);

        long pendingId = createReservation(
                customerId,
                tentId,
                "PENDING"
        );
        long waitlistedId = createReservation(
                customerId,
                tentId,
                "WAITLISTED"
        );

        mockMvc.perform(get("/api/tents/{id}/waitlist", tentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reservationId").value(waitlistedId))
                .andExpect(jsonPath("$[0].customerId").value(customerId));

        mockMvc.perform(get("/api/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) pendingId)))
                .andExpect(jsonPath("$[*].id", hasItem((int) waitlistedId)));

        mockMvc.perform(get("/api/tents/{id}/availability", tentId)
                        .queryParam("from", FROM)
                        .queryParam("until", UNTIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(0));
    }

    @Test
    void staffCanConfirmAndCompletePendingReservation() throws Exception {
        long customerId = createCustomer(
                "Jamie Patel",
                "jamie.lifecycle@example.com"
        );
        long tentId = createTent(10, 40, 1);
        long reservationId = createReservation(
                customerId,
                tentId,
                "PENDING"
        );

        mockMvc.perform(post(
                        "/api/reservations/{id}/confirm",
                        reservationId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(post(
                        "/api/reservations/{id}/complete",
                        reservationId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(post(
                        "/api/reservations/{id}/confirm",
                        reservationId
                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value(
                        "/api/reservations/" + reservationId + "/confirm"
                ));
    }

    @Test
    void staffCanRejectPendingReservation() throws Exception {
        long customerId = createCustomer(
                "Robin Chen",
                "robin.reject@example.com"
        );
        long tentId = createTent(20, 20, 1);
        long reservationId = createReservation(
                customerId,
                tentId,
                "PENDING"
        );

        mockMvc.perform(post(
                        "/api/reservations/{id}/reject",
                        reservationId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        mockMvc.perform(get("/api/tents/{id}/availability", tentId)
                        .queryParam("from", FROM)
                        .queryParam("until", UNTIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(1));
    }

    @Test
    void cancellingReservationPromotesWaitlist() throws Exception {
        long customerId = createCustomer(
                "Casey Brown",
                "casey.cancel@example.com"
        );
        long tentId = createTent(20, 30, 1);
        long activeId = createReservation(
                customerId,
                tentId,
                "PENDING"
        );
        long waitingId = createReservation(
                customerId,
                tentId,
                "WAITLISTED"
        );

        mockMvc.perform(post(
                        "/api/reservations/{id}/cancel",
                        activeId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/reservations/{id}", activeId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/reservations/{id}", waitingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/tents/{id}/waitlist", tentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void maintenanceEndpointsBlockInventoryAndRemovalPromotesWaitlist()
            throws Exception {
        long customerId = createCustomer(
                "Drew Wilson",
                "drew.maintenance@example.com"
        );
        long tentId = createTent(20, 40, 1);
        long blockId = createMaintenanceBlock(tentId);
        long waitingId = createReservation(
                customerId,
                tentId,
                "WAITLISTED"
        );

        mockMvc.perform(get(
                        "/api/maintenance-blocks/{id}",
                        blockId
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(blockId))
                .andExpect(jsonPath("$.tentId").value(tentId))
                .andExpect(jsonPath("$.reason").value("Safety inspection"));

        mockMvc.perform(get("/api/maintenance-blocks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) blockId)))
                .andExpect(jsonPath("$[*].tentId", hasItem((int) tentId)));

        mockMvc.perform(delete(
                        "/api/maintenance-blocks/{id}",
                        blockId
                ))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(
                        "/api/maintenance-blocks/{id}",
                        blockId
                ))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/reservations/{id}", waitingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void requestFailuresUseConsistentStatusCodesAndJsonEnvelope()
            throws Exception {
        mockMvc.perform(get("/api/reservations/{id}", 999_999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(
                        "Reservation 999999 was not found."
                ));

        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Request body is missing or contains invalid JSON values."
                ));

        long tentId = createTent(30, 30, 1);

        mockMvc.perform(get("/api/tents/{id}/availability", tentId)
                        .queryParam("from", "not-a-date")
                        .queryParam("until", UNTIL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(
                        "Parameter 'from' has an invalid value."
                ));

        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value(
                        "/api/does-not-exist"
                ));

        mockMvc.perform(post("/api/tents")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void apiRejectsTorontoDaylightSavingGapAndOverlap()
            throws Exception {
        long tentId = createTent(10, 20, 1);

        mockMvc.perform(get("/api/tents/{id}/availability", tentId)
                        .queryParam("from", "2027-03-14T02:30:00")
                        .queryParam("until", "2027-03-14T04:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(
                        "is not an unambiguous time in America/Toronto"
                )));

        mockMvc.perform(get("/api/tents/{id}/availability", tentId)
                        .queryParam("from", "2027-11-07T01:30:00")
                        .queryParam("until", "2027-11-07T03:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(
                        "is not an unambiguous time in America/Toronto"
                )));
    }

    @Test
    void microsecondTimesRoundTripExactlyAndFinerPrecisionIsRejected()
            throws Exception {
        long customerId = createCustomer(
                "Precision Customer",
                "precision@example.com"
        );
        long tentId = createTent(10, 20, 1);
        String reservedFrom = "2027-06-12T08:00:00.123456";
        String reservedUntil = "2027-06-12T18:00:00.123456";

        MvcResult created = mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": %d,
                                  "tentId": %d,
                                  "quantity": 1,
                                  "eventStart": "2027-06-12T10:00:00.123456",
                                  "eventEnd": "2027-06-12T16:00:00.123456",
                                  "reservedFrom": "%s",
                                  "reservedUntil": "%s",
                                  "location": "Precision venue"
                                }
                                """.formatted(
                                customerId,
                                tentId,
                                reservedFrom,
                                reservedUntil
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservedTime.start").value(
                        reservedFrom
                ))
                .andExpect(jsonPath("$.reservedTime.end").value(
                        reservedUntil
                ))
                .andReturn();

        long reservationId = idFrom(created);

        mockMvc.perform(get("/api/reservations/{id}", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservedTime.start").value(
                        reservedFrom
                ))
                .andExpect(jsonPath("$.reservedTime.end").value(
                        reservedUntil
                ));

        mockMvc.perform(get("/api/tents/{id}/availability", tentId)
                        .queryParam("from", reservedUntil)
                        .queryParam("until", "2027-06-12T19:00:00.123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(1));

        mockMvc.perform(get("/api/tents/{id}/availability", tentId)
                        .queryParam("from", "2027-06-13T08:00:00.123456789")
                        .queryParam("until", "2027-06-13T09:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString(
                        "supported six fractional-second digits"
                )));
    }

    @Test
    void concurrentHttpRequestsReceiveUniqueCustomerIds()
            throws Exception {
        int requestCount = 24;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        List<Future<CreatedCustomer>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < requestCount; index++) {
                int customerNumber = index;

                futures.add(executor.submit(() -> {
                    start.await();

                    MvcResult result = mockMvc.perform(post("/api/customers")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(customerJson(
                                            "Concurrent "
                                                    + customerNumber,
                                            "concurrent-"
                                                    + customerNumber
                                                    + "@example.com"
                                    )))
                            .andReturn();

                    return new CreatedCustomer(
                            result.getResponse().getStatus(),
                            idFrom(result)
                    );
                }));
            }

            start.countDown();

            Set<Long> ids = new HashSet<>();

            for (Future<CreatedCustomer> future : futures) {
                CreatedCustomer created = future.get(
                        10,
                        TimeUnit.SECONDS
                );

                assertEquals(201, created.status());
                assertTrue(ids.add(created.id()));
            }

            assertEquals(requestCount, ids.size());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentCaseVariantEmailsCreateOnceAndConflictOnce()
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Integer> lowerCase = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(customerJson(
                                        "Lower Case",
                                        "same-email@example.com"
                                )))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });

            Future<Integer> upperCase = executor.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/customers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(customerJson(
                                        "Upper Case",
                                        "SAME-EMAIL@EXAMPLE.COM"
                                )))
                        .andReturn()
                        .getResponse()
                        .getStatus();
            });

            start.countDown();

            Set<Integer> statuses = new HashSet<>();
            statuses.add(lowerCase.get(10, TimeUnit.SECONDS));
            statuses.add(upperCase.get(10, TimeUnit.SECONDS));

            assertEquals(Set.of(201, 409), statuses);

            mockMvc.perform(get("/api/customers"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentOverlappingRequestsCannotBothHoldTheOnlyTent()
            throws Exception {
        long customerId = createCustomer(
                "Concurrency Booker",
                "reservation-race@example.com"
        );
        long tentId = createTent(30, 40, 1);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<CreatedReservation>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < 2; index++) {
                futures.add(executor.submit(() -> {
                    start.await();

                    MvcResult result = mockMvc.perform(
                                    post("/api/reservations")
                                            .contentType(
                                                    MediaType.APPLICATION_JSON
                                            )
                                            .content(reservationJson(
                                                    customerId,
                                                    tentId
                                            ))
                            )
                            .andReturn();

                    String responseBody = result
                            .getResponse()
                            .getContentAsString();

                    return new CreatedReservation(
                            result.getResponse().getStatus(),
                            JsonPath.read(responseBody, "$.status")
                    );
                }));
            }

            start.countDown();

            List<CreatedReservation> created = new ArrayList<>();

            for (Future<CreatedReservation> future : futures) {
                created.add(future.get(10, TimeUnit.SECONDS));
            }

            assertTrue(created.stream().allMatch(
                    result -> result.httpStatus() == 201
            ));

            assertEquals(
                    Set.of("PENDING", "WAITLISTED"),
                    created.stream()
                            .map(CreatedReservation::reservationStatus)
                            .collect(java.util.stream.Collectors.toSet())
            );

            mockMvc.perform(get("/api/tents/{id}/availability", tentId)
                            .queryParam("from", FROM)
                            .queryParam("until", UNTIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableQuantity").value(0));

            mockMvc.perform(get("/api/tents/{id}/waitlist", tentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void reservationAndMaintenanceRaceShareTheSameInventoryLock()
            throws Exception {
        long customerId = createCustomer(
                "Mixed Writer",
                "mixed-writer@example.com"
        );
        long tentId = createTent(20, 30, 1);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<MvcResult> reservationFuture = executor.submit(() -> {
                start.await();

                return mockMvc.perform(post("/api/reservations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reservationJson(
                                        customerId,
                                        tentId
                                )))
                        .andReturn();
            });

            Future<MvcResult> maintenanceFuture = executor.submit(() -> {
                start.await();

                return mockMvc.perform(post("/api/maintenance-blocks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "tentId": %d,
                                          "quantityUnavailable": 1,
                                          "from": "%s",
                                          "until": "%s",
                                          "reason": "Concurrent inspection"
                                        }
                                        """.formatted(
                                        tentId,
                                        FROM,
                                        UNTIL
                                )))
                        .andReturn();
            });

            start.countDown();

            MvcResult reservation = reservationFuture.get(
                    10,
                    TimeUnit.SECONDS
            );
            MvcResult maintenance = maintenanceFuture.get(
                    10,
                    TimeUnit.SECONDS
            );

            String reservationStatus = JsonPath.read(
                    reservation.getResponse().getContentAsString(),
                    "$.status"
            );

            int maintenanceStatus =
                    maintenance.getResponse().getStatus();

            assertEquals(201, reservation.getResponse().getStatus());

            assertTrue(
                    (reservationStatus.equals("PENDING")
                            && maintenanceStatus == 409)
                            || (reservationStatus.equals("WAITLISTED")
                            && maintenanceStatus == 201),
                    "Only one concurrent writer may hold the final tent."
            );

            mockMvc.perform(get("/api/tents/{id}/availability", tentId)
                            .queryParam("from", FROM)
                            .queryParam("until", UNTIL))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.availableQuantity").value(0));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void openApiDocumentPublishesTentFlowRoutes() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("TentFlow API"))
                .andExpect(content().string(containsString(
                        "\"/api/reservations\""
                )))
                .andExpect(content().string(containsString(
                        "\"/api/tents/{tentId}/availability\""
                )));
    }

    private long createCustomer(
            String fullName,
            String email
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson(fullName, email)))
                .andExpect(status().isCreated())
                .andReturn();

        long id = idFrom(result);

        assertTrue(result.getResponse()
                .getHeader("Location")
                .endsWith("/api/customers/" + id));

        return id;
    }

    private long createTent(
            int width,
            int length,
            int totalQuantity
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tentJson(width, length, totalQuantity)))
                .andExpect(status().isCreated())
                .andReturn();

        long id = idFrom(result);

        assertTrue(result.getResponse()
                .getHeader("Location")
                .endsWith("/api/tents/" + id));

        return id;
    }

    private long createReservation(
            long customerId,
            long tentId,
            String expectedStatus
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(customerId, tentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andReturn();

        long id = idFrom(result);

        assertTrue(result.getResponse()
                .getHeader("Location")
                .endsWith("/api/reservations/" + id));

        return id;
    }

    private long createMaintenanceBlock(long tentId) throws Exception {
        String json = """
                {
                  "tentId": %d,
                  "quantityUnavailable": 1,
                  "from": "%s",
                  "until": "%s",
                  "reason": "Safety inspection"
                }
                """.formatted(tentId, FROM, UNTIL);

        MvcResult result = mockMvc.perform(post("/api/maintenance-blocks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();

        long id = idFrom(result);
        String location = result.getResponse().getHeader("Location");

        assertTrue(location.endsWith(
                "/api/maintenance-blocks/" + id
        ));

        mockMvc.perform(get(URI.create(location)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.tentId").value(tentId))
                .andExpect(jsonPath("$.reason").value("Safety inspection"));

        return id;
    }

    private static long idFrom(MvcResult result) throws Exception {
        Number id = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );

        return id.longValue();
    }

    private static String customerJson(
            String fullName,
            String email
    ) {
        return """
                {
                  "fullName": "%s",
                  "email": "%s",
                  "phone": "416-555-0199"
                }
                """.formatted(fullName, email);
    }

    private static String tentJson(
            int width,
            int length,
            int totalQuantity
    ) {
        return """
                {
                  "widthFeet": %d,
                  "lengthFeet": %d,
                  "totalQuantity": %d
                }
                """.formatted(width, length, totalQuantity);
    }

    private static String reservationJson(
            long customerId,
            long tentId
    ) {
        return """
                {
                  "customerId": %d,
                  "tentId": %d,
                  "quantity": 1,
                  "eventStart": "2027-06-12T10:00:00",
                  "eventEnd": "2027-06-12T16:00:00",
                  "reservedFrom": "%s",
                  "reservedUntil": "%s",
                  "location": "Toronto, Ontario"
                }
                """.formatted(customerId, tentId, FROM, UNTIL);
    }

    private record CreatedCustomer(int status, long id) {
    }

    private record CreatedReservation(
            int httpStatus,
            String reservationStatus
    ) {
    }
}