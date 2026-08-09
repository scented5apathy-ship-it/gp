# platform/keycloak — Keycloak OIDC identity provider + config-as-code

Keycloak 26.x (per ADR-E0.5-01) is the platform identity provider.
Per `tasks.md` E3.1 + `design.md` §4.2 the platform manages
realm / client / flow / federation / key rotation as
**config-as-code**; the runtime is the upstream
`quay.io/keycloak/keycloak:26.x` image and the bootstrap Job
applies the source-of-truth on every `helm upgrade` via the
Keycloak Admin REST API.

## Realm strategy — ADR-E0.5-05

| Env         | Topology                       | Realm(s)                      |
| ----------- | ------------------------------ | ------------------------------ |
| SaaS        | realm-per-tenant-group default | `genealogy-shared` (default); dedicated realm opt-in for enterprise isolation |
| on-prem     | realm-per-tenant-group default | `genealogy-shared` (default); dedicated realm opt-in for isolation  |
| dev         | realm-per-tenant-group         | `genealogy-shared` (single)    |

A dedicated realm requires `justification`, `owner` and
`isolationPolicy` in the per-env values file; the linter rejects
a dedicated realm block without all three fields.

Group claim size limit is 1000 per tenant — when exceeded,
switch to a dedicated realm (ADR-E0.5-05 §Consequences).

## Authentication posture — R2

| Concern              | Posture                                               |
| -------------------- | ----------------------------------------------------- |
| Authorization Code   | Required + PKCE S256                                  |
| Resource Owner Pwd   | FORBIDDEN (no client_credentials grant at realm level)|
| Implicit grant       | FORBIDDEN (no `response_type=token`)                  |
| Client credentials   | BFF + Public API only (service-to-service)            |
| Refresh tokens       | Rotated (RFC 6749 §1.5)                               |
| MFA                  | Conditional via `mfa` sub-flow (WebAuthn / OTP)       |
| Step-up              | `step-up` flow, 5 min max age, admin + DNA only       |
| Access token TTL     | 30 min                                                |
| SSO idle TTL     | 8 h                                                   |
| SSO max TTL     | 12 h                                                  |
| Brute force          | `MULTIPLE`, 5 failures / 15 min wait                  |
| Password policy      | 12 chars + 1 upper + 1 lower + 1 digit + 1 symbol + history(12) + PBKDF2-SHA512(10000) |

## Five mandatory clients

| Client              | Type                | Grant                            | BFF / SPA   |
| ------------------- | ------------------- | -------------------------------- | ----------- |
| `web-app`           | public              | Authorization Code + PKCE        | Next.js PWA |
| `web-bff`           | confidential        | Authorization Code + PKCE        | Spring Boot |
| `public-api`        | confidential        | Client credentials + scopes      | Partner REST API |
| `kong-oidc-broker`  | confidential        | Client credentials + JWT valid   | Kong Gateway |
| `grafana-sso`       | confidential        | Authorization Code + PKCE        | Grafana     |

## Federation posture — ADR-E0.5-05

| Protocol | Status                  | Notes                                        |
| -------- | ----------------------- | -------------------------------------------- |
| OIDC     | Canonical               | Google Workspace / Microsoft Entra / Okta / Auth0 / PingFederate |
| SAML     | Deprecated              | Okta SAML / PingFederate SAML; requires contractual justification |

Federation is opt-in per tenant (`optInPerTenant: true`). The
realm strategy declares `dedicatedRealmEligibility = opt-in`;
enterprise tenants enable federation via the `tenant-service`
admin API after DPO approval.

## Key rotation

| Key                      | Rotation | Job                                |
| ------------------------ | -------- | ---------------------------------- |
| Realm signing key        | 90 d     | `keycloak-key-rotation`            |
| Provider keys (JWKS)     | 60 min   | `keycloak-jwks-refresh`            |
| Client secrets           | 30 d     | `keycloak-client-secret-rotation`  |
| DB password              | 30 d     | `keycloak-db-password-rotation`    |

## Validation

| Command                                | What it asserts                                        |
| -------------------------------------- | ------------------------------------------------------ |
| `pnpm lint:keycloak`                   | YAML + realm / client / federation contract            |
| `pnpm check:platform:baseline`         | Static Keycloak invariants + version pin               |
| `pnpm smoke:keycloak`                  | Structural probe (12 PASS structural-only)             |
| `node --test scripts/__tests__/lint-keycloak-config.test.mjs` | Unit tests        |

## Files

| File                                                  | Purpose                                  |
| ----------------------------------------------------- | ---------------------------------------- |
| [`realm-strategy.yaml`](./realm-strategy.yaml)        | ADR-E0.5-05 realm topology + event sink  |
| [`realm-export.yaml`](./realm-export.yaml)            | Canonical `genealogy-shared` realm JSON  |
| [`client-configs.yaml`](./client-configs.yaml)        | 5 mandatory OIDC clients + protocol mappers |
| [`federation.yaml`](./federation.yaml)                | Identity brokering + attribute mappers   |
| [`key-rotation.yaml`](./key-rotation.yaml)            | Signing / provider / client-secret rotation |

Mirror: `platform/helm/genealogy-platform/files/keycloak/*.yaml`
(byte-identical, asserted by the deep linter).

Helm chart: `platform/helm/genealogy-platform/templates/components/keycloak/`
— `configmap.yaml`, `secrets.yaml`, `serviceaccounts.yaml`,
`services.yaml`, `statefulset.yaml`, `network-policies.yaml`,
`bootstrap-job.yaml`.

Contract stub: `platform/helm/genealogy-platform/templates/components/contract-stubs.yaml`
(`keycloak-contract-stub` ConfigMap in `gp-edge`).

## Owners

Primary: `identity-primary` (`config/teams.yaml`). Secondary:
`@genealogy/platform`. On-call: `identity-primary`. Mirrors
`platform/keycloak/OWNERS`.