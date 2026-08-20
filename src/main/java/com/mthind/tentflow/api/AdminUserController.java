package com.mthind.tentflow.api;

import com.mthind.tentflow.api.dto.CreateUserAccountRequest;
import com.mthind.tentflow.api.dto.UserAccountResponse;
import com.mthind.tentflow.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AccountService accountService;

    public AdminUserController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<UserAccountResponse> createAccount(
            @Valid @RequestBody CreateUserAccountRequest request
    ) {
        UserAccountResponse account =
                accountService.createPrivilegedAccount(request);
        return ResponseEntity
                .created(URI.create("/api/admin/users/" + account.id()))
                .body(account);
    }

    @GetMapping
    public List<UserAccountResponse> getAccounts() {
        return accountService.getAllAccounts();
    }

    @GetMapping("/{accountId}")
    public UserAccountResponse getAccount(@PathVariable long accountId) {
        return accountService.getAccount(accountId);
    }
}