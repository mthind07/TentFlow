package com.mthind.tentflow.api;

import com.mthind.tentflow.api.dto.LoginRequest;
import com.mthind.tentflow.api.dto.RegisterRequest;
import com.mthind.tentflow.api.dto.TokenResponse;
import com.mthind.tentflow.api.dto.UserAccountResponse;
import com.mthind.tentflow.service.AccountService;
import com.mthind.tentflow.service.AuthenticationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AccountService accountService;
    private final AuthenticationService authenticationService;

    public AuthController(
            AccountService accountService,
            AuthenticationService authenticationService
    ) {
        this.accountService = accountService;
        this.authenticationService = authenticationService;
    }

    @PostMapping("/register")
    @SecurityRequirements
    public ResponseEntity<UserAccountResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        UserAccountResponse account = accountService.registerCustomer(request);
        return ResponseEntity
                .created(URI.create("/api/auth/me"))
                .body(account);
    }

    @PostMapping("/login")
    @SecurityRequirements
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.login(request);
    }

    @GetMapping("/me")
    public UserAccountResponse me(Authentication authentication) {
        return authenticationService.currentUser(authentication.getName());
    }
}