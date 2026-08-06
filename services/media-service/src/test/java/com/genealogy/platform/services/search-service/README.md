# services/media-service → search-service

Cross-service package marker. The `com.genealogy.platform.services.search-service`
test source directory inside `services/media-service` is intentionally empty
in the E1.1 scaffold. It exists so the ArchUnit boundary guard
(`AGENTS.md` §2 / `scripts/check-monorepo-boundaries.mjs`) can
detect any future drift where `services/media-service` accidentally
imports another service's `db/` or `domain/` package.

Per `design.md` §14 and `ownership-catalog.md` §2 each service
owns its own aggregate and contract:

- Each `services/<svc>/src/main/java/com/genealogy/platform/services/<svc>/`
  package is the canonical home for that service's aggregate root,
  command handlers, gRPC service implementations, REST controllers
  and outbox publisher.
- `services/<svc>/src/test/java/com/genealogy/platform/services/<svc>/`
  hosts the unit, contract and ArchUnit tests for the same service.

Boundary rules enforced by CI:

- No file under `services/media-service/` may import another service's
  `db/` (Flyway / jOOQ) or `domain/` (entities, aggregates)
  package — including the `search-service` package.
- Shared cross-cutting concerns live in `libs/platform-{errors,
  feature-flags, security, telemetry}`, not here.
- Cross-service interaction happens via gRPC + Kafka events, never
  via shared database tables or domain imports.

Owner of `services/media-service`: see `services/media-service/OWNERS`.
Owner of the `search-service` package: see `services/search-service/OWNERS`.
