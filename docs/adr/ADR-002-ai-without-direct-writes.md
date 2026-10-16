# ADR-002: The AI has no direct write access

- **Status:** accepted
- **Date:** 2026-10-14

## Context

An LLM can be led, by prompt injection or by mistake, to perform actions the user did not want.
Cancellation and rebooking involve money and are not trivial to undo.

## Decision

- The AI's tools only **query** and **propose**. There is no tool that cancels or confirms anything.
- A proposal creates a `PendingAction` (type, user, payload, expiry, status).
- Execution happens in a dedicated REST endpoint (`POST /pending-actions/{id}/confirm`), authenticated and
  called by the UI, never by the model.
- The backend revalidates everything at confirmation time, inside a transaction.
- The user id comes from the token (`CurrentUser`), never from a parameter the model chooses.

## Consequences

- Removes the whole class of risk "the model was convinced to confirm on its own".
- The UI needs an explicit confirmation step (a card with Confirm and Reject).
- There is an intermediate state (`PendingAction`) to model, expire and audit.
