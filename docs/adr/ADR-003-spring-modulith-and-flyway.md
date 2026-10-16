# ADR-003: Spring Modulith for boundaries and Flyway for the schema

- **Status:** accepted
- **Date:** 2026-10-14

## Decision

1. **Spring Modulith** enforces the boundaries between modules: only the module's root package is public,
   `internal` is private. `ModularityTests` runs in CI.
2. **Flyway** owns the schema. Hibernate runs with `ddl-auto: validate`. Migrations are immutable once
   applied.
3. **Modern JPA** for data access: Hibernate 6, records as read projections, Specifications for dynamic
   queries and entity graphs against N+1.

## Consequences

- Drift between entities and schema is detected when the application starts.
- Schema changes can be reviewed like code and reproduced in any environment.
- Extra discipline: every table change needs a new migration.
