# ADR-001: Modular monolith instead of microservices

- **Status:** accepted
- **Date:** 2026-10-14

## Context

The system has four domain areas (catalog, booking, cancellation, rebooking) plus an AI orchestrator. It is
a portfolio project maintained by one person, with a single deployment.

## Decision

A single deployable, split into modules whose boundaries are verified by tests (Spring Modulith). Modules
talk to each other through public interfaces and domain events.

## Consequences

- Simple operations: one process, one database, local transactions where they make sense.
- Boundaries are enforced by code, not by convention; the build fails if they are violated.
- Extracting a module into a service later is possible because dependencies already go through
  interfaces and events.
- Cost: modules cannot be scaled independently. That is not a requirement of this project.
