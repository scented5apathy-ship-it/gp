# runbook/openfga.md — OpenFGA authorization model runbook

Operator-facing runbook for the platform-wide OpenFGA 1.x
authorization stack. Per `tasks.md` E3.3 +
`architecture-decisions.md` ADR-E0.5-06 (store-per-tenant +
shared model + cache invalidation on every Write + audit hook on
every Write) + `ownership-catalog.md` §3 (OpenFGA owner =
platform-primary + identity team, on-call identity-primary, SLO
99.95 %, check p95 < 80 ms).

## Source-of-truth map

| File | Purpose |
| ---- | ------- |
| `contracts/openfga/model.v1.json`           | Canonical OpenFGA v1.1 model. Object types: `user`, `group`, `tenant`, `tree`, `branch`, `person`, `resource`, `dna`. Conditions: `tenant_match`, `consent_active`, `revoked_blocks`. Tuple content = opaque IDs only. |
| `contracts/openfga/migrations/v1-to-v2.json`| Expand-contract delta for v1 → v2 (adds `tree#contributor`, `dna#reader`, `tenant#support`). Refuses in-place removal. |
| `contracts/openfga/compatibility.test.mjs`  | Node `--test` asserting v1 ⊂ v2 + bootstrap tuple syntax + no PII literal. |
| `platform/openfga/store-strategy.yaml`      | ADR-E0.5-06 — store-per-tenant, shared model, readConsistency p95 ≤ 500 ms, cache invalidation on every Write, audit hook required. |
| `platform/openfga/model-registry.yaml`      | ConfigMap that ships the canonical model + migration files into the cluster. |
| `platform/openfga/bootstrap-tuples.json`    | Default-role tuples emitted on a fresh store (OWNER / AUDITOR / SUPPORT / BILLING). Revoke-first priority enforced. |
| `platform/openfga/audit-hook.yaml`          | Audit hook sink (gRPC `audit-service:9090/audit.v1.AuditService/Append`) + redaction list + severity escalation for `pii` / `dna` / `secret` tags. |
| `platform/openfga/sync-workflow.yaml`       | Temporal workflow `OpenfgaTupleSync` — idempotent write + revoke-first priority + cache invalidation ack activity. |
| `platform/openfga/OWNERS`                   | Ownership mirror — primary `platform`, secondary `@genealogy/identity`, on-call `identity-primary`. |
| `platform/openfga/README.md`                | Operator-facing README. |

## Mirror

`platform/helm/genealogy-platform/files/openfga-*` mirrors the five
`platform/openfga/*` source-of-truth files byte-identical. The deep
linter (`scripts/lint-openfga-config.mjs`) rejects drift.

## Helm templates

`platform/helm/genealogy-platform/templates/components/openfga/`
ships:
- `statefulset.yaml` — 2 replicas, Postgres datastore (in-memory
  on dev), mounts `openfga-model-registry` + `openfga-bootstrap-tuples`
  + `openfga-audit-hook` ConfigMaps, startup + liveness +
  readiness probes against `/healthz`, Prometheus scrape annotation
  on `:9100`.
- `services.yaml` — headless ClusterIP for `openfga` on admin
  `:8080` + gRPC `:8081`.
- `serviceaccounts.yaml` — `openfga` ServiceAccount with
  `automountServiceAccountToken: false`.
- `network-policies.yaml` — default-deny + allow from
  `gp.genealogy/contract-only: "true"` namespaces on 8080 + 8081;
  egress to Postgres (5432) + OTel Collector (4317/4318).
- `configmaps.yaml` — 5 source-of-truth ConfigMaps
  (`openfga-store-strategy`, `openfga-model-registry`,
  `openfga-bootstrap-tuples`, `openfga-audit-hook`,
  `openfga-sync-workflow`).
- `bootstrap-job.yaml` — pre-install / pre-upgrade Helm hook Job
  that uploads the model + writes default-role tuples to every
  per-tenant store. Idempotent on `helm upgrade`.

## Validation

| Command | Asserts |
| ------- | ------- |
| `pnpm lint:openfga` | Deep YAML + model + migration + audit-hook + sync-workflow + bootstrap-tuple contracts. |
| `node --test contracts/openfga/compatibility.test.mjs` | Static compatibility assertions (v1 ⊂ v2, no PII literal, tuple syntax). |
| `pnpm smoke:openfga` | Boots OpenFGA 1.10, uploads model, runs read/write round-trip + revoke + re-issue on a fresh store. **BLOCKED** when docker daemon is unavailable (CI-only gate). |
| `pnpm check:openfga` | `lint:openfga` + compatibility test + platform baseline. |

## Operating procedures

### Onboard a new tenant (store-per-tenant)

1. tenant-service creates a tenant row + emits a
   `tenant.onboarded.v1` event (E3.2 / E3.5).
2. The event consumer triggers the `openfga-bootstrap` Job
   (via Temporal workflow `OpenfgaStoreInit`) which:
   - creates a new store via the Admin API,
   - uploads `model.v1.json` via `WriteAuthorizationModel`,
   - writes the bootstrap default-role tuples,
   - emits an audit entry per upload.
3. tenant-service writes the OWNER tuple
   (`tenant:<id>#owner@user:<opaque_id>`) — at this point
   the ABAC overlay (`AbacPolicyEngine`) validates the
   Keycloak subject matches the tenant.

### Member-role change (revoke-first priority)

1. The calling service emits a `membership.role_changed.v1`
   event.
2. Temporal workflow `OpenfgaTupleSync` runs:
   - **revoke** the affected tuples for the subject,
   - **wait** for the calling service's Valkey cache key
     invalidation ack (closes the eventual-consistency window),
   - **write** the new role tuples,
   - **write** the audit entry.
3. The ABAC overlay gates every step (Semgrep rule
   `no-openfga-allow-without-abac`).

### Model upgrade (v1 → v2)

1. Add the migration entry to `contracts/openfga/migrations/v1-to-v2.json`
   declaring every added relation + the expand-contract
   invariants.
2. Update `platform/openfga/store-strategy.yaml` →
   `bootstrap.modelVersion` → bump `bootstrap.modelPath` to
   `model.v2.json`.
3. Land `contracts/openfga/model.v2.json` mirroring the migration.
4. Run `pnpm lint:openfga` + `node --test contracts/openfga/compatibility.test.mjs`.
5. The `openfga-bootstrap` Job on the next `helm upgrade`
   uploads v2 in **shadow** mode (no tuple migration yet).
6. After the rollout window completes, the migration Job
   translates v1 tuples to v2 (idempotent — re-runnable).
7. Bump `bootstrap.modelVersion` to `2` and roll forward.

### Emergency revocation (R16 §R16.3 — support access)

1. Operator opens a support ticket; the platform issues a
   time-bounded `tenant:<id>#support@user:<opaque_id>` tuple
   with `support_window_active` condition.
2. On issue close, the `OpenfgaTupleSync` workflow DELETEs
   the tuple (revoke-first). Without the workflow the
   audit hook still flags the revocation when the condition
   expires (next `Check` after `support_expires_at` returns
   `allowed=false`).
3. Audit entry captures `actor_subject` + `support_justification_id`.

### Cache invalidation invariant

Every `Write` MUST invalidate the Valkey cache key
`openfga:check:{tenant_id}:*` BEFORE the response returns
to the caller. The `cache.invalidationOnWrite: required`
flag in `store-strategy.yaml` is enforced by the
`OpenfgaTupleSync` workflow + the `cacheInvalidationAck`
activity. The linter refuses the store strategy if the
flag is missing.

### Privacy posture (audit hook)

The `audit-hook.yaml` `redact` list is exhaustive — adding
a new attribute category (e.g. `biometric`) requires:
1. Bumping the redact list in `audit-hook.yaml`,
2. Adding the new field to the `severityEscalation` map,
3. Re-running `pnpm lint:openfga`.

The linter refuses an `audit-hook.yaml` whose redact list
diverges from the store-strategy redaction list.

## Observability

- **Latency** — `openfga_check_duration_seconds` histogram
  (label: `tenant_pseudo_id`, `relation`). SLO check p95 < 80 ms.
- **Errors** — `openfga_write_failures_total` counter
  (label: `tenant_pseudo_id`, `error_kind`).
- **Audit** — every Write emits one entry on
  `audit-service:9090/audit.v1.AuditService/Append`. Severity
  escalates to `critical` for tuples tagged `dna` or `secret`.
- **Cache** — `openfga_cache_invalidations_total` counter
  (label: `tenant_pseudo_id`).

## Owners

Primary: `platform` (`config/teams.yaml`). Secondary:
`@genealogy/identity`. On-call: `identity-primary`. Mirrors
`platform/openfga/OWNERS`.
