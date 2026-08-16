# ADR 0002: Serialize inventory writers with a PostgreSQL tent-row lock

- Status: Accepted
- Date: 2026-08-16

## Context

Milestone 2 synchronized Java methods. That protects one JVM only. Two
application processes could otherwise approve the same last tent.

## Decision

The `tent_types` row acts as the database mutex for one inventory pool.

- Inventory-changing use cases run in Spring transactions.
- The service loads the affected tent using `PESSIMISTIC_WRITE`.
- PostgreSQL holds the row lock through calculation, writes, waitlist
  promotion, and commit or rollback.
- Different tent types can proceed independently.
- Availability uses a `REPEATABLE READ` snapshot.
- `@Version` is a secondary stale-update guard.

## Consequences

Concurrent requests for one remaining tent produce one `PENDING` and one
`WAITLISTED` reservation. Writes for one tent type deliberately serialize,
which is a simple and auditable Milestone 3 tradeoff.