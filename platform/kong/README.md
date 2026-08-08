# platform/kong — Kong Gateway declarative config + values

Kong OSS 3.8 (per ADR-E0.5-01) ships in DB-less mode. The full route
and plugin set lives in
[`platform/helm/genealogy-platform/files/kong.yml`](../helm/genealogy-platform/files/kong.yml)
(the `kong.yml` in this directory is a symlink to that file so the
local docker-compose can mount it at `/etc/kong/kong.yml`). The
runtime values (images, plugin allow-list, upstreams, SNIs) live in
[`values.yaml`](./values.yaml). The umbrella Helm chart and the local
`docker-compose.yml` both consume the same source-of-truth file.

## Route classes

| Class         | Kong service | Backend namespace  | Use case                                |
| ------------- | ------------ | ------------------ | --------------------------------------- |
| `public`      | `web-public` | `gp-bff`           | Marketing, public tree views            |
| `authenticated` | `web-bff`  | `gp-bff`           | Authenticated SPA → BFF                 |
| `partner`     | `public-api` | `gp-bff`           | Versioned public OpenAPI                |
| `admin`       | `admin-api`  | `gp-platform`      | Operator / support (IP-restricted)      |

Every route carries a `route-class:<name>` tag so a config drift is
grep-able from CI logs.

## Plugins

| Plugin                  | Routes                                     | Why                                                  |
| ----------------------- | ------------------------------------------ | ---------------------------------------------------- |
| `correlation-id`        | all                                        | `X-Request-Id` propagation across CDN → BFF → gRPC   |
| `cors`                  | public, authenticated                      | Browser CORS with origin allow-list                 |
| `request-size-limiting` | authenticated, partner, admin              | Multipart / JSON body cap (decompression bomb guard)|
| `rate-limiting`         | all                                        | Coarse edge limit (per-route, `local` policy)        |
| `ip-restriction`        | admin                                      | Bastion / Argo CD / VPN only                         |
| `jwt`                   | reserved for E3.1 (no key resolver today)  | Slot for Keycloak OIDC verification                  |
| `prometheus`            | n/a (global `/metrics`)                    | RED metrics on `:9542` for the OTel Collector        |

The `KONG_PLUGINS` env var sets a hard allow-list at startup so a
developer cannot accidentally load `oauth2-introspection`,
`mtls-auth`, `key-auth`, `acl` or any other plugin that could carry
domain authorization. Domain authorization stays in the destination
service per `design.md` §4.1 / `tasks.md` E2.2.

## Validation

| Command                              | What it asserts                                   |
| ------------------------------------ | ------------------------------------------------ |
| `pnpm lint:kong`                     | YAML + route/plugin contract                     |
| `pnpm check:platform:baseline`       | Static Kong invariants + version pin             |
| `pnpm smoke:kong`                    | Live `/status`, route match, plugin behaviour    |
| `node --test scripts/__tests__/lint-kong-config.test.mjs` | 4 unit tests |

## Owners

Primary: `platform` (`config/teams.yaml`). Secondary: `@genealogy/sre`.
On-call: `platform`. Mirrors `platform/helm/OWNERS`.