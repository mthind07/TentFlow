package com.mthind.tentflow.persistence;

import com.mthind.tentflow.persistence.repository.TentTypeRepository;
import com.mthind.tentflow.service.BookingService;
import com.mthind.tentflow.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class DatabaseSchemaIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private TentTypeRepository tentTypeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void flywayCreatedTheDeliberateHybridTimeSchema() {
        assertEquals(
                "timestamp without time zone",
                columnType("reservations", "event_start")
        );
        assertEquals(
                "timestamp without time zone",
                columnType("maintenance_blocks", "blocked_from")
        );
        assertEquals(
                "timestamp with time zone",
                columnType("waitlist_entries", "joined_at")
        );
        assertEquals(6, columnPrecision("reservations", "event_start"));
        assertEquals(6, columnPrecision(
                "maintenance_blocks",
                "blocked_from"
        ));
        assertEquals(6, columnPrecision("waitlist_entries", "joined_at"));

        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT count(*)
                        FROM flyway_schema_history
                        WHERE version = '1' AND success
                        """,
                        Integer.class
                )
        );
    }

    @Test
    void postgresEnforcesCaseInsensitiveEmailAndWaitlistForeignKey() {
        jdbcTemplate.update(
                """
                INSERT INTO customers (full_name, email, phone)
                VALUES (?, ?, ?)
                """,
                "Database Customer",
                "database@example.com",
                "416-555-0101"
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO customers (full_name, email, phone)
                        VALUES (?, ?, ?)
                        """,
                        "Duplicate Customer",
                        "DATABASE@EXAMPLE.COM",
                        "416-555-0102"
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        """
                        INSERT INTO waitlist_entries (
                            reservation_id,
                            joined_at
                        ) VALUES (?, now())
                        """,
                        999_999L
                )
        );
    }

    @Test
    void postgresConstraintsRejectImpossibleBusinessRows() {
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertTent(9, 20, 1)
        );
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertTent(30, 20, 1)
        );
        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertTent(10, 11, 0)
        );

        long customerId = insertCustomer(
                "Constraint Customer",
                "constraints@example.com"
        );
        long tentTypeId = insertTent(20, 30, 2);

        LocalDateTime eight =
                LocalDateTime.of(2027, 6, 12, 8, 0);
        LocalDateTime ten =
                LocalDateTime.of(2027, 6, 12, 10, 0);
        LocalDateTime twelve =
                LocalDateTime.of(2027, 6, 12, 12, 0);
        LocalDateTime sixteen =
                LocalDateTime.of(2027, 6, 12, 16, 0);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertReservation(
                        customerId,
                        tentTypeId,
                        twelve,
                        ten,
                        eight,
                        sixteen,
                        "PENDING"
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertReservation(
                        customerId,
                        tentTypeId,
                        ten,
                        twelve,
                        twelve,
                        sixteen,
                        "PENDING"
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertReservation(
                        customerId,
                        tentTypeId,
                        ten,
                        twelve,
                        eight,
                        sixteen,
                        "UNKNOWN"
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertMaintenance(
                        tentTypeId,
                        1,
                        twelve,
                        ten,
                        "Inspection"
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertMaintenance(
                        tentTypeId,
                        0,
                        eight,
                        sixteen,
                        "Inspection"
                )
        );
    }

    @Test
    void proxiedServiceJoinsOuterTransactionAndRollsBackAllWrites() {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        assertThrows(
                IntentionalRollback.class,
                () -> transaction.executeWithoutResult(status -> {
                    bookingService.registerCustomer(
                            "Rollback Customer",
                            "rollback@example.com",
                            "416-555-0111"
                    );
                    bookingService.addTentType(10, 20, 1);
                    throw new IntentionalRollback();
                })
        );

        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM customers",
                        Integer.class
                )
        );
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM tent_types",
                        Integer.class
                )
        );
    }

    @Test
    void repeatableReadKeepsOneSnapshotAcrossSeparateQueries()
            throws Exception {
        long customerId = insertCustomer(
                "Snapshot Customer",
                "snapshot@example.com"
        );
        long tentTypeId = insertTent(20, 30, 1);

        CountDownLatch readerHasSnapshot = new CountDownLatch(1);
        CountDownLatch writerCommitted = new CountDownLatch(1);
        AtomicInteger firstCount = new AtomicInteger(-1);
        AtomicInteger secondCount = new AtomicInteger(-1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> reader = executor.submit(() -> {
            TransactionTemplate transaction =
                    new TransactionTemplate(transactionManager);
            transaction.setReadOnly(true);
            transaction.setIsolationLevel(
                    TransactionDefinition.ISOLATION_REPEATABLE_READ
            );

            transaction.executeWithoutResult(status -> {
                firstCount.set(reservationCount());
                readerHasSnapshot.countDown();
                await(writerCommitted);
                secondCount.set(reservationCount());
            });
        });

        Future<?> writer = executor.submit(() -> {
            await(readerHasSnapshot);
            try {
                new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> insertReservation(
                                customerId,
                                tentTypeId,
                                LocalDateTime.of(2027, 6, 12, 10, 0),
                                LocalDateTime.of(2027, 6, 12, 12, 0),
                                LocalDateTime.of(2027, 6, 12, 8, 0),
                                LocalDateTime.of(2027, 6, 12, 16, 0),
                                "PENDING"
                        ));
            } finally {
                writerCommitted.countDown();
            }
        });

        try {
            reader.get(10, TimeUnit.SECONDS);
            writer.get(10, TimeUnit.SECONDS);
            assertEquals(0, firstCount.get());
            assertEquals(0, secondCount.get());
            assertEquals(1, reservationCount());
        } finally {
            readerHasSnapshot.countDown();
            writerCommitted.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void pessimisticTentRowLockSerializesIndependentTransactions()
            throws Exception {
        long tentTypeId =
                bookingService.addTentType(30, 40, 1).getId();

        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        CountDownLatch secondAttemptedLock = new CountDownLatch(1);
        CountDownLatch secondHasLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> first = executor.submit(() ->
                new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> {
                            tentTypeRepository
                                    .findByIdForUpdate(tentTypeId)
                                    .orElseThrow();
                            firstHasLock.countDown();
                            await(allowFirstCommit);
                        })
        );

        try {
            assertTrue(firstHasLock.await(10, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() ->
                    new TransactionTemplate(transactionManager)
                            .executeWithoutResult(status -> {
                                secondAttemptedLock.countDown();
                                tentTypeRepository
                                        .findByIdForUpdate(tentTypeId)
                                        .orElseThrow();
                                secondHasLock.countDown();
                            })
            );

            assertTrue(secondAttemptedLock.await(10, TimeUnit.SECONDS));
            assertFalse(
                    secondHasLock.await(500, TimeUnit.MILLISECONDS),
                    "The second transaction must wait for PostgreSQL's row lock."
            );

            allowFirstCommit.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertTrue(secondHasLock.await(1, TimeUnit.SECONDS));
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private String columnType(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """,
                String.class,
                tableName,
                columnName
        );
    }

    private int columnPrecision(String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT datetime_precision
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                tableName,
                columnName
        );
    }

    private long insertCustomer(String fullName, String email) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO customers (full_name, email, phone)
                VALUES (?, ?, '416-555-0199')
                RETURNING id
                """,
                Long.class,
                fullName,
                email
        );
    }

    private long insertTent(
            int widthFeet,
            int lengthFeet,
            int quantity
    ) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO tent_types (
                    width_feet,
                    length_feet,
                    total_quantity
                ) VALUES (?, ?, ?)
                RETURNING id
                """,
                Long.class,
                widthFeet,
                lengthFeet,
                quantity
        );
    }

    private void insertReservation(
            long customerId,
            long tentTypeId,
            LocalDateTime eventStart,
            LocalDateTime eventEnd,
            LocalDateTime reservedFrom,
            LocalDateTime reservedUntil,
            String status
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO reservations (
                    customer_id,
                    tent_type_id,
                    quantity,
                    event_start,
                    event_end,
                    reserved_from,
                    reserved_until,
                    location,
                    status
                ) VALUES (?, ?, 1, ?, ?, ?, ?, 'Database venue', ?)
                """,
                customerId,
                tentTypeId,
                eventStart,
                eventEnd,
                reservedFrom,
                reservedUntil,
                status
        );
    }

    private void insertMaintenance(
            long tentTypeId,
            int quantity,
            LocalDateTime from,
            LocalDateTime until,
            String reason
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO maintenance_blocks (
                    tent_type_id,
                    quantity_unavailable,
                    blocked_from,
                    blocked_until,
                    reason
                ) VALUES (?, ?, ?, ?, ?)
                """,
                tentTypeId,
                quantity,
                from,
                until,
                reason
        );
    }

    private int reservationCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM reservations",
                Integer.class
        );
    }

    private static final class IntentionalRollback
            extends RuntimeException {

        private static final long serialVersionUID = 1L;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Timed out waiting for test latch."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for test latch.",
                    exception
            );
        }
    }
}