package com.mthind.tentflow.model;

//stores contact information for a customer
public final class Customer {

    private final long id;
    private final String fullName;
    private final String email;
    private final String phone;

    public Customer(
            long id,
            String fullName,
            String email,
            String phone
    ) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "Customer ID must be positive."
            );
        }

        this.fullName = requireText(
                fullName,
                "Full name"
        );

        String normalizedEmail = requireText(
                email,
                "Email"
        );

        this.phone = requireText(
                phone,
                "Phone"
        );

        //this is intentionally simple email validation
        //stronger validation exists at the API boundary
        int atPosition = normalizedEmail.indexOf('@');

        if (atPosition <= 0
                || atPosition != normalizedEmail.lastIndexOf('@')
                || atPosition == normalizedEmail.length() - 1
                || normalizedEmail
                .chars()
                .anyMatch(Character::isWhitespace)) {

            throw new IllegalArgumentException(
                    "Email must look like a valid email address."
            );
        }

        this.email = normalizedEmail;
        this.id = id;
    }

    private static String requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be blank."
            );
        }

        return value.trim();
    }

    public long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }
}
