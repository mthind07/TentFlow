package com.mthind.tentflow.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 100, message = "Full name must be at most 100 characters.")
        String fullName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be valid.")
        @Size(max = 254, message = "Email must be at most 254 characters.")
        String email,

        @NotBlank(message = "Phone is required.")
        @Size(max = 40, message = "Phone must be at most 40 characters.")
        String phone,

        @NotBlank(message = "Password is required.")
        @Size(
                min = 12,
                max = 72,
                message = "Password must have at least 12 characters and at most 72 UTF-8 bytes."
        )
        String password
) {

    //normalize harmless surrounding whitespace before Bean Validation runs
    //keeps the HTTP contract consistent with the account service
    //treats email identity case-insensitively after trimming it
    //password is intentionally left unchanged
    //whitespace may be part of a password and silently trimming it would change the user's secret
    public RegisterRequest {
        fullName = stripIfPresent(fullName);
        email = stripIfPresent(email);
        phone = stripIfPresent(phone);
    }

    private static String stripIfPresent(String value) {
        return value == null ? null : value.strip();
    }
}