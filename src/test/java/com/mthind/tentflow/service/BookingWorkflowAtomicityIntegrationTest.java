package com.mthind.tentflow.service;

import com.mthind.tentflow.audit.AuditService;
import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.model.ReservationStatus;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.model.TentType;
import com.mthind.tentflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class BookingWorkflowAtomicityIntegrationTest
        extends PostgresIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingWorkflowService workflowService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AuditService auditService;

    @Test
    void failedAuditRollsBackTheLifecycleMutation() {
        Customer customer = bookingService.registerCustomer(
                "Atomicity Customer",
                "atomicity@example.com",
                "416-555-0104"
        );
        TentType tent = bookingService.addTentType(20, 30, 1);
        ReservationView reservation = bookingService.requestReservation(
                customer.getId(),
                tent.getId(),
                1,
                LocalDateTime.of(2027, 6, 12, 10, 0),
                LocalDateTime.of(2027, 6, 12, 16, 0),
                LocalDateTime.of(2027, 6, 12, 8, 0),
                LocalDateTime.of(2027, 6, 12, 18, 0),
                "Atomic transaction"
        );

        doThrow(new IllegalStateException("Audit write failed"))
                .when(auditService)
                .lifecycleSuccess(
                        eq("CONFIRM_RESERVATION"),
                        eq(reservation.id()),
                        eq(ReservationStatus.PENDING),
                        eq(ReservationStatus.CONFIRMED),
                        any()
                );

        assertThrows(
                IllegalStateException.class,
                () -> workflowService.confirmReservation(reservation.id())
        );
        assertEquals(
                ReservationStatus.PENDING,
                bookingService.getReservation(reservation.id()).status()
        );
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_events",
                Integer.class
        ));
    }
}