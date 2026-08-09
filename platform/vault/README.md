# platform/vault — Vault + cloud KMS abstraction (E2.6)

Source-of-truth Vault + cloud KMS abstraction for the genealogy
platform. Per `design.md` §13 + `tasks.md` E2.6 the platform
NEVER stores credentials in Git, Helm values, images, logs, or
Temporal payloads; every credential lives in Vault and is
retrieved at runtime via the Vault Agent Injector.

## Layout

- `server-config.yaml` — `ConfigMap` carrying the Vault server
  HCL config (storage Raft + listener + seal + telemetry). The
  seal stanza is rendered from the active KMS provider
  (`kms-abstraction.yaml`). **No mesh-level retry** / no literal
  AWS access keys per `design.md` §13.
- `auth-methods.yaml` — Kubernetes auth method (canonical
  workload auth) + Keycloak JWT (operator auth) + GitHub
  Actions AppRole (CI auth). The deep linter forbids
  `userpass` / `ldap` / `cert` — credential storage belongs to
  Keycloak per `design.md` §4.2.
- `policies.yaml` — Per-component ACL list (deny-all default +
  path-scoped read/rotate). No policy grants `root`; no
  policy references `auth/token/`, `sys/`, `database/`,
  `transit/`, or `ssh/`.
- `kms-abstraction.yaml` — One `KmsProvider` contract; SaaS
  uses AWS KMS, on-prem uses Vault transit. The application
  code is provider-neutral; the platform selects the
  implementation at deploy time. Per-data-class key
  assignments per `privacy-and-legal-gate.md` §5.
- `injector-templates.yaml` — Canonical annotation set per
  workload class (services / workers / bff / data /
  observability). The linter enforces `agent-inject: "true"`
  - a policy from `policies.yaml`.

The same files are mirrored into the umbrella chart's
`files/vault/` directory so `helm template` can render without
reading outside the chart root. Anything you change here MUST
be mirrored into the chart or the umbrella will drift.

## Runtime

The umbrella chart installs Vault + the Vault Agent Injector
via the upstream `hashicorp/vault` + `hashicorp/vault-k8s`
Helm subcharts. The server StatefulSet + Injector Deployment

- bootstrap Job render the source-of-truth ConfigMaps from
  `platform/vault/` and apply them via a single
  `vault-bootstrap` Helm-hook Job (`pre-install,pre-upgrade`).

The `vault-bootstrap` Job:

1. Initialises the Raft cluster (only on first install —
   re-runs are no-ops).
2. Unseals Vault via the active KMS provider (SaaS) or
   Shamir (on-prem / dev).
3. Enables the Kubernetes + Keycloak JWT + GitHub Actions
   AppRole auth methods.
4. Writes the per-component policies.
5. Writes the KV v2 mounts (`secret/`, `transit/`).
6. Renders the bootstrap + rotation workload tokens into
   the per-namespace `vault-bootstrap-token` Secrets.

After bootstrap, every workload that needs a secret gets a
Vault Agent sidecar injected via the `injector-templates.yaml`
annotations.

## Privacy / security

- **No literal credentials in any file under `platform/vault/`**
  — the deep linter rejects `password:`, `apiKey:`,
  `token:`, `private_key:` patterns.
- **No `root` capabilities in any policy** — root token
  access = unsealed storage = critical severity.
- **No `userpass` / `ldap` / `cert` auth methods** —
  credential storage belongs to Keycloak per `design.md`
  §4.2.
- **Per-data-class key** — `kms-abstraction.yaml` rejects a
  reused `keyId` across two data classes (blast-radius
  isolation per `privacy-and-legal-gate.md` §5).
- **AppRole + JWT rotation** — GitHub Actions AppRole
  secret IDs are rotated every 90d by the
  `vault-rotate-approle` workflow (E14 follow-up).
- **Vault Agent `agent-revoke-on-shutdown: "true"`** —
  every workload's token is revoked when the pod terminates.
- **No PII / DNA / raw secret payload in Vault audit** —
  the audit log is shipped to the OTel log pipeline (E2.10)
  which redacts PII / DNA per `privacy-and-legal-gate.md`
  §11.

## Validation

- `pnpm lint:vault` — deep validation of the four YAML
  files + the per-file invariants (KMS abstraction contract
  - deny-all default policy + no forbidden auth methods +
    per-data-class key assignment).
- `pnpm check:platform:baseline` — extended with E2.6
  invariants (chart templates present, image pin, per-env
  overrides, server-config / auth-methods / policies /
  kms-abstraction ConfigMaps, alert rules, local profile
  pin).

## Smoke

- `pnpm smoke:vault` — local Docker smoke probe that brings
  up the platform Vault dev server on a disposable kind
  cluster and asserts the four source-of-truth ConfigMaps
  are applied end-to-end (see `scripts/smoke-vault.mjs`).
  The script falls through to a structural-only PASS when
  kind / docker / helm are not on PATH.

## Ownership

`OWNERS` mirrors `config/teams.yaml`. Primary =
`platform-secondary`, secondary = `@genealogy/security`,
on-call = `sre-primary`.
