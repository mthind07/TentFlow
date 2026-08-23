# Deploying TentFlow

## Local production-shaped deployment

1. If `.env` does not exist, create it without overwriting an existing file:

   ```shell
   [ -f .env ] || cp .env.example .env
   ```

2. Generate `TENTFLOW_JWT_SECRET` with `openssl rand -base64 32` and paste it
   into `.env`.
3. Set a non-default database password. The PostgreSQL volume uses its password
   when first initialized, so do not casually replace the password in an
   existing `.env`.
4. Set both bootstrap administrator values for a new administrator, or leave
   both empty when a known administrator already exists. Bootstrap creates only;
   it does not reset an existing account's password.
5. If host port 5432 is occupied, set `TENTFLOW_DATABASE_PORT` to a free port
   such as `55432`.
6. If host port 8080 is occupied, set `TENTFLOW_APPLICATION_HOST_PORT` to a free
   port such as `18080`.
7. Run:

   ```shell
   docker compose --profile full up --build --detach --wait
   ```

8. Open `/readyz`, then the application root.
9. Sign in successfully and save the administrator credentials before removing
   bootstrap configuration.
10. Run the smoke test without putting the password in shell history:

    ```shell
    export TENTFLOW_ADMIN_EMAIL='YOUR_ADMIN_EMAIL'
    read -r -s "TENTFLOW_ADMIN_PASSWORD?Admin password: "
    printf '\n'
    export TENTFLOW_ADMIN_PASSWORD
    ./scripts/smoke-test.sh
    unset TENTFLOW_ADMIN_EMAIL TENTFLOW_ADMIN_PASSWORD
    ```

11. Set both bootstrap values to empty, clear any exported bootstrap variables,
    recreate the application service, and verify login again:

    ```shell
    unset TENTFLOW_BOOTSTRAP_ADMIN_EMAIL
    unset TENTFLOW_BOOTSTRAP_ADMIN_PASSWORD
    docker compose --profile full up \
      --detach \
      --force-recreate \
      --wait \
      application
    ```

Compose publishes the application port only. Management port 8081 exists only
on the Compose network. The application container has a read-only filesystem,
drops Linux capabilities, prevents privilege escalation, and runs as UID 10001.

## Render Blueprint

1. Push the finished repository to GitHub and ensure every required check is
   green on `main`.
2. In Render, choose **New → Blueprint** and connect the repository containing
   `render.yaml`.
3. Review the proposed `tentflow` web service and `tentflow-database` database.
4. When prompted during initial Blueprint creation, supply both bootstrap
   values: `TENTFLOW_BOOTSTRAP_ADMIN_EMAIL` and
   `TENTFLOW_BOOTSTRAP_ADMIN_PASSWORD`.
5. Use a new administrator email and a unique 12–72-byte password. Save them in
   a password manager before applying the Blueprint.
6. Render generates the Base64 256-bit JWT secret and supplies the database
   connection, username, and password values. Do not paste local `.env`
   secrets into Render.
7. Wait until `/readyz` is healthy, open the generated HTTPS URL, and sign in.
   Landing on the home page after login is successful authentication.
8. Open the staff workspace and verify the administrator features.
9. Remove both bootstrap variables from the web service environment together,
   save, and redeploy.
10. Wait for `/readyz`, then sign in again to prove the administrator remains
    stored as a bcrypt hash.
11. Run the smoke script:

    ```shell
    export TENTFLOW_BASE_URL='https://YOUR-SERVICE.onrender.com'
    export TENTFLOW_ADMIN_EMAIL='YOUR_ADMIN_EMAIL'
    read -r -s "TENTFLOW_ADMIN_PASSWORD?Admin password: "
    printf '\n'
    export TENTFLOW_ADMIN_PASSWORD
    ./scripts/smoke-test.sh
    unset TENTFLOW_BASE_URL TENTFLOW_ADMIN_EMAIL TENTFLOW_ADMIN_PASSWORD
    ```

Do not commit the generated service URL as if it were guaranteed to exist.
Blueprint creation belongs to the repository owner and can be removed or
renamed independently of the code.

## Free versus always-on hosting

A Render free web service can sleep after inactivity, so the first request may
require a cold start. A free PostgreSQL database expires and does not provide
backups. That is suitable only for a temporary portfolio demonstration. Choose
paid web and database plans and configure backups for an always-on durable site.

## Required runtime configuration

| Variable | Purpose |
| --- | --- |
| `TENTFLOW_DATABASE_URL` | JDBC URL, or `postgresql://` URI in the container |
| `TENTFLOW_DATABASE_USERNAME` | PostgreSQL user |
| `TENTFLOW_DATABASE_PASSWORD` | PostgreSQL password |
| `TENTFLOW_JWT_SECRET` | Base64 value decoding to at least 32 random bytes |
| `SPRING_PROFILES_ACTIVE` | Use `production` when deployed |
| `TENTFLOW_COOKIE_SECURE` | Keep `true` behind HTTPS |
| `TENTFLOW_BOOTSTRAP_ADMIN_EMAIL` | Optional first-run administrator pair |
| `TENTFLOW_BOOTSTRAP_ADMIN_PASSWORD` | Optional first-run administrator pair |

`PORT` is supplied by Render and honored by the application and container
health check. `TENTFLOW_MANAGEMENT_PORT` defaults to 8081 and must remain
internal.

## Rollback

1. Identify a previously green `v1.0.x` image/tag and its database migration
   compatibility.
2. Back up the database before changing runtime versions.
3. Deploy the earlier image. V1/V2 are forward-compatible within 1.0.x.
4. Check `/livez`, `/readyz`, login, and a read-only reservation route.
5. Run the complete smoke test only against an environment where its demo rows
   are acceptable.