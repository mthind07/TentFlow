package com.mthind.tentflow.persistence;

import com.mthind.tentflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class SecurityAuditSchemaIntegrationTest
        extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayV2AndDueCompletionIndexAreInstalled() {
        assertEquals(1, jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM flyway_schema_history
                WHERE version = '2' AND success
                """,
                Integer.class
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND indexname = 'idx_reservations_due_completion'
                """,
                Integer.class
        ));
    }

    @Test
    void accountIdentityRoleLinksAndAuditStatusesAreConstrained() {
        Long customerId = jdbcTemplate.queryForObject(
                """
                INSERT INTO customers (full_name, email, phone)
                VALUES ('Schema Customer', 'schema.customer@example.com',
                        '416-555-0105')
                RETURNING id
                """,
                Long.class
        );
        jdbcTemplate.update(
                """
                INSERT INTO user_accounts
                    (email, password_hash, role, customer_id, enabled,
                     created_at, version)
                VALUES (?, '{bcrypt}placeholder', 'CUSTOMER', ?, true, ?, 0)
                """,
                "schema.account@example.com",
                customerId,
                Timestamp.from(Instant.parse("2026-01-15T15:00:00Z"))
        );

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update(
                        """
                        INSERT INTO user_accounts
                            (email, password_hash, role, customer_id, enabled,
                             created_at, version)
                        VALUES (?, '{bcrypt}other', 'STAFF', null, true, ?, 0)
                        """,
                        "SCHEMA.ACCOUNT@EXAMPLE.COM",
                        Timestamp.from(Instant.parse(
                                "2026-01-15T15:00:00Z"
                        ))
                )
        );
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update(
                        """
                        INSERT INTO user_accounts
                            (email, password_hash, role, customer_id, enabled,
                             created_at, version)
                        VALUES (?, '{bcrypt}other', 'STAFF', ?, true, ?, 0)
                        """,
                        "linked.staff@example.com",
                        customerId,
                        Timestamp.from(Instant.parse(
                                "2026-01-15T15:00:00Z"
                        ))
                )
        );
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update(
                        """
                        INSERT INTO user_accounts
                            (email, password_hash, role, customer_id, enabled,
                             created_at, version)
                        VALUES (?, '{bcrypt}other', 'CUSTOMER', null, true,
                                ?, 0)
                        """,
                        "unlinked.customer@example.com",
                        Timestamp.from(Instant.parse(
                                "2026-01-15T15:00:00Z"
                        ))
                )
        );

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update(
                        """
                        INSERT INTO audit_events
                            (occurred_at, request_id, actor_email, action,
                             resource_type, resource_id, old_status,
                             new_status, outcome)
                        VALUES (?, ?, 'system:test', 'INVALID_STATUS_TEST',
                                'RESERVATION', '1', 'NOT_A_STATUS',
                                'PENDING', 'SUCCEEDED')
                        """,
                        Timestamp.from(Instant.parse(
                                "2026-01-15T15:00:00Z"
                        )),
                        UUID.randomUUID()
                )
        );
    }
}