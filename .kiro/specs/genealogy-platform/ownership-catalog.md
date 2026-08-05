# Ownership Catalog — Discovery deliverable

> Companion to E0.6 of `tasks.md`. Authoritative registry of who owns every
> deployable service and shared platform component, with synchronous dependency
> budget, compatibility/deprecation policy and RACI for product, domain,
> platform, security, privacy and operations. Treat every value marked
> `DRAFT → E0.6 sign-off` as binding only after Product + Engineering + Security
>
> - Privacy + Operations have ratified it.

## 1. Owner taxonomy

- **Domain owner** = accountable for the aggregate/data, API contract, event
  contract and SLO of a single domain service (`tenant-service`, `genealogy-service`,
  …). Names an individual engineer or EM; rotation documented in the runbook.
- **Platform owner** = accountable for a shared platform (Kong, Keycloak,
  Kafka, …). Owns Helm/config-as-code, upgrade/rollback, security hardening,
  backup/restore and observability dashboards for that platform.
- **Product owner** = accountable for the user journey served by one or
  more domain services. Owns roadmap, success metric and Flagsmith flag
  scope for the journey.
- **Privacy owner** = DPO delegate accountable for lawful basis, consent
  receipts, retention evidence and DPIA per data class.
- **Security owner** = accountable for threat model, secret/PII/DNA hygiene,
  security scanning and incident response coordination.
- **SRE / on-call lead** = accountable for SLO dashboard, alert routing,
  error budget and incident command per service/platform tier.

Roles above are abstracted on purpose: the table below names the team or
function, not a single individual. Per ADR §C #3, every named team MUST
publish a rotation roster in `runbook/<service>.md` with the on-call
individual, backup and escalation chain. The roster is owned by the named
team's lead and renewed quarterly.

## 2. Domain service ownership

The 11 services come from `design.md` §4 and are mandated by NFR4
("service owns data; no cross-service DB read"). Edge components (Kong,
web-app, web-bff, public-api) live in §3 because they cross multiple
business journeys.

For every service, the catalog records: accountability triad, data
ownership, public contracts (REST/gRPC/event), synchronous dependency
budget, SLO slice, on-call tier and runbook path. The synchronous
dependency budget column is normative — any change that pushes the
budget beyond its cap must reopen §5.

### 2.1 tenant-service (E3.2)

| Field                   | Value                                                                                                                                                  |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Domain owner            | Identity & Tenant team (lead placeholder: EM-Identity)                                                                                                 |
| Product owner           | Onboarding journey (Product-IC-01)                                                                                                                     |
| Privacy owner           | DPO delegate (Consents & Tenancy)                                                                                                                      |
| Security owner          | AppSec partner (Identity)                                                                                                                              |
| SRE / on-call lead      | sre-primary                                                                                                                                            |
| Owns aggregate/data     | `Tenant`, `Membership`, `Invitation`, `Entitlement` (per `design.md` §4)                                                                               |
| Public REST             | `services/tenant-service/openapi.yaml` (provisionally `…/v1/tenants`, `…/v1/memberships`)                                                              |
| gRPC                    | `gp.tenant.v1.{TenantService, MembershipService}`                                                                                                      |
| Events published        | `gp.tenant.v1.{TenantCreated, MembershipInvited, MembershipActivated, MembershipRevoked, EntitlementChanged}` (Apicurio)                               |
| Events consumed         | `gp.identity.v1.SubjectProvisioned` (Keycloak mirror)                                                                                                  |
| Sync dependencies (S2S) | Keycloak `/admin/realms/{r}/users` (≤ 200 ms p95, ≤ 1 hop); PostgreSQL primary (≤ 50 ms p95)                                                           |
| Sync dep budget         | **max 2 downstream synchronous calls per request** (`n_sync ≤ 2`), see §5                                                                              |
| SLO class               | API read path — 99.95 % / month per `scale-and-slo.md` §5.2; p95 read 300 ms, write 600 ms                                                             |
| On-call tier            | Tier-1 business hours, Tier-2 24×7                                                                                                                     |
| Capacity skeleton       | in `runbook/tenant-service.md` §Capacity, per `scale-and-slo.md` §8 (`scale_and_slo.yml::tenant`)                                                      |
| Runbook                 | `runbook/tenant-service.md`, dashboard `grafana/dashboards/tenant-service.json`, alert rules `alerts/tenant-service.yaml`                              |
| Backup/restore owner    | platform-primary (PostgreSQL PITR per ADR-E0.5-02)                                                                                                     |
| Deprecation contract    | REST envelope pinned for **12 months** after a breaking change is announced in CHANGELOG.md; gRPC API pinned for **9 months** after a stage transition |
| Compatible with         | ADR-E0.5-01 pinned baseline (Spring Boot, jOOQ, Flyway)                                                                                                |

### 2.2 genealogy-service (E4)

| Field                   | Value                                                                                                   |
| ----------------------- | ------------------------------------------------------------------------------------------------------- |
| Domain owner            | Core Genealogy team (lead placeholder: EM-Genealogy)                                                    |
| Product owner           | Editor + Researcher journeys                                                                            |
| Privacy owner           | DPO delegate (Living/Minor redaction)                                                                   |
| Security owner          | AppSec partner (Core domain)                                                                            |
| SRE / on-call lead      | sre-primary                                                                                             |
| Owns aggregate/data     | `Tree`, `Person`, `Relationship`, `LifeEvent`, `Claim` (per `design.md` §4)                             |
| Public REST             | `services/genealogy-service/openapi.yaml` (CRUD + visibility + merge endpoints)                         |
| gRPC                    | `gp.genealogy.v1.{TreeService, PersonService, RelationshipService, ClaimService}`                       |
| Events published        | `gp.genealogy.v1.{TreeVisibilityChanged, PersonRedacted, ClaimMerged, MergeReversed}`                   |
| Events consumed         | `gp.tenant.v1.MembershipRevoked` (cache invalidation), `gp.research.v1.ClaimVerified`                   |
| Sync dependencies (S2S) | OpenFGA check (≤ 80 ms p95, ≤ 1 hop); ABAC overlay evaluated in-process                                 |
| Sync dep budget         | **max 2 downstream synchronous calls per request** (`n_sync ≤ 2`); merges are async via Temporal (E9.1) |
| SLO class               | API read 99.95 %, write 99.9 %; p95 read 300 ms, write 600 ms; merge job 99 %                           |
| On-call tier            | Tier-1 24×7 (privacy critical)                                                                          |
| Capacity skeleton       | `scale_and_slo.yml::genealogy`                                                                          |
| Runbook                 | `runbook/genealogy-service.md`, dashboard `grafana/dashboards/genealogy-service.json`                   |
| Backup/restore owner    | platform-primary (PITR + RPO ≤ 5 min)                                                                   |
| Deprecation contract    | REST 12 months, gRPC 9 months, event `gp.genealogy.v1.*` 6 months with consumer opt-in window           |
| Compatible with         | ADR-E0.5-01 baseline; Apicurio BACKWARD evolution per ADR-E0.5-08                                       |

### 2.3 research-service (E6.1)

| Field               | Value                                                                                           |
| ------------------- | ----------------------------------------------------------------------------------------------- |
| Domain owner        | Research & Evidence team                                                                        |
| Product owner       | Genealogist journey                                                                             |
| Privacy owner       | DPO delegate (Citation metadata)                                                                |
| Security owner      | AppSec partner (Evidence)                                                                       |
| SRE / on-call lead  | sre-secondary                                                                                   |
| Owns aggregate/data | `Source`, `Citation`, `ResearchTask`, `Hypothesis`                                              |
| Public REST         | `services/research-service/openapi.yaml`                                                        |
| gRPC                | `gp.research.v1.{RepositoryService, CitationService, ResearchTaskService}`                      |
| Events published    | `gp.research.v1.{CitationCreated, ClaimVerified, ConflictDetected}`                             |
| Events consumed     | `gp.genealogy.v1.{TreeVisibilityChanged, PersonRedacted}`                                       |
| Sync dependencies   | None cross-domain synchronous; all reads served from own Postgres                               |
| Sync dep budget     | `n_sync = 0` (read path), `n_sync ≤ 1` (composite write through genealogy-service via Temporal) |
| SLO class           | API read 99.95 %, write 99.9 %                                                                  |
| On-call tier        | Tier-2 24×7                                                                                     |
| Capacity skeleton   | `scale_and_slo.yml::research`                                                                   |
| Runbook             | `runbook/research-service.md`                                                                   |

### 2.4 collaboration-service (E6.2, E6.4)

| Field               | Value                                                                                 |
| ------------------- | ------------------------------------------------------------------------------------- |
| Domain owner        | Collaboration team                                                                    |
| Product owner       | Reviewer journey                                                                      |
| Privacy owner       | DPO delegate (Comments/metadata)                                                      |
| Security owner      | AppSec partner (Comments)                                                             |
| SRE / on-call lead  | sre-secondary                                                                         |
| Owns aggregate/data | `ChangeProposal`, `Review`, `Comment`, `ActivityFeed`                                 |
| Public REST         | `services/collaboration-service/openapi.yaml`                                         |
| gRPC                | `gp.collab.v1.{ProposalService, CommentService}`                                      |
| Events published    | `gp.collab.v1.{ProposalSubmitted, ProposalApproved, ProposalRejected, PartialMerged}` |
| Events consumed     | All domain events for activity aggregation; ABAC redacted at projection               |
| Sync dependencies   | None mandatory; OpenFGA check when reply.comment authorises thread                    |
| Sync dep budget     | `n_sync ≤ 1`                                                                          |
| SLO class           | API read 99.95 %, best-effort activity feed 99.5 %                                    |
| On-call tier        | Tier-2 business hours                                                                 |

### 2.5 media-service (E7)

| Field               | Value                                                                                                                          |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Domain owner        | Media team                                                                                                                     |
| Product owner       | Editor + Album journeys                                                                                                        |
| Privacy owner       | DPO delegate (Quarantine/DNA prefix)                                                                                           |
| Security owner      | AppSec partner (Uploads)                                                                                                       |
| SRE / on-call lead  | sre-primary                                                                                                                    |
| Owns aggregate/data | `MediaAsset`, `MediaVariant`, `Album`                                                                                          |
| Public REST         | `services/media-service/openapi.yaml` (signed URL issuance, album CRUD)                                                        |
| gRPC                | `gp.media.v1.{AssetService, AlbumService}`                                                                                     |
| Events published    | `gp.media.v1.{AssetUploaded, AssetScanned, AssetReady, AssetRevoked, DerivativeProduced}`                                      |
| Events consumed     | `gp.tenant.v1.{MembershipRevoked, TenantDeleted}` (revoke delivery)                                                            |
| Sync dependencies   | S3/MinIO `HeadObject` (≤ 100 ms p95); Valkey quota check (≤ 5 ms p95)                                                          |
| Sync dep budget     | `n_sync ≤ 2` (S3 HEAD + Valkey GET). Processing is async via Temporal + ClamAV/FFmpeg/libvips/Tika/Tesseract/Gotenberg sandbox |
| SLO class           | Upload finalize 99.9 %, derivative pipeline 99.0 %, scan success 99.5 %                                                        |
| On-call tier        | Tier-1 24×7                                                                                                                    |
| Quarantine          | Mandatory — `READY` only after ClamAV clean + Tika metadata per ADR-E0.5-11                                                    |
| Capacity skeleton   | `scale_and_slo.yml::media`                                                                                                     |

### 2.6 search-service (E8)

| Field               | Value                                                                                                                         |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Domain owner        | Search team                                                                                                                   |
| Product owner       | Researcher + Public discovery journeys                                                                                        |
| Privacy owner       | DPO delegate (Public projection, redaction)                                                                                   |
| Security owner      | AppSec partner (Projection poisoning)                                                                                         |
| SRE / on-call lead  | sre-primary                                                                                                                   |
| Owns aggregate/data | `SearchDocument`, `SavedSearch`, `PublicProjection`                                                                           |
| Public REST         | `services/search-service/openapi.yaml` (authorised search only; public read goes via public-api + Kong)                       |
| gRPC                | `gp.search.v1.{AuthorizedSearchService, SavedSearchService}`                                                                  |
| Events published    | `gp.search.v1.{ProjectionRebuilt, SavedSearchEvaluated}`                                                                      |
| Events consumed     | `gp.genealogy.v1.*`, `gp.research.v1.*`, `gp.collab.v1.*`, `gp.media.v1.{AssetReady, AssetRevoked}` (idempotent inbox)        |
| Sync dependencies   | PostgreSQL FTS projection (≤ 100 ms p95); optional Valkey cache (≤ 5 ms p95, tenant-aware key)                                |
| Sync dep budget     | `n_sync ≤ 2` (DB + cache); no synchronous OpenFGA call (membership filter is precomputed into projection under a refresh SLA) |
| SLO class           | Authorised search 99.95 %, p95 < 1 s; public projection 99.5 % best effort; cache hit ratio ≥ 85 %                            |
| On-call tier        | Tier-1 24×7                                                                                                                   |
| Privacy gate        | `UNLISTED` returns `noindex` per `requirements.md` R11; projection rebuild is a Temporal workflow                             |

### 2.7 import-export-service (E9)

| Field               | Value                                                                                                                   |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Domain owner        | Interop team                                                                                                            |
| Product owner       | Power-user + Partner journeys                                                                                           |
| Privacy owner       | DPO delegate (Export redaction, DNA opt-out)                                                                            |
| Security owner      | AppSec partner (Parser sandbox, SSRF)                                                                                   |
| SRE / on-call lead  | sre-primary                                                                                                             |
| Owns aggregate/data | `TransferJob`, `MappingProfile`, `ExportManifest`                                                                       |
| Public REST         | `services/import-export-service/openapi.yaml` (job lifecycle + signed URL)                                              |
| gRPC                | `gp.interop.v1.{TransferService, MappingProfileService}`                                                                |
| Events published    | `gp.interop.v1.{TransferStarted, TransferProgressed, TransferCompleted, TransferFailed, MappingSaved, ExportDelivered}` |
| Events consumed     | `gp.tenant.v1.MembershipRevoked`                                                                                        |
| Sync dependencies   | Object storage GET for streaming inputs (≤ 200 ms p95 to fetch manifest metadata)                                       |
| Sync dep budget     | `n_sync ≤ 1`; all heavy work in Temporal workflow, idempotent activities (per ADR-E0.5-07)                              |
| SLO class           | Job success 99.0 %; download URL availability 99.9 % during TTL window                                                  |
| On-call tier        | Tier-2 24×7                                                                                                             |
| Privacy gate        | GEDCOM/CSV dry-run writes no domain data; export must strip DNA by default and require consent receipt                  |

### 2.8 dna-service (E10)

| Field               | Value                                                                                                                                                                                      |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Domain owner        | DNA team                                                                                                                                                                                   |
| Product owner       | DNA owner + Guardian journeys                                                                                                                                                              |
| Privacy owner       | DPO delegate (DNA module)                                                                                                                                                                  |
| Security owner      | AppSec partner (DNA isolation); Security on-call lead during incidents                                                                                                                     |
| SRE / on-call lead  | sre-primary + privacy-secondary (dual on-call)                                                                                                                                             |
| Owns aggregate/data | `DnaKit`, `Consent`, `DnaMatch`, `Segment` in dedicated schema/bucket/KMS key per ADR-E0.5-15                                                                                              |
| Public REST         | `services/dna-service/openapi.yaml` (consent + upload session + match query)                                                                                                               |
| gRPC                | `gp.dna.v1.{ConsentService, KitService, MatchService}`                                                                                                                                     |
| Events published    | `gp.dna.v1.{ConsentGranted, ConsentRevoked, KitUploaded, MatchProduced, KitDeleted}`                                                                                                       |
| Events consumed     | `gp.tenant.v1.MembershipRevoked` (mandatory revocation across all DNA flows)                                                                                                               |
| Sync dependencies   | None cross-service; OpenFGA namespace `dna.*` evaluated locally                                                                                                                            |
| Sync dep budget     | `n_sync = 0`; all DNA workflows live inside its worker pool with strict Istio/NetworkPolicy egress                                                                                         |
| SLO class           | Privacy-critical: 99.9 %; consent revocation propagation ≤ 60 s; revoke/export job 99.0 %                                                                                                  |
| On-call tier        | Tier-0 24×7 (any DNA privacy finding freezes release per `scale-and-slo.md` §5.4)                                                                                                          |
| Default state       | **Feature flag `legal.dna.enabled = false` per ADR-E0.5-15** until E10.1 architecture gate signs off                                                                                       |
| Privacy gate        | Raw genotype MUST NEVER enter Kafka, log, trace, search index, media preview, public API or notification payload; consent re-evaluated at activity time per `privacy-and-legal-gate.md` §9 |

### 2.9 notification-service (E11.1, E11.2)

| Field               | Value                                                                                             |
| ------------------- | ------------------------------------------------------------------------------------------------- |
| Domain owner        | Comms & Delivery team                                                                             |
| Product owner       | Cross-journey (admin, editor, guardian)                                                           |
| Privacy owner       | DPO delegate (Notification payload)                                                               |
| Security owner      | AppSec partner (Provider adapters)                                                                |
| SRE / on-call lead  | sre-secondary                                                                                     |
| Owns aggregate/data | `Notification`, `Preference`, `Template`                                                          |
| Public REST         | `services/notification-service/openapi.yaml` (preferences, inbox)                                 |
| gRPC                | `gp.notify.v1.{PreferenceService, NotificationService}`                                           |
| Events published    | `gp.notify.v1.{NotificationDispatched, NotificationFailed, SubscriptionUnsubscribe}`              |
| Events consumed     | All domain events (idempotent inbox); ABAC re-check before render per `design.md` §11.2           |
| Sync dependencies   | Valkey rate-limit bucket (≤ 5 ms p95); provider adapter via Temporal activity                     |
| Sync dep budget     | `n_sync ≤ 2` (rate-limit + preference fetch); provider send is async                              |
| SLO class           | Dispatch 99.5 %, p95 dispatch decision ≤ 200 ms                                                   |
| On-call tier        | Tier-2 business hours                                                                             |
| Privacy gate        | No DNA / no sensitive living payload sent to third-party providers without explicit ADR exception |

### 2.10 reporting-service (E11.3)

| Field               | Value                                                                                                         |
| ------------------- | ------------------------------------------------------------------------------------------------------------- |
| Domain owner        | Reporting team                                                                                                |
| Product owner       | Power-user + Operator dashboards                                                                              |
| Privacy owner       | DPO delegate (Report redaction)                                                                               |
| Security owner      | AppSec partner (Generated PDFs)                                                                               |
| SRE / on-call lead  | sre-secondary                                                                                                 |
| Owns aggregate/data | `ReportJob`, `ReportTemplate`, `AnalyticsProjection`                                                          |
| Public REST         | `services/reporting-service/openapi.yaml` (job status + signed download URL)                                  |
| gRPC                | `gp.report.v1.{ReportService, AnalyticsService}`                                                              |
| Events published    | `gp.report.v1.{ReportRequested, ReportCompleted, ReportFailed, AnalyticsRefreshed}`                           |
| Events consumed     | Domain events for projection rebuild                                                                          |
| Sync dependencies   | Gotenberg (HTTP, ≤ 300 ms p95) for PDF preview; never synchronous on the request path — all jobs are Temporal |
| Sync dep budget     | `n_sync ≤ 1` (job submission only)                                                                            |
| SLO class           | Job success 99.0 %, deterministic report version pin per `design.md` §11.3                                    |

### 2.11 audit-service (E3.6)

| Field               | Value                                                                             |
| ------------------- | --------------------------------------------------------------------------------- |
| Domain owner        | Security Engineering team                                                         |
| Product owner       | Compliance & Operator journeys                                                    |
| Privacy owner       | DPO delegate (Retention evidence)                                                 |
| Security owner      | Security Engineering (separation of duties)                                       |
| SRE / on-call lead  | sre-primary                                                                       |
| Owns aggregate/data | `AuditEntry`, `AuditExport` (append-only WORM bucket) per `scale-and-slo.md` §5.3 |
| Public REST         | internal only via BFF / admin shell; external audit export via Kong-signed URL    |
| gRPC                | `gp.audit.v1.{AuditService}`                                                      |
| Events published    | none (audit is a sink, never a source of business events)                         |
| Events consumed     | none — every service writes via gateway API or Kafka audit topic                  |
| Sync dependencies   | Kafka audit topic; append hash chain verified on read                             |
| Sync dep budget     | `n_sync ≤ 1` (Kafka produce, fire-and-forget)                                     |
| SLO class           | Durability = none-lost; availability 99.9 %; integrity check 100 %                |
| On-call tier        | Tier-0 24×7 (privacy/security)                                                    |

## 3. Edge, identity and shared platform ownership

These components are not domain services but appear in every interaction;
they are listed once here so that the SLO, runbook and on-call tier can be
discovered from a single document.

| Component                                                                  | Owner                            | Privacy owner  | Security owner | SRE / on-call lead | SLO slice                                           | Sync budget                                                                 | Runbook                                     |
| -------------------------------------------------------------------------- | -------------------------------- | -------------- | -------------- | ------------------ | --------------------------------------------------- | --------------------------------------------------------------------------- | ------------------------------------------- |
| Kong Gateway (E2.2)                                                        | platform-primary                 | AppSec partner | AppSec partner | sre-primary        | 99.99 %; per-tenant rate limit decision < 5 ms      | `n_sync ≤ 0` (edge policy evaluated in-process)                             | `runbook/kong.md`                           |
| CDN / WAF / Ingress (ADR-E0.5-04)                                          | platform-primary                 | AppSec partner | AppSec partner | sre-primary        | 99.99 %                                             | `n_sync ≤ 0`                                                                | `runbook/edge.md`                           |
| Keycloak (E3.1)                                                            | platform-secondary               | DPO delegate   | AppSec partner | sre-primary        | 99.95 %; OIDC token p95 < 150 ms                    | `n_sync ≤ 1` (DB lookup inside Keycloak)                                    | `runbook/keycloak.md`                       |
| OpenFGA (E3.3, ADR-E0.5-06)                                                | platform-primary + identity team | DPO delegate   | AppSec partner | sre-primary        | 99.95 %; check p95 < 80 ms                          | `n_sync ≤ 1` per request                                                    | `runbook/openfga.md`                        |
| Strimzi Kafka + Apicurio (E2.3, ADR-E0.5-08)                               | platform-primary                 | AppSec partner | AppSec partner | sre-primary        | 99.9 %; producer ack p95 < 50 ms                    | `n_sync ≤ 1` (produce)                                                      | `runbook/strimzi.md`, `runbook/apicurio.md` |
| Temporal (E2.4, ADR-E0.5-07)                                               | platform-secondary               | DPO delegate   | AppSec partner | sre-primary        | 99.9 %; workflow start p95 < 400 ms                 | `n_sync ≤ 1` (start)                                                        | `runbook/temporal.md`                       |
| Istio service mesh (E2.5)                                                  | platform-primary                 | AppSec partner | AppSec partner | sre-primary        | 99.99 % control plane, 99.9 % data plane            | `n_sync ≤ 0` (transparent mTLS)                                             | `runbook/istio.md`                          |
| Vault / cloud KMS (E2.6)                                                   | platform-secondary               | AppSec partner | AppSec partner | sre-primary        | 99.99 %; secret retrieval p95 < 100 ms              | `n_sync ≤ 1` (read at startup, cached via Kubernetes service account token) | `runbook/vault.md`, `runbook/kms.md`        |
| S3/MinIO (E2.7)                                                            | platform-primary                 | DPO delegate   | AppSec partner | sre-primary        | 99.9 %; HEAD p95 < 100 ms                           | `n_sync ≤ 1` (signed URL issuance, object metadata)                         | `runbook/s3.md`, `runbook/minio.md`         |
| Valkey (Redis-compatible, E2.7)                                            | platform-secondary               | AppSec partner | AppSec partner | sre-secondary      | 99.9 %; GET p95 < 5 ms                              | `n_sync ≤ 1` per request                                                    | `runbook/valkey.md`                         |
| Flagsmith / OpenFeature (E2.8)                                             | platform-secondary               | AppSec partner | AppSec partner | sre-secondary      | 99.9 %; flag fetch p95 < 30 ms (with safe fallback) | `n_sync ≤ 1`                                                                | `runbook/flagsmith.md`                      |
| Argo CD + Rollouts (E2.9)                                                  | platform-primary                 | N/A            | AppSec partner | sre-primary        | 99.9 %; rollout decision < 60 s                     | `n_sync ≤ 0` (controller-only)                                              | `runbook/argo.md`                           |
| Grafana OSS + OTel Collector (E2.10)                                       | platform-secondary               | AppSec partner | AppSec partner | sre-primary        | 99.9 %; dashboard load < 5 s                        | `n_sync ≤ 0` (read-only)                                                    | `runbook/observability.md`                  |
| CI pipeline (ADR-E0.5-13)                                                  | platform-primary                 | AppSec partner | AppSec partner | sre-primary        | 99.5 %; mean run < 12 min                           | `n_sync ≤ 0`                                                                | `runbook/ci.md`                             |
| Container registry + Cosign                                                | platform-primary                 | AppSec partner | AppSec partner | sre-primary        | 99.9 %; image pull p95 < 5 s                        | `n_sync ≤ 0`                                                                | `runbook/registry.md`                       |
| web-app (Next.js, E1.5, E5)                                                | web-app team                     | DPO delegate   | AppSec partner | sre-secondary      | 99.9 %; LCP p75 < 2.5 s                             | `n_sync ≤ 0` (client)                                                       | `runbook/web-app.md`                        |
| web-bff (Spring Boot, E1.4, E5)                                            | web-bff team                     | DPO delegate   | AppSec partner | sre-secondary      | 99.95 %; p95 compose < 800 ms                       | `n_sync ≤ 3` (see §5)                                                       | `runbook/web-bff.md`                        |
| public-api (Spring Boot, E9.5)                                             | public-api team                  | DPO delegate   | AppSec partner | sre-secondary      | 99.95 %; p95 < 600 ms                               | `n_sync ≤ 2`                                                                | `runbook/public-api.md`                     |
| ClamAV / Tika / libvips / FFmpeg / Tesseract / Gotenberg (E7, ADR-E0.5-11) | media team                       | AppSec partner | AppSec partner | sre-primary        | 99.5 % each                                         | `n_sync ≤ 0` (worker pool only)                                             | `runbook/media-pipeline.md`                 |

## 4. Synchronous dependency budget

`design.md` §11.4 forbids long synchronous chains; this section makes that
rule quantitative so every service can fail PR review if it exceeds it.

### 4.1 Definitions

- `n_sync(req)` = number of **outbound network round-trips to another
  trust zone** (another service, Kong, OpenFGA, Keycloak, PostgreSQL
  primary, Valkey, S3/MinIO HEAD) measured on the request path **after**
  in-process caches, RLS predicate evaluation and circuit breaker fallback.
- `chain_depth(req)` = maximum path length from the user to the deepest
  service in the synchronous tree (edge → BFF → service → platform counted
  as a chain).

### 4.2 Budget caps

| Component                           | `n_sync` cap | `chain_depth` cap           | Notes                                                                                                           |
| ----------------------------------- | ------------ | --------------------------- | --------------------------------------------------------------------------------------------------------------- |
| Edge (Kong, CDN)                    | 0            | 1 (downstream only)         | Edge MUST NOT call another service on hot path                                                                  |
| web-app                             | 0            | n/a                         | PWA — no cross-service calls                                                                                    |
| web-bff                             | **3**        | 4 (edge → BFF → 2 services) | BFF composition is the only place chains > 2 are allowed; longer compositions move to a Temporal read-model job |
| public-api                          | 2            | 3                           | Public API must not depend on `web-bff`                                                                         |
| Domain services (read path)         | **2**        | 3                           | DB + cache + (optional) policy check; OpenFGA call counts as 1                                                  |
| Domain services (write path)        | 2            | 3                           | DB + audit/event publish; OpenFGA tuple write is async via Temporal                                             |
| DNA service                         | **0**        | 1                           | Any DNA request that requires a cross-service fetch MUST be reworked to async; on-call reviews every exception  |
| Async workers / Temporal activities | 4            | 5                           | Higher limit only because failures are retried, never user-blocking                                             |

### 4.3 Enforcement

- Every PR changing a service MUST update `services/<svc>/docs/dependencies.md`
  with the synchronous edge list and `n_sync` budget. Reviewer checklist:
  - `n_sync(req)` ≤ cap in §4.2 for happy path and p95 degraded path.
  - Each downstream synchronous call has an explicit cache key, TTL or
    circuit breaker fallback. **No unbounded retries** in synchronous code.
  - If the new call pushes `n_sync` to the cap, a diagram of the new chain
    is attached and the justification references an ADR or design.md section.
- The web-bff team owns a weekly report `reports/sync-budget.md` listing
  any composite that exceeds cap and the queue to remediate. A composite
  above cap for two consecutive weeks opens a P1 SRE ticket.

### 4.4 What changes the budget

- Adding a new mandatory synchronous dependency (e.g. consent verdict
  inline instead of cached) requires reopening §4.2 via ADR and Product +
  SRE sign-off.
- Switching a downstream call from synchronous to async (cached aggregate,
  outbox/inbox) lowers `n_sync` and DOES NOT require ADR; it MUST be
  recorded in `services/<svc>/docs/dependencies.md`.

## 5. Compatibility and deprecation window

### 5.1 REST envelope (`/api/**`, BFF and public-api)

- REST endpoints MUST follow RFC 9457, support `ETag`, `If-Match`
  optimistic concurrency and idempotency keys per `design.md` §1.
- **Breaking changes** (path removed, schema field removed, semantic
  change of field, status code change) require a new API version
  (`/v{n+1}/`) running in parallel for **12 months** before the old
  version is removed.
- The old version's changelog is announced 6 months before removal in
  `CHANGELOG.md` and is exposed on the dev portal if available.
- Exception: field marked `deprecated` in `openapi.yaml` and protected
  by `x-deprecation-sunset: YYYY-MM-DD` may be removed after the sunset
  date without the 12-month window, provided that no production tenant
  is still calling it (verified by access log scan).

### 5.2 gRPC service definitions

- Protobuf packages follow semver on the package version; `gp.<domain>.v1`
  is **stable for 9 months** after a stage transition (alpha → beta →
  GA). At GA, a deprecated method MAY be removed **only after** all
  consumers declared in `compat/<pkg>.yaml` are migrated.
- Wire-incompatible field renumbering, type change or removal requires a
  new `gp.<domain>.v{n+1}` package; old package coexists for 9 months.

### 5.3 Async events (Kafka topics)

- Schema compatibility settings come from Apicurio per `design.md` §8 and
  ADR-E0.5-08: **BACKWARD** for domain events, **FORWARD** only for
  consumer-driven contracts negotiated per topic in
  `contracts/events/<topic>.yaml`.
- Topic name changes are forbidden; payload schema changes follow
  Apicurio compatibility rules. A topic MAY be deprecated via
  `X-Deprecation` header (encoded into envelope metadata) and is
  retained for **6 months** of consumer opt-in window.
- Outbox producer MUST continue to publish to the old topic until the
  last consumer declared in `compat/<topic>.yaml` signals readiness; a
  `gp.platform.v1.DeprecationReadiness` consumer-flag event governs the
  cutover.

### 5.4 Feature flags

- Flagsmith flags follow the rule in `design.md` §2.12: every flag has
  owner, expiry and audit; safe fallback is mandatory.
- A flag that is `BOOLEAN` and exists solely to enable/disable a closed
  beta or a switchable kill MUST have `expiry ≤ 90 days` from creation.
  Flags overdue for review are auto-reported weekly to
  `reports/feature-flag-hygiene.md` and gate release if any critical
  flag (auth, consent, residency, DNA) is overdue.

### 5.5 Platform version policy

- Platform components run on the pinned baseline from ADR-E0.5-01.
  Renovate opens weekly PRs. Major/security-sensitive upgrades NEVER
  auto-merge per `tasks.md` E1.6; they require product + platform + SRE
  sign-off on the change ticket before merge.
- On-premise customers running an N-1 platform release receive security
  patches for **12 months** after N becomes the default.

### 5.6 Notification of consumers

- API/event changelog (`CHANGELOG.md`) is published at every release.
- A breaking announcement triggers:
  1. Email to partner integrations (mailing list `api-partners@`).
  2. Banner in the developer console when applicable.
  3. Flagsmith flag if a runtime toggle can keep the old behaviour live.
  4. SRE on-call ticket to contact high-volume API consumers.

## 6. RACI

RACI per activities drawn from `requirements.md` and `tasks.md`. Team names
are placeholders until Product + Engineering ratifies real team IDs in
`config/teams.yaml` (E1.1 deliverable).

> Convention: **R** = responsible (does the work), **A** = accountable
> (signs off), **C** = consulted, **I** = informed.

### 6.1 Product

| Activity                  | Product Mgr | Eng EM | Design | Privacy | Security | SRE | Platform |
| ------------------------- | ----------- | ------ | ------ | ------- | -------- | --- | -------- |
| Roadmap / epic sign-off   | **A**       | R      | C      | C       | C        | C   | I        |
| Feature flag scope        | **A**       | R      | C      | C       | C        | I   | I        |
| Success metric definition | **A**       | R      | C      | I       | I        | C   | I        |
| User-visible change comms | **A**       | I      | R      | I       | I        | I   | I        |

### 6.2 Domain (per-service activities)

| Activity                           | Domain EM | Domain team | Privacy | Security | SRE | Platform | Product |
| ---------------------------------- | --------- | ----------- | ------- | -------- | --- | -------- | ------- |
| API/gRPC/event contract            | **A**     | R           | C       | C        | C   | I        | C       |
| Aggregate/data model               | **A**     | R           | C       | C        | C   | I        | I       |
| Migration (Flyway expand-contract) | A         | R           | C       | C        | C   | R        | I       |
| Authorization (OpenFGA + ABAC)     | A         | R           | C       | C        | C   | I        | I       |
| Functional test suites             | A         | R           | I       | I        | C   | C        | I       |
| Runbook & capacity table           | A         | R           | C       | C        | C   | C        | I       |
| Service deprecation / sunset       | A         | R           | C       | C        | C   | C        | C       |

### 6.3 Platform

| Activity                                | Platform EM | Platform team | SRE   | Security | Privacy | Domain EM |
| --------------------------------------- | ----------- | ------------- | ----- | -------- | ------- | --------- |
| Platform version policy                 | **A**       | R             | C     | C        | C       | I         |
| Helm/config-as-code                     | A           | R             | C     | C        | I       | I         |
| Backup / restore drill                  | A           | R             | **A** | C        | C       | I         |
| Upgrade & rollback runbook              | A           | R             | C     | C        | I       | I         |
| Tenant onboarding automation            | A           | R             | C     | C        | C       | C         |
| mTLS / NetworkPolicy baseline           | A           | R             | C     | **A**    | C       | I         |
| Observability baseline (OTel + Grafana) | A           | R             | **A** | C        | C       | I         |

### 6.4 Security

| Activity                     | Security EM | AppSec | Domain EM | SRE | Privacy | Platform |
| ---------------------------- | ----------- | ------ | --------- | --- | ------- | -------- |
| Threat model                 | **A**       | R      | R         | C   | C       | C        |
| Secret/PII/DNA scan          | **A**       | R      | R         | R   | C       | I        |
| Pen-test scope & remediation | **A**       | R      | R         | C   | C       | C        |
| SBOM / Cosign verification   | A           | R      | I         | C   | I       | **A**    |
| Vulnerability triage & SLA   | **A**       | R      | R         | R   | I       | I        |
| Kong / WAF policy            | C           | R      | I         | C   | I       | **A**    |

### 6.5 Privacy

| Activity                                   | DPO   | Privacy delegate | Domain EM | Security | SRE | Product | Platform |
| ------------------------------------------ | ----- | ---------------- | --------- | -------- | --- | ------- | -------- |
| DPIA per data class                        | **A** | R                | R         | C        | I   | C       | C        |
| Lawful basis / consent purpose             | **A** | R                | R         | C        | I   | C       | I        |
| Retention & deletion evidence              | **A** | R                | R         | C        | C   | I       | C        |
| Data subject request (export/delete)       | A     | R                | R         | C        | C   | I       | C        |
| Pseudonymous label & rotation              | A     | R                | I         | C        | I   | I       | **A**    |
| Jurisdiction sign-off (E10.1, ADR-E0.5-03) | **A** | R                | C         | C        | I   | C       | C        |
| Breach notification SLA per jurisdiction   | **A** | R                | C         | R        | C   | C       | C        |

### 6.6 Operations / SRE

| Activity                        | SRE lead | SRE on-call | Platform | Security | Privacy | Product |
| ------------------------------- | -------- | ----------- | -------- | -------- | ------- | ------- |
| SLO definition & review         | **A**    | R           | C        | C        | C       | C       |
| Alert routing & on-call         | **A**    | R           | C        | I        | I       | I       |
| Incident command                | **A**    | R           | C        | R        | C       | I       |
| Error budget & release freeze   | **A**    | R           | C        | C        | C       | C       |
| Chaos / DR drill (E13.4, E14.2) | **A**    | R           | R        | C        | C       | I       |
| Capacity plan & HPA/KEDA        | A        | R           | **A**    | I        | I       | C       |
| Support bundle redaction        | A        | R           | C        | **A**    | C       | I       |

## 7. Cross-cutting operational rules

1. **Per-service runbook location**: `runbook/<service>.md`. Required
   sections (per `scale-and-slo.md` §8 and NFR5):
   - **Contacts** — domain EM, on-call rotation, escalation chain.
   - **Topology** — diagram + dependency list (cross-link §2 / §3).
   - **SLO** — class, SLI formula, current dashboard link, alert rules
     link, error budget monthly burn-rate.
   - **Capacity** — peak multiplier, headroom targets, scale formula
     substituted with current dataset sizes.
   - **Run procedures** — common operational actions (failover, key
     rotation, drain, lockdown, redeploy).
   - **Incident playbook** — known failure modes and remediation.
   - **Privacy/security tests** — last run date and link to report.
   - **Changelog** — service-level changes since last SoR update.
2. **Per-platform runbook location**: `runbook/<platform>.md` with the
   same skeleton as a service runbook plus an **upgrade matrix** table
   (current → next, prerequisites, validation, rollback).
3. **Quarterly ownership review**: every quarter, the EM owning the
   catalog runs a review of §2 and §3 — team IDs, on-call tiers, runbook
   links and SLO slices must be accurate. The review record is filed in
   `evidence/quarterly-ownership/<YYYY-Q>.md`.
4. **Adoption rule for product changes** (§5 deprecation windows):
   a feature is not generally available to customers until §5 windows
   are satisfied; otherwise the GA gate (E15.4) will block release.
5. **Telemetry contract** (NFR5 + `design.md` §13): every service emits
   the OTel semantic conventions with a `tenant_pseudo_id` label only;
   raw `tenant_id`, `user_id` or `person_id` are forbidden at the
   metric/log/trace level. Cardinality rules per
   `scale-and-slo.md` §5.4.
6. **Privileged access** (NFR1, E11.5): JIT support access to DNA or
   audit data MUST be bannered, time-bounded, requires owner/admin
   approval and is recorded in `audit-service` with `actor_pseudo_id`.

## 8. Open questions handed to E1.1+

These items cannot close inside E0.6 because they need real team IDs and
GitHub identities; the catalog above records the **function** that owns
each row and tracks the open sign-off as a discovery residual risk.

1. Replace each `team/EM placeholder` with the real team ID once
   Product + Engineering ratify team boundaries (filed under
   `config/teams.yaml`, owner E1.1).
2. Adopt the catalog into the monorepo at `docs/ownership/OWNERSHIP.md`
   when E1.1 lands, with each service directory mirroring a thin
   `OWNERS` file (CODEOWNERS-compatible). Owner: E1.1 workspace lead.
3. Wire `services/<svc>/docs/dependencies.md` and CI lint enforcing
   `n_sync` into the quality commands of E1.2. Owner: E1.2 quality lead.
4. Per-service runbook skeleton §7.1 must be generated by the template
   in E1.4 (Spring Boot) and the web template in E1.5 (Next.js). Owner:
   E1.4 + E1.5 leads.
5. E2.1 cluster baseline must export the SLO + alert routing derived
   from §3 to Grafana/Prometheus on day one. Owner: E2.1 platform lead.
6. E13.2 owns the quarterly review described in §7.3 and ingests the
   catalog drift alerts. Owner: E13.2 SRE lead.
7. Numeric thresholds in **§A** of `architecture-decisions.md` remain
   `DRAFT` until product / security / privacy / operations sign the
   table in this document's inherited buckets; the sign-off ceremony is
   owned by the catalog EM and recorded in `evidence/E0.6-signoff.md`.

## 9. Sign-off

This catalog is the single source of truth for service and platform
ownership across all epics. Any change that re-assigns a row in §2 / §3
or alters §4 (sync budget) / §5 (deprecation) / §6 (RACI) requires:

- PR with reviewer from the old owner team and the new owner team.
- Notice in `#gen-eng` 48 h before merge so downstream epic owners can
  raise concerns.
- For §4 changes, ADR alignment per `architecture-decisions.md` §B
  review date cadence.

Until Product + Engineering + Security + Privacy + Operations sign the
catalog and the placeholder team IDs (§8 #1) are filled, this document
remains `DRAFT → E0.6 sign-off` and downstream epics consume it as
planning input only.
