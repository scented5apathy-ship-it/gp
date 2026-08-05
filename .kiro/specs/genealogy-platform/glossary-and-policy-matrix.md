# E0.2 — Glossary and Policy Matrix

Status: Accepted for downstream discovery (E0.5 ADR closure, E0.6 ownership, E1 contracts, E3 identity, E4 genealogy, E5 sharing, E10 DNA, E11 audit).
Scope: Stabilise the canonical vocabulary used across requirements, design and implementation epics, and encode the policy decisions that govern visibility, living-person handling, redaction, guardianship, merge, uncertainty and direct-edit/approval. This document produces decision tables that E3, E4, E5, E7, E10 and E11 will implement as OpenFGA tuples + ABAC obligations.
Out of scope: Scale/SLO numbers (E0.3), privacy/legal gate and DPIA (E0.4), ADR closure (E0.5), ownership RACI (E0.6). Specific jurisdiction rules are absorbed from E0.4 once that gate is complete; the present document only records the jurisdictional hooks that the glossary/policy must respect.

## 1. Glossary

Every glossary term carries: canonical English name, Vietnamese equivalent, definition, owner service, persistence location, traceability to requirements/design, and a status flag (`STABLE` = consumable by E1 contracts, `DRAFT` = open refinement). All terms are tenant-scoped unless explicitly noted.

### 1.1 Identity and tenancy

#### Tenant

- **VN**: Tổ chức/khách thuê.
- **Definition**: The top-level isolation boundary. Owns all users, trees, media, audit, billing and quotas. Activated through OIDC (Keycloak) and a signed tenant context propagated by BFF.
- **Owner**: `tenant-service`.
- **Persistence**: `tenant.tenant` aggregate; tenant identity is an opaque ULID (R6.1).
- **Status**: STABLE.
- **References**: R1, R1.4, R1.5, NFR1, NFR4, Design §1, §6.1.

#### User

- **VN**: Người dùng.
- **Definition**: An authenticated human or service principal that logs in via OIDC. A User exists once across tenants only if cross-tenant federation is in use; per tenant there is a `Membership` binding the User to a tenant role.
- **Owner**: `tenant-service` (identity), keycloak (credentials).
- **Persistence**: No business data of its own; credentials in Keycloak; `tenant.membership` stores `{tenant_id, user_id, role, status}`.
- **Status**: STABLE.
- **References**: R2, R10.4, R16.4, Design §4.2.

#### Membership

- **VN**: Tư cách thành viên.
- **Definition**: Authoritative binding between User and Tenant with current effective role. Revocable. Last write wins; UI must show the server-effective role, never the cached persona (R10.4).
- **Owner**: `tenant-service`.
- **Persistence**: `tenant.membership`, append-only history in `tenant.membership_history`.
- **Status**: STABLE.
- **References**: R1.2, R10.4, E3.4.

#### Anonymous Visitor

- **VN**: Khách vãng lai.
- **Definition**: A non-authenticated principal that interacts with the public projection only. They have no User record and no Tenant membership.
- **Owner**: Implicit (no service owns anonymous identifiers; abuse signals go to `audit-service`).
- **References**: R3.6, NFR1, personas §1.11.

### 1.2 Genealogy core

#### Tree (Genealogy)

- **VN**: Gia phả / cây gia phả.
- **Definition**: A genealogical dataset belonging to one Tenant. Has metadata (slug, locale, timezone, default calendar, name conventions, collaboration policy), visibility setting, branches and a version counter.
- **Owner**: `genealogy-service`.
- **Persistence**: `genealogy.tree`, `genealogy.tree_branch`.
- **Status**: STABLE.
- **References**: R3, R5, R6, Design §5.2.

#### Branch

- **VN**: Nhánh.
- **Definition**: A scoping unit anchored at a root Person, used to delegate subtree authority (e.g. an Editor manages a single branch). A Tree has at least one implicit root branch.
- **Owner**: `genealogy-service`.
- **Persistence**: `genealogy.tree_branch` `{tree_id, root_person_id, name, scope}`.
- **Status**: STABLE.
- **References**: R3.1, R10.4, Design §5.4.

#### Person

- **VN**: Cá nhân.
- **Definition**: A historical or living individual represented in a Tree. A Person has zero or more User links but is never identical to a User (R6.3). Has a `living_status` field with values: `LIVING`, `DECEASED`, `PRESUMED_LIVING`, `PRESUMED_DECEASED`, `UNKNOWN`.
- **Owner**: `genealogy-service`.
- **Persistence**: `genealogy.person` with `tenant_id`, `tree_id`, `living_status`, `birth_date_range`, `death_date_range`, `privacy_level`.
- **Status**: STABLE.
- **References**: R4, R5, R6.3, E4.1.

#### UserLink

- **VN**: Liên kết người dùng.
- **Definition**: Verified binding between a User and a Person they claim to be. Verified by email + identity proof (R2.4). Without verification, the link is `UNVERIFIED` and does not unlock self-claim privileges.
- **Owner**: `genealogy-service` (link), `tenant-service` (verification token).
- **Persistence**: `genealogy.person_user_link` `{person_id, user_id, status, verified_at, evidence_ref}`.
- **Status**: STABLE.
- **References**: R2.4, R10.4, E3.4.

#### Relationship

- **VN**: Quan hệ.
- **Definition**: A typed, temporal, evidence-bearing association between two or more Persons through `RelationshipParticipant`. Types include `BIOLOGICAL_PARENT`, `ADOPTIVE_PARENT`, `FOSTER_PARENT`, `GUARDIAN`, `STEP_PARENT`, `SPOUSE`, `PARTNER`, `CUSTOM`.
- **Owner**: `genealogy-service`.
- **Persistence**: `genealogy.relationship`, `genealogy.relationship_participant`.
- **Status**: STABLE.
- **References**: R5, R5.1, R5.3, Design §5.2.

#### LifeEvent

- **VN**: Sự kiện đời.
- **Definition**: An event in a Person's life (birth, death, marriage, residence, migration, education, military, religion, custom). Uses `DateExpression` and `Place`. Can involve multiple Persons via `EventParticipant`.
- **Owner**: `genealogy-service`.
- **Persistence**: `genealogy.life_event`, `genealogy.event_participant`, `genealogy.date_expression`, `genealogy.place`.
- **Status**: STABLE.
- **References**: R7, Design §5.3.

#### Claim

- **VN**: Khẳng định.
- **Definition**: An assertion about a Person, Relationship or LifeEvent. Has a `certainty` (`HYPOTHESIS`, `ASSERTED`, `VERIFIED`, `DISPUTED`), provenance, and is supported by zero or more Citations. Claims are first-class so multiple hypotheses can co-exist (R5.5).
- **Owner**: `genealogy-service` (fact claims), `research-service` (research claims).
- **Persistence**: `genealogy.claim` with `claim_status`, `confidence`, `subject_ref`, `evidence_summary`.
- **Status**: STABLE.
- **References**: R5.5, R8, R10.1, Design §5.3.

#### Citation

- **VN**: Trích dẫn.
- **Definition**: A reference to a Source that supports a Claim. Carries page/locator, URL, transcription snippet, quality assessment and a hash of the underlying evidence.
- **Owner**: `research-service`.
- **Persistence**: `research.citation`, `research.citation_link`.
- **Status**: STABLE.
- **References**: R8, R8.2, R8.4.

#### Source

- **VN**: Nguồn.
- **Definition**: Repository entry (book, archive, oral interview, online database, GEDCOM file). Classified by quality tier, jurisdiction and licensing.
- **Owner**: `research-service`.
- **Persistence**: `research.source`, `research.repository`.
- **Status**: STABLE.
- **References**: R8, R8.1.

#### MergeRecord

- **VN**: Bản ghi hợp nhất.
- **Definition**: A traceable merge of two Persons (or a duplicate into a master). Stored as a `MergeRecord` aggregate that links the source-of-truth switch, the audit trail, the proposal that triggered it (if any) and a revert path.
- **Owner**: `genealogy-service`.
- **Persistence**: `genealogy.merge_record` (append-only), never destroyed.
- **Status**: STABLE.
- **References**: R4.5, E4.5.

### 1.3 Privacy and consent

#### LivingStatus

- **VN**: Trạng thái sống.
- **Definition**: Server-computed classification of a Person. Values: `LIVING`, `DECEASED`, `PRESUMED_LIVING`, `PRESUMED_DECEASED`, `UNKNOWN`. Computed via the inference rule in §2.1.
- **Owner**: `genealogy-service` (computation), `tenant-service` (configuration of the inference window).
- **Status**: STABLE.
- **References**: R3.6, R4.4, R6.3, E4.1.

#### Minor

- **VN**: Trẻ vị thành niên.
- **Definition**: A Person whose `age_years` is below the jurisdictional `minority_age` threshold. Until jurisdiction is set, the platform applies a `DEFAULT_MINORITY_AGE = 18` fallback that can be overridden per tenant.
- **Owner**: `tenant-service` (config), `genealogy-service` (annotation).
- **Status**: STABLE (default), with a hook for E0.4 jurisdictional overrides.
- **References**: R13.8, GDPR Art 8, COPPA, common-law majority.

#### MinorSensitiveField

- **VN**: Trường nhạy cảm của trẻ vị thành niên.
- **Definition**: Any field flagged in `genealogy.person_field_privacy` with `sensitivity = MINOR_RESTRICTED`. Examples: residence, contact info, school, medical conditions, photographs that could identify the minor in a public space.
- **Owner**: `genealogy-service`.
- **Status**: DRAFT — the field list is finalized in E4.4; this glossary reserves the term.
- **References**: R4.4, R13.8.

#### PrivacyLevel

- **VN**: Cấp độ riêng tư.
- **Definition**: Per-field classification `PUBLIC_OK`, `MEMBERS_ONLY`, `EDITOR_ONLY`, `OWNER_ONLY`, `REDACTED`. Inheritance is field-level, not record-level (R4.4).
- **Owner**: `genealogy-service`.
- **Status**: STABLE.
- **References**: R4.4, R6.

#### Visibility

- **VN**: Chế độ hiển thị cây.
- **Definition**: Tree-wide setting: `PRIVATE`, `UNLISTED`, `PUBLIC`. Combined with `PrivacyLevel` and `LivingStatus` to produce effective ACL (see §3).
- **Owner**: `genealogy-service` (state), `search-service` (projection).
- **Status**: STABLE.
- **References**: R3.3–R3.6, R11.4, Design §6.3.

#### Consent

- **VN**: Sự đồng ý.
- **Definition**: A grant, by a subject (DNA owner, legal guardian, or jurisdictional authority) for a specific `purpose` over a specific `subject` for a `policy_version` with `effective_from`, `expiry`, and `revoked_at`. Required before any DNA collection, matching, sharing, research use or export (R13.3).
- **Owner**: `dna-service`.
- **Persistence**: `dna.consent` (append-only).
- **Status**: STABLE.
- **References**: R13.1–R13.4, Design §5.5.

#### Purpose

- **VN**: Mục đích xử lý.
- **Definition**: Taxonomy of legally recognised purposes: `MATCHING`, `RELATIVE_FINDING`, `RESEARCH`, `EXPORT`, `SHARING`, `DOWNLOAD`, `PUBLICATION`. Each purpose binds a Consent to a specific `action`.
- **Owner**: `dna-service` (taxonomy), `tenant-service` (locale label).
- **Status**: STABLE.
- **References**: R13.3, E10.3.

#### Guardian

- **VN**: Người giám hộ.
- **Definition**: A User authorised to act on behalf of a DNA subject who cannot consent themselves (minor, legally incapacitated, deceased with active research consent). Bound to a jurisdiction and a time-bound approval window (R13.8).
- **Owner**: `dna-service` (binding), `tenant-service` (identity verification).
- **Status**: STABLE.
- **References**: R13.8, personas §1.10.

#### RedactionPolicy

- **VN**: Chính sách che dữ liệu.
- **Definition**: A deterministic filter applied to responses based on `Role × Resource × LivingStatus × PrivacyLevel`. Defined per resource type and versioned alongside the schema that emits the response.
- **Owner**: cross-cutting (implemented in `shared/redaction` library used by every service).
- **Status**: STABLE.
- **References**: R3.6, R11.4, R13.7, Design §6.2.

### 1.4 Collaboration and governance

#### Role

- **VN**: Vai trò.
- **Definition**: A string key from a static role catalogue. Tenant roles: `OWNER`, `ADMIN`, `MEMBER`, `AUDITOR`, `BILLING_ADMIN`. Tree roles: `TREE_ADMIN`, `EDITOR`, `CONTRIBUTOR`, `VIEWER`, `GUEST`. Platform roles: `OPERATOR`, `AUDITOR`. Platform roles do not inherit tenant scopes.
- **Owner**: `tenant-service` (tenant), `genealogy-service` (tree), platform bootstrap.
- **Status**: STABLE.
- **References**: R3, R10.4, R16, Design §4.2.

#### CollaborationMode

- **VN**: Chế độ cộng tác.
- **Definition**: Per-tree mode regulating how writes are applied: `DIRECT_EDIT`, `APPROVAL_REQUIRED`, `MIXED` (configurable per role/branch/data class per R3.7, R10.4).
- **Owner**: `genealogy-service`.
- **Status**: STABLE.
- **References**: R3.7, R10.4, R10.5.

#### ChangeProposal

- **VN**: Đề xuất thay đổi.
- **Definition**: A controlled mutation request containing a normalised command, base version, diff, reason and citations. Used when the policy does not allow direct edits.
- **Owner**: `collaboration-service`.
- **Persistence**: `collab.change_proposal` with `status` (`OPEN`, `MERGED`, `REJECTED`, `NEEDS_REVISION`).
- **Status**: STABLE.
- **References**: R10.1–R10.3, R10.6.

#### Review

- **VN**: Phản biện.
- **Definition**: A decision on a ChangeProposal by an authorised person. Records `verdict`, `verifier_id`, `reason`, `timestamp`.
- **Owner**: `collaboration-service`.
- **Status**: STABLE.
- **References**: R10.2, R10.6.

#### AuditEntry

- **VN**: Bản ghi kiểm toán.
- **Definition**: Append-only, hash-chained record of any decision that affects identity, privacy, consent, DNA, billing, role, support access or destructive operation (R16.2).
- **Owner**: `audit-service`.
- **Status**: STABLE.
- **References**: R16.2, R16.3, Design §4.

### 1.5 Sharing, media, import/export

#### ShareToken

- **VN**: Mã chia sẻ.
- **Definition**: A bounded, revocable grant that allows a non-member to access an `UNLISTED` tree. Hash-stored, scoped, expirable, rate-limited, marked `noindex` (R3.5, Design §6.3).
- **Owner**: `genealogy-service`.
- **Persistence**: `genealogy.share_token` with hash + metadata. Never the raw token in the database.
- **Status**: STABLE.
- **References**: R3.5, R12.6.

#### PublicProjection

- **VN**: Hình chiếu công khai.
- **Definition**: A derived, redacted representation of a Tree used for `PUBLIC` and anonymous browsing. Built from the primary aggregate and re-built whenever Policy or redaction rules change (R6.6, Design §5.4).
- **Owner**: `search-service` + `genealogy-service`.
- **Status**: STABLE.
- **References**: R3.6, R6.6, R11.4, Design §5.4.

#### MediaAsset

- **VN**: Tài sản media.
- **Definition**: A persisted object (image, audio, video, PDF, document) with metadata, processing state (`QUARANTINED`, `READY`, `REJECTED`), ownership and privacy.
- **Owner**: `media-service`.
- **Status**: STABLE.
- **References**: R9, Design §4.

#### TransferJob

- **VN**: Tác vụ chuyển dữ liệu.
- **Definition**: An asynchronous GEDCOM/CSV/JSON/PDF import or export run. Includes mapping profile, dry-run report, progress and error log.
- **Owner**: `import-export-service`.
- **Status**: STABLE.
- **References**: R12.1–R12.5.

### 1.6 Cross-cutting

#### Resource

- **VN**: Tài nguyên.
- **Definition**: A server-owned entity subject to authorisation. Classified as `TREE`, `BRANCH`, `PERSON`, `RELATIONSHIP`, `EVENT`, `CLAIM`, `SOURCE`, `CITATION`, `MEDIA`, `DNA_KIT`, `CONSENT`, `CONSENT_PURPOSE`, `AUDIT_ENTRY`, `JOB`, `WEBHOOK`, `PROPOSAL`, `MATCH`.
- **Owner**: cross-cutting.
- **Status**: STABLE.
- **References**: Design §4, §6.

#### Action

- **VN**: Hành động.
- **Definition**: A verb from a static catalogue: `READ`, `LIST`, `CREATE`, `UPDATE`, `DELETE`, `SHARE`, `EXPORT`, `DOWNLOAD`, `ATTACH`, `APPROVE`, `REJECT`, `MATCH`, `GRANT_CONSENT`, `REVOKE_CONSENT`, `STEP_UP`, `IMPERSONATE`. Each action maps to a redaction obligation.
- **Owner**: cross-cutting.
- **Status**: STABLE.
- **References**: R3, R10, R13, Design §6.2.

#### ReasonCode

- **VN**: Mã lý do.
- **Definition**: A machine-readable policy diagnostic string returned in `allow/deny` decisions and Problem Details (e.g. `tenant_mismatch`, `living_redact`, `consent_missing`, `minor_restricted`, `policy_version_unknown`). Required for audit and observability.
- **Owner**: `audit-service` (registry).
- **Status**: STABLE.
- **References**: R16.2, NFR5.

## 2. Policy decisions

The matrix below encodes the rules that every domain service must enforce. Services cannot override these rules; they can only narrow them. Any widening is an ADR.

### 2.1 Living inference

Rule `LIVING_INFERENCE`:

1. If a Person has `death_date_range` whose latest end is in the past and `certainty = VERIFIED`, status = `DECEASED`.
2. If a Person has `death_date_range` with `certainty = HYPOTHESIS` or `ASSERTED`, status = `PRESUMED_DECEASED`.
3. If a Person has `birth_date_range` whose earliest start is more than `LIVING_INFERENCE_YEARS` (default `110`) in the past, status = `PRESUMED_DECEASED` or `DECEASED` depending on evidence.
4. If a Person has `birth_date_range` whose earliest start is ≤ `now`, status is `LIVING` until proven otherwise.
5. If only partial information exists, status defaults to `PRESUMED_LIVING`.
6. A change from `DECEASED` to `LIVING` (death record disputed) MUST trigger `J-TREE-2` reversal flow (E0.1 §3) and re-projection.
7. The inference window is per tenant configuration; the default is shared. Override MUST be approved by ADR.

All Person projections (read models, search documents, public projection, export bundle) must use the computed `LivingStatus`, not the stored `birth/death` dates alone.

References: R3.6, R4.4, R6.3, R13.7, Design §6.2.

### 2.2 Redaction

Rule `REDACTION`:

1. Every response builder (REST, gRPC, event payload, export, webhook) must apply a `RedactionPolicy` derived from `{Action, Resource, Role, LivingStatus, PrivacyLevel, JurisdictionHook}`.
2. Redaction is **field-level and adding-only**: a field can be elided, redacted, or marked `UNAVAILABLE_*`; it must never be partially truthy.
3. `PUBLIC` projection never carries: raw DNA, contact info, residence of living persons, places more precise than administrative level 2, dates of living persons that are more precise than year, citations that reveal living participants, media showing identifiable living persons without consent.
4. `UNLISTED` projection follows the same rules as `PUBLIC` plus token scoping.
5. Persona redaction filters MUST run inside the service that owns the aggregate; the BFF only applies coarse formatting. Cross-service redaction is forbidden.
6. Redaction obligations are reported in `audit.redaction_event` with `ReasonCode` and the count of fields dropped.
7. Logging: every log line is filtered by the same library; PII, raw DNA, file content, tokens MUST be excluded.

References: R3.6, R4.4, R11.4, R13.7, NFR1, NFR5, Design §6.2.

### 2.3 Guardian

Rule `GUARDIAN`:

1. A Guardian can act for a DNA subject only when (a) the subject is a Minor, OR (b) the subject has been declared legally incapacitated by an authoritative source, OR (c) the subject is deceased but a research consent was issued in advance by the subject before death.
2. Guardian authority is jurisdiction-bound: a jurisdiction mismatch between Guardian's verified identity and the subject's jurisdiction invalidates the action.
3. Guardian consent cannot grant a wider scope than the subject would have had themselves; the scope is the intersection of legal capacity and configured default scopes.
4. Guardian consent expires at the latest of: subject's majority date, end of legal incapacity, or `expiry` set on the consent.
5. Emancipation, legal capacity restoration, or subject reaching majority MUST auto-revoke all Guardian-held consents for the subject, with a 30-day grace period only for data retrieval (not for new actions).
6. Guardian workflow includes a verified-then-step-up step-up authz (R13.8).

References: R13.8, personas §1.10, E10.3.

### 2.4 Merge

Rule `MERGE`:

1. Two Persons are merge candidates when automated duplicate detection (exact-match on normalised identity + fuzzy on names + dates) returns a score above `MERGE_AUTO_THRESHOLD` (default `0.85`) or an editor approves the suggestion.
2. A merge always produces a MergeRecord capturing: source-of-truth switch, participants, proposal id (if any), `merged_at`, `merged_by`, pre-merge snapshot hash, and a `revert_path` (the inverse command).
3. The losing Person is retained as a tombstone (`status = MERGED`) with references to the winning Person; it is never `HARD_DELETE`d.
4. All Claims, Citations, Events, Media links, Relationships and Proposals must be re-keyed to the winning Person in the same transactional boundary.
5. Identifiers that were exposed externally (share tokens, public URLs) MUST be reissued and the old ones revoked.
6. Reverts are allowed for `MERGE_REVERT_WINDOW_DAYS` (default `30`) and must produce a new MergeRecord.
7. Citations are preserved verbatim; evidence is never destroyed by merge (R5.5, R8.5).

References: R4.5, R5.5, R8.5, E4.5.

### 2.5 Uncertainty

Rule `UNCERTAINTY`:

1. A Claim can have `certainty` ∈ {`HYPOTHESIS`, `ASSERTED`, `VERIFIED`, `DISPUTED`}.
2. Only `VERIFIED` claims reach the public projection; `ASSERTED` is shown only when the visibility and PrivacyLevel allow it; `HYPOTHESIS` and `DISPUTED` are visible only inside the tree and never in public projection.
3. Two competing `HYPOTHESIS` claims can coexist on the same target (e.g. conflicting biological parents).
4. Reports and exports must mark each claim with its `certainty`; the `MERGED`/`VERIFIED` badge cannot be shown without an attached citation.
5. A claim moves from `ASSERTED` to `VERIFIED` only when at least one Citation of `quality ≥ MEDIUM` is attached and reviewer authorisation is recorded.
6. A claim can move to `DISPUTED` only via a proposal or an editor action; the disposition must cite the conflicting evidence.

References: R5.5, R8.3, R8.5, R10.1.

### 2.6 Direct edit vs approval

Rule `COLLABORATION_MODE`:

1. A Tree has a `CollaborationMode` ∈ {`DIRECT_EDIT`, `APPROVAL_REQUIRED`, `MIXED`}.
2. `MIXED` resolves per role × resource × branch from a precedence table:
   - `TREE_ADMIN`, `EDITOR` on `PERSON`, `RELATIONSHIP`, `EVENT`, `CLAIM`, `SOURCE`, `CITATION`: `DIRECT_EDIT` unless the resource is `LIVING` or `MINOR_RESTRICTED`, in which case `APPROVAL_REQUIRED`.
   - `CONTRIBUTOR` on any genealogical resource: `APPROVAL_REQUIRED`.
   - `CONTRIBUTOR` on `MEDIA` upload: `DIRECT_EDIT` for own uploads; `APPROVAL_REQUIRED` for tagging living persons.
   - `TREE_ADMIN`, `EDITOR` on `MEDIA` owned by others: `APPROVAL_REQUIRED`.
   - `CONSENT`, `POLICY`, `VISIBILITY`, `ROLE`: always `APPROVAL_REQUIRED`; `OWNER` may also require dual control.
3. The mode is evaluated inside the service, never trusted from the client.
4. Every direct edit MUST be recorded in `audit.entry` with a `policy_version` token; policy changes invalidate the recorded token and require a re-validation (Design §6.2).
5. Approval workflows MUST respect `optimistic concurrency` (R10.3): if the base version has moved, the proposal becomes a conflict and the reviewer receives a comparison model.

References: R3.7, R10.1–R10.6, Design §6.2, §8.3.

### 2.7 Visibility decision

Rule `VISIBILITY`:

`VISIBILITY` is the tree-wide flag (`PRIVATE`, `UNLISTED`, `PUBLIC`). The effective ACL combines `VISIBILITY`, `Role`, `LivingStatus`, `PrivacyLevel`, `Resource`, `ConsentState` and `ShareToken` (when `UNLISTED`).

Decisions are returned as `{allow, deny, obligations: [redact, watermark, audit], reason_code}`.

The full decision table is in §3.

### 2.8 Consent formality

Rule `CONSENT_FORM`:

1. No DNA action is permitted without a `Consent` matching `(purpose, subject, action, policy_version, jurisdiction)` whose `effective_from ≤ now < expiry` and `revoked_at IS NULL`.
2. Each Consent can grant multiple `actions` for the same `purpose`; cross-purpose grants require separate Consents.
3. A `policy_version` mismatch denies the action with `ReasonCode = policy_version_unknown`.
4. Consent revocation is append-only; the revocation propagation budget is `60s` (J-DNA-1, E0.1).
5. Consent cannot widen the underlying visibility rule; if visibility is `PRIVATE`, only matched invitees may use the data, even with consent.
6. Consent ledger is read-only to anyone except `DNA_OWNER`, `GUARDIAN`, `AUDITOR`, and platform `OPERATOR` (via JIT).

References: R13.3, R13.4, R13.6, Design §5.5.

### 2.9 Reason code registry

A Reason Code is mandatory on every deny and on every obligation. Initial codes:

| Code                                | Meaning                                                 |
| ----------------------------------- | ------------------------------------------------------- |
| `tenant_mismatch`                   | Tenant in token does not match resource tenant          |
| `role_insufficient`                 | Role lacks the action                                   |
| `resource_scope_missing`            | Tree/branch/resource scope not granted                  |
| `living_redact`                     | Living person field hidden by redaction                 |
| `minor_restricted`                  | Minor field restricted                                  |
| `consent_missing`                   | Consent not present or expired                          |
| `consent_revoked`                   | Consent revoked before action                           |
| `consent_policy_mismatch`           | Policy version mismatch                                 |
| `policy_version_unknown`            | Server cannot evaluate policy version                   |
| `guardian_required`                 | Subject cannot self-consent; Guardian workflow required |
| `visibility_private`                | Resource private                                        |
| `visibility_unlisted_token_invalid` | Token missing/expired/revoked                           |
| `visibility_public_redacted`        | Resource protected by public redaction                  |
| `dna_isolation_required`            | Caller not in DNA worker scope                          |
| `tenant_suspended`                  | Tenant is suspended                                     |
| `legal_hold`                        | Legal hold blocks action                                |
| `quota_exceeded`                    | Tenant quota exceeded                                   |
| `audit_required`                    | Action requires auditable context                       |
| `step_up_required`                  | Step-up authentication required                         |

New Reason Codes are added via `audit-service` registry and gated by ADR.

## 3. Decision table

The decision table governs how a Subject (User or Anonymous Visitor) may access a Resource in a Tree. Inputs: `Visibility`, `Role`, `Resource`, `LivingStatus`, `PrivacyLevel`, `ConsentState`, `ShareTokenState`. Output: `allow | deny | allow_with_obligations`.

Symbols:

- `—` = not applicable / default redaction
- `R` = redact (field-level, see §2.2)
- `W` = watermark and signed URL only
- `A` = audit required
- `T` = step-up auth required
- `✗` = deny
- `✓` = allow, no special obligation
- `O` = owner/admin only

### 3.1 PRIVATE Tree

| Subject                | Resource                          | LivingStatus                             | PrivacyLevel            | Result                                                   |
| ---------------------- | --------------------------------- | ---------------------------------------- | ----------------------- | -------------------------------------------------------- |
| `OWNER`/`ADMIN`        | any                               | any                                      | any                     | ✓, A                                                     |
| `MEMBER` (viewer)      | `PERSON`, `RELATIONSHIP`, `EVENT` | `DECEASED`/`PRESUMED_DECEASED`/`UNKNOWN` | `MEMBERS_ONLY` or lower | ✓                                                        |
| `MEMBER`               | `PERSON`, `RELATIONSHIP`, `EVENT` | `LIVING`/`PRESUMED_LIVING`               | `MEMBERS_ONLY`          | R fields per §2.2, A                                     |
| `MEMBER`               | `MEDIA` of living                 | `LIVING`                                 | `MINOR_RESTRICTED`      | ✗ minor_restricted                                       |
| `CONTRIBUTOR`          | genealogical resources            | —                                        | —                       | ✓ for direct edit only when policy allows, else proposal |
| `GUEST` via ShareToken | any                               | `DECEASED` only                          | `MEMBERS_ONLY` or lower | ✓ for token scope, A                                     |
| `GUEST`                | any                               | `LIVING`                                 | —                       | ✗ visibility_private                                     |
| Anonymous              | any                               | —                                        | —                       | ✗ visibility_private                                     |

### 3.2 UNLISTED Tree

| Subject                 | Resource                                                                       | LivingStatus | PrivacyLevel            | Result                              |
| ----------------------- | ------------------------------------------------------------------------------ | ------------ | ----------------------- | ----------------------------------- |
| `OWNER`/`ADMIN`         | any                                                                            | any          | any                     | ✓, A                                |
| `MEMBER`                | any                                                                            | any          | per PrivacyLevel        | ✓, A                                |
| Token holder (valid)    | `TREE`, `BRANCH`, `PERSON`, `RELATIONSHIP`, `EVENT`, `MEDIA` (per token scope) | `DECEASED`   | `MEMBERS_ONLY` or lower | ✓, W, A                             |
| Token holder            | any                                                                            | `LIVING`     | —                       | R fields per §2.2, W, A             |
| Token holder            | `DNA_*`                                                                        | —            | —                       | ✗ visibility_unlisted_token_invalid |
| Anonymous without token | any                                                                            | —            | —                       | ✗ visibility_unlisted_token_invalid |

Tokens must be `noindex`, hashed, expirable, revocable, rate-limited and scoped to a subset of branches. Revocation is effective within 60 seconds through the policy cache.

### 3.3 PUBLIC Tree

| Subject         | Resource                       | LivingStatus               | PrivacyLevel     | Result                                                 |
| --------------- | ------------------------------ | -------------------------- | ---------------- | ------------------------------------------------------ |
| `OWNER`/`ADMIN` | any                            | any                        | any              | ✓, A                                                   |
| `MEMBER`        | any                            | any                        | per PrivacyLevel | ✓, A                                                   |
| Anonymous       | `TREE`, `BRANCH` metadata      | —                          | —                | ✓                                                      |
| Anonymous       | `PERSON` (deceased)            | `DECEASED`                 | `PUBLIC_OK`      | ✓ fields per §2.2                                      |
| Anonymous       | `PERSON` (living)              | `LIVING`/`PRESUMED_LIVING` | —                | R: only display name, lifespan year-range, no contacts |
| Anonymous       | `RELATIONSHIP` (deceased only) | `DECEASED`                 | `PUBLIC_OK`      | ✓                                                      |
| Anonymous       | `RELATIONSHIP` (living)        | `LIVING`                   | —                | ✗ visibility_public_redacted                           |
| Anonymous       | `MEDIA` (deceased)             | `DECEASED`                 | `PUBLIC_OK`      | ✓ with W                                               |
| Anonymous       | `MEDIA` (living)               | `LIVING`                   | —                | ✗ visibility_public_redacted                           |
| Anonymous       | `DNA_*`                        | any                        | —                | ✗ dna_isolation_required                               |
| Anonymous       | `AUDIT_*`, `CONSENT_*`         | any                        | —                | ✗ visibility_public_redacted                           |

### 3.4 Cross-cutting denial rules

The following are unconditional denials and supersede allow entries:

| Rule                                                                 | Reason code                           |
| -------------------------------------------------------------------- | ------------------------------------- |
| `tenant_suspended` (any subject, any resource)                       | `tenant_suspended`                    |
| `legal_hold` on the resource                                         | `legal_hold`                          |
| `consent_missing` or `consent_revoked` on any DNA action             | `consent_missing` / `consent_revoked` |
| `consent_policy_mismatch`                                            | `consent_policy_mismatch`             |
| `guardian_required` for minor/incapacitated subject without Guardian | `guardian_required`                   |
| `dna_isolation_required` (non-DNA worker)                            | `dna_isolation_required`              |
| `step_up_required` on destructive admin/support action               | `step_up_required`                    |
| `quota_exceeded` on create/upload                                    | `quota_exceeded`                      |

### 3.5 Effect on policy decision

Every `allow` may carry obligations. Service code MUST honour them:

| Obligation  | Meaning                                          |
| ----------- | ------------------------------------------------ |
| `redact`    | Apply §2.2 redaction                             |
| `watermark` | Apply W marker, signed URL, no cache             |
| `audit`     | Append `AuditEntry` before returning             |
| `step_up`   | Require step-up authz, re-evaluate after success |
| `notify`    | Notify tenant Owner/Admin of the action          |

The policy decision must be cached only at the granularity of `(subject_id, resource_id, action, policy_version)`. Invalidation triggers: role change, policy change, consent change, branch scope change, ShareToken revoke.

## 4. Aggregation of policies into journeys

The following table maps the E0.1 journeys to the rules that govern them. It is the policy-consumer view that E3–E15 will follow.

| Journey                       | Rules that apply                                                              |
| ----------------------------- | ----------------------------------------------------------------------------- |
| J-ONB-1 (tenant bootstrap)    | §1.1 Tenant, §1.1 Role, §1.1 Membership, §2.6 default `DIRECT_EDIT` for Owner |
| J-ONB-3 (invitation)          | §1.1 Membership, §1.4 Role, §2.6 `MIXED` resolution                           |
| J-TREE-1 (tree creation)      | §1.2 Tree, §1.2 Visibility, §2.7, §2.6 mode                                   |
| J-TREE-2 (person update)      | §1.2 Person, §1.2 Claim, §1.3 LivingStatus, §2.1, §2.2, §2.5, §2.6            |
| J-TREE-3 (import GEDCOM)      | §1.2 Claim, §1.2 Source, §2.4, §2.5, §2.6                                     |
| J-TREE-4 (merge)              | §1.2 MergeRecord, §2.4                                                        |
| J-COL-1 (proposal)            | §1.4 ChangeProposal, §2.5, §2.6                                               |
| J-COL-2 (review)              | §1.4 Review, §2.6                                                             |
| J-SHARE-1 (visibility change) | §1.2 Visibility, §1.3 PrivacyLevel, §2.7, §3                                  |
| J-SHARE-2 (share token)       | §1.5 ShareToken, §3.2                                                         |
| J-MED-1 (upload)              | §1.5 MediaAsset, §2.2, §2.6                                                   |
| J-MED-3 (public access)       | §1.3 RedactionPolicy, §3.3                                                    |
| J-RES-1 (citation)            | §1.2 Citation, §2.5                                                           |
| J-RES-2 (hypothesis)          | §1.2 Claim, §2.5                                                              |
| J-DNA-1 (consent grant)       | §1.3 Consent, §1.3 Purpose, §1.3 Guardian, §2.3, §2.8                         |
| J-DNA-2 (match)               | §1.3 Consent, §2.5, §2.8, §3.4                                                |
| J-DNA-3 (revoke)              | §1.3 Consent, §2.2, §2.8, §3.4                                                |
| J-DSR-1 (access)              | §1.1 UserLink, §1.3 RedactionPolicy, §2.2                                     |
| J-DSR-2 (correction)          | §1.2 MergeRecord, §2.4, §2.5                                                  |
| J-OPS-1 (support JIT)         | §2.9 reason, §3.4 `step_up_required`                                          |
| J-OPS-3 (feature flag)        | §1.4 Role (binding), §2.9 reason                                              |

## 5. Open questions for E0.4 / E0.5

E0.2 does not block E0.1 or E0.6, but the following decisions are still pending and will be revisited in the listed tracks:

1. **Jurisdiction matrix** (E0.4): minority age per jurisdiction, lawful basis per purpose, cross-border transfer rules. Until E0.4 closes, the default `minority_age = 18` and the Rule `REDACTION` §2.2 / §3 stay in force.
2. **Policy versioning** (E0.5 ADR): how the `policy_version` token is issued, signed, scoped per tenant and rotated. Decision affects cache invalidation strategy.
3. **Sharing expiry** (E0.5 ADR): default expiry for `UNLISTED` share tokens, allowed max duration, and re-authentication window.
4. **Merge auto-merge threshold** (E0.5 ADR): default `0.85` and tunable per tenant; legal exposure when auto-merging living persons.
5. **Consent purpose taxonomy** (E0.4): final list of `Purpose` strings and their categories, including provider-specific variants.
6. **Living inference window** (E0.5 ADR): default `110 years` and per-tenant override process.
7. **Watermark policy** (E0.5 ADR): legal language, image vs document watermarks, retention of watermark for downloadable bundles.

These are tracked as open questions in `tasks.md` E0.1 §6 (already noted) and will be re-iterated in E0.5 once ADR candidates are filed.

## 6. Downstream traceability

| E0.2 concept              | Epic that consumes it                                     |
| ------------------------- | --------------------------------------------------------- |
| Glossary §1.1             | E1 contracts, E3 identity, E6 tenancy                     |
| Glossary §1.2             | E4 genealogy, E9 import/export                            |
| Glossary §1.3             | E5 sharing, E10 DNA, E11 audit                            |
| Glossary §1.4             | E6 collaboration                                          |
| Glossary §1.5             | E7 media, E9 import/export                                |
| Glossary §1.6             | E1 shared libraries, E6 platform SDK                      |
| Policy §2.1 Living        | E4.1, E5.2                                                |
| Policy §2.2 Redaction     | E5.2, E7.4, E9.3, E11.3                                   |
| Policy §2.3 Guardian      | E10.3                                                     |
| Policy §2.4 Merge         | E4.5                                                      |
| Policy §2.5 Uncertainty   | E4.4, E6.1                                                |
| Policy §2.6 Collaboration | E6.2, E6.3                                                |
| Policy §2.7 Visibility    | E5.1, E5.2                                                |
| Policy §2.8 Consent       | E10.3, E10.5                                              |
| Policy §2.9 Reason codes  | E11.1, E12.1                                              |
| Decision table §3         | E1.3 (OpenAPI scopes), E3.4 (OpenFGA model), E5, E10, E11 |

## 7. Acceptance criteria for E0.2

- [x] Glossary standardizes Tree, Person, User, Relationship, Claim, Citation, Living, Minor, Consent, Tenant (per tasks.md E0.2 acceptance).
- [x] Living inference, redaction, guardian, merge, uncertainty and direct-edit/approval are decided (per tasks.md E0.2 acceptance).
- [x] Decision table §3 covers `PRIVATE`, `UNLISTED`, `PUBLIC` × role × resource × living status (per tasks.md E0.2 acceptance).
- [x] Every rule references requirements, design sections and the E0.1 personas/journeys catalog.
- [x] Open questions handed to E0.4 / E0.5 are non-blocking but enumerated.
