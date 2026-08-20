package com.mthind.tentflow.security;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

//enforces BCrypt's 72-byte input limit before delegating to the configured encoder
//returning {@code false} from {@link #matches}
//keeps an oversized browser-login attempt on Spring Security's ordinary bad-credentials path instead of allowing BCrypt to throw an exception
public final class Utf8BoundedPasswordEncoder implements PasswordEncoder {

    public static final int MAXIMUM_UTF8_BYTES = 72;

    private final PasswordEncoder delegate;

    public Utf8BoundedPasswordEncoder(PasswordEncoder delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (!withinLimit(rawPassword)) {
            throw new IllegalArgumentException(
                    "Password must contain at most 72 UTF-8 bytes."
            );
        }
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(
            CharSequence rawPassword,
            String encodedPassword
    ) {
        return withinLimit(rawPassword)
                && delegate.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }

    private boolean withinLimit(CharSequence rawPassword) {
        return rawPassword != null
                && rawPassword.toString()
                .getBytes(StandardCharsets.UTF_8).length
                <= MAXIMUM_UTF8_BYTES;
    }
}