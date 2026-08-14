package com.mthind.tentflow.service;

import com.mthind.tentflow.exception.InsufficientAvailabilityException;
import com.mthind.tentflow.exception.InvalidReservationStateException;
import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.model.MaintenanceBlock;
import com.mthind.tentflow.model.ReservationStatus;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.model.TentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

//tests the main Milestone 1 business rules
class TentBookingServiceTest {

    private static final ZoneId TORONTO =
            ZoneId.of("America/Toronto");

    private TentBookingService service;
    private Customer customer;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2027-01-01T15:00:00Z"),
                TORONTO
        );

        service =
                new TentBookingService(fixedClock);

        customer =
                service.registerCustomer(
                        "Taylor Morgan",
                        "taylor@example.com",
                        "416-555-0100"
                );
    }

    @Test
    void availableRequestBecomesPendingAndHoldsInventory() {
        TentType tent =
                service.addTentType(20, 20, 3);

        ReservationView reservation =
                request(tent, 2, at(8), at(18));

        assertEquals(
                ReservationStatus.PENDING,
                reservation.status()
        );

        assertEquals(
                1,
                service.getAvailableQuantity(
                        tent.getId(),
                        at(8),
                        at(18)
                )
        );
    }

    @Test
    void overbookedRequestEntersWaitlist() {
        TentType tent =
                service.addTentType(40, 40, 1);

        request(tent, 1, at(8), at(18));

        ReservationView second =
                request(tent, 1, at(8), at(18));

        assertEquals(
                ReservationStatus.WAITLISTED,
                second.status()
        );

        assertEquals(
                1,
                service
                        .getWaitlistForTent(tent.getId())
                        .size()
        );
    }

    @Test
    void cancellationPromotesWaitlistedRequestBackToPending() {
        TentType tent =
                service.addTentType(40, 40, 1);

        ReservationView first =
                request(tent, 1, at(8), at(18));

        ReservationView second =
                request(tent, 1, at(8), at(18));

        service.cancelReservation(first.id());

        first = service.getReservation(first.id());
        second = service.getReservation(second.id());

        assertEquals(
                ReservationStatus.CANCELLED,
                first.status()
        );

        assertEquals(
                ReservationStatus.PENDING,
                second.status()
        );

        assertEquals(
                0,
                service
                        .getWaitlistForTent(tent.getId())
                        .size()
        );
    }

    @Test
    void nonOverlappingReservationsCanReuseTheSameTent() {
        TentType tent =
                service.addTentType(20, 30, 1);

        ReservationView morning =
                request(tent, 1, at(8), at(12));

        ReservationView evening =
                request(tent, 1, at(13), at(18));

        assertEquals(
                ReservationStatus.PENDING,
                morning.status()
        );

        assertEquals(
                ReservationStatus.PENDING,
                evening.status()
        );
    }

    @Test
    void backToBackWindowsThatTouchAtOneTimeDoNotOverlap() {
        TentType tent =
                service.addTentType(20, 40, 1);

        ReservationView first =
                request(tent, 1, at(8), at(12));

        ReservationView second =
                request(tent, 1, at(12), at(18));

        assertEquals(
                ReservationStatus.PENDING,
                first.status()
        );

        assertEquals(
                ReservationStatus.PENDING,
                second.status()
        );
    }

    @Test
    void maintenanceBlockReducesAvailability() {
        TentType tent =
                service.addTentType(30, 30, 3);

        service.addMaintenanceBlock(
                tent.getId(),
                1,
                at(8),
                at(18),
                "Repairing a tear"
        );

        ReservationView fits =
                request(tent, 2, at(8), at(18));

        ReservationView doesNotFit =
                request(tent, 1, at(8), at(18));

        assertEquals(
                ReservationStatus.PENDING,
                fits.status()
        );

        assertEquals(
                ReservationStatus.WAITLISTED,
                doesNotFit.status()
        );

        assertEquals(
                0,
                service.getAvailableQuantity(
                        tent.getId(),
                        at(8),
                        at(18)
                )
        );
    }

    @Test
    void removingMaintenanceBlockPromotesWaitlistedRequest() {
        TentType tent =
                service.addTentType(10, 20, 1);

        MaintenanceBlock block =
                service.addMaintenanceBlock(
                        tent.getId(),
                        1,
                        at(8),
                        at(18),
                        "Cleaning"
                );

        ReservationView waiting =
                request(tent, 1, at(8), at(18));

        service.removeMaintenanceBlock(
                block.getId()
        );

        waiting =
                service.getReservation(waiting.id());

        assertEquals(
                ReservationStatus.PENDING,
                waiting.status()
        );
    }

    @Test
    void maintenanceCannotTakeInventoryAlreadyHeldByReservation() {
        TentType tent =
                service.addTentType(30, 40, 2);

        request(tent, 2, at(8), at(18));

        assertThrows(
                InsufficientAvailabilityException.class,
                () -> service.addMaintenanceBlock(
                        tent.getId(),
                        1,
                        at(8),
                        at(18),
                        "Damage inspection"
                )
        );
    }

    @Test
    void availabilityUsesPeakUsageInsteadOfAddingSeparateBookings() {
        TentType tent =
                service.addTentType(30, 30, 5);

        request(tent, 4, at(8), at(10));
        request(tent, 4, at(16), at(18));

        assertEquals(
                1,
                service.getAvailableQuantity(
                        tent.getId(),
                        at(8),
                        at(18)
                )
        );
    }

    @Test
    void reservedWindowMustContainEntireEvent() {
        TentType tent =
                service.addTentType(20, 20, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.requestReservation(
                        customer.getId(),
                        tent.getId(),
                        1,
                        at(9),
                        at(17),
                        at(10),
                        at(18),
                        "Toronto, Ontario"
                )
        );
    }

    @Test
    void waitlistedReservationCannotBeConfirmedDirectly() {
        TentType tent =
                service.addTentType(40, 40, 1);

        request(tent, 1, at(8), at(18));

        ReservationView waiting =
                request(tent, 1, at(8), at(18));

        assertThrows(
                InvalidReservationStateException.class,
                () -> service.confirmReservation(
                        waiting.id()
                )
        );

        assertEquals(
                ReservationStatus.WAITLISTED,
                waiting.status()
        );
    }

    @Test
    void firstEligibleWaitlistRequestIsPromoted() {
        TentType tent =
                service.addTentType(20, 20, 3);

        ReservationView twoTentHold =
                request(tent, 2, at(8), at(18));

        ReservationView oneTentHold =
                request(tent, 1, at(8), at(18));

        ReservationView waitingForTwo =
                request(tent, 2, at(8), at(18));

        ReservationView waitingForOne =
                request(tent, 1, at(8), at(18));

        service.cancelReservation(
                oneTentHold.id()
        );

        twoTentHold =
                service.getReservation(twoTentHold.id());

        waitingForTwo =
                service.getReservation(waitingForTwo.id());

        waitingForOne =
                service.getReservation(waitingForOne.id());

        assertEquals(
                ReservationStatus.PENDING,
                twoTentHold.status()
        );

        assertEquals(
                ReservationStatus.WAITLISTED,
                waitingForTwo.status()
        );

        assertEquals(
                ReservationStatus.PENDING,
                waitingForOne.status()
        );
    }

    @Test
    void tentSizesHaveIndependentInventory() {
        TentType small =
                service.addTentType(10, 10, 1);

        TentType large =
                service.addTentType(40, 40, 1);

        ReservationView smallReservation =
                request(small, 1, at(8), at(18));

        ReservationView largeReservation =
                request(large, 1, at(8), at(18));

        assertEquals(
                ReservationStatus.PENDING,
                smallReservation.status()
        );

        assertEquals(
                ReservationStatus.PENDING,
                largeReservation.status()
        );
    }

    private ReservationView request(
            TentType tent,
            int quantity,
            LocalDateTime reservedFrom,
            LocalDateTime reservedUntil
    ) {
        return service.requestReservation(
                customer.getId(),
                tent.getId(),
                quantity,
                reservedFrom,
                reservedUntil,
                reservedFrom,
                reservedUntil,
                "Toronto, Ontario"
        );
    }

    private LocalDateTime at(int hour) {
        return LocalDateTime.of(
                2027,
                6,
                12,
                hour,
                0
        );
    }
}
