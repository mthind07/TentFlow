package com.mthind.tentflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

//JSON body for temporarily removing inventory from service
public record CreateMaintenanceBlockRequest(
        @NotNull(message = "Tent ID is required.")
        @Positive(message = "Tent ID must be positive.")
        Long tentId,

        @NotNull(message = "Unavailable quantity is required.")
        @Positive(message = "Unavailable quantity must be positive.")
        Integer quantityUnavailable,

        @NotNull(message = "Maintenance start is required.")
        LocalDateTime from,

        @NotNull(message = "Maintenance end is required.")
        LocalDateTime until,

        @NotBlank(message = "Reason is required.")
        @Size(
                max = 250,
                message = "Reason must be at most 250 characters."
        )
        String reason
) {
}