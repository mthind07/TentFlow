# TentFlow 1.0

TentFlow is a Java 21 portfolio application for tent inventory and reservation
operations. It combines a transactional booking engine, a secured REST API,
PostgreSQL persistence, customer/staff browser workflows, audit history, and a
production-oriented release pipeline.

## What 1.0 delivers

- Overlap-aware inventory for setup-through-pickup windows
- Pending and confirmed holds, maintenance blocks, and oldest-request-first
  waitlist scanning that promotes the first request that currently fits
- PostgreSQL row locking and atomic business/audit transactions
- Customer, staff, and administrator roles
- Server-rendered Thymeleaf workflows with sessions and CSRF protection
- Stateless REST authentication with short-lived signed JWTs
- Scheduled completion of due confirmed reservations
- Actuator liveness/readiness probes and internal Prometheus metrics
- A non-root multi-stage container and an app-plus-PostgreSQL Compose profile
- JaCoCo 80% line/60% branch gates, Checkstyle, SpotBugs, CodeQL, dependency
  review, Dependabot, and Trivy image scanning
- A Render Blueprint, backup/restore helpers, release smoke test, GHCR image,
  SBOM, checksum, and GitHub release automation

No Milestone 5 schema change is required: Flyway migrations remain at V2.

## Stack

- Java 21 and Maven Wrapper 3.9.11
- Spring Boot 4.0.7 and Spring Security 7.0.6
- PostgreSQL 18.4 and Flyway 11.14.1
- springdoc-openapi 3.0.2
- Testcontainers 2.0.5
- JaCoCo 0.8.15, Checkstyle Maven Plugin 3.6.0, and SpotBugs Maven Plugin
  4.10.3.0

## Fastest local start: complete container stack

Requirements: Docker Desktop and Git. Java is not required for this path.

Create `.env` only when it does not already exist. Never copy the template over
an existing configured `.env`, because that can replace the database password
and JWT secret used by an existing local database.

```shell
[ -f .env ] || cp .env.example .env
openssl rand -base64 32
```

Paste the generated value after `TENTFLOW_JWT_SECRET=` in `.env`. Set **both**
`TENTFLOW_BOOTSTRAP_ADMIN_EMAIL` and a long
`TENTFLOW_BOOTSTRAP_ADMIN_PASSWORD`, or leave both blank when an administrator
already exists.

Bootstrap is create-only. Supplying an existing administrator email with a new
password does not reset that administrator's password.

Start both services:

```shell
docker compose --profile full up --build --detach --wait
```

Open:

- Application: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Readiness: <http://localhost:8080/readyz>

An administrator is considered created only after readiness is UP and a login
with the configured email and password succeeds. Save those credentials before
blanking both bootstrap values:

```dotenv
TENTFLOW_BOOTSTRAP_ADMIN_EMAIL=
TENTFLOW_BOOTSTRAP_ADMIN_PASSWORD=
```

Clear any older shell exports and recreate the application:

```shell
unset TENTFLOW_BOOTSTRAP_ADMIN_EMAIL
unset TENTFLOW_BOOTSTRAP_ADMIN_PASSWORD
docker compose --profile full up \
  --detach \
  --force-recreate \
  --wait \
  application
```

Sign in again to prove the stored administrator survives without bootstrap
configuration.

Stop without deleting data:

```shell
docker compose --profile full down
```

Do not add `--volumes` unless you intentionally want to delete the local
database volume.

## IntelliJ/Maven development start

Requirements: Temurin 21 and Docker Desktop.

Create `.env` only if it is missing. Do not overwrite an existing `.env`.

```shell
[ -f .env ] || cp .env.example .env
docker compose --profile full stop application
docker compose up --detach --wait database
set -a
. ./.env
set +a
./mvnw spring-boot:run
```

If port 5432 is occupied, set `TENTFLOW_DATABASE_PORT` to another free host port
in `.env`; the Compose database and local Maven application will use the same
value.

In IntelliJ IDEA, open the folder containing `pom.xml`, set the Project SDK and
Maven Runner JRE to Temurin 21, then reload the Maven project. Put the same
environment variables in the `TentFlowApplication` run configuration when
using the green Run button. To create the first administrator, supply both
bootstrap email and password variables. Once login succeeds, remove both
together. Never store secrets in a shared run configuration.

## Verification

Docker Desktop must be running because integration tests start PostgreSQL 18.4
with Testcontainers.

```shell
./mvnw clean verify
```

Expected release result:

```text
Tests run: 75, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The `verify` lifecycle enforces Java/Maven versions, style, all tests, aggregate
JaCoCo coverage of at least 80% lines and 60% branches, and SpotBugs. The HTML
coverage report is generated at `target/site/jacoco/index.html`.

Run the production-shaped stack and complete one real booking lifecycle without
placing the administrator password in shell history:

```shell
export TENTFLOW_ADMIN_EMAIL='your-admin@example.com'
read -r -s "TENTFLOW_ADMIN_PASSWORD?Admin password: "
printf '\n'
export TENTFLOW_ADMIN_PASSWORD
./scripts/smoke-test.sh
unset TENTFLOW_ADMIN_EMAIL TENTFLOW_ADMIN_PASSWORD
```

Expected final line:

```text
8/8 TentFlow smoke test passed.
```

## Security and operations

- `/api/**` is stateless and accepts bearer JWTs only.
- Browser routes use form login, server-side sessions, and CSRF tokens.
- CORS is intentionally disabled because the UI and API are same-origin.
- Public `/livez` reports process liveness; `/readyz` includes PostgreSQL.
- Port 8081 exposes only health and Prometheus inside the deployment network.
  It is not published by the supported Compose or Render configuration.
- Secrets are configuration only. No JWT, database, or administrator password
  has a checked-in default.
- Database dumps are ignored by Git and the Docker build context.

See [SECURITY.md](SECURITY.md) and
[docs/OPERATIONS.md](docs/OPERATIONS.md).

## Deployment and live website

`render.yaml` can create a public Render web service and managed PostgreSQL
database. Once deployed, TentFlow has a normal HTTPS URL and does not need your
laptop or IntelliJ to stay running.

The included free plans are suitable for a temporary portfolio demonstration,
not an always-on durable production service. A free web service sleeps after
inactivity, and a free PostgreSQL database expires. Choose paid plans and
configure backups for durable hosting. See
[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Deployment](docs/DEPLOYMENT.md)
- [Operations and recovery](docs/OPERATIONS.md)
- [API examples](docs/API_EXAMPLES.md)
- [Five-minute release demo](docs/DEMO.md)
- [Architecture decisions](docs/adr/)
- [Release history](CHANGELOG.md)

## Release

Version `1.0.0` is the final five-milestone portfolio release. A `v1.0.0` tag
on a commit already merged to `main` runs all gates, scans the exact image being
published, pushes immutable and convenience tags to GHCR, generates an SPDX
SBOM and SHA-256 JAR checksum, and creates the GitHub release.