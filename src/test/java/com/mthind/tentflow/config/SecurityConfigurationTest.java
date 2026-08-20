package com.mthind.tentflow.config;

import com.mthind.tentflow.security.Utf8BoundedPasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigurationTest {

    private final SecurityConfiguration configuration =
            new SecurityConfiguration();

    @Test
    void jwtSecretMustBeBase64AndAtLeast256Bits() {
        assertThrows(
                IllegalStateException.class,
                () -> configuration.jwtSecretKey(properties("not-base64!"))
        );
        assertThrows(
                IllegalStateException.class,
                () -> configuration.jwtSecretKey(properties("c2hvcnQ="))
        );
        assertTrue(
                configuration.jwtSecretKey(properties(
                        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
                )).getEncoded().length >= 32
        );
    }

    @Test
    void utf8BoundedEncoderRejectsOversizedValuesWithoutCallingBcrypt() {
        Utf8BoundedPasswordEncoder encoder =
                new Utf8BoundedPasswordEncoder(
                        new BCryptPasswordEncoder(4)
                );
        String valid = "Correct-Horse-42";
        String encoded = encoder.encode(valid);

        assertTrue(encoder.matches(valid, encoded));
        assertFalse(encoder.matches("🛡️".repeat(25), encoded));
        assertThrows(
                IllegalArgumentException.class,
                () -> encoder.encode("🛡️".repeat(25))
        );
    }

    private TentFlowSecurityProperties properties(String secret) {
        return new TentFlowSecurityProperties(
                secret,
                "tentflow-test",
                "tentflow-test-api",
                Duration.ofMinutes(15),
                4,
                "",
                ""
        );
    }
}