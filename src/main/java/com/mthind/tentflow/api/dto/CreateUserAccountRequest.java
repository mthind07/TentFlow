package com.mthind.tentflow.api.dto;

import com.mthind.tentflow.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserAccountRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be valid.")
        @Size(max = 254, message = "Email must be at most 254 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(
                min = 12,
                max = 72,
                message = "Password must have at least 12 characters and at most 72 UTF-8 bytes."
        )
        String password,

        @NotNull(message = "Role is required.")
        Role role
) {

    //keep administrator-created account identity normalization consistent
    public CreateUserAccountRequest {
        email = email == null ? null : email.strip();
    }
}