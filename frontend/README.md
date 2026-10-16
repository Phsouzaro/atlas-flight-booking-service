# Front end (Angular 22)

Chat with the assistant, the booking list and confirmation of the actions the assistant proposes. Standalone
components, signals, zoneless, no UI library. The interface is in Brazilian Portuguese, like the assistant.

## Running

Requires Node 24 (Angular 22 needs 22.22+ or 24.15+).

```bash
npm install
npm start            # http://localhost:4200, proxying /api to the backend on localhost:8080
npm run test:ci      # tests (Vitest)
npm run build        # production build in dist/atlas-web/browser
```

Through Compose (at the repository root) the front end runs on `http://localhost:4200`, served by nginx with
the same `/api` proxy.

## Demo mode (no backend)

```bash
npm run start:demo   # http://localhost:4200/atlas-flight-booking-service/
npm run build:demo   # dist/atlas-web-demo/browser, the build published to GitHub Pages
```

The `demo` configuration swaps `src/app/demo/demo-mode.ts` for `demo-mode.demo.ts`, which adds an HTTP
interceptor that answers every `/api` call in the browser (`src/app/demo/`). The fake backend follows the
real API contract and rules (15-minute holds, test cards, cancellation and rebooking only through a
confirmed pending action, revalidation, Idempotency-Key) and keeps its state in `sessionStorage`. The
assistant's replies come from a script, not from a model. In the regular build that list of interceptors is
empty, so none of this code ships to production.

## API client

Types come from the backend contract; they are not written by hand:

```bash
npm run api:update     # downloads /v3/api-docs from the running backend into openapi.json (versioned)
npm run api:generate   # generates src/app/api/schema.ts from it
```

Screenshots for the root README (with Compose running; uses the installed Edge or Chrome in headless mode and
talks to the real assistant):

```bash
SMOKE_USER_EMAIL=... SMOKE_USER_PASSWORD=... npm run screenshots   # writes to ../docs/images
```

`src/app/api/types.ts` only gives short names to the generated types and fixes what OpenAPI cannot express
(nullable `refund`, free-form `payload`). The services in `src/app/core/api.ts` use those types with
`HttpClient`.

## Rules the UI follows

- **Only the UI confirms.** Cancellation and rebooking arrive as a pending action (a card with Confirm and
  Reject); execution is `POST /pending-actions/{id}/confirm`, called when the user clicks inside a dialog
  (ADR-002).
- **Idempotency-Key** on every POST that changes state. The same key is reused on "try again" and when the
  network fails without a response; a rejection from the backend gets a new key.
- **Simulated payment:** the UI only offers test cards (`tok_visa`, `tok_decline`...). No card number ever
  goes through the application.
- **Times:** flights in the airport's time zone, as the backend sends them; instants (creation, deadlines)
  in Brasília time.
- **Session:** the access token lives only in memory and goes only to `/api`; the refresh token is an
  HttpOnly cookie the page cannot read (ADR-007). A failed refresh ends the session and returns to login.

## Structure

```
src/app/
  api/         schema.ts (generated) and types.ts
  core/        auth (service, interceptor, guard), API clients, problem+json errors, formatting
  shared/      dialog, pending action card, flight and booking cards, test card picker
  features/    login, chat, bookings
  demo/        fake backend for the static demo (only in the demo build)
nginx/         container configuration (proxy, cache, CSP)
scripts/       API contract update and README screenshots
```
