package com.mthind.tentflow.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

public final class TentFlowPrincipal implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long userId;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final Long customerId;
    private final boolean enabled;

    public TentFlowPrincipal(
            long userId,
            String email,
            String passwordHash,
            Role role,
            Long customerId,
            boolean enabled
    ) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.customerId = customerId;
        this.enabled = enabled;
    }

    public long userId() {
        return userId;
    }

    public Role role() {
        return role;
    }

    public Long customerId() {
        return customerId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}