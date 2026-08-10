# platform/openfga — OpenFGA store, model and tuple lifecycle

OpenFGA 1.x (per ADR-E0.5-01 + ADR-E0.5-06) is the relationship
authorization layer. The store topology, the model registry, the
bootstrap tuples, the audit hook and the sync workflow live here as
**config-as-code**. The runtime is the upstream
`openfga/openfga:1.10` image and the bootstrap Job applies the
source-of-truth on every `helm upgrade` via the OpenFGA Admin API.

## Files

| File | Purpose |
| ---- | ------- |
| `store-strategy.yaml` | ADR-E0.5-06 — store-per-tenant, shared model, consistency + cache policy, audit hook config. |
| `model-registry.yaml` | ConfigMap that ships the canonical `model.v1.json` + `migrations/v1-to-v2.json` + bootstrap tuples into the cluster. Mirrors `contracts/openfga/*.json`. |
| `bootstrap-tuples.json` | Default-role tuples emitted on a fresh store (OWNER/ADMIN default membership, AUDITOR implicit grant). |
| `audit-hook.yaml` | Webhook target + filter — every `Write` posts to `audit-service` over gRPC; tuples tagged `pii`, `dna`, `secret` are flagged. |
| `sync-workflow.yaml` | Temporal workflow config for `OpenfgaTupleSync` — idempotent write, revoke-first priority, deterministic timeout/retry. |

## Topology — store-per-tenant (ADR-E0.5-06)

| Env | Topology | Notes |
| --- | ------- | ----- |
| SaaS | store-per-tenant, shared model | Bootstrap Job creates the store on tenant onboarding (E3.5 trusted tenant context wires the `tenant_id` opaque ID). |
| on-prem | store-per-tenant, shared model | Identical to SaaS. |
| dev | single shared store | `scripts/local-up.sh` mounts the model via `platform/local/openfga/openfga-model-draft.yaml`; the linter refuses if the model diverges from `model.v1.json`. |

## Validation

| Command | Asserts |
| ------- | ------- |
| `pnpm lint:openfga` | YAML + model-registry + audit-hook + sync-workflow + tuple-content contracts. |
| `node --test contracts/openfga/compatibility.test.mjs` | Model structure + migration expand-contract. |
| `pnpm smoke:openfga` | Boots OpenFGA 1.x, uploads the model, runs 3 read/write round-trips. |
| `pnpm check:openfga` | `lint:openfga` + `node --test contracts/openfga/...` + platform baseline. |

## Owners

Primary: `platform` (`config/teams.yaml`). Secondary: `@genealogy/identity`. On-call: `identity-primary`. Mirrors `platform/helm/OWNERS`.
