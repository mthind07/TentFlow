# ADR 0003: Separate browser-session and bearer-token security chains

- Status: Accepted
- Date: 2026-08-17

## Context

TentFlow now serves two different clients. Browser pages use HTML forms and
cookies, while `/api/**` is consumed as a JSON REST API. Treating both clients
as one authentication mechanism would either expose cookie-authenticated API
mutations without CSRF protection or force REST clients to manage browser
sessions.

The application also needs customer ownership checks, staff workflows, and
administrator-only account/audit routes without weakening the inherited M1–M3
business rules.

## Decision

Use two ordered Spring Security filter chains:

1. The first chain matches `/api/**`, is stateless, accepts only signed HS256
   bearer JWTs, disables CSRF, and returns stable JSON `401`/`403` errors.
2. The second chain handles browser routes with database-backed form login,
   server-side sessions, CSRF, and role-restricted customer/staff pages.

API registration and login are public. Every other API path is deny-by-default
unless its URL and/or method authorization explicitly permits a role. JWTs
carry issuer, audience, subject, user ID, role, optional customer ID, issued-at,
expiration, and unique token ID. The Base64 signing secret has no checked-in
default and must decode to at least 32 bytes.

Passwords use Spring Security's delegating format with `{bcrypt}`. A wrapper
enforces BCrypt's 72-byte UTF-8 limit both when encoding and matching. Customer
ownership is derived from authenticated identity, not trusted from a request
body.

## Consequences

- A browser session cannot authenticate an API request, and a bearer token
  cannot silently create a browser session.
- CSRF remains mandatory for all unsafe HTML forms while stateless bearer API
  calls do not need CSRF tokens.
- API clients must renew authentication after the 15-minute access token
  expires; M4 has no refresh/revocation mechanism.
- UI and API authentication paths both record login success/failure, but never
  record credentials or tokens.
- Multi-instance browser sessions need shared session storage in a future
  operational milestone.