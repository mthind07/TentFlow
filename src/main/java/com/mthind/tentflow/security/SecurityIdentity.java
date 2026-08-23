package com.mthind.tentflow.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityIdentity {

    public Optional<CurrentUser> currentUser() {
        return from(SecurityContextHolder.getContext().getAuthentication());
    }

    public Optional<CurrentUser> from(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.empty();
        }

        Long userId = null;
        Long customerId = null;

        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            userId = numberClaim(jwtToken, "user_id");
            customerId = numberClaim(jwtToken, "customer_id");
        } else if (authentication.getPrincipal()
                instanceof TentFlowPrincipal principal) {
            userId = principal.userId();
            customerId = principal.customerId();
        }

        Role role = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> Role.valueOf(authority.substring(5)))
                .findFirst()
                .orElse(null);

        return Optional.of(new CurrentUser(
                userId,
                authentication.getName(),
                role,
                customerId
        ));
    }

    public boolean isStaffOrAdmin(Authentication authentication) {
        return hasRole(authentication, Role.STAFF)
                || hasRole(authentication, Role.ADMIN);
    }

    public boolean hasRole(Authentication authentication, Role role) {
        String requiredAuthority = "ROLE_" + role.name();
        return authentication != null
                && authentication.getAuthorities().stream().anyMatch(
                authority -> requiredAuthority.equals(
                        authority.getAuthority()
                )
        );
    }

    private Long numberClaim(
            JwtAuthenticationToken jwtToken,
            String claimName
    ) {
        Object value = jwtToken.getToken().getClaim(claimName);
        return value instanceof Number number ? number.longValue() : null;
    }

    public record CurrentUser(
            Long userId,
            String email,
            Role role,
            Long customerId
    ) {
    }
}