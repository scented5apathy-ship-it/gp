# Architecture Decision Records — E0.5 closure

- Status: Accepted for downstream discovery (closed loop with E0.6 ownership catalog and E1 contracts).
- Scope: closure of the 16 open ADR items enumerated in `.kiro/specs/genealogy-platform/design.md` §16.
- Inputs absorbed: `requirements.md` (R1–R18, NFR1–NFR8), `design.md` §1–§17, `personas-and-journeys.md` (E0.1), `glossary-and-policy-matrix.md` (E0.2 §5 open questions), `scale-and-slo.md` (E0.3 §11 open questions), `privacy-and-legal-gate.md` (E0.4 §14 open questions).
- Out of scope: numeric binding values that remain `DRAFT` (they are ratified only when product/security/privacy/operations sign off in E0.6), customer DPA template (open question E0.4 §14 #7), runtime incident response (E16).
- ADR template: each record follows the format mandated by `design.md` §16 — context, options, decision, consequences, license/TCO, security/privacy, SaaS/on-premise parity, rollback/evolution path. Owner, version policy, review date, migration and rollback path are recorded per `tasks.md` E0.5 acceptance.

## Index

| ID | Title | Status |
|---|---|---|
| ADR-E0.5-01 | Pinned baseline versions (Spring Boot, Gradle, Next.js, platform components) | ACCEPTED (defaults ratified, see §1) |
| ADR-E0.5-02 | Database topology: database-per-service vs schema-per-service on shared cluster | ACCEPTED (default = schema-per-service, opt-in DB-per-service) |
| ADR-E0.5-03 | Cloud / region / residency provider selection | DEFERRED (depends on first-launch jurisdiction selection; see §3) |
| ADR-E0.5-04 | Ingress / WAF in front of Kong and Kong OSS vs Enterprise edition | ACCEPTED (Kong OSS + cloud-managed WAF/CDN; Enterprise only if license delta justified) |
| ADR-E0.5-05 | Keycloak topology, realm strategy and federation policy | ACCEPTED (realm-per-tenant-group, dedicated realm only for enterprise isolation) |
| ADR-E0.5-06 | OpenFGA store / model lifecycle and consistency policy | ACCEPTED (store-per-tenant with shared model, eventual consistency ≤ 500 ms p95) |
| ADR-E0.5-07 | Temporal distribution: self-host everywhere vs managed SaaS + on-prem | ACCEPTED (self-host mandatory; SaaS only if vendor offers on-prem parity) |
| ADR-E0.5-08 | Kafka serialization (Apicurio) and topic / retention strategy | ACCEPTED (Avro + Apicurio, partition by aggregate id, retention by class) |
| ADR-E0.5-09 | Frontend query / cache / state / form libraries | ACCEPTED (TanStack Query + Zustand + React Hook Form + Zod) |
| ADR-E0.5-10 | Tree layout / render engine | DEFERRED (post-prototype benchmark, see §10) |
| ADR-E0.5-11 | CDN, malware signature update, OCR language packs, media codec policy | ACCEPTED (managed CDN; ClamAV daily; Tesseract packs on-demand; FFmpeg pinned) |
| ADR-E0.5-12 | Notification provider (email/push/SMS), billing provider, developer portal | ACCEPTED (SMTP for on-prem + SES/SendGrid adapter for SaaS; billing via adapter; no portal in v1) |
| ADR-E0.5-13 | CI platform + Argo CD + container registry + artifact retention | ACCEPTED (Tekton or GitHub Actions self-hosted; registry per tenant-class) |
| ADR-E0.5-14 | Calendar / geocoding / place authority providers | ACCEPTED (open data first; commercial adapter only for fallback) |
| ADR-E0.5-15 | DNA file format / matching algorithm / jurisdiction for release | DEFERRED (depends on E10.1 architecture gate and E0.5-04 residency) |
| ADR-E0.5-16 | Product analytics, consent manager, DR topology | ACCEPTED (analytics opt-in; consent ledger in `dna-service`; multi-region active-passive) |

## Cross-cutting rules

- **No platform may be selected without owner, version policy, Helm/config-as-code, backup/restore, monitoring, security hardening, upgrade/rollback, runbook** (per `requirements.md` NFR8 and `design.md` §3.1).
- **Every ADR must specify migration and rollback path.** Migration is `expand-contract` only; rollback must restore prior version within the same release window.
- **Every ADR must document SaaS / on-premise parity.** Code path must be identical; only adapter and configuration differ.
- **Owner column is required.** If owner is missing at E0.5 close, status remains `DEFERRED` and feeds E0.6.
- **Default license posture: Apache-2.0 / MIT / MPL-2.0 / Business Source License with commercial path is allowed only when enterprise requirements force it.** AGPL is forbidden for the SaaS control plane (viral copyleft risk).
- **Numerical thresholds** (110 years living inference, 0.85 merge auto-threshold, 30-day revert window, 60-second consent revocation, 3.0×/2.0× burst, 85 % cache hit ratio, p95 latency targets, RPO ≤ 15 min / RTO ≤ 4 h) remain `DRAFT` and are ratified in E0.6 ownership catalog after product / security / privacy / operations sign-off. They are listed in §A below for downstream traceability.

---

## 1. ADR-E0.5-01 — Pinned baseline versions

### Context

`design.md` §1 only names the platform stack but leaves exact versions open. Multiple downstream epics (E1.1 workspace, E1.4 Spring Boot template, E1.5 Next.js shell, E2.1 cluster baseline) require a pinned baseline so that reproducibility, security scanning and Renovate policy can be tuned.

### Options

1. Adopt the latest GA of each tool at scaffold time.
2. Adopt the latest LTS / maintained line and pin minor version.
3. Pin exact patch version and rebuild via Renovate PRs.

### Decision

Adopt option 3 with the following matrix. All versions are referenced as `MAJOR.MINOR` ranges in `renovate.json` and pinned to exact patch in the lockfile (Gradle `lockfile`, pnpm `lockfileVersion`, Helm chart `appVersion`):

| Component | Pinned baseline | Version policy | Review date |
|---|---|---|---|
| Java | 21 LTS (latest patch) | Quarterly minor bump, security-critical same-week | 2026-11-01 |
| Spring Boot | 3.3.x (latest patch) | Quarterly minor, security critical immediate | 2026-11-01 |
| Gradle | 8.10.x | Minor bump per Spring Boot train | 2026-11-01 |
| Node.js | 22 LTS | Quarterly bump | 2026-11-01 |
| Next.js | 15.x | Quarterly minor bump | 2026-11-01 |
| TypeScript | 5.6.x | Quarterly bump | 2026-11-01 |
| PostgreSQL | 16.x (latest minor) | Annual major; security back-port quarterly | 2027-01-15 |
| Kafka (Strimzi) | 3.8.x | Minor per Strimzi release train | 2026-12-01 |
| Apicurio Registry | 2.6.x | Minor per Apicurio release train | 2026-12-01 |
| Keycloak | 26.x | Minor per Keycloak release cadence | 2026-12-01 |
| OpenFGA | 1.x (latest) | Minor per OpenFGA release | 2026-12-01 |
| Temporal | 1.26.x (latest) | Minor per Temporal release | 2026-12-01 |
| Istio | 1.23.x | Quarterly minor; security critical immediate | 2026-11-01 |
| Kong | 3.8.x | Quarterly minor; security critical immediate | 2026-11-01 |
| Vault | 1.17.x | Quarterly minor | 2026-11-01 |
| Flagsmith | LTS | Quarterly minor | 2026-11-01 |
| Argo CD | 2.13.x | Quarterly minor | 2026-11-01 |
| Argo Rollouts | 1.7.x | Quarterly minor | 2026-11-01 |
| OpenTelemetry SDK / Collector | latest stable | Quarterly minor | 2026-11-01 |
| Prometheus / Grafana / Loki / Tempo | latest stable | Quarterly minor | 2026-11-01 |

### Consequences

- Reproducible builds; lockfile is the contract.
- Renovate opens PRs weekly; security-critical bumps auto-merge after CI green; majors require manual review per `tasks.md` E1.6.
- New language majors (Java 22, Node 24) require a separate ADR (supersedes this one).

### License / TCO

- All listed baselines are open-source under Apache-2.0 / MIT / MPL-2.0. No licence cost.
- TCO dominated by operations and cloud spend; tracked in E0.6.

### Security / privacy

- Pinned patches reduce CVE exposure window.
- Renovate policy `vulnerabilityAlerts: enabled`; `osvVulnerabilityAlerts: enabled`; security-only auto-merge for patch-level updates.
- SBOM per release (CycloneDX) from E1.6.

### SaaS / on-premise parity

Identical. On-prem mirrors the same pin via Helm values.

### Rollback / evolution path

Rollback = revert lockfile entry; CI rebuilds previous pin. Evolution = superseding ADR with a new version matrix.

### Owner

Platform team. On-call: platform-primary.

---

## 2. ADR-E0.5-02 — Database topology

### Context

`privacy-and-legal-gate.md` §6 risk `T-06` and §14 open question #3 require a documented choice between **database-per-service** (blast-radius isolation, heavier ops) and **schema-per-service on a shared cluster** (cheaper ops, weaker isolation). The choice affects E1.4 (Spring Boot template), E2.1 (cluster baseline), E14 (DR topology).

### Options

1. Database-per-service everywhere.
2. Schema-per-service on a shared cluster (default).
3. Schema-per-service with opt-in dedicated database for enterprise tenants.

### Decision

Adopt option 3. Default for SaaS shared-tenant tier and on-premise is **schema-per-service** on a shared PostgreSQL cluster. Dedicated enterprise tenants and any tenant tagged `residency=dedicated` or `tier=enterprise-isolated` receive a dedicated PostgreSQL instance. Choice is encoded in `tenant-service` and propagates through `tenancy.kind` to every service. E14 owns the runbook.

PostgreSQL Row-Level Security remains enabled as defense-in-depth per `design.md` §5.1. Each service has its own Flyway schema and database role; cross-schema read is denied by `GRANT`.

### Consequences

- Default tenants keep ops cost low; enterprise tenants keep blast-radius isolation.
- Application code is topology-agnostic; routing happens at Flyway + datasource layer.
- Migration scripts must remain schema-aware; multi-schema transactions are forbidden.
- RLS tests in CI per `tasks.md` E1.6 + E2.1.

### License / TCO

- Schema-per-service uses fewer PostgreSQL instances → lower cloud cost.
- Dedicated instances add cost on enterprise tier; absorbed into plan pricing.

### Security / privacy

- Tenant isolation: every query carries `tenant_id` predicate + RLS. The `tenancy.kind` choice does not weaken this.
- Blast radius for shared cluster is bounded by per-service schema and read-only role separation.

### SaaS / on-premise parity

Identical topology in both deployments.

### Rollback / evolution path

Switching a tenant from shared to dedicated is a controlled migration: snapshot → restore → cutover via dual-write window (2 release trains) → drop legacy schema. Reverse path exists.

### Owner

Platform + DBA. On-call: platform-primary + dba-secondary.

---

## 3. ADR-E0.5-03 — Cloud / region / residency provider

### Context

`requirements.md` §9 lists data residency as an assumption to confirm. `privacy-and-legal-gate.md` §3 lists nine jurisdictions (EU/UK/US/CA-QC/APAC-ANZ/APAC-JP/APAC-SG/ROW/ONPREM). `scale-and-slo.md` §7 builds a residency matrix on top of this choice. The decision drives E0.5-04 (ingress/WAF), E0.5-07 (Temporal distribution), E14 (DR), and the residency flags in `privacy-and-legal-gate.md` §12.

### Options

1. Single cloud, single region per launch jurisdiction.
2. Multi-cloud, multi-region active-active.
3. Single cloud, multi-region active-passive, with on-premise as the third tier.

### Decision

**DEFERRED**. Final choice depends on the first-launch jurisdiction and the customer pipeline. The candidate short-list is:

| Candidate | Strength | Weakness |
|---|---|---|
| AWS (eu-central-1 / eu-west-2 / us-east-1 / ap-southeast-1) | broadest coverage, KMS integration, Kong/Strimzi/Istio validated | vendor lock, residency variance |
| GCP (europe-west3 / asia-southeast1) | strong data-protection story | Kong/Istio maturity, on-prem story weaker |
| Azure (westeurope / canadacentral) | enterprise federation story | Kong/Istio maturity on Azure Arc |

The default for v1 launch is **single cloud, multi-region active-passive per jurisdiction**, with on-premise as the third tier. Cross-region transfer uses the mechanism selected in ADR-E0.5-07 (transfer mechanism).

This ADR remains `DEFERRED` until product + security + privacy sign the launch jurisdiction. Sign-off owner: DPO + CTO. Inputs: §15 cross-reference map, `scale-and-slo.md` §7, customer pipeline.

### Consequences

- Until this ADR closes, residency flag `legal.data_residency.allowlist` is empty per `privacy-and-legal-gate.md` §12.
- `legal.cross_region_transfer.enabled` defaults to `false` per §12.
- All other epics may use the candidate short-list as planning input but must not pin a provider in code or config.

### License / TCO

- Active-passive is roughly 1.4× the single-region cost; multi-cloud is ≥ 2×.
- Lock-in mitigated by adapter layer per `design.md` §3.1.

### Security / privacy

- Residency chosen per `privacy-and-legal-gate.md` §3 jurisdiction matrix.
- Cross-region transfer requires the legal mechanism in ADR-E0.5-07.

### SaaS / on-premise parity

On-prem remains a third tier; adapter parity preserved.

### Rollback / evolution path

Region failover is an Argo CD + DNS switch; multi-cloud failover requires an explicit follow-up ADR.

### Owner

Platform + DPO. On-call: platform-secondary. **Open** until launch jurisdiction is approved.

---

## 4. ADR-E0.5-04 — Ingress / WAF in front of Kong and Kong edition

### Context

`design.md` §1 lists Kong Gateway but the edition (OSS vs Enterprise) and the layer in front of it (cloud-managed WAF/CDN, self-hosted reverse proxy) remain open. License delta for Kong Enterprise is significant and only justifiable if a feature is strictly required.

### Options

1. Kong OSS, no external WAF (TLS termination at Kong).
2. Kong OSS behind a cloud-managed CDN/WAF.
3. Kong Enterprise with its built-in WAF and developer portal.

### Options evaluation

| Option | License | Features | TCO | Notes |
|---|---|---|---|---|
| 1 | Apache-2.0 (Kong OSS) | Routing, auth, rate limit, CORS, correlation | Low | No WAF; reliant on application-layer controls |
| 2 | Apache-2.0 + cloud WAF/CDN | Adds DDoS / bot / OWASP rule set | Medium | Default choice |
| 3 | Kong Enterprise (proprietary) | Adds WAF, RBAC for admin, dev portal | High | Reserved for enterprise tier |

### Decision

**Default = option 2** (Kong OSS behind cloud-managed CDN/WAF). Kong Enterprise is reserved for the enterprise tier only when a tenant contract requires built-in WAF or developer portal; that decision is per-tenant and documented in E0.6 ownership catalog. Edge layer never carries domain authorization per `design.md` §4.1.

### Consequences

- Kong OSS is the engine everywhere; the WAF is replaceable per cloud provider.
- Kong Enterprise evaluation tracked in E14 cost model; license procurement owned by finance + platform.

### License / TCO

- Kong OSS is free; CDN/WAF is pay-per-GB / per-request.
- Kong Enterprise procurement requires a separate commercial agreement.

### Security / privacy

- TLS termination at CDN; Kong enforces mTLS to backend.
- WAF rules covered by OWASP CRS baseline + custom rules in `platform/local/edge/waf.yaml`.

### SaaS / on-premise parity

On-prem uses self-hosted reverse proxy (Envoy + ModSecurity) with the same rule set exported from CDN config.

### Rollback / evolution path

CDN switch is DNS-driven; Kong config rollback via Argo CD. Migration to Kong Enterprise requires re-evaluation of license delta.

### Owner

Platform + Security. On-call: security-primary.

---

## 5. ADR-E0.5-05 — Keycloak topology and realm strategy

### Context

`design.md` §4.2 names Keycloak but leaves realm strategy, federation and operator distribution open. Enterprise tenants expect SAML/OIDC federation and per-tenant isolation; SaaS tenants share a realm but expect strong separation.

### Options

1. Single realm, all tenants.
2. Realm-per-tenant-group.
3. Realm-per-tenant.

### Decision

Adopt **option 2 by default; option 3 opt-in for enterprise isolation**.

- **SaaS shared tenants**: realm `genealogy-shared`, with `tenant_id` claim and `tenant-membership` group attribute. Group membership is the authorization boundary inside the realm.
- **Enterprise / on-prem tenants**: dedicated realm with isolated user federation and per-realm Keycloak instance when required by isolation policy.
- Custom SPI extensions are forbidden per `design.md` §13; integrations go through OIDC protocol.

### Consequences

- Group claim size limits enforced: if a tenant exceeds 5 000 groups, switch to dedicated realm (runbook in E3.6).
- Federation policy: enterprise IdP is added via OIDC broker; SAML only when contractually mandated (deprecated path).

### License / TCO

- Keycloak is Apache-2.0; ops cost dominated by Realm DB and Infinispan cache.

### Security / privacy

- MFA enforced via realm policy; step-up for admin via Keycloak `execution`.
- Federation trust requires signed metadata and clock skew < 30 s.

### SaaS / on-premise parity

Identical Keycloak topology; on-prem adds a dedicated operator (`keycloak-operator`) and external PostgreSQL.

### Rollback / evolution path

Realm split is a controlled migration: dual-write users to the new realm → cut over SSO → archive old realm. Reversible by re-pointing DNS.

### Owner

Identity + Security. On-call: identity-primary.

---

## 6. ADR-E0.5-06 — OpenFGA store / model lifecycle

### Context

`design.md` §4.2 + §6 require OpenFGA for relationship authorization. Decisions on store layout, model versioning, caching and consistency affect E3 (identity), E4 (genealogy), E5 (sharing), E10 (DNA).

### Options

1. Single store, all tenants.
2. Store-per-tenant.
3. Store-per-tenant-group, shared model.

### Decision

Adopt **store-per-tenant with shared authorization model**. Model is versioned in git (`contracts/openfga/*.yaml`) and published to OpenFGA via CI per `tasks.md` E1.6. Read consistency is eventual with target p95 ≤ 500 ms; cache invalidation on tuple write is mandatory at the calling service (no TTL-only caching). Write path uses `Write` + `Read` round-trip to absorb eventual consistency.

### Consequences

- Tenant blast radius = one store; OpenFGA outage is contained per tenant via circuit breaker.
- Model upgrade is rolling per `expand-contract` rule; no breaking change to authorization tuples without a migration.
- ABAC overlay (`design.md` §6.2) remains in application code; OpenFGA only decides relationships.

### License / TCO

- OpenFGA is Apache-2.0; cost is cluster size and tuple count.
- Cache (Valkey) sized per `scale-and-slo.md` §10.

### Security / privacy

- Tuple content must not contain raw PII, DNA, or token; only opaque IDs.
- Audit hook on every `Write` writes an audit entry (deny-by-default).

### SaaS / on-premise parity

Identical topology.

### Rollback / evolution path

Store rollback = restore from PostgreSQL snapshot; model rollback = revert CI-published model and re-evaluate all stores.

### Owner

Identity + Platform. On-call: identity-primary.

---

## 7. ADR-E0.5-07 — Temporal distribution

### Context

`design.md` §1 + §7.4 + §11 put Temporal at the center of long-running workflow. The choice between managed SaaS and self-host affects data residency (cross-region transfer of workflow state), air-gap on-prem, and TCO.

### Options

1. Self-host everywhere.
2. Managed SaaS in SaaS region + self-host on-prem.
3. Managed SaaS everywhere.

### Decision

Adopt **option 2**. SaaS uses Temporal Cloud only if the vendor offers an in-region deployment matching the ADR-E0.5-04 region AND a contractual commitment that namespace state never leaves the region AND on-prem parity exists via the same Helm chart. Otherwise SaaS also self-hosts.

Cross-region / cross-cloud transfer of Temporal namespace state is **prohibited by default**; the `legal.cross_region_transfer.enabled` flag remains `false` until the legal mechanism (SCC, IDTA, adequacy decision) is approved in E0.4 + E0.5-04.

### Consequences

- Workflow state stays inside the chosen residency region.
- Workflow code must remain deterministic (per `design.md` §7.4).
- Activity idempotency, timeout, retry, heartbeat all configured per activity class.

### License / TCO

- Self-host = ops cost (PostgreSQL + worker + visibility cluster).
- Temporal Cloud licence = per-action + per-GB retention.

### Security / privacy

- Namespace isolation per tenant via `namespace_strategy = per-tenant`.
- Search attributes scrubbed: no raw DNA, no PII, no token.
- Worker runs in the same trust zone as the calling service.

### SaaS / on-premise parity

Identical chart; SaaS only adds cloud-specific auth.

### Rollback / evolution path

Namespace rollback = restore from namespace export. Migration to managed SaaS requires a re-evaluation of residency flags.

### Owner

Platform. On-call: platform-primary.

---

## 8. ADR-E0.5-08 — Kafka serialization and topic / retention

### Context

`design.md` §7.3 mandates event envelopes and Apicurio compatibility but does not pick a serialization. Topic layout, partition key, retention, compaction also open.

### Options

1. JSON Schema (Apicurio).
2. Avro (Apicurio).
3. Protobuf (Apicurio).

### Decision

Adopt **Avro** with Apicurio as the canonical serializer. Reasonings: smallest payload, native schema evolution, mature Apicurio compatibility checker. Protobuf stays for internal gRPC per `design.md` §7.2. JSON Schema is reserved for webhooks / partner endpoints.

Topic naming: `<domain>.<aggregate>.<version>.v<n>` (e.g. `genealogy.person.v1.v1`). Partition key = `tenantId + aggregateId` when ordering across aggregate is required; otherwise `aggregateId` only. Retention:

| Class | Retention | Compaction | Replay |
|---|---|---|---|
| Domain event | 30 days | No | Replay via outbox |
| Projection rebuild | 7 days | No | Manual |
| Audit | 365 days | No | Restricted replay |
| DLQ | 14 days | No | Manual triage |

Compatibility policy = `BACKWARD` for domain events; `FORWARD` for command intents; `FULL` for shared enums.

### Consequences

- Apicurio contract tests in CI per E1.6 + E1.3.
- Outbox + inbox pattern mandatory (`design.md` §7.3).

### License / TCO

- Avro tooling is open-source; Apicurio is Apache-2.0.
- Storage cost dominated by retention per class.

### Security / privacy

- Payload must not contain raw DNA / file content / token / PII not required for the consumer (`design.md` §7.3).
- Avro schema validated against the forbidden payload classes per `privacy-and-legal-gate.md` §11.

### SaaS / on-premise parity

Identical Strimzi operator + Apicurio chart.

### Rollback / evolution path

Topic rollback = pause consumer, restore from snapshot of aggregate tables, resume. Schema rollback = re-publish prior Apicurio version and re-emit events.

### Owner

Platform + Data. On-call: data-primary.

---

## 9. ADR-E0.5-09 — Frontend libraries (query / cache / state / form)

### Context

`design.md` §10.1 leaves the query / cache / state / form libraries open. They affect bundle size, accessibility and accessibility budget.

### Options

1. TanStack Query + Zustand + React Hook Form + Zod.
2. Apollo + Redux Toolkit + Formik + Yup.
3. SWR + Jotai + Final Form + Yup.

### Decision

Adopt **option 1** (TanStack Query + Zustand + React Hook Form + Zod).

Rationale:

- TanStack Query has first-class support for cursor pagination and ETag (`design.md` §7.1) and ships small.
- Zustand keeps bundle small and works inside Web Workers.
- React Hook Form integrates with shadcn/ui and supports Zod resolver.
- Zod doubles as runtime + compile-time validator and feeds the generated OpenAPI client.

Bundle budget per route: ≤ 180 kB gzipped JS (excluding media worker). Tested in CI via `apps/web/perf-budget.test.ts`.

### Consequences

- Bundle budget enforced in E1.5 / E1.6.
- Form validation reuses Zod schemas generated from OpenAPI.

### License / TCO

- All libraries are MIT; no licence cost.

### Security / privacy

- Zod rejects malformed input before reaching the network.
- TanStack Query never caches DNA or raw media (`design.md` §10.3).

### SaaS / on-premise parity

Identical (no backend dependency).

### Rollback / evolution path

Library swap requires a follow-up ADR; major version bump is manual review per Renovate policy.

### Owner

Web. On-call: web-primary.

---

## 10. ADR-E0.5-10 — Tree layout / render engine

### Context

`design.md` §10.2 requires virtualization/canvas or SVG hybrid and explicitly defers the choice until after a prototype benchmark.

### Options

1. SVG + DOM virtualization (`react-svg-tree` + custom virtualizer).
2. Canvas + custom layout (e.g. `d3-hierarchy` + OffscreenCanvas).
3. Hybrid (SVG for ≤ 5 K nodes, Canvas for > 5 K).

### Decision

**DEFERRED** until the prototype benchmark in E1.5/E6.1 produces a p75 interaction time under 2.5 s on a 10 K-person synthetic tree on mid-tier mobile. The benchmark must compare options 1, 2, 3 and include accessibility cost (keyboard alternative, screen reader).

Inputs:

- `personas-and-journeys.md` J-EDIT-1 / J-VIEW-1.
- `scale-and-slo.md` §3 synthetic dataset.
- WCAG 2.2 AA per `requirements.md` R6, R18.

Closing owner: Web lead + Performance lead. Sign-off: product + a11y.

### Consequences

- Until this ADR closes, `apps/web/components/tree/` uses a placeholder canvas that renders at most 1 K nodes.
- Storybook stories and Playwright visual regression are gated to 1 K nodes until the engine is selected.

### License / TCO

- All candidate libraries are open-source.

### Security / privacy

- Renderer must respect visibility class (`design.md` §6.3) and redaction rules (`glossary-and-policy-matrix.md` §2.2).

### SaaS / on-premise parity

Identical.

### Rollback / evolution path

Renderer swap requires a feature flag and incremental rollout via OpenFeature + Argo Rollouts.

### Owner

Web + Performance. On-call: web-primary. **Open** until prototype benchmark closes.

---

## 11. ADR-E0.5-11 — CDN, malware signature update, OCR language packs, media codec

### Context

`design.md` §11 pins the media toolchain (ClamAV, libvips, ImageMagick, Tika, Tesseract, FFmpeg, Gotenberg) but leaves update cadence, codec allowlist and CDN selection open.

### Options

1. Self-managed CDN + daily signature update.
2. Cloud-managed CDN + on-demand signature update.
3. Hybrid: cloud CDN for static, self-managed for media worker.

### Decision

Adopt **option 3**.

- **CDN**: cloud-managed for static assets (PWA shell, marketing). Self-managed (or storage gateway) for media worker outputs because residency must align with ADR-E0.5-03.
- **ClamAV**: signature update daily at 02:00 UTC; emergency push when CVE issued.
- **Tesseract language packs**: installed on-demand per locale; pinned to a specific pack version.
- **FFmpeg**: pinned to a single minor version across all workers; codec allowlist excludes proprietary codecs without licence.
- **libvips**: preferred for image; ImageMagick only for legacy formats and only inside the sandbox per `design.md` §11.
- **Tika**: pinned minor version; metadata stripper configured to drop EXIF GPS by default.

### Consequences

- CVE update policy enforced in CI via Trivy + Grype on worker images.
- Sandbox policy (non-root, read-only FS, no network egress) enforced in E1.4.

### License / TCO

- All open-source; licence cost is zero.

### Security / privacy

- No network egress from binary parsers.
- Media worker runs in a dedicated namespace with NetworkPolicy default-deny.

### SaaS / on-premise parity

Identical toolchain; on-prem only swaps cloud CDN for self-hosted.

### Rollback / evolution path

Worker image rollback via Helm revision. Codec allowlist update requires ADR amendment.

### Owner

Platform + Security. On-call: security-primary.

---

## 12. ADR-E0.5-12 — Notification / billing providers, developer portal

### Context

`design.md` §1 names "Delivery adapters" but leaves the actual providers open.

### Options

1. SES / SendGrid for SaaS + SMTP for on-prem.
2. Single provider everywhere.
3. SES only.

### Decision

Adopt **option 1**.

- **Email**: SES (default) or SendGrid (when contract mandates) for SaaS; SMTP relay for on-prem. Adapter interface `NotificationProvider`.
- **Push**: FCM / APNs adapter; opt-in only.
- **SMS**: not in v1; adapter scaffolding only.
- **Billing**: Stripe adapter (SaaS only); on-prem uses offline license file.
- **Developer portal**: out of scope for v1; gated by ADR if partner demand emerges.

### Consequences

- Provider-specific failure modes handled by adapter retries + circuit breaker.
- `NotificationProvider` abstraction keeps domain code vendor-free.

### License / TCO

- Provider licence / per-message cost is part of the TCO tracked in E0.6.

### Security / privacy

- Provider configured with TLS + DKIM + SPF + DMARC.
- PII minimization: emails contain deep-link tokens, never raw person data.

### SaaS / on-premise parity

Email is the parity channel; push/SMS are SaaS-only and behind feature flag.

### Rollback / evolution path

Provider swap is adapter-level; requires ADR amendment only when contract terms change.

### Owner

Platform + Finance. On-call: platform-secondary.

---

## 13. ADR-E0.5-13 — CI platform, container registry, artifact retention

### Context

`design.md` §13 names Argo CD + Rollouts but leaves CI choice (Tekton, GitHub Actions self-hosted, Jenkins) and registry retention policy open.

### Options

1. Tekton self-hosted.
2. GitHub Actions self-hosted runners.
3. Jenkins self-hosted.

### Decision

Adopt **GitHub Actions self-hosted runners** as the default CI for SaaS and on-prem (GitOps-compatible, fast iteration). Tekton remains an option for air-gapped on-prem where GitHub is unavailable; CI choice is configured per tenant in `platform/local/ci/`.

Container registry: regional registry per residency; artifact retention = 90 days for images, indefinite for signed SBOMs. Cosign signing is mandatory.

### Consequences

- Renovate + GitHub Actions matrix keep PR CI < 10 min.
- Self-hosted runners require credential rotation runbook (E14).

### License / TCO

- GitHub Actions licence cost is per-minute; self-hosted runners eliminate per-minute charges but add ops cost.

### Security / privacy

- Runner images hardened; secrets from Vault; no plaintext secrets in CI logs.
- Pipeline OIDC token exchange with cloud per `design.md` §13.

### SaaS / on-premise parity

Same runner image, same pipeline templates.

### Rollback / evolution path

Pipeline rollback via workflow re-run with previous commit. Registry swap = re-push + re-sign.

### Owner

Platform. On-call: platform-primary.

---

## 14. ADR-E0.5-14 — Calendar / geocoding / place authority providers

### Context

`design.md` §16 item 14 leaves calendar / geocoding / place authority providers open. Genealogy needs multilingual calendar conversion and historical place lookup.

### Options

1. Open data (Wikidata, OpenStreetMap, CLDR) first; commercial fallback.
2. Commercial-first (e.g. Google Places + GeoNames).
3. Pure on-prem curated dataset.

### Decision

Adopt **option 1**.

- **Calendar**: ICU/CLDR for formatting and conversion; per-locale Hijri / Hebrew / Jalali converters from open-source libraries.
- **Place authority**: OpenStreetMap Nominatim + Wikidata; commercial adapter (`PlaceProvider`) for fallback when open data lacks historical coverage.
- **Geocoding**: Photon / Pelias self-hosted; commercial adapter for fallback.

### Consequences

- Adapter interface `PlaceProvider` + `CalendarProvider` keeps the domain code provider-free.
- Cache (Valkey) per `scale-and-slo.md` §10; cache key = `(provider, query_hash)`.

### License / TCO

- Open data cost is hosting + bandwidth.
- Commercial fallback is per-request; gated by `legal.cross_region_transfer.enabled` per `privacy-and-legal-gate.md` §12.

### Security / privacy

- Query logs must not include personal data; pseudonymize tenant id and person id before logging.
- Provider outages degrade to local placeholder; never block tree edit.

### SaaS / on-premise parity

Identical adapter; on-prem may disable commercial fallback.

### Rollback / evolution path

Provider swap = adapter swap; no migration.

### Owner

Platform. On-call: platform-secondary.

---

## 15. ADR-E0.5-15 — DNA file formats, matching algorithm, release jurisdiction

### Context

`design.md` §5.5 + §16 item 15 and `privacy-and-legal-gate.md` §14 open question #4 + #5 require ADR before DNA can ship.

### Options

1. Limit to one open format (e.g. 23andMe-style CSV + raw FASTQ-like binary).
2. Multi-format ingestion with provider adapter.
3. Provider-mediated ingestion only.

### Decision

**DEFERRED**. Final choice depends on:

1. ADR-E0.5-04 residency selection (drives which jurisdictions are eligible).
2. ADR E10.1 architecture gate (`privacy-and-legal-gate.md` §14 #4).
3. Provider onboarding (currently evaluating two providers under NDA).

Until this ADR closes, the Flagsmith flag `legal.dna.enabled` stays `false` per `privacy-and-legal-gate.md` §12. `dna-service` may be scaffolded (E10.1) but cannot accept real uploads.

### Consequences

- DNA feature is opt-in and gated.
- E10 epics plan against this ADR as a dependency.

### License / TCO

- License: TBD per provider.

### Security / privacy

- DNA module is the highest-risk data class; isolation requirements per `design.md` §5.5, §12.

### SaaS / on-premise parity

On-prem DNA requires explicit customer DPA and architecture gate; otherwise disabled.

### Rollback / evolution path

Not applicable until first release; rollback = feature flag.

### Owner

Privacy + DNA lead. On-call: privacy-primary. **Open** until ADR E10.1 closes.

---

## 16. ADR-E0.5-16 — Product analytics, consent manager, DR topology

### Context

`design.md` §13 + §16 item 16 leave analytics platform, consent manager scope, and DR topology open. `privacy-and-legal-gate.md` §11 forbids PII / DNA / raw sensitive content in telemetry.

### Options

1. Self-hosted analytics + consent ledger in `dna-service`.
2. SaaS analytics (e.g. Amplitude / Mixpanel) + external consent manager.
3. No analytics, log-only.

### Decision

Adopt **option 1**.

- **Analytics**: self-hosted (PostHog self-hosted) opt-in per tenant; telemetry gateway enforces forbidden payload classes per `privacy-and-legal-gate.md` §11; CI regex gate blocks forbidden fields.
- **Consent manager**: purpose-versioned ledger stored inside `dna-service` (consent ledger) and a thin policy-version registry inside `tenant-service`. Cross-purpose consent reuse is forbidden.
- **DR topology**: multi-region active-passive (per ADR-E0.5-03) plus on-prem third tier. RPO ≤ 15 min, RTO ≤ 4 h per `scale-and-slo.md` §5.3; breach-notification SLA per jurisdiction deferred to E0.4 + E0.6.

### Consequences

- Analytics feature behind tenant-level consent flag.
- Consent ledger is the source of truth; OpenFGA does not store consent.

### License / TCO

- PostHog self-hosted = Apache-2.0 / MIT mix; ops cost dominated by ClickHouse.

### Security / privacy

- Telemetry gateway is the chokepoint; CI gate enforces forbidden payload classes.
- Pseudonymous tenant labels rotate quarterly per `privacy-and-legal-gate.md` §14 #9.

### SaaS / on-premise parity

Identical telemetry gateway; on-prem PostHog is opt-in.

### Rollback / evolution path

Analytics rollback = drop PostHog adapter and re-deploy with `analytics.enabled = false`. DR rollback = DNS failback + DB PITR.

### Owner

Privacy + SRE. On-call: sre-primary.

---

## A. Default numeric thresholds awaiting E0.6 sign-off

These values are documented here so that downstream epics have a single source of truth. They remain `DRAFT` until E0.6 ownership catalog sign-off.

| Threshold | Current default | Source | ADR / open question |
|---|---|---|---|
| `LIVING_INFERENCE_YEARS` | 110 | `glossary-and-policy-matrix.md` §2.1 | E0.2 §5 #6 |
| `MINORITY_AGE` | 18 | `glossary-and-policy-matrix.md` §1.2 | E0.4 §3 |
| `MERGE_AUTO_THRESHOLD` | 0.85 | `glossary-and-policy-matrix.md` §2.4 | E0.2 §5 #4 |
| `MERGE_REVERT_WINDOW_DAYS` | 30 | `glossary-and-policy-matrix.md` §2.4 | E0.2 |
| `CONSENT_REVOCATION_PROPAGATION_SECONDS` | 60 | `glossary-and-policy-matrix.md` §2.8 | E0.4 |
| `SHARE_TOKEN_DEFAULT_EXPIRY_DAYS` | 30 (max 365) | `glossary-and-policy-matrix.md` §5 #3 | E0.2 §5 #3 |
| Burst multiplier (SaaS) | 3.0× | `scale-and-slo.md` §4 | E0.3 §11 #1 |
| Burst multiplier (on-prem) | 2.0× | `scale-and-slo.md` §4 | E0.3 §11 #1 |
| Cache hit ratio target | 85 % | `scale-and-slo.md` §5 | E0.3 §11 #2 |
| RPO | ≤ 15 min | `requirements.md` NFR3 | E0.3 §11 #3 |
| RTO | ≤ 4 h | `requirements.md` NFR3 | E0.3 §11 #3 |
| Retention per plan | TBD per plan | `scale-and-slo.md` §6 | E0.3 §11 #4 |
| Breach-notification SLA | TBD per jurisdiction | `privacy-and-legal-gate.md` §14 #8 | E0.4 |
| Pseudonymous tenant-label rotation | quarterly | `privacy-and-legal-gate.md` §14 #9 | E0.4 |
| p95 read latency (popular APIs) | 300 ms | `requirements.md` NFR2 | E0.3 |
| p95 write latency (popular APIs) | 600 ms | `requirements.md` NFR2 | E0.3 |
| p95 search latency | 1 s | `requirements.md` NFR2 | E0.3 |
| Tree view first interaction | 2.5 s p75 on 10 K nodes | `requirements.md` NFR2 | E0.3 + ADR-E0.5-10 |
| Availability (SaaS) | 99.9 % / month | `requirements.md` NFR3 | E0.3 |
| Error-budget burn-rate alert | TBD per SRE workbook | `scale-and-slo.md` §5 | E0.3 §11 #6 |

---

## B. Version policy, review date and migration / rollback per ADR

| ADR | Review date | Migration policy | Rollback path |
|---|---|---|---|
| E0.5-01 | 2026-11-01 | Renovate-driven PR + lockfile update | Revert lockfile |
| E0.5-02 | 2026-12-15 | expand-contract via Flyway; cutover window 2 release trains | Reverse cutover + dual-write |
| E0.5-03 | TBD (jurisdiction-dependent) | Region failover via DNS + Argo CD | DNS revert |
| E0.5-04 | 2026-12-01 | CDN config as code; Kong declarative | Argo CD revert |
| E0.5-05 | 2026-12-01 | Realm split per runbook | Dual-write users |
| E0.5-06 | 2026-12-01 | Model upgrade via CI; store migration via dual-write | Revert CI model; restore store |
| E0.5-07 | 2026-12-01 | Namespace export / import; chart rollback | Helm revision rollback |
| E0.5-08 | 2026-12-01 | Avro schema evolution BACKWARD; consumer replay | Re-publish prior Apicurio version |
| E0.5-09 | 2026-12-01 | Library swap via follow-up ADR | Library revert + feature flag |
| E0.5-10 | TBD (post-prototype) | Renderer swap via feature flag + Argo Rollouts | Feature flag off |
| E0.5-11 | 2026-12-01 | Worker image bump via Helm | Helm revision rollback |
| E0.5-12 | 2026-12-01 | Adapter swap; no migration | Adapter rollback |
| E0.5-13 | 2026-12-01 | Pipeline template bump; runner image bump | Workflow re-run previous commit |
| E0.5-14 | 2026-12-01 | Adapter swap; no migration | Adapter rollback |
| E0.5-15 | TBD (post-E10.1) | Provider onboarding per contract | Feature flag off |
| E0.5-16 | 2026-12-01 | Analytics disable = flag; DR = DNS + PITR | DNS failback |

---

## C. Open questions handed to E0.6

1. Product / security / privacy / operations sign-off for §A numeric thresholds.
2. Launch jurisdiction sign-off for ADR-E0.5-03 and ADR-E0.5-15.
3. RACI assignment for each ADR owner (this document names the team; E0.6 names the individual + escalation).
4. Cost model for Kong Enterprise evaluation (per ADR-E0.5-04), Temporal Cloud evaluation (ADR-E0.5-07) and commercial fallback providers (ADR-E0.5-14).
5. Breach-notification SLA per jurisdiction (per `privacy-and-legal-gate.md` §14 #8).
6. Pseudonymous tenant-label rotation cadence ratification (per `privacy-and-legal-gate.md` §14 #9).
7. Customer DPA template (per `privacy-and-legal-gate.md` §14 #7).
8. ADR E10.1 jurisdiction allowlist for DNA (per `privacy-and-legal-gate.md` §14 #4).