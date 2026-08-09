# runbook/keycloak.md — Keycloak OIDC identity provider runbook

Operator-facing runbook for the platform-wide Keycloak 26.x
identity stack. Per `tasks.md` E3.1 + `architecture-decisions.md`
ADR-E0.5-05 (Keycloak topology + realm strategy + federation
policy) + `ownership-catalog.md` §3 (Keycloak owner =
identity-primary, on-call identity-primary, SLO 99.95 %,
OIDC token p95 < 150 ms).

## Source-of-truth map

| File | Purpose |
| ---- | ------- |
| `platform/keycloak/realm-strategy.yaml`   | ADR-E0.5-05 realm topology (`realm-per-tenant-group`), event listener sink, custom SPI forbidden posture |
| `platform/keycloak/realm-export.yaml`     | Canonical `genealogy-shared` realm (Authorization Code + PKCE + MFA conditional + step-up + recovery + email verification + brute-force protection + password policy + i18n) |
| `platform/keycloak/client-configs.yaml`   | 5 mandatory OIDC clients (`web-app`, `web-bff`, `public-api`, `kong-oidc-broker`, `grafana-sso`) with PKCE S256 + Vault-managed client secrets + `tenant_pseudo_id` / `actor_pseudo_id` protocol mappers |
| `platform/keycloak/federation.yaml`       | 5 OIDC + 2 SAML identity providers; SAML marked `deprecatedPath: true`; federated attribute allowlist + denylist |
| `platform/keycloak/key-rotation.yaml`     | RS256 / 4096-bit signing key rotation (90d), client-secret rotation (30d), JWKS algorithm allowlist (no `none`/`HS256`/`HS512`), backup posture |
| `platform/keycloak/OWNERS`                | Ownership mirror (`config/teams.yaml`) — primary `identity-primary`, secondary `@genealogy/platform`, on-call `identity-primary` |
| `platform/keycloak/README.md`             | Operator-facing README |

## Mirror

`platform/helm/genealogy-platform/files/keycloak/` mirrors the
five source-of-truth files byte-identical. The deep linter
(`scripts/lint-keycloak-config.mjs`) rejects drift.

## Helm templates

`platform/helm/genealogy-platform/templates/components/keycloak/`
ships the StatefulSet (`quay.io/keycloak/keycloak:26.x`, `--db=postgres`)
+ 5 ConfigMaps + 7 ExternalSecrets (admin / 4 client secrets /
SMTP / DB password) + ServiceAccounts (`keycloak` +
`keycloak-bootstrap`) + 2 Services (`keycloak` ClusterIP 8080/8443
+ `keycloak-internal` headless 9000) + NetworkPolicies (default-deny
+ allow from `gp-edge` / `gp-bff` / `gp-services` / `gp-workers` /
`gp-observability` / `gp-platform`) + bootstrap Job (`pre-install,
pre-upgrade` hook; annotations under `metadata.annotations`).

## Common operations

### Realm export / import (operator drill)

1. Pull the latest source-of-truth from
   `platform/keycloak/realm-export.yaml` (ConfigMap data key).
2. Export the live realm to JSON via the Keycloak Admin REST
   API or the `kc.sh export` command.
3. Diff the live export against the source-of-truth using a
   JSON-aware diff (e.g. `jq -S`).
4. Any drift outside the operator-managed fields (e.g. last-login
   metadata) is logged to OTel Collector as `keycloak_drift_event`
   with `actor_pseudo_id`.

### Key rotation (90-day cadence)

1. The `keycloak-key-rotation` CronJob (Sunday 03:00 UTC) generates
   a new RS256 / 4096-bit key pair via Keycloak Admin REST
   `/admin/realms/{realm}/keys`.
2. The new key is stored in the platform KMS (SaaS = AWS KMS via
   IRSA; on-prem = Vault transit).
3. The grace key remains valid for `keyOverlapHours` (default 48h)
   so any in-flight token is still verifiable.
4. After `graceRemovalSchedule` (Sunday 04:00 UTC) the grace key
   is dropped from the JWKS.
5. The rotation emits a `key_rotation_completed` event to the
   OTel Collector with `actor_pseudo_id`.

### Client-secret rotation (30-day cadence)

1. The `keycloak-client-secret-rotation` CronJob generates a new
   client secret per non-excluded client.
2. The new secret is written to Vault
   (`secret/<env>/data/keycloak/<client>`).
3. The Chart reconciliation (`helm upgrade`) rolls the dependent
   workload (web-bff / public-api / kong / grafana) within
   `clientSecretOverlapHours` (default 24h).
4. Excluded clients: `kong-oidc-broker` + `grafana-sso` — these
   use static secrets pinned at install time (long-lived
   platform components with their own restart sequence).

### DB password rotation (30-day cadence)

1. The `keycloak-db-password-rotation` CronJob rotates the
   `keycloak` Postgres user password.
2. The new password is written to Vault
   (`secret/<env>/data/keycloak/db-password`).
3. ESO re-syncs the `keycloak-db-password` Secret within
   `refreshInterval` (default 24h).
4. Keycloak restart picks up the new password (StatefulSet
   rolling restart).

### Backup / restore (E14.1)

1. Snapshot of `genea_keycloak` Postgres database daily at
   `0 2 * * *` UTC.
2. Snapshot encrypted with the platform KMS key
   (SaaS = AWS KMS; on-prem = Vault transit).
3. Retention: 14 days (production), 90 days (SaaS).
4. RPO ≤ 15 minutes, RTO ≤ 30 minutes.
5. Restore drill: spin up a dev Keycloak from the snapshot +
   verify Admin REST + JWKS + login.

### Federation enablement (per tenant)

1. `tenant-service` admin API marks the tenant
   `federationOptIn = true` after DPO approval.
2. The bootstrap Job (or Admin REST reconciler in E3.5) creates
   a per-tenant realm when `dedicatedRealmEligibility = opt-in`.
3. The 5 OIDC + 2 SAML providers are enabled per realm.
4. Federated subjects land in `tenant_groups` user attribute via
   force-sync (mappers in `federation.yaml`).
5. `tenant-service` (E3.2) reconciles federated subjects to
   canonical Keycloak groups + provisioning tuples to OpenFGA.

## Incident response

### Keycloak outage

1. Check the `gp-edge` namespace for the `keycloak` StatefulSet
   status: `kubectl -n gp-edge describe statefulset keycloak`.
2. Check the Postgres DB connectivity:
   `kubectl -n gp-edge exec keycloak-0 -- kc.sh status`.
3. If the cluster is healthy but login fails, check the
   `keycloak_health` alert in Grafana → runbook link.
4. If the DB is down, fail over to the standby Postgres
   (per `scale-and-slo.md` §3).

### Login loop (PKCE failure)

1. Check the `keycloak_login_error_rate` alert.
2. Verify the client `pkce.code.challenge.method = S256` attribute
   is set via `kcadm.sh get clients/<id>`.
3. If `web-app` (public client) is missing PKCE, re-apply
   `client-configs.yaml` via the bootstrap Job:
   `kubectl -n gp-edge create job --from=cronjob/keycloak-bootstrap manual`.

### Federation discovery failure

1. Check the `keycloak_idp_discovery_failed` alert (per IdP).
3. Verify the per-IdP `discoveryEndpoint` resolves and is HTTPS.
4. Re-apply `federation.yaml` via the bootstrap Job if the URL
   changed.

### Signing key compromise

1. Trigger emergency rotation via the Admin REST API:
   `POST /admin/realms/{realm}/keys/generate`.
2. Force-revoke all sessions:
   `POST /admin/realms/{realm}/logout-all`.
3. Notify `identity-primary` + `appsec-partner`.
4. Update the `genea-keycloak-key-rotation` ConfigMap to a shorter
   rotation window (e.g. 24h).
5. Audit: pull `keycloak_event_log` from the OTel Collector +
   cross-reference with the audit-service.

## Owners

Primary: `identity-primary` (`config/teams.yaml`).
Secondary: `@genealogy/platform`.
On-call: `identity-primary`.
AppSec partner reviews redaction + tenant pseudonym posture.

## Residual gaps (tracked in evidence/E3.1.md)

- Native Keycloak 26 JSON realm import (E3.5).
- Replace self-invented realm YAML with Admin REST reconciliation (E3.5).
- Real Keycloak container smoke test (E15.1).
- Federated `groups` → canonical groups via `tenant-service` (E3.2 / E3.5).
- Spring Security session cookie + CSRF + SameSite=Strict in BFF (E3.5, E5).
- E2.10 Grafana `client_id: grafana` ↔ new `grafana-sso` (E2.10 hotfix).
- E2.9 Argo CD `client_id: argocd` ↔ Keycloak clients (E3.5).
- E2.6 Vault `realm: genealogy-platform` + `client_id: vault-operator` (E3.5).