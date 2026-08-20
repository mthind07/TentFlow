package com.mthind.tentflow.security;

import com.mthind.tentflow.api.dto.TokenResponse;
import com.mthind.tentflow.config.TentFlowSecurityProperties;
import com.mthind.tentflow.persistence.entity.UserAccountEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final TentFlowSecurityProperties properties;
    private final Clock clock;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            TentFlowSecurityProperties properties,
            Clock clock
    ) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    public TokenResponse issue(UserAccountEntity account) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        Long customerId = account.getCustomer() == null
                ? null
                : account.getCustomer().getId();

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(account.getEmail())
                .audience(List.of(properties.audience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("user_id", Objects.requireNonNull(account.getId()))
                .claim("roles", List.of(account.getRole().name()));

        if (customerId != null) {
            claims.claim("customer_id", customerId);
        }

        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims.build()
        )).getTokenValue();

        return new TokenResponse(
                token,
                "Bearer",
                properties.accessTokenTtl().toSeconds(),
                expiresAt,
                Objects.requireNonNull(account.getId()),
                account.getEmail(),
                account.getRole(),
                customerId
        );
    }
}