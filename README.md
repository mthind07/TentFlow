# TentFlow

TentFlow is a Java-based tent inventory and reservation management system.

Milestone 1 is a pure-Java booking engine. It is not yet a web application.

## Milestone 1 features

- Customer registration
- Configurable tent sizes and quantities
- Separate event and setup/pickup windows
- Pending inventory holds
- Reservation confirmation, rejection, cancellation, and completion
- Overlap detection
- Peak simultaneous inventory calculation
- Maintenance blocks
- Ordered waitlists
- Automatic promotion of the oldest request that currently fits
- Immutable reservation and waitlist views
- Automated JUnit tests

## Technology

- Java 21
- Maven 3.9.11 through Maven Wrapper
- JUnit Jupiter
- IntelliJ IDEA

## Run the tests

On macOS/Linux:

```bash
./mvnw clean test
```

## Expected demo

```text
=== TentFlow Milestone 1 demo ===
Inventory: 1 x 40x40 tent
Alice's first result: PENDING
Bob's first result: WAITLISTED
Available in that window: 0
Alice after staff approval: CONFIRMED
Alice after cancellation: CANCELLED
Bob after waitlist promotion: PENDING
```

## Current limitations

- All information is stored in memory and disappears when the program stops.
- Milestone 1 is sequential and is not thread-safe.
- There is no REST API.
- There is no database.
- There is no authentication.
- There is no web interface.
- There are no payments.
- `LocalDateTime` does not yet persist a business time zone.
- Waitlist promotion means “oldest request that currently fits.” A smaller
  request may pass an older request that needs more inventory.
- A maintenance block is rejected if it would consume inventory already held
  by pending or confirmed reservations.

## Planned milestones

1. Pure Java booking engine
2. Spring Boot REST API
3. PostgreSQL, JPA, Flyway, and database transaction safety
4. Web interface, security, scheduling, and audit history
5. Docker, CI/CD expansion, deployment, and release documentation