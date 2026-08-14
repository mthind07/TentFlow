package com.mthind.tentflow.service;

import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.model.ReservationStatus;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.model.TentType;
import com.mthind.tentflow.model.WaitlistEntryView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

//guards fixes found during the Milestone 1 audit
class TentBookingServiceRegressionTest {

    private TentBookingService service;
    private Customer customer;
    private TentType tent;

    @BeforeEach
    void setUp() {
        service = new TentBookingService();

        customer = service.registerCustomer(
                "Regression Customer",
                "regression@example.com",
                "416-555-0199"
        );

        tent = service.addTentType(
                40,
                40,
                1
        );
    }

    @Test
    void emailIsNormalizedBeforeItIsValidated() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.registerCustomer(
                        "Invalid Email Customer",
                        " @ ",
                        "416-555-0100"
                )
        );

        Customer normalized =
                service.registerCustomer(
                        "Normalized Email Customer",
                        "  normalized@example.com  ",
                        "416-555-0101"
                );

        assertEquals(
                "normalized@example.com",
                normalized.getEmail()
        );
    }

    @Test
    void reservationViewHasNoLifecycleMutationMethods() {
        ReservationView view = request();

        Set<String> forbiddenMethodNames = Set.of(
                "confirm",
                "promoteFromWaitlist",
                "reject",
                "cancel",
                "complete"
        );

        boolean exposesMutationMethod =
                Arrays.stream(
                                ReservationView.class.getMethods()
                        )
                        .map(Method::getName)
                        .anyMatch(
                                forbiddenMethodNames::contains
                        );

        assertFalse(exposesMutationMethod);
        assertTrue(ReservationView.class.isRecord());

        assertTrue(
                Arrays.stream(
                                ReservationView.class
                                        .getDeclaredFields()
                        )
                        .allMatch(field ->
                                Modifier.isFinal(
                                        field.getModifiers()
                                )
                        )
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> service
                        .getAllReservations()
                        .clear()
        );

        assertEquals(
                ReservationStatus.PENDING,
                view.status()
        );
    }

    @Test
    void waitlistViewCannotMutateOrRetainInternalReservationState() {
        ReservationView inventoryHolder =
                request();

        ReservationView waiting =
                request();

        List<WaitlistEntryView> waitlist =
                service.getWaitlistForTent(
                        tent.getId()
                );

        WaitlistEntryView entry =
                waitlist.getFirst();

        assertEquals(
                ReservationView.class,
                entry.reservation().getClass()
        );

        assertThrows(
                UnsupportedOperationException.class,
                waitlist::clear
        );

        service.cancelReservation(
                inventoryHolder.id()
        );

        assertEquals(
                ReservationStatus.WAITLISTED,
                entry.reservation().status()
        );

        assertEquals(
                ReservationStatus.PENDING,
                service
                        .getReservation(waiting.id())
                        .status()
        );
    }

    private ReservationView request() {
        return service.requestReservation(
                customer.getId(),
                tent.getId(),
                1,
                at(9),
                at(17),
                at(8),
                at(18),
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
