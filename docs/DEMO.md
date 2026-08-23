# TentFlow five-minute release demo

This script demonstrates the finished product without exposing real customer
data or credentials. Use a local disposable database or a portfolio deployment
that you own.

## Before the demo

1. Confirm `GET /readyz` returns `{"status":"UP"}`.
2. Prepare one administrator account and keep its password outside slides,
   source control, screen recordings, and shell history.
3. Use fictional names, email addresses, phone numbers, and event details.
4. Use a normal browser window for the administrator session.
5. Use a separate private/incognito window for the customer session. Tabs in
   one regular browser window share cookies, so customer login would replace
   the administrator session in the other tabs.
6. Open the landing page, staff dashboard, customer portal, audit page, and
   Swagger UI as needed during the walkthrough.

## Walkthrough

1. **Landing page:** explain that TentFlow is one responsive Java 21/Spring
   application, so the browser UI and REST API share the same origin.
2. **Inventory:** sign in as an administrator, create a tent type, and show its
   availability for an event window that includes setup and pickup time.
3. **Reservation:** create a fictional customer, request inventory, then
   confirm the reservation. Point out that pending and confirmed reservations
   both hold inventory.
4. **Waitlist:** request more inventory than remains. Show the waitlisted item
   and explain that TentFlow scans oldest-first and promotes the first request
   that currently fits when inventory returns.
5. **Customer boundary:** use the private/incognito customer window and show
   that the portal exposes only that customer's reservations and permitted
   cancellation action.
6. **Operations:** return to the administrator window, complete or cancel a
   reservation, and show the append-only audit entry for the state change.
7. **API and production proof:** briefly show Swagger UI, public `/livez` and
   `/readyz`, green GitHub Actions checks, the coverage report, image scan, and
   the `v1.0.0` release assets: JAR, checksum, SBOM, and GHCR image.

## Automated release smoke

For repeatable deployment proof, run the checked-in smoke script. It creates
uniquely named demo inventory and customer data, then completes a full booking
lifecycle.

```shell
export TENTFLOW_BASE_URL='https://YOUR-SERVICE.onrender.com'
export TENTFLOW_ADMIN_EMAIL='YOUR_ADMIN_EMAIL'
read -r -s "TENTFLOW_ADMIN_PASSWORD?Admin password: "
printf '\n'
export TENTFLOW_ADMIN_PASSWORD
./scripts/smoke-test.sh
unset TENTFLOW_BASE_URL TENTFLOW_ADMIN_EMAIL TENTFLOW_ADMIN_PASSWORD
```

The script must finish with:

```text
8/8 TentFlow smoke test passed.
```

Delete its fictional rows later only through an explicitly reviewed retention
process. The application does not hide destructive cleanup inside the demo.