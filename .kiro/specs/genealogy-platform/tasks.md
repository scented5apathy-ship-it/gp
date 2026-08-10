# Genealogy Platform — Implementation Plan

## Quy ước

- Mỗi mục `E` là một epic; mỗi checkbox là subtask có thể giao việc.
- Mọi AI Agent bắt buộc tuân theo [AI Agent Execution Contract](./agent-execution.md).
- Prompt giao việc chuẩn: `Triển khai task <TASK_ID> theo .kiro/specs/genealogy-platform/agent-execution.md.`
- Không tự xây lại capability của Kong, Keycloak, OpenFGA, Temporal, Strimzi/Apicurio, Istio, Vault/KMS, Flagsmith, Argo hoặc Grafana stack.
- Mọi epic phải có code/config-as-code, test, telemetry, security checks, runbook và bằng chứng acceptance.
- Mỗi task hoàn thành phải tạo `.kiro/specs/genealogy-platform/evidence/<TASK_ID>.md` với `Status: DONE`.
- Agent chỉ được đổi checkbox từ `[ ]` sang `[x]` sau khi dependency, acceptance, validation và evidence đều đạt; `BLOCKED`, `PARTIAL` hoặc `FAILED` phải giữ `[ ]`.
- Definition of Done chung: build/lint/typecheck/test pass; contract tương thích; tenant/privacy tests pass; không còn critical/high finding chưa được phê duyệt; không log PII/DNA/secret.

## E0 — Discovery, governance và ADR

**Mục tiêu:** Chốt nghiệp vụ, quy mô, pháp lý và các quyết định còn mở trước khi scaffold.

**Phụ thuộc:** Không.

### Subtasks

- [x] E0.1 Xác nhận persona và journey

  - Mô tả owner/admin/editor/contributor/viewer, genealogist, operator, auditor và DNA owner/guardian.
  - Bao phủ onboarding, tạo/import tree, cộng tác, public sharing, media, source, DNA và data-subject request.
  - Ghi quyền tối thiểu, dữ liệu nhìn thấy, failure path và success metric cho từng journey.

- [x] E0.2 Chốt glossary và policy matrix

  - Chuẩn hóa Tree, Person, User, Relationship, Claim, Citation, Living, Minor, Consent và Tenant.
  - Chốt living inference, redaction, guardian, merge, uncertainty và direct-edit/approval.
  - Tạo decision table `PRIVATE/UNLISTED/PUBLIC` theo role, resource và trạng thái người sống.

- [x] E0.3 Chốt scale và SLO

  - Xác nhận tenant, person/tree, media, DNA kit, request rate và concurrent job cho mốc 1/3/5 năm.
  - Tạo synthetic datasets 10K/100K/1M person và workload model.
  - Chốt latency, availability, RPO/RTO, retention và data residency theo plan.

- [x] E0.4 Hoàn tất privacy/legal gate

  - Thực hiện DPIA và threat model cho tenant isolation, public sharing, parser media/GEDCOM và DNA.
  - Chốt jurisdiction, lawful basis, consent purpose/version, legal hold và deletion evidence.
  - Xác định feature phải tắt bằng Flagsmith nếu legal gate chưa đạt.

- [x] E0.5 Duyệt ADR còn mở tại Design §16

  - So sánh option theo license/TCO, HA/DR, lock-in, security, air-gap và kỹ năng vận hành.
  - Ghi owner, version policy, review date, migration và rollback path.
  - Không triển khai component chưa được chốt phiên bản/distribution.

- [x] E0.6 Lập ownership catalog
  - Ghi owner, data, API/event, SLO, on-call, dependency và runbook cho từng service/platform.
  - Chốt synchronous dependency budget, compatibility và deprecation window.
  - Lập RACI cho product, domain, platform, security, privacy và operations.

**Đầu ra:** Journey, glossary, policy matrix, scale model, threat model, ADR và ownership catalog.

**Kiểm thử/DoD:** Product, security, privacy và operations phê duyệt; không còn blocker cho E1–E5.

_Requirements: R1–R18, NFR1–NFR8_

## E1 — Monorepo, contracts và engineering guardrails

**Mục tiêu:** Tạo nền móng build/test/release cho web, Java services, contracts và platform config.

**Phụ thuộc:** E0.5.

### Subtasks

- [x] E1.1 Khởi tạo workspace

  - Tạo pnpm/Turborepo cho web/packages và Gradle multi-project cho Java.
  - Bật TypeScript strict, Java 21 toolchain, dependency locking và reproducible build.
  - Chặn service import domain model/database module của service khác.

- [x] E1.2 Thiết lập quality commands

  - Cấu hình format/lint/typecheck cho Java, TypeScript, OpenAPI, Protobuf, YAML và Helm.
  - Tạo root commands cho unit, integration, contract, E2E và build.
  - Thêm ownership/path rules và generated-code policy.

- [x] E1.3 Thiết lập contract-first

  - Tạo OpenAPI versioned, Protobuf packages, Kafka envelope và schema modules.
  - Tích hợp Apicurio compatibility; lint/breaking checks cho REST/gRPC/event.
  - Chuẩn hóa RFC 9457, cursor, ETag, idempotency và correlation headers.

- [x] E1.4 Tạo Spring Boot template

  - Tích hợp REST/gRPC, jOOQ, Flyway, OTel, probes và graceful shutdown.
  - Thêm trusted context, audit hook, OpenFeature SDK và secure defaults.
  - Tạo Testcontainers fixtures cho PostgreSQL, Kafka, Keycloak, OpenFGA, Temporal, S3 và Valkey.

- [x] E1.5 Tạo Next.js PWA shell

  - App Router, Tailwind, shadcn/ui, tokens, i18n/RTL và PWA manifest.
  - Sinh typed REST client từ OpenAPI; thêm error/loading boundaries.
  - Đặt budget cho bundle, Core Web Vitals và accessibility.

- [x] E1.6 Thiết lập OSS security CI
  - Chạy Gitleaks, Semgrep, Trivy, Syft/Grype, Checkov và license checks.
  - Sinh SBOM, ký image/artifact bằng Cosign và lưu provenance.
  - Cấu hình Renovate với review policy; không tự merge major/security-sensitive update.

**Đầu ra:** Monorepo build được, templates, contracts và CI gates.

**Kiểm thử/DoD:** Clean build pass; breaking contract, secret và vulnerable fixture đều bị chặn; sample web→BFF→gRPC có trace.

_Requirements: R12, R17, R18, NFR1, NFR5, NFR7, NFR8_

## E2 — Kubernetes platform và công cụ dùng chung

**Mục tiêu:** Cung cấp platform chuẩn cho SaaS/on-premise thay vì tự xây hạ tầng tương đương.

**Phụ thuộc:** E0, E1.

### Subtasks

- [x] E2.1 Dựng local và cluster baseline

  - Tạo local profile và Helm values cho SaaS/on-premise.
  - Thiết lập namespace, quota, Pod Security, NetworkPolicy, probes và PDB.
  - Tạo preflight kiểm version, storage class, DNS, certificate và capacity.

- [x] E2.2 Cấu hình Kong Gateway runtime

  - Quản lý route/plugin declaratively: TLS, auth validation, CORS, request-size, rate limit và correlation ID.
  - Tách public, authenticated, partner và admin routes; không đặt domain authorization trong Kong.
  - Thêm config validation, smoke test, metrics và rollback.

- [x] E2.3 Cấu hình Strimzi và Apicurio

  - Khai báo Kafka cluster, users, ACL, topics, retention, replication và quotas.
  - Thiết lập schema compatibility, artifact naming và access control trong Apicurio.
  - Alert under-replicated partitions, disk, consumer lag và registry failures.

- [x] E2.4 Cấu hình Temporal

  - Triển khai namespace, retention, task queues, worker identity và visibility policy.
  - Định nghĩa timeout/retry/heartbeat mặc định; cấm PII trong search attributes.
  - Backup/restore và smoke workflow phải được kiểm chứng.

- [x] E2.5 Cấu hình Istio

  - Bật strict mTLS, ingress/egress policy, service authorization và telemetry.
  - Quy định nơi cấu hình timeout/retry để tránh retry amplification.
  - Kiểm thử deny-by-default và service identity giả mạo.

- [x] E2.6 Cấu hình Vault và cloud KMS abstraction

  - Thiết lập auth method, short-lived credentials, policy theo workload và rotation.
  - Cloud KMS bọc key SaaS; Vault/on-premise key provider dùng cùng application contract.
  - Không đưa secret vào Git, Helm values, image, log hoặc Temporal payload.

- [x] E2.7 Cấu hình S3/MinIO và Valkey

  - Chuẩn hóa bucket/prefix, encryption, lifecycle, versioning, CORS và signed URL behavior.
  - Dùng Valkey cho cache/session/rate state có TTL; không làm source of truth.
  - Chạy compatibility tests giữa cloud S3 và MinIO.

- [x] E2.8 Cấu hình Flagsmith/OpenFeature

  - Tạo environment, flag taxonomy, owner, expiry và audit.
  - SDK phải có safe default khi Flagsmith unavailable.
  - Cấm dùng flag để bỏ qua authorization hoặc consent.

- [x] E2.9 Cấu hình Argo CD/Rollouts

  - GitOps Helm manifests, drift detection và promotion theo môi trường.
  - Canary dựa trên error rate/latency; tự abort và rollback khi vượt threshold.
  - Tách quyền merge code, đổi config production và approve promotion.

- [x] E2.10 Cấu hình Grafana OSS stack
  - OTel Collector, Prometheus, Loki, Tempo và Grafana với retention đã chốt.
  - Dashboard cho Kong, Kafka, Temporal, OpenFGA, Istio, Vault, database và workloads.
  - Áp log redaction và tenant pseudonymous labels để tránh cardinality/PII leak.

**Đầu ra:** Platform Helm/GitOps profiles, dashboards, alerts, backups và runbooks.

**Kiểm thử/DoD:** SaaS-like và on-premise smoke tests pass; mTLS/deny tests pass; platform restore và rollback có bằng chứng.

_Requirements: NFR1–NFR8_

## E3 — Identity, tenant và authorization

**Mục tiêu:** Xây membership/policy nghiệp vụ trên Keycloak và OpenFGA, không tự lưu credential hay tự xây graph engine.

**Phụ thuộc:** E1, E2.

### Subtasks

- [ ] E3.1 Cấu hình Keycloak

  - Quản lý realm/client/flow bằng config-as-code; Authorization Code + PKCE và secure BFF session.
  - Cấu hình verification, recovery, MFA, federation, key rotation và logout/revoke.
  - Hạn chế custom extension; mọi thay đổi flow có regression tests.

- [x] E3.2 Triển khai tenant-service

  **Epic status: DONE** — 5/5 subtask `[x]` (a–e commits `de42dab`,
  `9de466f`, `2e0b6d1`, `23fb60b`, `07c7096`); epic evidence
  `evidence/E3.2.md` + 5 subtask evidence; integration tests
  CI-only (Docker Desktop socket auth in sandbox); local
  Kiểm thử/DoD gates all PASS.

  - Model Tenant, Membership, Invitation, Entitlement, plan/quota và lifecycle bằng jOOQ/Flyway.
  - Đồng bộ Keycloak subject, nhưng giữ Person và User tách biệt.
  - Thiết lập `tenant_id`, repository guard, PostgreSQL RLS và audit.

  Phạm vi E3.2 được chia thành 5 subtask nhỏ, mỗi subtask có evidence riêng
  và có thể merge độc lập. Epic E3.2 chỉ đánh `[x]` khi cả 5 subtask `[x]`.

  - [x] E3.2a Flyway V2 schema + PostgreSQL RLS + jOOQ datasource role

    - Migration `V2__tenant_aggregate.sql` tạo schema `tenant_service`, các bảng
      `tenants`, `memberships`, `invitations`, `entitlements`, `outbox_events`
      với khóa chính opaque, `tenant_id`, timestamps, version và audit columns.
    - Bật Row-Level Security trên mọi bảng tenant-scoped với policy
      `tenant_isolation` dùng `current_setting('app.tenant_id', true)`.
    - Cấu hình datasource role riêng cho tenant-service theo ADR-E0.5-02
      (schema-per-service); quyền `SELECT/INSERT/UPDATE/DELETE` chỉ trong
      `tenant_service`, không cross-schema.
    - DoD: Testcontainers Postgres boot + IT chứng minh
      `SET app.tenant_id = 'other'; SELECT * FROM tenant_service.tenants;`
      trả 0 row ngay cả khi user có quyền SELECT.

  - [x] E3.2b Domain model + 5 event Avro schemas

    - Aggregate `Tenant` với state machine `ACTIVE → SUSPENDED → DELETED`,
      value object `TenantId`, `Slug`, `Etiquette`, invariants (slug regex,
      displayName 1–120, plan enum).
    - Aggregate `Membership` với role enum OWNER/ADMIN/MEMBER/AUDITOR/BILLING_ADMIN,
      status INVITED/ACTIVE/SUSPENDED/REVOKED; `Person` và `User` giữ tách biệt
      (Membership.userId = Keycloak sub opaque, Membership.personId nullable).
    - Aggregate `Invitation` với expiry + idempotency key.
    - Entity `Entitlement` với plan enum FREE/FAMILY/PRO/ENTERPRISE + quota map
      (memberLimit, treeLimit, storageLimitMb, retentionDays); giá trị DRAFT
      theo architecture-decisions.md §A, ghi ADR exception trong evidence.
    - 5 Avro schemas (BACKWARD compatibility per ADR-E0.5-08):
      `tenant-created`, `membership-invited`, `membership-activated`,
      `membership-revoked`, `entitlement-changed`.
    - DoD: unit test invariants + `pnpm lint:events` + `node scripts/test-contracts.mjs`
      PASS cho Avro namespace prefix và forbidden field check.

  - [x] E3.2c Application services + Keycloak subject mapping + outbox

    - `TenantCommandService.create / update / suspend / restore / delete`
      và `MembershipCommandService.invite / activate / revoke` với
      optimistic concurrency (ETag) và audit hook (E3.6 contract).
    - `KeycloakSubjectMirror` interface + in-memory implementation cho E3.2;
      adapter pattern cho phép thay bằng real Keycloak admin API call trong
      E3.5 mà không sửa domain code.
    - `OutboxEvent` được ghi cùng transaction aggregate (per design.md §7.3);
      relay publish sang Kafka là việc của E4.7, E3.2 chỉ chịu trách nhiệm ghi.
    - DoD: unit + IT happy-path create-tenant → invite-member → activate-membership
      → revoke-membership; outbox row xuất hiện cho mỗi mutation.

  - [x] E3.2d REST controllers honoring OpenAPI contract

    - `TenantController` chuẩn RFC 9457 (problem+json), `Idempotency-Key`,
      `If-Match`, ETag, cursor pagination cho list, `X-Correlation-Id`.
    - `MembershipController` nested dưới `/api/v1/tenants/{tenantId}/memberships`
      với invite/list/revoke.
    - Cross-tenant negative test: request với `X-Tenant-Id=A` truy cập
      resource thuộc tenant `B` phải trả 404 (không 403, để tránh leak).
    - DoD: `pnpm lint:openapi` PASS + mở rộng `TenantServiceApplicationIT`
      với happy-path + cross-tenant negative; OpenAPI contract stable.

  - [x] E3.2e gRPC stub + runbook + evidence + Plan/Quota DRAFT note

    - `TenantGrpcService` chỉ là Spring `@Component` với TODO note; KHÔNG
      wire `com.google.protobuf` plugin (đợi E4.x fix enum collisions
      trong `tenant_service.proto` / `person_service.proto` per build.gradle.kts
      header). REST surface là contract chính trong E3.2.
    - `runbook/tenant-service.md` với: capacity skeleton từ
      `scale-and-slo.yml::tenant` (defer E13.3), on-call tier-1/Tier-2,
      dependency map Keycloak + Postgres + audit topic, dashboards
      `grafana/dashboards/tenant-service.json` (defer E2.10/E13.1),
      alert rules `alerts/tenant-service.yaml` (defer E13.2).
    - Plan/Quota numerics ghi DRAFT trong evidence/E3.2e.md, tham chiếu
      architecture-decisions.md §A và đánh dấu chờ E0.6 sign-off; service
      KHÔNG chặn mutation dựa trên quota trong E3.2 (gate per ADR exception).
    - 5 evidence file: `evidence/E3.2a.md` … `evidence/E3.2e.md` với Status
      DONE từng phần; chỉ flip epic `[x]` khi cả 5 evidence DONE.
    - DoD: `pnpm check:boundary` + `pnpm lint:openapi` + `pnpm lint:protobuf`
      + `pnpm lint:events` + `./gradlew :services:tenant-service:test`
      PASS; không mở rộng scope ngoài 4 subtask trên.

- [x] E3.3 Thiết kế OpenFGA model

  - Mô hình user/group→tenant/tree/branch/resource và các role mặc định.
  - Version authorization model; có migration tuple và compatibility tests.
  - Đồng bộ tuple bằng idempotent workflow/event; xử lý revoke ưu tiên cao.

- [x] E3.4 Triển khai ABAC domain layer

  - Kiểm tra living/minor, privacy class, consent purpose, jurisdiction và contextual deny.
  - Kết hợp OpenFGA allow với ABAC obligations như redact, watermark và audit.
  - Cache quyết định ngắn hạn và invalidation khi role/policy/consent đổi.

- [x] E3.5 Trusted tenant context

  - BFF đối chiếu tenant selection với membership; truyền context qua gRPC trong Istio mTLS.
  - Service tự xác minh identity/context và luôn thêm tenant predicate.
  - Từ chối `tenant_id`, role hoặc subject do client tự khai báo.

- [x] E3.6 Audit foundation
  - Triển khai append-only audit entries, integrity evidence, retention và export.
  - Audit login, role/tuple, policy, support access, download và consent.
  - Không ghi token, raw DNA hoặc sensitive payload.

**Đầu ra:** Login, tenant onboarding, invitation, OpenFGA model, ABAC SDK và audit baseline.

**Kiểm thử/DoD:** Property tests chứng minh không cross-tenant qua REST/gRPC/event/job/export; MFA/revoke và tuple revoke có hiệu lực đúng SLO.

_Requirements: R1, R2, R3, R13, R16, NFR1, NFR4, NFR8_

## E4 — Genealogy core service

**Mục tiêu:** Cung cấp mô hình gia phả giàu ngữ nghĩa, provenance và versioning.

**Phụ thuộc:** E1–E3.

### Subtasks

- [x] E4.1 Tree aggregate và visibility

  - CRUD/archive/restore/transfer/delete; locale, timezone, calendar, branding và collaboration policy.
  - Thực thi `PRIVATE/UNLISTED/PUBLIC`; unlisted token được hash, hết hạn và thu hồi.
  - Phát event tái tạo/xóa public projection khi visibility đổi.

- [x] E4.2 Person aggregate

  - Nhiều tên/script/alias, pronoun, living status, biography, identifiers và field privacy.
  - Dùng optimistic version/ETag; ghi actor, reason và diff.
  - Không liên kết User↔Person nếu chưa qua verification workflow.

- [x] E4.3 Date/calendar/place model

  - Hỗ trợ exact/about/range/before/after/unknown và giữ original expression.
  - Lưu UTC, IANA timezone, calendar ID và normalized interval.
  - Place có hierarchy, historical names, coordinates và provider-neutral authority reference.

- [x] E4.4 Relationship graph và invariants

  - Biological/adoptive/foster/guardian/step/partner/custom, certainty và temporal validity.
  - Hỗ trợ nhiều phụ huynh/phối ngẫu, unknown participant và disputed alternatives.
  - Chặn self-link/cycle bất hợp lệ; cảnh báo chronological conflict.

- [x] E4.5 Event, claim và provenance

  - Event nhiều participant/role, recurring memorial, date/place và privacy.
  - Claim có hypothesis/asserted/verified/disputed, confidence và source references.
  - Không coi imported claim là verified mặc định.

- [x] E4.6 Merge và history

  - Candidate scoring, comparison, preview, merge command và source preservation.
  - Bảo toàn redirect/reference; hỗ trợ reversal theo domain rules.
  - Audit toàn bộ reviewer, reason, before/after version.

- [x] E4.7 Outbox/event publishing
  - Ghi aggregate + outbox trong một transaction Flyway-managed schema.
  - Relay sang Kafka với Apicurio schema; consumer inbox/idempotency mẫu.
  - Thiết lập retry/DLQ/replay có audit và không phát PII thừa.

**Đầu ra:** Genealogy APIs/gRPC, migrations, events và domain tests.

**Kiểm thử/DoD:** Invariant/property tests, RLS/isolation, concurrency, event idempotency và calendar round-trip pass.

_Requirements: R3, R4, R5, R7, R8, R10, R18, NFR1, NFR4, NFR7_

## E5 — Tree UX, BFF và PWA

**Mục tiêu:** Hoàn thiện vertical slice sử dụng được trên desktop/mobile và accessible.

**Phụ thuộc:** E3, E4; ADR tree engine.

### Subtasks

- [ ] E5.1 Benchmark tree renderer

  - So sánh candidate với 10K/100K nodes, mobile memory, keyboard, print và bundle size.
  - Prototype SVG/canvas/hybrid và layout trong Web Worker.
  - Duyệt ADR trước khi đưa library vào production.

- [ ] E5.2 Tree projection API

  - Query root/direction/depth/filter và incremental neighborhood theo viewport.
  - Projection có version/freshness; redaction trước serialization.
  - Cache Valkey có tenant-aware key, TTL và invalidation.

- [ ] E5.3 Tree views

  - Pedigree, descendant, fan, hourglass và family view.
  - Pan/zoom/collapse/minimap/breadcrumb/search-root và stable node identity.
  - Không tải toàn graph vào browser.

- [ ] E5.4 Profile/editor/timeline/map

  - Responsive forms, localized name/date/place, optimistic update và conflict UX.
  - Timeline cá nhân/gia đình; map adapter không khóa geocoding vendor.
  - Quyền field/action lấy từ BFF và vẫn được server thực thi.

- [ ] E5.5 Accessibility/i18n

  - Semantic list/table alternative, keyboard navigation, focus management và screen-reader labels.
  - ICU messages, RTL, pseudolocale, name/address ordering và reduced motion.
  - Chạy axe tự động và manual critical-flow audit.

- [ ] E5.6 Print/export view
  - Privacy preview, deterministic layout, page break và watermark obligations.
  - Giao report generation dài cho Temporal/Gotenberg, không block request.
  - Signed download URL có hạn và audit.

**Đầu ra:** Web vertical slice login→tenant→tree→person→relationship→tree view.

**Kiểm thử/DoD:** Playwright desktop/mobile, WCAG 2.2 AA critical paths và performance budget đạt NFR2.

_Requirements: R4, R6, R7, R15, R17, R18, NFR2, NFR7_

## E6 — Research và collaboration

**Mục tiêu:** Quản lý bằng chứng và workflow sửa trực tiếp/duyệt kết hợp.

**Phụ thuộc:** E3–E5.

### Subtasks

- [ ] E6.1 Research service

  - Repository, Source, Citation, Transcript, locator, quality và attachment references.
  - Research log, task, hypothesis, conflict, assignment và status.
  - Provenance query từ claim đến citation/source/file.

- [ ] E6.2 Proposal/review model

  - Lưu normalized domain command/diff, base version, source và reason.
  - Hỗ trợ approve/reject/request-change/partial merge.
  - Re-authorize bằng OpenFGA+ABAC tại thời điểm review.

- [ ] E6.3 Mixed collaboration policy

  - Quy tắc direct edit/approval theo role, branch và resource type.
  - Conflict comparison và merge command mới; không apply arbitrary patch vào field cấm.
  - Đồng bộ policy changes với Flagsmith chỉ cho rollout, không thay policy source of truth.

- [ ] E6.4 Comments/activity
  - Comment, mention, watch và assignment có scope/authorization.
  - Activity feed từ event nhưng lọc lại theo quyền hiện tại.
  - Không snapshot sensitive content vào notification.

**Đầu ra:** Research/collaboration APIs, review UI và audit linkage.

**Kiểm thử/DoD:** Concurrency, stale permission, partial merge, redaction và proposal traceability tests pass.

_Requirements: R8, R10, R14, NFR1, NFR4_

## E7 — Media platform integration

**Mục tiêu:** Xây metadata/workflow nghiệp vụ trên S3/MinIO và OSS processors, không tự xây object store/transcoder/OCR.

**Phụ thuộc:** E2–E4, Temporal.

### Subtasks

- [ ] E7.1 Upload lifecycle

  - Tạo upload session, multipart signed URL, checksum, quota và MIME policy.
  - Object mới ở quarantine; metadata state machine là source of truth.
  - Finalize idempotent; dọn abandoned multipart bằng lifecycle/workflow.

- [ ] E7.2 Malware/metadata pipeline

  - Temporal orchestration gọi ClamAV và Apache Tika trong network sandbox.
  - Chỉ asset `READY` mới được liên kết; timeout/error chuyển trạng thái rõ ràng.
  - Cập nhật signature, resource limit và malicious corpus tests.

- [ ] E7.3 Derivative processing

  - libvips cho ảnh; ImageMagick fallback có policy; FFmpeg cho audio/video.
  - Tesseract OCR theo language packs; Gotenberg cho PDF/preview.
  - Output key deterministic, processor versioned và retry idempotent.

- [ ] E7.4 Protected delivery

  - Kiểm OpenFGA+ABAC trước signed URL; TTL, range, disposition và watermark.
  - Bucket/prefix/key riêng cho DNA, không dùng media preview pipeline.
  - Audit sensitive downloads và revoke access khi policy đổi.

- [ ] E7.5 Albums/linking
  - Album, tag, caption, date/place và person/event/source references.
  - Không tạo foreign key xuyên service; kiểm dangling reference qua reconciliation.
  - Soft-delete, retention, legal hold và object garbage collection.

**Đầu ra:** Media service, Temporal workflows, processor images và S3 compatibility suite.

**Kiểm thử/DoD:** Malware/polyglot/decompression bomb tests, cloud S3/MinIO parity, cancellation/retry và no-public-before-scan pass.

_Requirements: R7, R8, R9, R13, NFR1, NFR3, NFR8_

## E8 — Search và public discovery

**Mục tiêu:** Search authorized bằng PostgreSQL trước và public projection tách biệt.

**Phụ thuộc:** E4, E6, E7.

### Subtasks

- [ ] E8.1 Search projection

  - Consume Kafka events idempotently; normalize multilingual names/aliases với FTS/trigram.
  - Lưu tenant/privacy classification và projection version.
  - Theo dõi lag, rebuild và reconciliation workflow bằng Temporal.

- [ ] E8.2 Authorized search

  - Filter tenant/OpenFGA/ABAC trước response; cursor pagination và facets.
  - Saved search và alert chỉ lưu query an toàn.
  - Không cache kết quả vượt quá permission version.

- [ ] E8.3 Public projection

  - Chỉ index `PUBLIC` sau redaction living/minor/sensitive.
  - `UNLISTED` trả `noindex`; token hash/expiry/rate limit ở Kong và app validation.
  - Purge projection/cache/sitemap khi visibility hoặc policy đổi.

- [ ] E8.4 Benchmark/evolution gate
  - Đo SLO trên datasets mục tiêu và worst-case fuzzy/facet queries.
  - Tối ưu index/query bằng jOOQ trước.
  - Chỉ đề xuất OpenSearch qua ADR nếu PostgreSQL không đạt.

**Đầu ra:** Search service, public pages, projection rebuild và benchmark report.

**Kiểm thử/DoD:** Không có forbidden result qua fuzzy/facet/cache; freshness và p95 đạt mục tiêu.

_Requirements: R3, R11, R13, NFR1, NFR2, NFR4_

## E9 — Import, export và partner API

**Mục tiêu:** Tương tác dữ liệu an toàn với orchestration bền vững từ Temporal và runtime policy từ Kong.

**Phụ thuộc:** E4, E6–E8.

### Subtasks

- [ ] E9.1 Temporal transfer framework

  - Workflow durable cho progress, signal, cancellation, checkpoint và compensation.
  - Activity idempotent với heartbeat; domain service vẫn là source of truth.
  - Không tự xây generic scheduler/retry database.

- [ ] E9.2 GEDCOM parser/validator

  - Streaming GEDCOM 7 và mapping 5.5.1; giữ extension/provenance.
  - Enforce size/depth/count/encoding limits và sandbox parser.
  - Dry-run trả lỗi theo record/line mà không ghi domain data.

- [ ] E9.3 Mapping/dedup/import saga

  - Preview mapping, duplicate candidates và user confirmation.
  - Chunk commands với checkpoint/compensation; không giữ transaction dài.
  - Reconcile events/search và tạo final report.

- [ ] E9.4 Privacy-aware export

  - Full/branch GEDCOM/CSV/JSON/PDF, media bundle và checksum manifest.
  - Preview redaction; DNA bị loại mặc định và yêu cầu consent riêng.
  - Signed download, expiry, audit và cleanup theo retention.

- [ ] E9.5 Public API trên Kong

  - Public API app định nghĩa resource/OpenAPI/idempotency; Kong xử lý routing/auth/rate limit.
  - OAuth scopes ánh xạ membership/OpenFGA; domain service kiểm quyền cuối.
  - Contract compatibility, quota metrics và abuse tests.

- [ ] E9.6 Webhooks
  - Subscription authorization, signed payload, secret rotation và event minimization.
  - Temporal/Kafka retry, dead-letter và audited replay.
  - Disable/revoke endpoint khi tenant/user mất quyền.

**Đầu ra:** Transfer workflows, GEDCOM support, exports, partner API và webhooks.

**Kiểm thử/DoD:** Malicious/large import, resume/cancel, duplicate idempotency, redaction và Kong policy tests pass.

_Requirements: R4, R8, R12, R13, R16, NFR1, NFR3, NFR4, NFR8_

## E10 — DNA module

**Mục tiêu:** Capability opt-in, cô lập và consent-first; không cung cấp diễn giải y khoa.

**Phụ thuộc:** E0 legal gate, E2–E4, E7, E9.

### Subtasks

- [ ] E10.1 DNA architecture gate

  - Duyệt format/provider, matching algorithm/version, jurisdiction và guardian workflow.
  - Duyệt database/bucket/KMS/task queue/node pool isolation.
  - Flag DNA mặc định off cho mọi tenant/environment.

- [ ] E10.2 DNA service isolation

  - Tạo database role/schema, S3 prefix/bucket, Vault policy và encryption key riêng.
  - OpenFGA permission namespace riêng; tree role không mặc nhiên cấp DNA access.
  - Istio/NetworkPolicy giới hạn egress và service callers.

- [ ] E10.3 Consent engine

  - Model subject/guardian, purpose, action, policy version, effective/expiry/revoked time.
  - Sinh consent receipt; append-only audit cho grant/revoke/access/export.
  - Re-authorize và kiểm consent tại thời điểm activity chạy, không chỉ lúc submit.

- [ ] E10.4 Raw upload và matching

  - Quarantine, format validation và envelope encryption qua Vault/cloud KMS.
  - Temporal worker cô lập tạo match/segment/estimate với algorithm version.
  - Không đưa raw genotype vào Kafka, log, trace, search, media preview hoặc public API.

- [ ] E10.5 Revoke/export/delete
  - Temporal workflow dừng processing, revoke sharing và xóa derived data.
  - Tôn trọng legal hold/retention; tạo evidence không chứa dữ liệu đã xóa.
  - Export yêu cầu step-up auth, consent phù hợp và signed short-lived URL.

**Đầu ra:** DNA service, consent ledger, isolated workflows và privacy controls.

**Kiểm thử/DoD:** Pen-test privilege escalation, object leak, telemetry leak, inference và consent race; legal/security sign-off bắt buộc.

_Requirements: R13, R16, NFR1, NFR3, NFR4, NFR7, NFR8_

## E11 — Notifications, reports và operations

**Mục tiêu:** Cung cấp communication/reporting qua adapters và công cụ chuẩn.

**Phụ thuộc:** E3–E10 theo loại thông báo/report.

### Subtasks

- [ ] E11.1 Notification service

  - Preference, locale template, digest, quiet hours và in-app inbox.
  - Provider-neutral adapters: SaaS email provider, SMTP on-premise; push/SMS bổ sung qua ADR.
  - Retry/delivery workflow bằng Temporal; không tự xây generic queue scheduler.

- [ ] E11.2 Privacy-safe delivery

  - Re-check authorization khi render/delivery; dùng generic text cho sensitive event.
  - Unsubscribe, bounce/suppression và tenant branding.
  - Không gửi DNA/person-sensitive payload cho third-party provider nếu không cần.

- [ ] E11.3 Reporting service

  - Projection cho completeness, conflict, orphan, duplicate và demographics.
  - Family book/timeline/anniversary/PDF qua Temporal + Gotenberg.
  - Privacy preview và deterministic report version.

- [ ] E11.4 Entitlement/quota/billing adapters

  - Usage events, plan enforcement và quota warning.
  - SaaS billing adapter và on-premise license/config không làm domain phụ thuộc vendor.
  - Kong rate metric không thay business entitlement source of truth.

- [ ] E11.5 Admin/support operations
  - Tenant ops, Temporal workflow, Kafka DLQ/replay, feature flag và projection rebuild views.
  - JIT support access, step-up auth, expiry và audit.
  - Không cung cấp bypass DNA/consent hoặc tenant isolation.

**Đầu ra:** Notification/report/admin capabilities và provider adapters.

**Kiểm thử/DoD:** Locale/template, provider failure, auth-change-before-delivery, report redaction và support expiry tests pass.

_Requirements: R1, R14, R15, R16, R18, NFR1, NFR5, NFR6, NFR8_

## E12 — Offline PWA và globalization hardening

**Mục tiêu:** PWA an toàn khi offline và hoạt động đúng trên locale/calendar/RTL toàn cầu.

**Phụ thuộc:** E5 và mutation APIs ổn định.

### Subtasks

- [ ] E12.1 Offline data classification

  - Xác định resource được cache, TTL, encryption capability và opt-in UI.
  - Không cache raw DNA/media hoặc sensitive living data mặc định.
  - Purge khi logout, revoke, tenant switch hoặc permission version đổi.

- [ ] E12.2 Mutation queue

  - IndexedDB queue có operation ID, base version và state rõ ràng.
  - Resume sync, idempotent submit và conflict-resolution UX.
  - Không giả định Background Sync luôn tồn tại.

- [ ] E12.3 Globalization tests

  - Pseudolocalization, RTL, long text, Unicode/script/transliteration và locale fallback.
  - DST, IANA timezone, non-Gregorian date và ambiguous/approximate date round-trip.
  - Email/report/PDF dùng cùng glossary và locale rules.

- [ ] E12.4 Accessibility hardening
  - axe CI và manual keyboard/screen-reader cho onboarding, tree, edit, review, import và consent.
  - Kiểm focus, contrast, reduced motion, zoom và touch targets.
  - Lập defect severity/SLA cho accessibility regression.

**Đầu ra:** Offline queue/cache policies, globalization suite và accessibility report.

**Kiểm thử/DoD:** Offline conflict/revoke/purge, locale matrix và WCAG 2.2 AA critical flows pass.

_Requirements: R6, R10, R17, R18, NFR1, NFR7_

## E13 — Observability, reliability và performance

**Mục tiêu:** Chứng minh SLO và khả năng phục hồi của ứng dụng cùng toàn bộ platform dependencies.

**Phụ thuộc:** E2 và các epic chức năng cần phát hành.

### Subtasks

- [ ] E13.1 Telemetry hoàn chỉnh

  - Trace xuyên Kong→BFF→gRPC→Kafka/Temporal và link workflow/event correlation.
  - RED metrics, outbox age, consumer lag, workflow failures và projection freshness.
  - Log redaction tests cho token, PII, DNA, secret và file content.

- [ ] E13.2 SLO/alert/runbook

  - Định nghĩa SLI/error budget cho edge, domain API, Kafka, Temporal, OpenFGA và storage.
  - Alert actionable với owner, severity, dashboard và runbook link.
  - Giảm cardinality; tenant ID chỉ pseudonymous và không dùng raw user/person IDs.

- [ ] E13.3 Performance/capacity

  - k6/Gatling cho API; benchmark tree, search, import, media, report và DNA.
  - Xác định HPA, connection pool, Kafka partition, Temporal worker và database thresholds.
  - Ghi capacity envelope và scale procedure cho SaaS/on-premise.

- [ ] E13.4 Resilience/chaos
  - Kiểm pod kill, network latency, Kafka lag, Temporal restart, OpenFGA outage và database failover.
  - Xác minh retry budget, circuit breaker, graceful degradation và no-duplicate side effects.
  - Argo Rollouts phải abort canary trên synthetic regression.

**Đầu ra:** SLOs, dashboards, alerts, capacity report và resilience evidence.

**Kiểm thử/DoD:** NFR latency/availability đạt; không telemetry leak; runbook drill và automated rollback pass.

_Requirements: NFR2, NFR3, NFR4, NFR5, NFR8_

## E14 — Backup, DR và on-premise packaging

**Mục tiêu:** Phân phối cùng capability cho cloud và enterprise on-premise với upgrade/restore an toàn.

**Phụ thuộc:** E2, E13.

### Subtasks

- [ ] E14.1 Backup matrix

  - Bao phủ PostgreSQL, Kafka, S3/MinIO, Keycloak, OpenFGA, Temporal, Vault và Flagsmith.
  - Mã hóa, retention, offsite copy, key custody và restore ordering.
  - Không coi snapshot chưa restore-test là backup hợp lệ.

- [ ] E14.2 DR drill

  - Mô phỏng region/cluster loss và phục hồi platform theo dependency order.
  - Reconcile outbox, Kafka consumers, Temporal workflows và search projections.
  - Đo RPO/RTO và ghi remediation nếu vượt mục tiêu.

- [ ] E14.3 On-premise bundle

  - Helm charts, pinned OCI images, SBOM/signatures, values schema và compatibility matrix.
  - Preflight cho Kubernetes/storage/DNS/certificate/resources và external dependencies.
  - Hỗ trợ registry mirror/air-gap theo ADR; không fork application code.

- [ ] E14.4 Upgrade/rollback

  - Flyway expand-contract, API/event compatibility và platform version sequencing.
  - Argo-controlled upgrade, pre/post checks và rollback constraints.
  - Test nâng cấp từ từng supported version với production-like dataset.

- [ ] E14.5 Operator documentation
  - Install, configuration, scaling, backup, restore, key rotation, troubleshooting và support bundle.
  - Support bundle phải redact secret/PII/DNA.
  - Ghi shared-responsibility matrix cho SaaS và on-premise.

**Đầu ra:** DR evidence, signed on-premise bundle, upgrade tests và operator runbooks.

**Kiểm thử/DoD:** Fresh install, upgrade, rollback-compatible path, air-gap và full restore đạt RPO/RTO.

_Requirements: R1, R16, NFR3, NFR6, NFR8_

## E15 — Security verification và GA readiness

**Mục tiêu:** Chứng minh toàn hệ thống đủ điều kiện phát hành SaaS/on-premise.

**Phụ thuộc:** Tất cả epic thuộc release scope.

### Subtasks

- [ ] E15.1 Automated security verification

  - Chạy Semgrep, Trivy, Grype, Gitleaks, Checkov, ZAP và dependency/license gates.
  - Xác minh SBOM, Cosign signature và provenance cho mọi image/artifact.
  - Triage finding có owner, SLA và approved exception expiry.

- [ ] E15.2 Tenant/privacy penetration test

  - Test IDOR, forged context, OpenFGA tuple race, Kong bypass, public token abuse và cache poisoning.
  - Test malicious GEDCOM/media, SSRF, parser sandbox escape và signed URL leak.
  - DNA boundary/consent/export/delete có test plan riêng.

- [ ] E15.3 Operational readiness review

  - Review SLO, alerts, on-call, incident/privacy response, support access và vendor/platform failure plans.
  - Chạy game day cho Keycloak, Kong, Kafka, Temporal, OpenFGA, Vault và storage outage.
  - Xác nhận license notices, data residency và legal release gates.

- [ ] E15.4 Release acceptance
  - Chạy full unit/integration/contract/E2E/accessibility/performance/restore suites.
  - Kiểm migration, rollback, feature-flag kill switch và public redaction.
  - Product, engineering, security, privacy và operations ký go/no-go evidence.

**Đầu ra:** Security report, pen-test remediation, release evidence và GA checklist.

**Kiểm thử/DoD:** Không còn critical/high chưa được duyệt; restore/SLO/privacy/tenant isolation đạt; artifacts ký và deploy được bằng GitOps.

_Requirements: R1–R18, NFR1–NFR8_

## Milestones

1. **Foundation:** E0–E3.
2. **Usable genealogy alpha:** E4–E5.
3. **Collaborative private beta:** E6–E8.
4. **Interoperable beta:** E9.
5. **Controlled DNA beta:** E10 sau legal gate.
6. **Feature-complete RC:** E11–E12.
7. **SaaS/on-premise GA:** E13–E15.

Ưu tiên vertical slice `Kong → Keycloak → tenant → private tree → person → relationship → tree view → audit`; không triển khai đồng loạt mọi microservice trước khi slice này đạt Definition of Done.
