# TentFlow — Milestone 4

TentFlow is a Java 21 tent-inventory and reservation application. Milestone 4
keeps the Milestone 1 booking engine, Milestone 2 REST contract, and Milestone
3 PostgreSQL transactions, then adds authenticated customer/staff workflows,
role-based authorization, a small server-rendered UI, scheduled completion,
and append-only audit history.

This is a local portfolio application, not a finished public SaaS product.
Milestone 5 still owns production packaging, operational health checks,
coverage gates, security scanning, deployment documentation, and final
demo/release polish.

## What works

- `CUSTOMER`, `STAFF`, and `ADMIN` accounts are stored in PostgreSQL with
  case-insensitive email identity and `{bcrypt}` password hashes.
- The REST API uses short-lived signed HS256 bearer tokens. Signature, issuer,
  audience, timestamps, and token IDs are validated.
- The browser UI uses a separate server-side session and Spring Security form
  login. Unsafe HTML forms require CSRF tokens.
- Customers can register, sign in, request future reservations, list only
  their reservations, and cancel only their own reservations.
- Staff and administrators can review all reservations and confirm, reject, or
  complete them.
- Administrators can create staff/admin accounts and read bounded audit pages.
- A fixed-delay job reconciles due confirmed reservations to `COMPLETED`.
- Lifecycle audit rows capture actor, time, request ID, resource, outcome, and
  old/new status. PostgreSQL rejects ordinary audit updates and deletes.
- M1–M3 booking, migration, PostgreSQL locking, and Toronto-time behavior
  remain intact.

## Versions

- Java 21
- Spring Boot 4.0.7
- Spring Security 7.0.6
- PostgreSQL 18.4 (`postgres:18.4-alpine`)
- Flyway 11.14.1
- Testcontainers 2.0.5
- springdoc-openapi 3.0.2
- Maven 3.9.11 through the Maven Wrapper

## First run in IntelliJ IDEA

1. Install and start Docker Desktop.

2. Choose **File → Open**, select the TentFlow folder containing `pom.xml`,
   and click **Open**.

3. Choose **File → Project Structure → Project** and select Temurin 21.

4. Choose **View → Tool Windows → Maven**, then click **Reload All Maven
   Projects**.

5. Generate a signing secret:
   ```shell
   openssl rand -base64 32

6. Set the required environment variables:
   export TENTFLOW_JWT_SECRET='PASTE_YOUR_GENERATED_BASE64_SECRET'
   export TENTFLOW_BOOTSTRAP_ADMIN_EMAIL='admin@tentflow.local'
   export TENTFLOW_BOOTSTRAP_ADMIN_PASSWORD='Change-This-Admin-Password-42'

7. Start PostgreSQL and TentFlow:
   docker compose up -d --wait
   ./mvnw spring-boot:run

8. Open http://localhost:8080 for the browser UI or
   http://localhost:8080/swagger-ui.html for Swagger.
   For IntelliJ’s Run button, choose Run → Edit Configurations, select
   TentFlowApplication, and add the same three environment variables.
   Never commit passwords, JWT secrets, .env files, or IntelliJ run
   configuration secrets.


## Browser workflows
- /register creates a customer account.
- /login signs in a customer, staff member, or administrator.
- Customers use /customer/reservations.
- Staff and administrators use /staff/reservations.
- Logout is a CSRF-protected POST.


## Swagger and REST API
1. Call POST /api/auth/login.
2. Copy only the accessToken.
3. Click Authorize.
4. Paste the raw token.
5. Exercise the authorized API routes.
   Access tokens expire after 15 minutes by default.


## Authorization map
   Area	                                                            Access
   POST /api/auth/register, POST /api/auth/login	                Public
   GET /api/auth/me, tent list/detail/availability	                Any bearer role
   Own profile and reservation create/list/read/cancel	            Customer
   Customer, tent, reservation, waitlist, maintenance operations	Staff or admin
   /api/admin/users/**, /api/admin/audit-events	                    Admin only
   /customer/**	                                                    Customer browser session
   /staff/**	                                                    Staff or admin browser session


A browser session cannot authenticate /api/**. A bearer token does not create
a browser login session.


## Security model
TentFlow uses two ordered Spring Security filter chains:
1. /api/** is stateless, uses bearer JWTs, returns JSON 401/403, and
   disables CSRF because it does not use cookie authentication.
2. Browser routes use form login, server-side sessions, and CSRF protection.
   Passwords must contain at least 12 characters and at most 72 UTF-8 bytes.
   Login failures do not reveal whether the supplied email exists.
   TENTFLOW_JWT_SECRET must be Base64 that decodes to at least 32 random bytes.
   There is no checked-in default secret.
   Optional settings:
- TENTFLOW_BCRYPT_STRENGTH
- TENTFLOW_AUTOMATION_ENABLED
- TENTFLOW_AUTOMATION_FIXED_DELAY
- TENTFLOW_DATABASE_URL
- TENTFLOW_DATABASE_USERNAME
- TENTFLOW_DATABASE_PASSWORD


## Scheduled completion and audit
  The scheduled job reads at most 100 due CONFIRMED reservation IDs. Each is
  processed in its own transaction. The worker locks the tent row and rechecks
  status and time before completing it.
  Successful transitions and resulting promotions are audited in the same
  business transaction. Failed authentication and access denial use isolated
  audit transactions. Passwords, bearer tokens, and request bodies are never
  stored in audit rows.
  See:
- docs/adr/0003-dual-security-chains.md
- docs/adr/0004-scheduled-completion-and-audit.md


## Build, test, package, and run
  Docker Desktop must be running:
  ./mvnw clean verify
  java -jar target/tentflow-0.4.0.jar
  Expected verification:
  Tests run: 73
  Failures: 0
  Errors: 0
  Skipped: 0
  BUILD SUCCESS
  Stop the local PostgreSQL service with:
  docker compose down
  Do not add -v unless you intentionally want to delete the database volume.
  