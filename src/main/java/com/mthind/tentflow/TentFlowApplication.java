package com.mthind.tentflow;

import com.mthind.tentflow.model.Customer;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.model.TentType;
import com.mthind.tentflow.service.TentBookingService;

import java.time.LocalDateTime;

//demonstrates the completed Milestone 1 engine
public final class TentFlowApplication {

    private TentFlowApplication() {
    }

    public static void main(String[] args) {
        TentBookingService bookingService =
                new TentBookingService();

        Customer alice =
                bookingService.registerCustomer(
                        "Alice Johnson",
                        "alice@example.com",
                        "416-555-0101"
                );

        Customer bob =
                bookingService.registerCustomer(
                        "Bob Singh",
                        "bob@example.com",
                        "647-555-0102"
                );

        TentType fortyByForty =
                bookingService.addTentType(
                        40,
                        40,
                        1
                );

        LocalDateTime eventStart =
                LocalDateTime.of(
                        2027,
                        6,
                        12,
                        16,
                        0
                );

        LocalDateTime eventEnd =
                LocalDateTime.of(
                        2027,
                        6,
                        12,
                        23,
                        0
                );

        LocalDateTime reservedFrom =
                LocalDateTime.of(
                        2027,
                        6,
                        12,
                        10,
                        0
                );

        LocalDateTime reservedUntil =
                LocalDateTime.of(
                        2027,
                        6,
                        13,
                        10,
                        0
                );

        ReservationView aliceReservation =
                bookingService.requestReservation(
                        alice.getId(),
                        fortyByForty.getId(),
                        1,
                        eventStart,
                        eventEnd,
                        reservedFrom,
                        reservedUntil,
                        "Toronto, Ontario"
                );

        ReservationView bobReservation =
                bookingService.requestReservation(
                        bob.getId(),
                        fortyByForty.getId(),
                        1,
                        eventStart,
                        eventEnd,
                        reservedFrom,
                        reservedUntil,
                        "Mississauga, Ontario"
                );

        System.out.println(
                "=== TentFlow Milestone 1 demo ==="
        );

        System.out.println(
                "Inventory: 1 x "
                        + fortyByForty.getSizeLabel()
                        + " tent"
        );

        System.out.println(
                "Alice's first result: "
                        + aliceReservation.status()
        );

        System.out.println(
                "Bob's first result: "
                        + bobReservation.status()
        );

        int available =
                bookingService.getAvailableQuantity(
                        fortyByForty.getId(),
                        reservedFrom,
                        reservedUntil
                );

        System.out.println(
                "Available in that window: "
                        + available
        );

        bookingService.confirmReservation(
                aliceReservation.id()
        );

        aliceReservation =
                bookingService.getReservation(
                        aliceReservation.id()
                );

        System.out.println(
                "Alice after staff approval: "
                        + aliceReservation.status()
        );

        bookingService.cancelReservation(
                aliceReservation.id()
        );

        aliceReservation =
                bookingService.getReservation(
                        aliceReservation.id()
                );

        bobReservation =
                bookingService.getReservation(
                        bobReservation.id()
                );

        System.out.println(
                "Alice after cancellation: "
                        + aliceReservation.status()
        );

        System.out.println(
                "Bob after waitlist promotion: "
                        + bobReservation.status()
        );
    }
}
