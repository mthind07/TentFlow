package com.mthind.tentflow.service;

import com.mthind.tentflow.model.ReservationStatus;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PersistentTentBookingServiceIntegrationTest
        extends PostgresIntegrationTest {

    private static final LocalDateTime EIGHT =
            LocalDateTime.of(2027, 6, 12, 8, 0);
    private static final LocalDateTime TWELVE =
            LocalDateTime.of(2027, 6, 12, 12, 0);
    private static final LocalDateTime SIXTEEN =
            LocalDateTime.of(2027, 6, 12, 16, 0);

    @Autowired
    private BookingService persistent;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void springPublishesExactlyOnePersistentBookingService() {
        assertEquals(
                1,
                applicationContext.getBeansOfType(BookingService.class).size()
        );
        assertEquals(
                PersistentTentBookingService.class,
                AopUtils.getTargetClass(persistent)
        );
        assertInstanceOf(PersistentTentBookingService.class, persistent);

        Transactional availabilityTransaction = assertDoesNotThrow(
                () -> PersistentTentBookingService.class
                        .getMethod(
                                "getAvailableQuantity",
                                long.class,
                                LocalDateTime.class,
                                LocalDateTime.class
                        )
                        .getAnnotation(Transactional.class)
        );

        assertNotNull(availabilityTransaction);
        assertTrue(availabilityTransaction.readOnly());
        assertEquals(
                Isolation.REPEATABLE_READ,
                availabilityTransaction.isolation()
        );
    }

    @Test
    void persistentRulesMatchTheM1ReferenceEngineForACompleteScenario() {
        BookingService reference = new TentBookingService(
                Clock.fixed(
                        Instant.parse("2027-01-01T12:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        ScenarioResult expected = runScenario(
                reference,
                "parity.reference@example.com"
        );
        ScenarioResult actual = runScenario(
                persistent,
                "parity.persistent@example.com"
        );

        assertEquals(expected, actual);
    }

    @Test
    void serviceLevelQuantityRulesRetainTheM1Messages() {
        var customer = persistent.registerCustomer(
                "Rule Checker",
                "rule-checker@example.com",
                "416-555-0123"
        );
        var tent = persistent.addTentType(10, 20, 2);

        IllegalArgumentException reservationError = assertThrows(
                IllegalArgumentException.class,
                () -> persistent.requestReservation(
                        customer.getId(),
                        tent.getId(),
                        3,
                        EIGHT.plusHours(1),
                        TWELVE.minusHours(1),
                        EIGHT,
                        TWELVE,
                        "Toronto"
                )
        );

        assertEquals(
                "Requested quantity must be between 1 and 2.",
                reservationError.getMessage()
        );

        IllegalArgumentException maintenanceError = assertThrows(
                IllegalArgumentException.class,
                () -> persistent.addMaintenanceBlock(
                        tent.getId(),
                        3,
                        EIGHT,
                        TWELVE,
                        "Inspection"
                )
        );

        assertEquals(
                "Maintenance quantity must be between 1 and 2.",
                maintenanceError.getMessage()
        );
    }

    @Test
    void releasingOneTentPromotesOnlyTheOldestOverlappingRequest() {
        var customer = persistent.registerCustomer(
                "Waitlist Customer",
                "persistent-waitlist@example.com",
                "416-555-0144"
        );
        var tent = persistent.addTentType(10, 20, 1);

        ReservationView active = requestOne(
                persistent,
                customer.getId(),
                tent.getId(),
                "Active venue"
        );
        ReservationView oldestWaiting = requestOne(
                persistent,
                customer.getId(),
                tent.getId(),
                "Oldest waiting venue"
        );
        ReservationView newestWaiting = requestOne(
                persistent,
                customer.getId(),
                tent.getId(),
                "Newest waiting venue"
        );

        assertEquals(ReservationStatus.PENDING, active.status());
        assertEquals(ReservationStatus.WAITLISTED, oldestWaiting.status());
        assertEquals(ReservationStatus.WAITLISTED, newestWaiting.status());

        persistent.cancelReservation(active.id());

        assertEquals(
                ReservationStatus.PENDING,
                persistent.getReservation(oldestWaiting.id()).status()
        );
        assertEquals(
                ReservationStatus.WAITLISTED,
                persistent.getReservation(newestWaiting.id()).status()
        );
        assertEquals(
                List.of(newestWaiting.id()),
                persistent.getWaitlistForTent(tent.getId())
                        .stream()
                        .map(entry -> entry.reservation().id())
                        .toList()
        );
    }

    private ScenarioResult runScenario(
            BookingService service,
            String email
    ) {
        var customer = service.registerCustomer(
                "Parity Customer",
                email,
                "416-555-0199"
        );
        var tent = service.addTentType(20, 30, 2);

        ReservationView morning = service.requestReservation(
                customer.getId(),
                tent.getId(),
                1,
                EIGHT.plusHours(1),
                TWELVE.minusHours(1),
                EIGHT,
                TWELVE,
                "Morning venue"
        );

        ReservationView afternoon = service.requestReservation(
                customer.getId(),
                tent.getId(),
                1,
                TWELVE.plusHours(1),
                SIXTEEN.minusHours(1),
                TWELVE,
                SIXTEEN,
                "Afternoon venue"
        );

        int acrossAdjacentRanges = service.getAvailableQuantity(
                tent.getId(),
                EIGHT,
                SIXTEEN
        );

        ReservationView wholeDay = service.requestReservation(
                customer.getId(),
                tent.getId(),
                2,
                EIGHT.plusHours(2),
                SIXTEEN.minusHours(2),
                EIGHT,
                SIXTEEN,
                "Whole-day venue"
        );

        int waitingBeforeRelease =
                service.getWaitlistForTent(tent.getId()).size();

        service.cancelReservation(morning.id());
        ReservationStatus afterFirstRelease =
                service.getReservation(wholeDay.id()).status();

        service.cancelReservation(afternoon.id());
        ReservationStatus afterSecondRelease =
                service.getReservation(wholeDay.id()).status();

        return new ScenarioResult(
                acrossAdjacentRanges,
                wholeDay.status(),
                waitingBeforeRelease,
                afterFirstRelease,
                afterSecondRelease,
                service.getAvailableQuantity(tent.getId(), EIGHT, SIXTEEN),
                service.getAllReservations()
                        .stream()
                        .map(ReservationView::id)
                        .toList()
        );
    }

    private ReservationView requestOne(
            BookingService service,
            long customerId,
            long tentTypeId,
            String location
    ) {
        return service.requestReservation(
                customerId,
                tentTypeId,
                1,
                EIGHT.plusHours(2),
                SIXTEEN.minusHours(2),
                EIGHT,
                SIXTEEN,
                location
        );
    }

    private record ScenarioResult(
            int availabilityAcrossAdjacentRanges,
            ReservationStatus initialWholeDayStatus,
            int waitingBeforeRelease,
            ReservationStatus statusAfterFirstRelease,
            ReservationStatus statusAfterSecondRelease,
            int finalAvailability,
            List<Long> reservationIdsInStableOrder
    ) {
    }
}
