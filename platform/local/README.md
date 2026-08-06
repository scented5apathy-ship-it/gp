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
