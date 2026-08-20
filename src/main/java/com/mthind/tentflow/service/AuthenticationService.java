package com.mthind.tentflow.service;

import com.mthind.tentflow.api.dto.LoginRequest;
import com.mthind.tentflow.api.dto.TokenResponse;
import com.mthind.tentflow.api.dto.UserAccountResponse;
import com.mthind.tentflow.audit.AuditService;
import com.mthind.tentflow.exception.InvalidCredentialsException;
import com.mthind.tentflow.persistence.entity.UserAccountEntity;
import com.mthind.tentflow.security.JwtTokenService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.nio.charset.StandardCharsets;

@Service
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final AccountService accountService;
    private final JwtTokenService jwtTokenService;
    private final AuditService auditService;

    public AuthenticationService(
            AuthenticationManager authenticationManager,
            AccountService accountService,
            JwtTokenService jwtTokenService,
            AuditService auditService
    ) {
        this.authenticationManager = authenticationManager;
        this.accountService = accountService;
        this.jwtTokenService = jwtTokenService;
        this.auditService = auditService;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            auditService.loginFailure();
            throw new InvalidCredentialsException();
        }

        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            email,
                            request.password()
                    )
            );
        } catch (AuthenticationException exception) {
            auditService.loginFailure();
            throw new InvalidCredentialsException();
        }

        UserAccountEntity account = accountService.requireByEmail(email);
        TokenResponse token = jwtTokenService.issue(account);
        auditService.successAs(
                account.getId(),
                account.getEmail(),
                "LOGIN",
                "AUTHENTICATION",
                null,
                null
        );
        return token;
    }

    @Transactional(readOnly = true)
    public UserAccountResponse currentUser(String email) {
        return accountService.toResponse(
                accountService.requireByEmail(email)
        );
    }

    private String normalizeEmail(String email) {
        return email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);
    }
}