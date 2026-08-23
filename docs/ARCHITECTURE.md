# TentFlow architecture

## System context

TentFlow is one Spring Boot application with two same-origin interfaces:

```text
Browser ── session + CSRF ──┐
                            ├── Spring Boot ── JPA/Flyway ── PostgreSQL
API client ── bearer JWT ───┘       │
                                    ├── public /livez and /readyz
                                    └── internal health and Prometheus :8081
```

The server-rendered UI is not a separate frontend deployment. CORS is therefore
disabled. A cross-origin client would require a separately reviewed allowlist.

## Application layers

| Layer | Responsibility |
| --- | --- |
| `model` | Pure booking rules and immutable views |
| `service` | Inventory operations and transaction boundaries |
| `persistence` | JPA entities, repositories, PostgreSQL locking |
| `api` | REST DTOs, validation, mapping, stable errors |
| `web` | Thymeleaf customer/staff workflows |
| `security` | JWT, identities, authorization, JSON 401/403 |
| `audit` / `automation` | Append-only lifecycle evidence and due completion |
| `config` | Spring wiring, ordered security chains, runtime policy |

## Data and concurrency

Flyway owns the V1/V2 schema; Hibernate validates it and never creates it.
Inventory-changing workflows lock the relevant tent row, recompute availability
inside the transaction, write the business change, and append audit evidence in
the same transaction. Database constraints provide the final uniqueness and
state-integrity boundary. No V3 migration is needed for release engineering.

## Security chains

Three ordered filter chains prevent authentication modes from bleeding across
interfaces:

1. Actuator endpoints: stateless; only exposed health and Prometheus endpoints
   are permitted on the internal management server.
2. `/api/**`: stateless bearer JWT; public registration/login only; JSON
   authentication/authorization failures.
3. Browser routes: form login, database users, server session, and CSRF.

The main server mirrors only `/livez` and `/readyz`. Supported deployment files
never publish port 8081.

## Runtime and release

The image is built with Java 21 in one stage and runs on a Java 21 JRE as UID
10001 in the final stage. Render's standard PostgreSQL URI is converted to JDBC
form by the entrypoint without printing credentials. Configuration and secrets
enter through environment variables.

Every pull request runs deterministic Maven gates. Separate workflows perform
CodeQL, dependency review, and image vulnerability scanning. A release tag must
match the Maven version and point to a commit already merged into `main`; the
exact locally loaded image is scanned before its tags are pushed.

## Quality strategy

The suite combines pure unit tests, MockMvc contract/security tests,
Testcontainers repository/transaction/concurrency tests, browser-workflow
tests, and real HTTP operational tests. The aggregate release gate requires at
least 80% line coverage and 60% branch coverage. Those numeric gates complement
the layered tests; they do not replace behavior, security, or concurrency
verification.