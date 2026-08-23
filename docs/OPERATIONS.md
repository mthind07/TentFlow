# TentFlow operations runbook

## Health and metrics

- `GET /livez`: the process can respond. Use for restart decisions.
- `GET /readyz`: the application and PostgreSQL are ready. Use for routing and
  deployment health checks.
- Internal `GET :8081/actuator/health`: detailed endpoint family, with details
  hidden from responses.
- Internal `GET :8081/actuator/prometheus`: Micrometer metrics.

Only `/livez` and `/readyz` belong on the public port. Keep 8081 inside a
trusted network and configure a private Prometheus scrape target if required.

## First response checklist

1. Check deployment status and `/livez`.
2. Check `/readyz`; if liveness is up but readiness is down, inspect PostgreSQL
   reachability and pool exhaustion before restarting.
3. Review application and platform logs using the `X-Request-ID` value from a
   failed API response when available.
4. Confirm secrets, request bodies, passwords, and bearer tokens are not copied
   into incident notes.
5. Record the affected release tag/image digest and first observed time.

## Local logs

```shell
docker compose --profile full logs --follow application
docker compose logs --follow database
```

## Back up the local Compose database

```shell
./scripts/backup-database.sh
```

The command writes a timestamped custom-format dump under `backups/`. That
directory and `*.dump` are excluded from Git and Docker builds, but the files
can still contain personal data. Encrypt and access-control copies stored
outside the development machine.

## Test a restore

1. Take a fresh backup:

   ```shell
   ./scripts/backup-database.sh
   ```

2. Select the newest dump and confirm it is readable:

   ```shell
   backup_file="$(ls -t backups/tentflow-*.dump | head -n 1)"
   test -n "$backup_file"
   test -r "$backup_file"
   printf 'Restoring from %s\n' "$backup_file"
   ```

3. Stop application writes:

   ```shell
   docker compose --profile full stop application
   ```

4. Restore the selected file:

   ```shell
   RESTORE_CONFIRM=restore \
     ./scripts/restore-database.sh "$backup_file"
   ```

   The script refuses to run while the Compose application container is
   running and uses `--clean --if-exists`, so it replaces current schema data.

5. Restart, wait for readiness, and run the smoke test:

   ```shell
   docker compose --profile full up --detach --wait application
   ./scripts/smoke-test.sh
   ```

## Secret rotation

- JWT secret: replace `TENTFLOW_JWT_SECRET` and redeploy. Existing API tokens
  immediately become invalid; browser sessions are separate.
- Database password: rotate it at the provider, update the service secret, and
  redeploy during a controlled window.
- Administrator credentials: while a working administrator session or token
  remains available, `POST /api/admin/users` can create a replacement staff or
  administrator account.
- TentFlow 1.0 does not provide a password-reset or account-disable endpoint.
  Containing a compromised existing account therefore requires a separately
  reviewed database operation or a future security feature.
- Never reuse bootstrap as a password reset. Bootstrap intentionally leaves an
  existing administrator unchanged.

## Capacity signals

Watch HTTP request latency/counts, JVM memory and GC, process CPU, Hikari active
and pending connections, readiness failures, PostgreSQL storage, and repeated
401/403 or 5xx outcomes. The production pool defaults to 10 maximum and 2
minimum idle connections; tune only with database connection limits in mind.