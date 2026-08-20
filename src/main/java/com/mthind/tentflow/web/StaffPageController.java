package com.mthind.tentflow.web;

import com.mthind.tentflow.exception.InsufficientAvailabilityException;
import com.mthind.tentflow.exception.InvalidReservationStateException;
import com.mthind.tentflow.exception.ResourceNotFoundException;
import com.mthind.tentflow.service.BookingService;
import com.mthind.tentflow.service.BookingWorkflowService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class StaffPageController {

    private final BookingService bookingService;
    private final BookingWorkflowService workflowService;

    public StaffPageController(
            BookingService bookingService,
            BookingWorkflowService workflowService
    ) {
        this.bookingService = bookingService;
        this.workflowService = workflowService;
    }

    @GetMapping("/staff/reservations")
    public String reservations(Model model) {
        model.addAttribute(
                "reservations",
                bookingService.getAllReservations()
        );
        return "staff-reservations";
    }

    @PostMapping("/staff/reservations/{reservationId}/confirm")
    public String confirm(
            @PathVariable long reservationId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            workflowService.confirmReservation(reservationId);
            return success(redirectAttributes, reservationId, "confirmed");
        } catch (ResourceNotFoundException
                 | InvalidReservationStateException
                 | InsufficientAvailabilityException exception) {
            return staleState(redirectAttributes);
        }
    }

    @PostMapping("/staff/reservations/{reservationId}/reject")
    public String reject(
            @PathVariable long reservationId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            workflowService.rejectReservation(reservationId);
            return success(redirectAttributes, reservationId, "rejected");
        } catch (ResourceNotFoundException
                 | InvalidReservationStateException exception) {
            return staleState(redirectAttributes);
        }
    }

    @PostMapping("/staff/reservations/{reservationId}/complete")
    public String complete(
            @PathVariable long reservationId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            workflowService.completeReservation(reservationId);
            return success(redirectAttributes, reservationId, "completed");
        } catch (ResourceNotFoundException
                 | InvalidReservationStateException exception) {
            return staleState(redirectAttributes);
        }
    }

    private String success(
            RedirectAttributes redirectAttributes,
            long reservationId,
            String action
    ) {
        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Reservation " + reservationId + " was " + action + "."
        );
        return "redirect:/staff/reservations";
    }

    private String staleState(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute(
                "errorMessage",
                "That reservation changed before your action was applied."
        );
        return "redirect:/staff/reservations";
    }
}