# Platform — Temporal (E2.4)

Source-of-truth Temporal configuration for the genealogy platform.

## Layout

- `namespace-config.yaml` — pre-created Temporal namespaces with
  retention + visibility policy (E2.4 §1).
- `search-attrs.yaml` — exhaustive visibility attribute schema
  - forbidden name list (E2.4 §3 + ADR-E0.5-07 §privacy).
- `dynamic-config.yaml` — server-side dynamic config: per-namespace
  retention, activity / workflow defaults, visibility attribute
  whitelist (E2.4 §2).
- `task-queues.yaml` — declarative task-queue list with worker
  identity allowlist + per-queue retention (E2.4 §1 + §4).
- `schemas/` — workflow / activity contract schemas (Protobuf).
  Populated as each consuming epic lands (E7.2 / E9.1 / E10.4 / …).

The same files are mirrored into the umbrella chart's
`files/temporal/` directory so `helm template` can render without
reading outside the chart root. Anything you change here MUST be
mirrored into the chart or the umbrella will drift.

## Runtime

The umbrella chart wires four StatefulSets + Deployments (frontend /
history / matching / worker + UI) and two Helm-hook Jobs
(`temporal-namespace-init`, `temporal-task-queue-init`) that
reconcile the namespace + task-queue config against the live
server via `temporal operator namespace` / `temporal task-queue update`.

## Privacy

- Search attribute whitelist enforces opaque IDs only — no raw
  PII, DNA, token, file content, or signed URL.
- Worker identities must match a Kubernetes service-account name.
  Any unknown identity is rejected at `RegisterTaskQueue`.
- The `genea-dna` namespace carries 365-day retention per
  `privacy-and-legal-gate.md` §14.

## Validation

- `pnpm lint:temporal` — deep validation of the four YAML files
  - the per-file invariants (retention, PII-forbidden search
    attributes, queue / namespace coverage).
- `pnpm check:platform:baseline` — extended with E2.4 invariants
  (chart templates present, per-env overrides, image pin).

## Smoke

- `pnpm smoke:temporal` — live probe against the `temporalio/auto-setup`
  image. Starts a single workflow, signals cancellation, and
  asserts visibility attribute whitelist rejects a forbidden
  field (the chart's dynamic config guarantees the rejection).

## Ownership

`OWNERS` mirrors `config/teams.yaml`. Primary = `platform`,
secondary = `@genealogy/data`, on-call = `data-primary`.
