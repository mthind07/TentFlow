package com.mthind.tentflow.web;

import com.mthind.tentflow.api.dto.RegisterRequest;
import com.mthind.tentflow.exception.DuplicateResourceException;
import com.mthind.tentflow.security.Role;
import com.mthind.tentflow.security.SecurityIdentity;
import com.mthind.tentflow.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class PublicPageController {

    private final AccountService accountService;
    private final SecurityIdentity securityIdentity;

    public PublicPageController(
            AccountService accountService,
            SecurityIdentity securityIdentity
    ) {
        this.accountService = accountService;
        this.securityIdentity = securityIdentity;
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        if (securityIdentity.hasRole(authentication, Role.CUSTOMER)) {
            return "redirect:/customer/reservations";
        }
        if (securityIdentity.isStaffOrAdmin(authentication)) {
            return "redirect:/staff/reservations";
        }
        return "home";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute RegistrationForm registrationForm,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            registrationForm.setPassword("");
            return "register";
        }

        try {
            accountService.registerCustomer(new RegisterRequest(
                    registrationForm.getFullName(),
                    registrationForm.getEmail(),
                    registrationForm.getPhone(),
                    registrationForm.getPassword()
            ));
        } catch (DuplicateResourceException | IllegalArgumentException exception) {
            bindingResult.reject("registration", exception.getMessage());
            registrationForm.setPassword("");
            return "register";
        }

        return "redirect:/login?registered";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }
}