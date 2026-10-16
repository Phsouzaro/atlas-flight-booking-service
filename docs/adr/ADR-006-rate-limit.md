# ADR-006: In-memory rate limiting, per route and per account

- **Status:** accepted
- **Date:** 2026-10-15

## Context

Two routes need a brake. Login, against password brute force. Chat, because every message costs OpenAI
tokens and a client stuck in a loop turns into a large bill. Registration too, so it does not become an
account factory.

## Decision

- **Token bucket with Bucket4j**, buckets kept in a Caffeine cache (evicted after 1 day without use, at most
  100 thousand keys). Time comes from the application's `Clock`, so tests can move it forward.
- **Per-route rules in `application.yml`** (`app.rate-limit.rules`), applied by a `HandlerInterceptor` in
  `shared.ratelimit`, after Spring Security:
  - login: 20 per minute per IP;
  - registration: 5 per hour per IP;
  - chat: 10 per minute and 200 per day per user (the second one is the cost ceiling).
- **Wrong password per email** (`app.rate-limit.login-failures`, 5 every 15 minutes), counted in
  `AuthService`. The per-IP limit does not stop brute force spread over many IPs against one account; this
  one does. Only failures spend a token, and the block happens before the password is compared, with the
  same response whether the email exists or not.
- **429 response** in problem+json with `reason: RATE_LIMITED`, `retryAfterSeconds` and the `Retry-After`
  header. The front end shows how long to wait.
- **Real IP behind nginx:** `server.forward-headers-strategy=native` (Tomcat only accepts X-Forwarded-For
  coming from the internal network) and nginx, which is the edge, **overwrites** X-Forwarded-For with the
  connection's IP. If it only appended to it, a client could send a fake value on each request and escape
  the per-IP limit.

## Why

- Bucket4j is the most widely used token bucket implementation in the Java ecosystem, and a token bucket
  allows a short burst (open the app and send two messages) without allowing sustained volume.
- In memory is enough for today's deployment: a single instance in Compose.

## Consequences

- **Single instance only.** With replicas, each one would have its own buckets and the effective limit
  would multiply. The fix is to configure Bucket4j with the `bucket4j-postgresql` (or Redis) backend behind
  the same `RateLimiter`; nothing outside `shared.ratelimit` changes.
- Restarting the backend resets the counters.
- Blocking per email lets someone lock an account for 15 minutes by deliberately getting its password
  wrong. Accepted: it is short and requires nothing from the victim; the alternative (IP only) leaves the
  account exposed.
- Tests that are not about rate limiting and create many users turn it off with
  `app.rate-limit.enabled=false`; the behavior itself is covered by `RateLimitTest`.
