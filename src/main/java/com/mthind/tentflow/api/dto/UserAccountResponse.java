package com.mthind.tentflow.api.dto;

import com.mthind.tentflow.security.Role;

import java.time.Instant;

public record UserAccountResponse(
        long id,
        String email,
        Role role,
        Long customerId,
        boolean enabled,
        Instant createdAt
) {
}