# ADR-008: Static demo with a fake backend in the browser

- **Status:** accepted
- **Date:** 2026-10-16

## Context

Evaluating the project used to require Docker, a `.env` and, for the chat, an OpenAI key with credit. Most
people who look at a portfolio (recruiters, reviewers) will not do that. Hosting the real stack (backend,
Postgres with pgvector, the model) costs money every month, needs maintenance and puts a paid API key behind
a public URL.

## Decision

- A **demo build of the same Angular app** (`npm run build:demo`), published to **GitHub Pages** by CI on
  every push to `main`.
- The `demo` build configuration swaps one file (`demo-mode.ts` → `demo-mode.demo.ts`) that adds an HTTP
  interceptor answering every `/api` call **inside the browser**. Nothing else in the app knows about it:
  the auth interceptor, error handling, cards and confirmation dialog are the production code.
- The fake backend (`src/app/demo/`) follows the real **contract and rules**: the same catalog as the backend
  seeder, 15-minute holds, the simulated payment gateway (`tok_decline` declines), the fare rules from
  `FarePolicy`, cancellation and rebooking only through a pending action confirmed in the UI, revalidation
  on confirmation, and Idempotency-Key replays. State lives in `sessionStorage`.
- The **assistant is scripted**: regular expressions pick the intent and the reply is built from the same
  tool results the backend returns. It shows PII tokenization and refuses instructions to skip
  confirmation, but it is not a language model, and the page says so on a banner.

## Consequences

- Anyone can try the full flow in seconds, at zero cost, with nothing to maintain.
- The demo does not prove the real assistant. That is what `AssistantFlowTest`, the live eval and the
  screenshots are for, and the README points to them.
- Two implementations of the same rules can drift apart. The demo has its own tests
  (`demo-server.spec.ts`), and the real rules remain the source of truth; changing a fare rule means
  updating both.
- The regular build ships none of the demo code: there the interceptor list is empty and the fake backend is
  never imported.
