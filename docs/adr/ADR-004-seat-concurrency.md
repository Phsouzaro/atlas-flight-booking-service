# ADR-004: Conditional update for the race on seats

- **Status:** accepted
- **Date:** 2026-10-15

## Context

In phase 2, two people may try to hold the last seat of a fare at the same time. Reading what is available,
checking it in the application and writing afterwards is the classic recipe for overbooking: both
transactions read 1, both pass, and the inventory goes to -1 (or, with the CHECK constraint from V3, one of
them blows up with a constraint error instead of a clean 409).

The two options considered:

1. **Conditional update:** a single statement checks and decrements.
   `UPDATE seat_inventory SET available_seats = available_seats - :n WHERE ... AND available_seats >= :n`.
   One affected row means the seats were reserved; zero means there were not enough.
2. **Optimistic locking:** a `version` column on `seat_inventory`, decrement in memory and an
   `OptimisticLockException` when another transaction wrote first. Requires retries.

## Decision

**Conditional update** (`SeatInventoryRepository.decrementAvailable`, exposed as
`AvailabilityApi.reserveSeats`). Returning seats is the mirror image: `available_seats + :n <= total_seats`.

## Why

- **Atomic in the database:** Postgres locks the row only for the duration of the `UPDATE`. Whoever comes
  second waits a few milliseconds, re-evaluates the `WHERE` against the new value and gets 0 rows. There is
  no window between reading and writing.
- **No retries:** with optimistic locking, under contention (a popular flight, the last seat) most attempts
  fail on a version conflict even when seats are still left, and each failure goes back to a retry. Here
  the answer is final: either the seats were reserved or there are none.
- **No long lock:** no `SELECT ... FOR UPDATE` holding the inventory row while the rest of the hold (code,
  passengers, audit) is written.
- **Safety net:** V3's `available_seats BETWEEN 0 AND total_seats` CHECK keeps the invariant even if some
  future code writes without going through here.

## Consequences

- The decrement is a JPA bulk update: it bypasses the persistence context. A `SeatInventory` loaded earlier in
  the same transaction keeps the old number. Code that reserves seats must not trust that value after calling
  `reserveSeats`.
- Reserving only takes seats; the state of the hold (expiry, confirmation) belongs to the `booking` module.
  The **booking** row is a different case: confirming involves charging the card and the expiration job
  competes for the same row, so there we use `SELECT ... FOR UPDATE` on confirmation and
  `FOR UPDATE SKIP LOCKED` in the job. Whoever gets the row first decides; the other sees the status
  already changed.
- Required test: `BookingConcurrencyTest` fires simultaneous requests for the last seat and checks that
  exactly one succeeds and the inventory ends at zero.
