# TentFlow security policy

## Supported version

Security fixes are applied to the current `1.0.x` release line.

## Reporting a vulnerability

If GitHub private vulnerability reporting has been enabled for the repository,
use **Security → Report a vulnerability**. Otherwise use a private contact
method published by the repository owner. Do not open a public issue containing
an exploit, credential, token, database dump, or customer information.

Include the affected version, reproduction steps, likely impact, and any safe
mitigation you have tested. The maintainer will acknowledge the report, assess
severity, and coordinate a fix and disclosure. Do not test against a deployment
you do not own or have explicit permission to assess.

## Deployment responsibilities

- Generate secrets outside Git and rotate them after suspected exposure.
- Use HTTPS and keep `TENTFLOW_COOKIE_SECURE=true` outside local HTTP.
- Keep management port 8081 private; publish only the application port.
- Back up PostgreSQL, protect dump files as sensitive data, and test restores.
- Apply Dependabot and container base-image updates after CI passes.
- Remove both bootstrap administrator environment variables after the first
  administrator has been created.
- Do not place passwords or bearer tokens in logs, screenshots, issues, or
  committed IntelliJ run configurations.