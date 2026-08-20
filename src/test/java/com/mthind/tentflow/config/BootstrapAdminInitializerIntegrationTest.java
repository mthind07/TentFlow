package com.mthind.tentflow.config;

import com.mthind.tentflow.service.AccountService;
import com.mthind.tentflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class BootstrapAdminInitializerIntegrationTest
        extends PostgresIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void absentBootstrapPairIsANoOp() {
        initializer("", "").run(null);

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_accounts",
                Integer.class
        ));
    }

    @Test
    void partialBootstrapPairFailsFast() {
        assertThrows(
                IllegalStateException.class,
                () -> initializer(
                        "admin@example.com",
                        ""
                ).run(null)
        );
        assertThrows(
                IllegalStateException.class,
                () -> initializer(
                        "",
                        "Admin-Password-42"
                ).run(null)
        );
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_accounts",
                Integer.class
        ));
    }

    @Test
    void completePairCreatesAdminOnceAndNeverOverwritesItsHash() {
        String originalPassword = "Original-Admin-Password-42";
        initializer("ADMIN@Example.com", originalPassword).run(null);

        String originalHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_accounts "
                        + "WHERE email = 'admin@example.com'",
                String.class
        );
        assertTrue(originalHash.startsWith("{bcrypt}"));
        assertFalse(originalHash.contains(originalPassword));
        assertEquals("ADMIN", jdbcTemplate.queryForObject(
                "SELECT role FROM user_accounts "
                        + "WHERE email = 'admin@example.com'",
                String.class
        ));

        initializer(
                "admin@example.com",
                "Replacement-Admin-Password-42"
        ).run(null);

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_accounts "
                        + "WHERE email = 'admin@example.com'",
                Integer.class
        ));
        assertEquals(originalHash, jdbcTemplate.queryForObject(
                "SELECT password_hash FROM user_accounts "
                        + "WHERE email = 'admin@example.com'",
                String.class
        ));
    }

    private BootstrapAdminInitializer initializer(
            String email,
            String password
    ) {
        TentFlowSecurityProperties properties =
                new TentFlowSecurityProperties(
                        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                        "tentflow-test",
                        "tentflow-test-api",
                        Duration.ofMinutes(15),
                        4,
                        email,
                        password
                );
        return new BootstrapAdminInitializer(properties, accountService);
    }
}