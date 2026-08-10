# contracts/openfga — OpenFGA authorization model

OpenFGA model + migration assets are the **source of truth** for the
relationship authorization layer (`design.md` §4.2 + §6.1,
`architecture-decisions.md` §6 / ADR-E0.5-06). The runtime
(per-store cluster) consumes this model via the `openfga-bootstrap`
Helm-hook Job on every `helm upgrade` per `tasks.md` E1.6.

## Files

| File | Purpose |
| ---- | ------- |
| `model.v1.json` | Canonical OpenFGA v1.1 model (`schema_version: 1.1`). All type_definitions, relations and conditions live here. This file is uploaded to a fresh store via `WriteAuthorizationModel` on tenant bootstrap. |
| `migrations/v1-to-v2.json` | Expand-contract delta for the v1→v2 promotion. v2 introduces `tree#contributor` and `dna#reader`; v1 is kept under `migrations/` until the rollout finishes. |
| `compatibility.test.mjs` | Node `--test` asserting v1 + v2 each parse, that v1 ⊂ v2 (no relation removed, no type removed), and that the canonical check `viewer can view tree#viewer` returns `allowed=true` for an owner tuple. |
| `README.md` | Operator-facing index (this file). |

## Versioning rules

1. **Never delete a type or relation in-place.** Promote to a new file
   under `migrations/` and add a `vN-to-vN+1` delta. The current
   model file name stays `model.v<n>.json` where `<n>` matches the
   version pinned in `platform/openfga/store-strategy.yaml`.
2. **Conditions are versioned.** New condition names live in the
   model file that introduces them; old condition names persist
   until the rollout that no longer references them ships.
3. **Tuple content is opaque IDs only.** No PII, raw DNA, token,
   email or display name appears in any tuple (ADR-E0.5-06
   §Security / privacy).
4. **Every `Write` emits an audit hook** (`audit_hook: required` in
   the model header per OpenFGA v1.1). The hook posts to
   `audit-service` over gRPC; the linter refuses a model that
   removes the hook.
5. **ABAC overlay stays in the application.** OpenFGA only decides
   *relationships*; the in-service `AbacPolicyEngine` (Java) /
   `PiiAbacGuard` (TS) enforces living / DNA / consent / contextual
   deny. The `no-openfga-allow-without-abac` Semgrep rule
   (`security/semgrep/semgrep.local.yaml` §4) enforces this at PR
   time.

## Validation

```
node --test contracts/openfga/compatibility.test.mjs
node scripts/lint-openfga-config.mjs
pnpm smoke:openfga        # boots openfga/openfga:1.10, uploads model, runs 3 read/write round-trips
```

The compatibility test MUST pass before any migration file is
merged; the lint script refuses a model that drops a relation
without a migration entry.
