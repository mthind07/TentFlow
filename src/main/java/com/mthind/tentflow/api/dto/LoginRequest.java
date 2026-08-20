package com.mthind.tentflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required.")
        @Size(max = 254, message = "Email must be at most 254 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(max = 72, message = "Password must be at most 72 UTF-8 bytes.")
        String password
) {
}