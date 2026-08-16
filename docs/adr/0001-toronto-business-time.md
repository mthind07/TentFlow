# ADR 0001: Preserve Toronto business wall time explicitly

- Status: Accepted
- Date: 2026-08-16

## Context

Milestone 2 exposes reservation, event, and maintenance values as
offset-free `LocalDateTime` strings. These values represent the tent
company's local schedule.

Toronto local times can be invalid during the spring daylight-saving gap
or identify two instants during the autumn overlap. Java also supports
nanoseconds while PostgreSQL stores six fractional-second digits.

## Decision

- The M2 `LocalDateTime` JSON shape remains unchanged.
- Business values mean `America/Toronto` wall time.
- They are stored as `TIMESTAMP(6) WITHOUT TIME ZONE`.
- DST gaps and overlaps are rejected.
- Precision finer than microseconds is rejected.
- Waitlist `joined_at` is an `Instant` stored as
  `TIMESTAMP(6) WITH TIME ZONE`.

## Consequences

Accepted API values round-trip exactly. Direct database imports must perform
equivalent Toronto-time validation. Changing the operating zone later requires
a data migration.