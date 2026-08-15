package com.mthind.tentflow.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

//JSON body accepted when a customer is registered
public record CreateCustomerRequest(
        @NotBlank(message = "Full name is required.")
        @Size(
                max = 100,
                message = "Full name must be at most 100 characters."
        )
        String fullName,

        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid email address.")
        @Size(
                max = 254,
                message = "Email must be at most 254 characters."
        )
        String email,

        @NotBlank(message = "Phone is required.")
        @Size(
                max = 40,
                message = "Phone must be at most 40 characters."
        )
        String phone
) {
}