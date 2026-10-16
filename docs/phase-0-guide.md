# Phase 0: how the foundation works

This guide explains what phase 0 delivered, why each piece exists and how to extend the project without
breaking the rules it set. It describes the foundation; where later phases changed something, the guide
says so.

## 1. Goal and definition of done

Phase 0 has no flight features. It delivers a base on which any new feature is born secure, testable and
inside clear boundaries.

Done when:

- [ ] `./mvnw verify` passes on your machine (unit, Modulith and integration tests with Testcontainers)
- [ ] `docker compose up --build` starts Postgres and the backend without errors
- [ ] `/actuator/health` answers `UP`
- [ ] registering, logging in and calling `/me` works through Swagger or curl
- [ ] CI is green on GitHub

## 2. Flow of a request

```
Client
  │  POST /auth/login  (or GET /me with a Bearer token)
  ▼
Spring Security filters
  │  /auth/** is public; everything else requires a valid JWT
  │  the JwtDecoder validates the signature (HS256), expiry and issuer
  ▼
Controller (identity.internal)      ← validates the payload with Jakarta Validation
  ▼
Service                             ← business rules and transaction
  ▼
Repository (Spring Data JPA)  ──►  PostgreSQL
  │
  ▼
Errors become application/problem+json (RFC 9457)
```

On `/me`, the controller does not receive the user id as a parameter. It asks `CurrentUser`, which reads the
`subject` of the already validated token. The AI's tools follow the same pattern.

## 3. Module boundaries (Spring Modulith)

Each direct package of `com.atlas` is a module. Rules:

| Location | Visibility |
|---|---|
| The module's root package (`identity`) | Public API: interfaces, DTOs and events other modules may use |
| `internal` subpackage | Private: controllers, services, entities, repositories |
| `shared` | Open module, anyone may depend on it; keep it small and free of business rules |

`ModularityTests` fails the build if a module touches another module's `internal` package or if there is a
dependency cycle. It also generates PlantUML diagrams in `backend/target/spring-modulith-docs`.

How modules talk to each other:

1. **Direct call to a public interface** when an immediate answer is needed
   (e.g. `booking` asks `catalog` for availability).
2. **Domain event** when it is a reaction with no answer
   (e.g. `BookingCancelled` makes the seats go back to the inventory).

Never access another module's tables or repositories.

## 4. Security

- **Authentication:** JWT signed with HS256. The secret comes from `JWT_SECRET` (at least 32 bytes). Without
  it the application does not start, on purpose.
- **Token:** `sub` = user id, `role` claim. Since phase 6 the access token lasts 15 minutes and is renewed with
  a rotated refresh token in an HttpOnly cookie ([ADR-007](adr/ADR-007-session-refresh-token.md)).
- **Passwords:** `DelegatingPasswordEncoder` (BCrypt by default), with a dummy hash on login so response
  time does not reveal which emails exist.
- **Stateless API:** no server-side session. The refresh cookie is `SameSite=Strict`, which is what protects
  the session routes against CSRF.
- **Identity never comes from the client or the model:** only from the token, through `CurrentUser`.
- **Rate limiting** on login, registration and chat arrived in phase 6 ([ADR-006](adr/ADR-006-rate-limit.md)).

## 5. Database and migrations

- The schema belongs to **Flyway**. Hibernate runs with `ddl-auto: validate` and only checks it.
- Files live in `src/main/resources/db/migration`, named `V<number>__<description>.sql`.
- **Never edit a migration that has already been applied.** Create the next one.
- `V1` enables pgvector (used by RAG in phase 4); `V2` creates `users`.
- To start over in development: `docker compose down -v`.

## 6. Configuration and secrets

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Postgres connection | `localhost:5432/flightsai` |
| `JWT_SECRET` | JWT secret | none (required) |
| `OPENAI_API_KEY` | Chat and embeddings (phase 4) | `missing`: the app starts, the chat answers 503 |

The `local` profile defines a development secret so the app can run from the IDE. The `.env` file stays out
of Git; the README shows the minimum it needs. API keys never go into the repository.

## 7. Tests

| Test | What it guarantees | Needs Docker |
|---|---|---|
| `ModularityTests` | Boundaries between modules | No |
| `JwtPropertiesTest` | Fails fast with a missing or short secret | No |
| `AuthFlowTest` | Register, login, `/me`, 401, 409, 400 against a real Postgres | Yes |

The integration test uses the same image as Compose (`pgvector/pgvector:pg16`), so the difference between
tests and the real environment is minimal.

## 8. Docker and CI

- **Dockerfile** in two stages (Maven to compile, a slim JRE to run), non-root user.
- **Compose:** `postgres` with a healthcheck and a `backend` that only starts once the database is healthy.
  The front end and Jaeger joined in later phases.
- **CI:** `./mvnw -B verify` on GitHub Actions and the Modulith diagrams uploaded as an artifact. Since phase
  6 it also tests and builds the front end, publishes the images to GHCR and deploys the static demo.
- **Path to the cloud:** the images are the same. Point `DB_URL` at a managed PostgreSQL with pgvector and
  inject the secrets through the platform.

## 9. How to create a new module (the phase 1 checklist)

1. Create the `com.atlas.catalog` package with a public API class (interface or record).
2. Put controllers, services, entities and repositories in `catalog.internal`, package-private.
3. Create the `V3__catalog.sql` migration with the module's tables.
4. Expose to the rest of the system only what is needed, in the root package.
5. Write the module's integration test and run `ModularityTests`.
6. Record relevant decisions as an ADR.

## 10. What came next

- **Phase 1:** catalog, seeded flights and REST search.
- **Phase 2:** bookings with hold, expiry and concurrency.
- **Phase 3:** `PendingAction`, two-step cancellation and rebooking.
- **Phase 4:** Spring AI (tool loop, memory, RAG with pgvector, PII tokenization).
- **Phase 5:** Angular with chat, cards and a confirmation dialog.
- **Phase 6:** rate limiting, observability, refresh tokens, published images and the static demo.

## 11. Things to check on the first run

- `pom.xml` versions: patches checked in phase 6 (Spring Boot 3.5.16, Modulith 1.4.13, springdoc 2.8.17).
  When updating again, check for the latest patches of the same line.
- If `spring-modulith-docs` or a test import does not resolve, adjust it to the Modulith version. It is the
  most version-sensitive part.
- The first build downloads a lot of dependencies (and, through `./mvnw`, Maven itself); it takes a few
  minutes.
- **Testcontainers with a recent Docker Desktop:** up to phase 5, Testcontainers 1.21.3 failed with "Could not
  find a valid Docker environment" and needed `api.version=1.44` in `~/.docker-java.properties`. Version
  1.21.4, which comes with Spring Boot 3.5.16, fixes it; the file is no longer needed.
- **Swagger returns 401 on `/me`:** expected without a token. Use the **Authorize** button (see the README).
