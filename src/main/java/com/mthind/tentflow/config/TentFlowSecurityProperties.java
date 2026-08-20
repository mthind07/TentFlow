package com.mthind.tentflow.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "tentflow.security")
public record TentFlowSecurityProperties(
        @NotBlank String jwtSecret,
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotNull Duration accessTokenTtl,
        @Min(4) @Max(16) int bcryptStrength,
        String bootstrapAdminEmail,
        String bootstrapAdminPassword
) {

    public TentFlowSecurityProperties {
        if (accessTokenTtl != null
                && (accessTokenTtl.isZero()
                || accessTokenTtl.isNegative()
                || accessTokenTtl.compareTo(Duration.ofHours(24)) > 0)) {
            throw new IllegalArgumentException(
                    "Access-token TTL must be greater than zero "
                            + "and no longer than 24 hours."
            );
        }
    }
}