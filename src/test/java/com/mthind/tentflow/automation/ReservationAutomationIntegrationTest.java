package com.mthind.tentflow.automation;

import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.model.ReservationStatus;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.model.TentType;
import com.mthind.tentflow.persistence.repository.ReservationRepository;
import com.mthind.tentflow.service.BookingService;
import com.mthind.tentflow.service.BookingWorkflowService;
import com.mthind.tentflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class ReservationAutomationIntegrationTest
        extends PostgresIntegrationTest {

    @Autowired
    private BookingWorkflowService workflowService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private ReservationAutomationJob automationJob;

    @BeforeEach
    void createJobWithoutStartingABackgroundScheduler() {
        automationJob = new ReservationAutomationJob(
                reservationRepository,
                workflowService,
                clock
        );
    }

    @Test
    void jobCompletesOnlyDueReservationsPromotesWaitlistAndIsIdempotent() {
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = workflowService.registerCustomer(
                "Automation Customer",
                "automation@example.com",
                "416-555-0101"
        );
        TentType dueTent = workflowService.addTentType(20, 40, 1);
        TentType futureTent = workflowService.addTentType(30, 40, 1);

        ReservationView due = request(
                customer.getId(),
                dueTent.getId(),
                now.minusHours(5),
                now.minusHours(1),
                "Due reservation"
        );
        workflowService.confirmReservation(due.id());
        ReservationView waiting = request(
                customer.getId(),
                dueTent.getId(),
                now.minusHours(2),
                now.plusHours(2),
                "Waiting reservation"
        );
        assertEquals(ReservationStatus.WAITLISTED, waiting.status());

        ReservationView future = request(
                customer.getId(),
                futureTent.getId(),
                now.plusHours(1),
                now.plusHours(5),
                "Future reservation"
        );
        workflowService.confirmReservation(future.id());

        automationJob.completeDueReservations();

        assertEquals(
                ReservationStatus.COMPLETED,
                bookingService.getReservation(due.id()).status()
        );
        assertEquals(
                ReservationStatus.PENDING,
                bookingService.getReservation(waiting.id()).status()
        );
        assertEquals(
                ReservationStatus.CONFIRMED,
                bookingService.getReservation(future.id()).status()
        );
        assertEquals(1, auditCount(
                "AUTO_COMPLETE_RESERVATION",
                due.id()
        ));
        assertEquals(1, auditCount(
                "PROMOTE_WAITLIST",
                waiting.id()
        ));

        automationJob.completeDueReservations();

        assertEquals(1, auditCount(
                "AUTO_COMPLETE_RESERVATION",
                due.id()
        ));
        assertEquals(1, auditCount(
                "PROMOTE_WAITLIST",
                waiting.id()
        ));
        assertEquals(0, auditCount(
                "AUTO_COMPLETE_RESERVATION",
                future.id()
        ));

        List<String> completionAudit = jdbcTemplate.queryForList(
                """
                SELECT concat_ws('|', actor_email, old_status, new_status,
                                  outcome)
                FROM audit_events
                WHERE action = 'AUTO_COMPLETE_RESERVATION'
                  AND resource_id = ?
                """,
                String.class,
                Long.toString(due.id())
        );
        assertEquals(
                List.of("system:automation|CONFIRMED|COMPLETED|SUCCEEDED"),
                completionAudit
        );
    }

    @Test
    void concurrentWorkersProduceOneTransitionAndOneAuditEvent()
            throws Exception {
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = workflowService.registerCustomer(
                "Concurrent Automation",
                "concurrent.automation@example.com",
                "416-555-0102"
        );
        TentType tent = workflowService.addTentType(30, 40, 1);
        ReservationView due = request(
                customer.getId(),
                tent.getId(),
                now.minusHours(4),
                now.minusMinutes(1),
                "Concurrent completion"
        );
        workflowService.confirmReservation(due.id());

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return workflowService.completeDueReservation(due.id(), now);
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return workflowService.completeDueReservation(due.id(), now);
            });

            start.countDown();
            List<Boolean> results = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS)
            );

            assertEquals(1, results.stream().filter(Boolean::booleanValue)
                    .count());
            assertEquals(1, results.stream().filter(result -> !result)
                    .count());
            assertEquals(
                    ReservationStatus.COMPLETED,
                    bookingService.getReservation(due.id()).status()
            );
            assertEquals(1, auditCount(
                    "AUTO_COMPLETE_RESERVATION",
                    due.id()
            ));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void databaseMakesAuditHistoryAppendOnly() {
        workflowService.registerCustomer(
                "Audit Customer",
                "audit.customer@example.com",
                "416-555-0103"
        );
        Long auditId = jdbcTemplate.queryForObject(
                "SELECT max(id) FROM audit_events",
                Long.class
        );
        assertTrue(auditId != null && auditId > 0);

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE audit_events SET details = 'changed' WHERE id = ?",
                auditId
        ));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "DELETE FROM audit_events WHERE id = ?",
                auditId
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_events WHERE id = ?",
                Integer.class,
                auditId
        ));
    }

    private ReservationView request(
            long customerId,
            long tentId,
            LocalDateTime reservedFrom,
            LocalDateTime reservedUntil,
            String location
    ) {
        long durationMinutes = java.time.Duration.between(
                reservedFrom,
                reservedUntil
        ).toMinutes();
        LocalDateTime eventStart = reservedFrom.plusMinutes(
                Math.max(1, durationMinutes / 3)
        );
        LocalDateTime eventEnd = reservedUntil.minusMinutes(
                Math.max(1, durationMinutes / 3)
        );
        assertTrue(eventStart.isBefore(eventEnd));

        return workflowService.requestReservation(
                customerId,
                tentId,
                1,
                eventStart,
                eventEnd,
                reservedFrom,
                reservedUntil,
                location
        );
    }

    private int auditCount(String action, long reservationId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM audit_events
                WHERE action = ? AND resource_id = ?
                """,
                Integer.class,
                action,
                Long.toString(reservationId)
        );
    }
}