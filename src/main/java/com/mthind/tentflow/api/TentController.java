package com.mthind.tentflow.api;

import com.mthind.tentflow.api.dto.AvailabilityResponse;
import com.mthind.tentflow.api.dto.CreateTentRequest;
import com.mthind.tentflow.api.dto.TentResponse;
import com.mthind.tentflow.api.dto.WaitlistEntryResponse;
import com.mthind.tentflow.model.TentType;
import com.mthind.tentflow.service.TentBookingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

//HTTP boundary for tent inventory, availability, and waitlists
@RestController
@RequestMapping("/api/tents")
public class TentController {

    private final TentBookingService bookingService;
    private final ApiMapper mapper;

    public TentController(
            TentBookingService bookingService,
            ApiMapper mapper
    ) {
        this.bookingService = bookingService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<TentResponse> createTent(
            @Valid @RequestBody CreateTentRequest request
    ) {
        TentType tentType =
                bookingService.addTentType(
                        request.widthFeet(),
                        request.lengthFeet(),
                        request.totalQuantity()
                );

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(tentType.getId())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(
                        mapper.toTentResponse(tentType)
                );
    }

    @GetMapping
    public List<TentResponse> getTents() {
        return bookingService
                .getAllTentTypes()
                .stream()
                .map(mapper::toTentResponse)
                .toList();
    }

    @GetMapping("/{tentId}")
    public TentResponse getTent(
            @PathVariable long tentId
    ) {
        return mapper.toTentResponse(
                bookingService.getTentType(tentId)
        );
    }

    @GetMapping("/{tentId}/availability")
    public AvailabilityResponse getAvailability(
            @PathVariable long tentId,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime from,

            @RequestParam
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime until
    ) {
        TentType tentType =
                bookingService.getTentType(tentId);

        int available =
                bookingService.getAvailableQuantity(
                        tentId,
                        from,
                        until
                );

        return new AvailabilityResponse(
                tentType.getId(),
                tentType.getSizeLabel(),
                from,
                until,
                available,
                tentType.getTotalQuantity()
        );
    }

    @GetMapping("/{tentId}/waitlist")
    public List<WaitlistEntryResponse> getWaitlist(
            @PathVariable long tentId
    ) {
        return bookingService
                .getWaitlistForTent(tentId)
                .stream()
                .map(mapper::toWaitlistEntryResponse)
                .toList();
    }
}