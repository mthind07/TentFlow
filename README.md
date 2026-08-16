# TentFlow — Milestone 3

TentFlow is a Java 21 tent-inventory and reservation API. Milestone 3 keeps
the Milestone 1 booking rules and Milestone 2 HTTP routes while moving runtime
state to PostgreSQL using Spring Data JPA, Flyway, transactions, and
database-level concurrency control.

This is a local portfolio/demo application. Do not deploy it publicly:
customer and staff mutation routes have no authentication or authorization.
Security is planned for Milestone 4.

## What works

- PostgreSQL persistence for customers, tents, reservations, maintenance,
  and waitlists
- Pending and confirmed inventory holds
- Peak concurrent-usage availability calculation
- Per-tent PostgreSQL row locking
- Transactional waitlist promotion
- Case-insensitive unique customer emails
- Flyway schema migrations
- OpenAPI and Swagger UI
- 45 automated tests against real PostgreSQL
- Java 21 GitHub Actions CI

## Requirements

- Java 21
- Docker Desktop
- Git

## Run locally

```shell
docker compose up -d --wait
./mvnw spring-boot:run