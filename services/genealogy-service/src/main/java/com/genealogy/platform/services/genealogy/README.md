# services/genealogy-service → genealogy

E4.1 (`Tree` aggregate + visibility + collaboration +
UNLISTED-token) lives in this package. Future E4 subtasks
(E4.2 Person, E4.3 Date / Place, E4.4 Relationship, E4.5 Event
/ Claim, E4.6 Merge, E4.7 Outbox) will land additional packages
under `com.genealogy.platform.services.genealogy.*`.

Boundary rules enforced by CI
(`scripts/check-monorepo-boundaries.mjs`):

- No file under `services/genealogy-service/` may import another
  service's `db/` (Flyway / jOOQ) or `domain/` package.
- Shared cross-cutting concerns live in `libs/platform-*`.
- Cross-service interaction happens via gRPC + Kafka events,
  never via shared database tables or domain imports.

Owner of `services/genealogy-service`: see
`services/genealogy-service/OWNERS`.
