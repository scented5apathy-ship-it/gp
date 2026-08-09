# Platform — Apicurio Schema Registry (E2.3)

Source-of-truth Apicurio configuration for the genealogy platform.

## Layout

- `registry-config.yaml` — `application.properties` (storage, auth,
  metrics) + the per-artifact compatibility matrix
  (ADR-E0.5-08 — `BACKWARD` default, `FORWARD` / `FULL` overrides).

The same file is mirrored into the umbrella chart's
`files/apicurio/` directory. The chart's Deployment mounts the
`application.properties` ConfigMap at runtime.

## Storage

PostgreSQL-backed (`registry.storage.kind=sql`). The in-memory
datastore is forbidden in production because the schema store must
survive a pod restart.

## Compatibility

The global default is `BACKWARD`; the per-artifact matrix overrides
are seeded by `scripts/seed-apicurio.mjs` (E2.3) and pinned in
`registry-config.yaml`. A breaking change must bump the inner
`v1` -> `v2` and reset the consumer offset accordingly.

## Validation

- `pnpm lint:apicurio` — deep validation of `registry-config.yaml`.
- `pnpm check:platform:baseline` — E2.3 invariants.
- `pnpm smoke:apicurio` — live validation (if Apicurio is up).

## Ownership

`OWNERS` mirrors `config/teams.yaml`. Primary = `platform`,
secondary = `@genealogy/data`, on-call = `data-primary`.
