# ADR 0004: Lock/recheck scheduled completion and transactional audit

- Status: Accepted
- Date: 2026-08-20

## Context

Confirmed reservations should be reconciled to their terminal lifecycle state
after their reservation window ends. Completion also invokes M3's existing
waitlist promotion recheck. A scheduled job can run twice, overlap with a staff
action, or run in more than one application process. Audit history must
describe the state that truly committed, not an intended but rolled-back
transition.

Maintenance blocks are already bounded by start/end time and therefore do not
need destructive expiry processing. TentFlow also has no approved policy for
automatically expiring pending holds.

## Decision

A conditional fixed-delay job selects a bounded page of IDs whose status is
`CONFIRMED` and whose `reserved_until` is due according to the injected Toronto
`Clock`. Each ID is processed through a separate transactional service call.

The worker resolves and locks the reservation's tent row before re-reading the
reservation status and end time. It performs no work if another transaction
already reconciled the reservation. A successful completion goes through the
existing M3 completion path, which rechecks the waitlist synchronously under
the same tent lock.

Lifecycle audit writes use the same transaction as successful status changes.
Each row stores actor, UTC instant, request ID, action, resource, old/new status,
outcome, and bounded non-secret details. Scheduler actions use
`system:automation`. PostgreSQL constraints restrict status values and a trigger
rejects `UPDATE` or `DELETE` on `audit_events`.

Rejected API logins and access denials use a new transaction because the
rejected request has no business transaction to commit.

## Consequences

- Repeated and concurrent job runs are idempotent at the business transition
  and audit level.
- PostgreSQL tent locks coordinate scheduler and interactive writers across
  application processes.
- A job pass handles at most 100 due IDs; later passes drain a larger backlog.
- A failed audit write rolls back its successful lifecycle mutation, preserving
  truthful history.
- The audit table is application-append-only; retention/export policy belongs
  to a later operational milestone.
- Maintenance deletion and pending-hold expiry are deliberately outside this
  job.
- Stale waitlist expiry/contact policy is also outside M4; the worker preserves
  the inherited promotion semantics without claiming an ended hold newly frees
  future inventory.