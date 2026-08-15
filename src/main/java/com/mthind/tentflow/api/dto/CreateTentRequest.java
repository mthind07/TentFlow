package com.mthind.tentflow.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

//JSON body accepted when staff adds an inventory type
public record CreateTentRequest(
        @NotNull(message = "Width is required.")
        @Min(
                value = 10,
                message = "Width must be at least 10 feet."
        )
        @Max(
                value = 40,
                message = "Width must be at most 40 feet."
        )
        Integer widthFeet,

        @NotNull(message = "Length is required.")
        @Min(
                value = 10,
                message = "Length must be at least 10 feet."
        )
        @Max(
                value = 40,
                message = "Length must be at most 40 feet."
        )
        Integer lengthFeet,

        @NotNull(message = "Total quantity is required.")
        @Positive(message = "Total quantity must be positive.")
        Integer totalQuantity
) {
}
