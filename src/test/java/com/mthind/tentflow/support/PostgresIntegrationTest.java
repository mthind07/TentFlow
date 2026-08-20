package com.mthind.tentflow.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Starts one PostgreSQL 18 container for the integration-test JVM and gives
 * each test an empty, identity-reset database.
 */
@Import(PostgresIntegrationTest.FixedClockConfiguration.class)
public abstract class PostgresIntegrationTest {

    private static final String TEST_JWT_SECRET =
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.4-alpine")
                    .withDatabaseName("tentflow_test")
                    .withUsername("tentflow")
                    .withPassword("tentflow");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("tentflow.security.jwt-secret", () -> TEST_JWT_SECRET);
        registry.add("tentflow.security.issuer", () -> "tentflow-test");
        registry.add("tentflow.security.audience", () -> "tentflow-test-api");
        registry.add("tentflow.security.bcrypt-strength", () -> 4);
        registry.add("tentflow.automation.enabled", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    protected void resetDatabase() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE
                    audit_events,
                    user_accounts,
                    waitlist_entries,
                    maintenance_blocks,
                    reservations,
                    customers,
                    tent_types
                RESTART IDENTITY CASCADE
                """);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedTentFlowTestClock() {
            return Clock.fixed(
                    Instant.parse("2026-01-15T15:00:00Z"),
                    ZoneId.of("America/Toronto")
            );
        }
    }
}