# AGENTS.md — Genealogy Platform

This file applies to **every** agent (human or AI) working in this
repository. It mirrors and extends the project-wide rules in
`.kiro/specs/genealogy-platform/agent-execution.md`.

## 1. Source of truth

| Document                                                   | When to read                     |
| ---------------------------------------------------------- | -------------------------------- |
| `.kiro/specs/genealogy-platform/requirements.md`           | Reading R1–R18 / NFR1–NFR8       |
| `.kiro/specs/genealogy-platform/design.md`                 | Architectural decisions          |
| `.kiro/specs/genealogy-platform/tasks.md`                  | Picking up an epic/subtask       |
| `.kiro/specs/genealogy-platform/ownership-catalog.md`      | Service ownership, RACI, SLOs    |
| `.kiro/specs/genealogy-platform/architecture-decisions.md` | ADR index + numeric thresholds   |
| `.kiro/specs/genealogy-platform/agent-execution.md`        | How to execute a task end-to-end |

Always start by reading the relevant section of these documents. If
something is unclear, ask **before** making code/config changes that
could be hard to revert.

## 2. Monorepo guardrails (E1.1)

The following rules are **non-negotiable**:

1. **Service boundary.** No file under `services/<svc>/` may import
   another service's `db/` (Flyway / jOOQ) or `domain/` (entities,
   aggregates) package. Enforced by:
   - ArchUnit tests in every service module.
   - `scripts/check-monorepo-boundaries.mjs` (run in CI).
2. **Java toolchain = 21.** Subprojects must apply
   `platform-build-logic.java-conventions`. Pinning any other version
   in a `build.gradle.kts` requires an ADR supersession.
3. **Dependency versions** come from `gradle/libs.versions.toml` only.
   No literal version inside a service `build.gradle.kts`.
4. **Lockfiles are committed.** `pnpm-lock.yaml` and per-module
   `gradle.lockfile` must be in the PR.
5. **TypeScript strict mode** is inherited from `tsconfig.base.json`.
   Per-package overrides must enable every strict flag present in the
   base config.
6. **OWNERS / CODEOWNERS** are mirrored from `config/teams.yaml`. The
   per-service `OWNERS` file lists the primary team, secondary
   (`@genealogy/platform`) and on-call slug.
7. **No shared domain model.** Shared libraries under `libs/` may only
   carry cross-cutting concerns (errors, telemetry, security,
   feature-flags, testing utilities) — never an aggregate root from
   `services/<svc>/domain/`.

## 3. Security / privacy / tenant isolation

Every change must respect:

- Tenant boundary is enforced in code **and** via Postgres RLS. Trust
  nothing from upstream metadata.
- OpenFGA decides relationships; ABAC overlays living status, DNA,
  consent and contextual deny.
- Kong handles edge / runtime policies; domain authorization lives in
  the service.
- Istio handles workload identity / mTLS. Do not configure retries
  that amplify at the mesh layer.
- Vault / KMS manage secrets. Never commit a secret, PII, DNA or
  webhook payload.
- OTel metrics, logs and traces must use the
  `tenant_pseudo_id` / `user_pseudo_id` label only — never raw
  identifiers.

## 4. Quality gates before any PR can merge

- [ ] `pnpm format:check` passes
- [ ] `pnpm lint` passes (incl. `lint:eslint`, `lint:yaml`,
      `lint:openapi`, `lint:protobuf`, `lint:events`,
      `lint:markdown`, `lint:ownership`, `lint:generated`)
- [ ] `pnpm typecheck` passes
- [ ] `pnpm test:unit` passes
- [ ] `pnpm test:contract` passes (Node-side contract tests +
      Gradle `ContractInvariantsTest`)
- [ ] `pnpm check:java` passes (Checkstyle + tests)
- [ ] `pnpm check:boundary` passes (cross-service boundary)
- [ ] `pnpm check:gradle:lock` passes (every `lockAll` subproject has
      `gradle.lockfile` committed)
- [ ] `pnpm check:platform:baseline` passes (cluster baseline
      preflight: namespaces, default-deny NetworkPolicy, PDB,
      StorageClass encryption, per-env values, probe contract,
      Kong runtime invariants — E2.2)
- [ ] `pnpm lint:kong` passes (Kong declarative config validator — E2.2)
- [ ] `pnpm lint:kafka` passes (Strimzi Kafka + Apicurio config validator — E2.3)
- [ ] `pnpm lint:temporal` passes (Temporal namespace / search-attribute /
      dynamic-config / task-queue validator — E2.4)
- [ ] `pnpm lint:istio` passes (Istio MeshConfig / PeerAuthentication /
      AuthorizationPolicy / disjoint retry validator — E2.5)
- [ ] `pnpm lint:vault` passes (Vault server-config / auth-methods /
      policies / KMS abstraction / injector templates validator — E2.6)
- [ ] OWNERS touched when ownership changes
- [ ] Completion Evidence file added at
      `.kiro/specs/genealogy-platform/evidence/<TASK_ID>.md`
- [ ] Checkbox in `tasks.md` flipped to `[x]` **only after** the
      evidence file is committed

## 5. Things you must NOT do

- Do not invent a platform capability that Kong, Keycloak, OpenFGA,
  Temporal, Strimzi/Apicurio, Istio, Vault/KMS, Argo or Grafana stack
  already provide.
- Do not pin a runtime version outside an ADR.
- Do not enable an `AGPL` dependency on the SaaS control plane.
- Do not store raw DNA or PII in code, config, logs, metrics, traces
  or event payloads.
- Do not commit `node_modules`, `.gradle`, `build/`, `target/`,
  `dist/`, `.next/` or `coverage/`.

## 6. References

- `.kiro/specs/genealogy-platform/agent-execution.md` — full execution
  contract for AI agents.
- `docs/ownership/OWNERS.md` — ownership mirror and CODEOWNERS docs.
- `OWNERS` (repo root) — top-level CODEOWNERS.
- `config/teams.yaml` — single source of truth for team IDs.
