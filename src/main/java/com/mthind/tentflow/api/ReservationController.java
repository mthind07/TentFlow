package com.mthind.tentflow.api;

import com.mthind.tentflow.api.dto.CreateReservationRequest;
import com.mthind.tentflow.api.dto.ReservationResponse;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.service.BookingService;
import com.mthind.tentflow.service.BookingWorkflowService;
import com.mthind.tentflow.security.BookingAuthorization;
import com.mthind.tentflow.service.CustomerReservationQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

//HTTP boundary for reservation requests and staff status actions
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final BookingService bookingService;
    private final ApiMapper mapper;
    private final BookingWorkflowService workflowService;
    private final BookingAuthorization bookingAuthorization;
    private final CustomerReservationQueryService customerReservations;

    public ReservationController(
            BookingService bookingService,
            ApiMapper mapper,
            BookingWorkflowService workflowService,
            BookingAuthorization bookingAuthorization,
            CustomerReservationQueryService customerReservations
    ) {
        this.bookingService = bookingService;
        this.mapper = mapper;
        this.workflowService = workflowService;
        this.bookingAuthorization = bookingAuthorization;
        this.customerReservations = customerReservations;
    }

    @PostMapping
    @PreAuthorize("@bookingAuthorization.canCreateReservation(authentication, #request.customerId())")
    public ResponseEntity<ReservationResponse> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            Authentication authentication
    ) {
        ReservationView reservation = bookingAuthorization
                .isStaffOrAdmin(authentication)
                ? workflowService.requestReservation(
                request.customerId(),
                request.tentId(),
                request.quantity(),
                request.eventStart(),
                request.eventEnd(),
                request.reservedFrom(),
                request.reservedUntil(),
                request.location()
        )
                : workflowService.requestCustomerReservation(
                request.customerId(),
                request.tentId(),
                request.quantity(),
                request.eventStart(),
                request.eventEnd(),
                request.reservedFrom(),
                request.reservedUntil(),
                request.location()
        );

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(reservation.id())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(mapper.toReservationResponse(reservation));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ReservationResponse> getReservations(
            Authentication authentication
    ) {
        List<ReservationView> reservations =
                bookingAuthorization.isStaffOrAdmin(authentication)
                        ? bookingService.getAllReservations()
                        : customerReservations.findForCustomer(
                        bookingAuthorization.customerId(authentication)
                );
        return reservations
                .stream()
                .map(mapper::toReservationResponse)
                .toList();
    }

    @GetMapping("/{reservationId}")
    @PreAuthorize("@bookingAuthorization.canAccessReservation(authentication, #reservationId)")
    public ReservationResponse getReservation(
            @PathVariable long reservationId
    ) {
        return responseFor(reservationId);
    }

    @PostMapping("/{reservationId}/confirm")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ReservationResponse confirmReservation(
            @PathVariable long reservationId
    ) {
        return mapper.toReservationResponse(
                workflowService.confirmReservation(reservationId)
        );
    }

    @PostMapping("/{reservationId}/reject")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ReservationResponse rejectReservation(
            @PathVariable long reservationId
    ) {
        return mapper.toReservationResponse(
                workflowService.rejectReservation(reservationId)
        );
    }

    @PostMapping("/{reservationId}/complete")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ReservationResponse completeReservation(
            @PathVariable long reservationId
    ) {
        return mapper.toReservationResponse(
                workflowService.completeReservation(reservationId)
        );
    }

    @PostMapping("/{reservationId}/cancel")
    @PreAuthorize("@bookingAuthorization.canAccessReservation(authentication, #reservationId)")
    public ReservationResponse cancelReservation(
            @PathVariable long reservationId
    ) {
        return mapper.toReservationResponse(
                workflowService.cancelReservation(reservationId)
        );
    }

    private ReservationResponse responseFor(long reservationId) {
        return mapper.toReservationResponse(
                bookingService.getReservation(reservationId)
        );
    }
}