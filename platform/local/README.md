# platform/local

Local-development stack that mirrors the SaaS topology on a single
developer machine (Docker Desktop / OrbStack / colima).

Per `design.md` §3.1 and §13, the same Helm artifacts that ship to
production also drive this stack so cloud/on-premise parity holds
during development. Components included:

- PostgreSQL with per-service schemas + RLS seed scripts.
- Keycloak realm export (`keycloak-realm-genealogy-dev.json`) with
  the test identities, client scopes and roles used by contract tests.
- OpenFGA with the canonical relationship model
  (`openfga-model-draft.yaml`).
- Strimzi Kafka single-node + Apicurio Registry.
- Temporal dev server with the namespace pre-created.
- MinIO with the bucket layout (`media`, `media-quarantine`,
  `dna-raw`) and per-bucket lifecycle policies.
- Valkey (Redis-compatible) and Flagsmith local container.
- OTel Collector + Grafana OSS dashboards from
  `platform/observability/`.

Entrypoints (added in later epics): `make up`, `make down`,
`make logs`, `make reset`. Owner: platform-primary.

## E2.1 — Local profile (added in E2.1)

`platform/local/profile.yaml` is the canonical description of the
local stack; `docker-compose.yml` is the Docker-Desktop / OrbStack
/ colima realisation. The values pinned here are tracked by
`scripts/check-platform-baseline.mjs`:

- PostgreSQL 16 (per ADR-E0.5-01) with per-service schemas + RLS
  seed scripts (`db/init/*.sql`).
- Keycloak 26 with `keycloak/keycloak-realm-genealogy-dev.json`.
- OpenFGA 1.10 with `openfga/openfga-model-draft.yaml` (in-memory
  datastore for deterministic contract tests).
- Strimzi Kafka single-node 0.43.0 + Apicurio Registry 2.6.
- Temporal 1.26 auto-setup.
- MinIO + the bucket layout (`media`, `media-quarantine`,
  `dna-raw`, `import-export`) with versioning and lifecycle
  policies.
- Valkey 7.2 for cache / session / rate state.
- Flagsmith latest with `sdkSafeDefault: true` (per E2.8).
- OTel Collector 0.110 + Grafana / Prometheus / Loki / Tempo.

Secrets come from `.env.local` (gitignored); the preflight script
refuses any literal credential in `docker-compose.yml`.
