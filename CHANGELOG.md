# Changelog

All notable TentFlow changes are recorded here. Versions follow Semantic
Versioning.

## 1.0.0 — 2026-08-23

### Added

- Production multi-stage, non-root Java 21 container
- App-plus-PostgreSQL Compose profile and Render Blueprint
- Public liveness/readiness probes and internal Prometheus metrics
- JaCoCo 80% line/60% branch gates, Checkstyle, and SpotBugs
- Real-server operational endpoint tests and deployed booking smoke test
- CodeQL, dependency review, Dependabot, Trivy, and release workflows
- GHCR publishing, SPDX SBOM, JAR checksum, backup/restore helpers, and runbooks
- Responsive 1.0 landing-page polish

### Preserved

- All 73 Milestone 1–4 tests and the V1/V2 Flyway schema
- Inventory, waitlist, maintenance, persistence, security, automation, and
  append-only audit behavior

## 0.4.0

- Customer/staff/admin workflows, browser security, JWT hardening, scheduled
  completion, and append-only audit history

## 0.3.0

- PostgreSQL persistence, Flyway migrations, transactional workflows, and
  database concurrency controls

## 0.2.0

- Spring Boot REST API, validation, stable error envelopes, and integration
  tests

## 0.1.0

- Pure Java booking engine, overlap-aware inventory, maintenance, waitlist,
  immutable views, and unit tests