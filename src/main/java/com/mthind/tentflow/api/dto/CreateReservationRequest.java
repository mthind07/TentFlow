package com.mthind.tentflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

//JSON body for a reservation request, the engine chooses PENDING or WAITLISTED
public record CreateReservationRequest(
        @NotNull(message = "Customer ID is required.")
        @Positive(message = "Customer ID must be positive.")
        Long customerId,

        @NotNull(message = "Tent ID is required.")
        @Positive(message = "Tent ID must be positive.")
        Long tentId,

        @NotNull(message = "Quantity is required.")
        @Positive(message = "Quantity must be positive.")
        Integer quantity,

        @NotNull(message = "Event start is required.")
        LocalDateTime eventStart,

        @NotNull(message = "Event end is required.")
        LocalDateTime eventEnd,

        @NotNull(message = "Reserved-from time is required.")
        LocalDateTime reservedFrom,

        @NotNull(message = "Reserved-until time is required.")
        LocalDateTime reservedUntil,

        @NotBlank(message = "Location is required.")
        @Size(
                max = 250,
                message = "Location must be at most 250 characters."
        )
        String location
) {
}
