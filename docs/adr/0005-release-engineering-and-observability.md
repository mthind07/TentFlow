# ADR 0005: Release engineering and observability

## Status

Accepted for TentFlow 1.0.0.

## Context

Milestones 1–4 proved domain, HTTP, persistence, security, browser, automation,
and audit behavior. A final portfolio release must be reproducible, observable,
deployable, and recoverable without weakening those boundaries.

## Decision

- Build a multi-stage Java 21 image and run it as a fixed non-root UID.
- Keep PostgreSQL outside the application container and configure it only with
  environment variables.
- Mirror liveness/readiness onto the application port and keep health plus
  Prometheus on a separate unpublished management port.
- Require 80% aggregate line and 60% aggregate branch coverage, Checkstyle,
  SpotBugs, CodeQL, dependency review, and container scanning.
- Publish only tags already merged to `main`; require Maven/tag equality and
  scan the exact image bytes before pushing them.
- Produce a JAR, SHA-256 checksum, SPDX SBOM, GHCR image, and GitHub release.
- Target Render Blueprint deployment while documenting that free resources are
  not an always-on durable production tier.

## Consequences

The release path is more trustworthy and demonstrable, but Docker is required
for the complete local gate and a hosted database carries operational cost for
always-on durability. Prometheus metrics require a private scrape path rather
than public exposure. Deployment credentials remain an owner responsibility
and are deliberately absent from the repository.