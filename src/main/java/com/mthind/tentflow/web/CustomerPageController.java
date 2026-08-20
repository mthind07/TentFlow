package com.mthind.tentflow.web;

import com.mthind.tentflow.exception.InsufficientAvailabilityException;
import com.mthind.tentflow.exception.InvalidReservationStateException;
import com.mthind.tentflow.exception.ResourceNotFoundException;
import com.mthind.tentflow.security.BookingAuthorization;
import com.mthind.tentflow.service.BookingService;
import com.mthind.tentflow.service.BookingWorkflowService;
import com.mthind.tentflow.service.CustomerReservationQueryService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class CustomerPageController {

    private final BookingService bookingService;
    private final BookingWorkflowService workflowService;
    private final CustomerReservationQueryService customerReservations;
    private final BookingAuthorization bookingAuthorization;

    public CustomerPageController(
            BookingService bookingService,
            BookingWorkflowService workflowService,
            CustomerReservationQueryService customerReservations,
            BookingAuthorization bookingAuthorization
    ) {
        this.bookingService = bookingService;
        this.workflowService = workflowService;
        this.customerReservations = customerReservations;
        this.bookingAuthorization = bookingAuthorization;
    }

    @GetMapping("/customer/reservations")
    public String reservations(
            Authentication authentication,
            Model model
    ) {
        long customerId = bookingAuthorization.customerId(authentication);
        model.addAttribute(
                "customer",
                bookingService.getCustomer(customerId)
        );
        model.addAttribute(
                "reservations",
                customerReservations.findForCustomer(customerId)
        );
        return "customer-reservations";
    }

    @GetMapping("/customer/reservations/new")
    public String newReservation(Model model) {
        if (!model.containsAttribute("reservationForm")) {
            model.addAttribute("reservationForm", new ReservationForm());
        }
        model.addAttribute("tents", bookingService.getAllTentTypes());
        return "customer-new-reservation";
    }

    @PostMapping("/customer/reservations")
    public String createReservation(
            @Valid @ModelAttribute ReservationForm reservationForm,
            BindingResult bindingResult,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("tents", bookingService.getAllTentTypes());
            return "customer-new-reservation";
        }

        try {
            long customerId = bookingAuthorization.customerId(authentication);
            var reservation = workflowService.requestCustomerReservation(
                    customerId,
                    reservationForm.getTentId(),
                    reservationForm.getQuantity(),
                    reservationForm.getEventStart(),
                    reservationForm.getEventEnd(),
                    reservationForm.getReservedFrom(),
                    reservationForm.getReservedUntil(),
                    reservationForm.getLocation()
            );
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Reservation "
                            + reservation.id()
                            + " was created as "
                            + reservation.status()
                            + "."
            );
            return "redirect:/customer/reservations";
        } catch (IllegalArgumentException
                 | ResourceNotFoundException
                 | InsufficientAvailabilityException exception) {
            bindingResult.reject("reservation", exception.getMessage());
            model.addAttribute("tents", bookingService.getAllTentTypes());
            return "customer-new-reservation";
        }
    }

    @PostMapping("/customer/reservations/{reservationId}/cancel")
    @PreAuthorize("@bookingAuthorization.canAccessReservation(authentication, #reservationId)")
    public String cancelReservation(
            @PathVariable long reservationId,
            RedirectAttributes redirectAttributes
    ) {
        try {
            workflowService.cancelReservation(reservationId);
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Reservation " + reservationId + " was cancelled."
            );
        } catch (ResourceNotFoundException
                 | InvalidReservationStateException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "That reservation can no longer be cancelled."
            );
        }
        return "redirect:/customer/reservations";
    }
}