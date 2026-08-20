package com.mthind.tentflow.api.dto;

import com.mthind.tentflow.security.Role;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        Instant expiresAt,
        long userId,
        String email,
        Role role,
        Long customerId
) {
}