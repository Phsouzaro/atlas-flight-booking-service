# ADR-007: Session with a short access token and a rotated refresh token in a cookie

- **Status:** accepted
- **Date:** 2026-10-15

## Context

Up to phase 5 there was only a 1-hour JWT, stored in `sessionStorage`. Problems: the session dropped in the
middle of use, there was no way to end a stolen session before it expired, and any script injected into the
page could read the token.

## Decision

- **15-minute access token (JWT HS256)**, kept only in memory on the front end. The decoder now validates the
  issuer (it did not before) and uses the application's `Clock`.
- **Opaque refresh token** (32 random bytes) in an `HttpOnly`, `Secure`, `SameSite=Strict` cookie. The page's
  JavaScript cannot read it and other sites cannot send it. The database (`refresh_tokens`, V14) only stores
  its SHA-256 hash.
- **Rotation on every use:** `POST /auth/refresh` exchanges the token for a new one and marks the old one as
  replaced. Tokens from the same login form a **family**.
- **Theft detection:** presenting a token that was already exchanged revokes the whole family and records
  `REFRESH_TOKEN_REUSED` in the audit log. Exception: within 10 s of the exchange it is treated as two tabs
  refreshing at once (409 `REFRESH_RACE`; the client retries with the already updated cookie).
- **Expiry:** 7 days without use (extended on each rotation) and at most 30 days since login.
- **Revocation:** `POST /auth/logout` ends the family of the current cookie; `DELETE /me/sessions` ends all of
  the user's sessions (audit `SESSIONS_REVOKED`).
- **Front end:** refreshes before the token expires and after a 401, one refresh at a time; restores the
  session from the cookie when the app opens.

## Consequences

- Revoking does not kill an access token that was already issued: it stays valid until it expires, at most
  15 minutes. That is the price of not hitting the database on every request. If immediate cut-off is ever
  needed, the way out is a session version on the user, checked against the JWT (with a cache).
- The cookie has `Path=/` and goes with every API call, not only `/auth`. There is no harm (the backend only
  reads it on the session routes) and it avoids depending on nginx's `/api` prefix.
- `SameSite=Strict` is what protects `/auth/refresh` and `/auth/logout` against CSRF; there is no anti-CSRF
  token. Browsers without SameSite support are not a target.
- Swagger and command-line clients use the access token from the login response body, as before; to refresh,
  they need to keep the cookie.
- Expired and revoked tokens stay in the table until `RefreshTokenCleanup` removes families that have been
  dead for 7 days.
