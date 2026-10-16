# Architecture decisions

Each ADR records a decision that changes the architecture: the context, what was decided, why, and what it
costs. A new decision becomes a new file; a superseded decision stays, with its status pointing to the one
that replaced it.

| ADR | Decision | Phase |
|---|---|---|
| [001](ADR-001-modular-monolith.md) | Modular monolith instead of microservices | 0 |
| [002](ADR-002-ai-without-direct-writes.md) | The AI has no direct write access | 0 |
| [003](ADR-003-spring-modulith-and-flyway.md) | Spring Modulith for boundaries and Flyway for the schema | 0 |
| [004](ADR-004-seat-concurrency.md) | Conditional update for the race on seats | 2 |
| [005](ADR-005-assistant-loop-memory-rag.md) | Assistant design: tool loop, memory, RAG and PII | 4 |
| [006](ADR-006-rate-limit.md) | In-memory rate limiting, per route and per account | 6 |
| [007](ADR-007-session-refresh-token.md) | Session with a short access token and a rotated refresh token | 6 |
| [008](ADR-008-static-demo.md) | Static demo with a fake backend in the browser | 6 |
