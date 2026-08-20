package com.mthind.tentflow.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationForm {

    @NotBlank(message = "Full name is required.")
    @Size(max = 100)
    private String fullName;

    @NotBlank(message = "Email is required.")
    @Email(message = "Enter a valid email address.")
    @Size(max = 254)
    private String email;

    @NotBlank(message = "Phone is required.")
    @Size(max = 40)
    private String phone;

    @NotBlank(message = "Password is required.")
    @Size(
            min = 12,
            max = 72,
            message = "Password must have at least 12 characters and at most 72 UTF-8 bytes."
    )
    private String password;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = stripIfPresent(fullName);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        // Normalize before Bean Validation so browser registration follows
        // the same whitespace policy as the JSON registration endpoint.
        this.email = stripIfPresent(email);
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = stripIfPresent(phone);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    private String stripIfPresent(String value) {
        return value == null ? null : value.strip();
    }
}