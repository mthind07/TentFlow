package com.mthind.tentflow.api;

import com.mthind.tentflow.api.dto.CreateReservationRequest;
import com.mthind.tentflow.api.dto.ReservationResponse;
import com.mthind.tentflow.model.ReservationView;
import com.mthind.tentflow.service.TentBookingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    private final TentBookingService bookingService;
    private final ApiMapper mapper;

    public ReservationController(
            TentBookingService bookingService,
            ApiMapper mapper
    ) {
        this.bookingService = bookingService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @Valid @RequestBody CreateReservationRequest request
    ) {
        ReservationView reservation =
                bookingService.requestReservation(
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
                .body(
                        mapper.toReservationResponse(
                                reservation
                        )
                );
    }

    @GetMapping
    public List<ReservationResponse> getReservations() {
        return bookingService
                .getAllReservations()
                .stream()
                .map(mapper::toReservationResponse)
                .toList();
    }

    @GetMapping("/{reservationId}")
    public ReservationResponse getReservation(
            @PathVariable long reservationId
    ) {
        return responseFor(reservationId);
    }

    @PostMapping("/{reservationId}/confirm")
    public ReservationResponse confirmReservation(
            @PathVariable long reservationId
    ) {
        return mapper.toReservationResponse(
                bookingService.confirmReservation(
                        reservationId
                )
        );
    }

    @PostMapping("/{reservationId}/reject")
    public ReservationResponse rejectReservation(
            @PathVariable long reservationId
    ) {
        return mapper.toReservationResponse(
                bookingService.rejectReservation(
                        reservationId
                )
        );
    }

    @PostMapping("/{reservationId}/complete")
    public ReservationResponse completeReservation(
            @PathVariable long reservationId
    ) {
        return mapper.toReservationResponse(
                bookingService.completeReservation(
                        reservationId
                )
        );
    }

    @PostMapping("/{reservationId}/cancel")
    public ReservationResponse cancelReservation(
            @PathVariable long reservationId
    ) {
        return mapper.toReservationResponse(
                bookingService.cancelReservation(
                        reservationId
                )
        );
    }

    private ReservationResponse responseFor(
            long reservationId
    ) {
        return mapper.toReservationResponse(
                bookingService.getReservation(
                        reservationId
                )
        );
    }
}