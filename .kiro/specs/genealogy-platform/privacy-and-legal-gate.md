# Privacy and Legal Gate

> Companion to E0.4 of the Genealogy Platform implementation plan. Together with
> `personas-and-journeys.md` (E0.1), `glossary-and-policy-matrix.md` (E0.2) and
> `scale-and-slo.md` (E0.3), this document closes the discovery gate that the
> rest of the epics depend on. All values flagged `DRAFT` are inputs to the
> ADRs in E0.5 and are **not** contractual until E0.6 ownership sign-off.

## 1. Purpose

1. Define the **legal gate** that the rest of the platform must satisfy before a
   given capability (tenant, public sharing, media parser, GEDCOM, DNA,
   cross-border transfer, …) can ship to a jurisdiction.
2. Capture the **Data Protection Impact Assessment (DPIA)** for the four
   high-risk surfaces called out in `tasks.md` E0.4 and `design.md` §12:
   tenant isolation, public sharing, media/GEDCOM parsers, and DNA.
3. Maintain a **threat model** that is small enough to live in the repo, ties
   every scenario to a control in the design, and routes unmitigated findings
   back into E0.5 (ADRs) or E10.1 (DNA architecture gate).
4. Define a **Flagsmith kill-switch catalog**: every feature that MUST be
   disabled when the legal gate for the tenant or environment is not yet met.
5. Specify the artifacts that constitute **deletion evidence** and the
   lifecycle of a **legal hold**, both required by `requirements.md` R1, R13,
   R16 and NFR1.

## 2. Scope and non-goals

**In scope**

- DPIA + threat model for the four surfaces named by E0.4.
- Jurisdiction matrix for the SaaS regions and the on-premise distribution.
- Lawful-basis register and consent purpose/version catalog.
- Flagsmith flag list and safe defaults when Flagsmith is unreachable.
- Deletion evidence and legal-hold design at the data layer.

**Out of scope (handled by other tasks)**

- Implementation of policy decision points (E3.4 ABAC domain layer).
- Concrete Keycloak/OpenFGA config (E3.1, E3.3).
- DR / backup topology (E14.1, E14.2).
- DPIA per customer on-premise deployment: this document is the **platform
  DPIA template**; enterprise customers are expected to extend it with their
  own data controller / processor agreement and jurisdictional addenda.

## 3. Jurisdiction matrix (DRAFT → ADR-E0.5-04)

The platform ships behind a single SaaS control plane but must respect the
data-residency decisions from `scale-and-slo.md` §7. The matrix below binds
each target residency to the legal regime that drives the rest of this
document. Final selection is an ADR.

| Code | Residency cluster (example) | Primary regime | Key obligations this platform must satisfy |
|---|---|---|---|
| `EU` | EU sovereign region | GDPR + national supplements (e.g. DE BDSG, FR Loi Informatique et Libertés) | Lawful basis per Art. 6, explicit consent for special-category data per Art. 9, Art. 30 records, Art. 32 security, Art. 33/34 breach notification, Art. 35 DPIA, Art. 44–49 transfer safeguards, Art. 17 right-to-erasure, Art. 20 portability, Art. 22 no solely-automated decisions |
| `UK` | UK sovereign region | UK GDPR + DPA 2018 | Same obligations as EU under retained EU law; ICO oversight; no IDTA → UK addendum required for onward transfer |
| `US` | US region (default West, opt-in East) | CCPA/CPRA (CA), VCDPA (VA), CPA (CO), CTDPA (CT), UTPA (UT), state breach notification laws | "Do not sell/share" for CA consumers; sensitive-PI minimisation; verifiable consumer request workflow (access, delete, correct, limit); sectoral addenda for HIPAA, COPPA, FERPA when applicable |
| `CA-QC` | Canadian region with French-first defaults | PIPEDA + Law 25 (Quebec) | Express consent for transfers outside Quebec, privacy officer appointment, breach notification to CAI and individuals, DPIA threshold met by this platform |
| `APAC-ANZ` | Australia / New Zealand region | Privacy Act 1988 (Cth) + NZ Privacy Act 2020 | APP 3–8 (collection, use, disclosure, storage, accuracy, access, correction), Notifiable Data Breaches scheme (AU), NZ Privacy Commissioner notification |
| `APAC-JP` | Japan region | APPI 2017 (as amended 2022) | Pre-2022 consent legacy data, opt-in for sensitive data, cross-border transfer rules (foreign-country equivalent), pseudonymised-data carve-out |
| `APAC-SG` | Singapore region | PDPA + MAS guidance | Consent / legitimate-interest exception, DNC registry, transfer to jurisdictions with comparable protection, breach notification within 3 days |
| `ROW` | Other regions | Local law | Activated only by ADR; defaults to GDPR-equivalent controls until overridden |
| `ONPREM` | Customer-managed | Customer's applicable law | Customer is the data controller; this DPIA becomes the processor baseline; customer signs DPA referencing this document |

**Cross-region rule (mirror of `scale-and-slo.md` §7):** a tenant is bound to
one residency cluster at creation time. Cross-region replication is only
allowed within the same jurisdiction class (e.g. `EU` ↔ `UK` only when an
adequacy decision or IDTA is in place). The Flagsmith flag
`legal.data_residency.allowlist` (see §12) carries the active cluster and is
evaluated by every data-plane component before storage.

## 4. Lawful basis register

Every processing activity must declare a lawful basis under Art. 6 GDPR (or
its local equivalent). The register below is the closed set that downstream
code may pick from; anything outside this set requires a new entry signed by
the DPO.

| Activity | Lawful basis (GDPR) | Local variants | Recipients / processors | Retention link |
|---|---|---|---|---|
| Tenant onboarding, billing, account recovery | Art. 6(1)(b) contract | Legitimate interest under common-law jurisdictions | Auth (Keycloak), billing adapter, audit log | `scale-and-slo.md` §6 Free / Pro / Enterprise |
| Member invitation, role assignment | Art. 6(1)(b) + 6(1)(f) | Same | OpenFGA, audit | Same |
| Family-tree research, citation, claims | Art. 6(1)(a) consent + 6(1)(b) contract | Same | Genealogy service, research service, audit | Plan retention; consent can shorten |
| Living/minor redaction on `PUBLIC` | Art. 6(1)(f) legitimate interest, balanced against Art. 6(1)(a) consent for data subject | Same | Search public projection | Until visibility flip or revocation |
| Public sharing via `UNLISTED` token | Art. 6(1)(a) consent (token issuance = consent) | Same | Public projection, audit | Token TTL + revocation |
| Media upload, scan, OCR, derivative | Art. 6(1)(b) contract + 6(1)(f) | Same | Media service, processors (ClamAV, libvips, FFmpeg, Tesseract, Tika, Gotenberg) | Plan retention; legal hold overrides |
| GEDCOM import/export | Art. 6(1)(b) contract | Same | Import-export service, Temporal | Plan retention |
| Notification delivery | Art. 6(1)(b) + 6(1)(a) consent per channel | Same + CAN-SPAM/CASL for marketing | Notification service, email/push adapter | Until preference revokes |
| Audit, fraud, abuse prevention | Art. 6(1)(f) | Same | Audit service | Per `scale-and-slo.md` §6 audit class |
| Support access (just-in-time) | Art. 6(1)(f) + 6(1)(a) consent for sensitive data | Same | Admin/support surfaces, audit | Session-bound; see E11.5 |
| **DNA: kit metadata, consent ledger** | Art. 9(2)(a) **explicit consent** (special category) | Country-specific genetic-data laws (e.g. FL GINA-style, IL GIPA, CA CCPA sensitive-PI) | DNA service, audit | Per E10.3 consent expiry; legal-hold override |
| **DNA: raw upload, matching** | Art. 9(2)(a) + (g) substantial public interest only where permitted | Same | DNA worker pool isolated (E10.2) | Revocation triggers E10.5 workflow |
| **DNA: cross-border transfer of genetic data** | Art. 49(1)(a) **explicit consent** OR adequacy decision | Same | Cross-region only if recipient region is in `legal.data_residency.allowlist` | Revoked on consent withdrawal |
| Product analytics (no DNA, no living) | Art. 6(1)(f) legitimate interest with opt-out | Same | Reporting service | Strict minimisation (see §11) |

**Standing rule:** if the basis is `consent`, a corresponding record MUST
exist in the consent ledger (`consent_purpose_id` + `policy_version` + signed
receipt). The platform never treats consent as bundled; each purpose is its
own row.

## 5. Data inventory and classification

The privacy-by-default promise in `design.md` §2 starts with a closed
classification. Every column or topic the platform ever stores is mapped to
one and only one class; downstream controls are derived from the class.

| Class | Examples | Default visibility | Encryption at rest | Key custody | Allowed recipients | Audit class |
|---|---|---|---|---|---|---|
| `PII.IDENTITY` | email, phone, recovery codes | `PRIVATE` | Tenant DB KMS | Vault tenant-scoped policy | Tenant members per role; never to processors without DPA | `auth.audit` |
| `PII.QUASI_ID` | name, place, event date | `PRIVATE` (redacted on `PUBLIC`) | Tenant DB KMS | Same | Same | `data.audit` |
| `PII.SENSITIVE` | health, ethnicity, religion, sexuality, criminal record | `PRIVATE`, redact everywhere outside consent | Envelope-encrypted | Vault `sensitive` policy | Strict role + consent purpose | `data.audit` |
| `GENETIC.RAW` | raw genotype file (e.g. FASTQ, 23andMe CSV, AncestryDNA CSV) | `PRIVATE`, isolated bucket/prefix | Envelope encryption with dedicated KEK | DNA KMS only; never reads from media bucket | DNA worker pool only | `dna.audit` (special) |
| `GENETIC.METADATA` | kit id, provider, test type, owner | `PRIVATE` | DNA DB KMS | DNA KMS | DNA service + consent holder | `dna.audit` |
| `GENETIC.DERIVED` | match, segment, relationship estimate | `PRIVATE`, per-consent redaction | DNA DB KMS | DNA KMS | Per consent purpose | `dna.audit` |
| `MEDIA.RAW` | uploaded file before scan | `QUARANTINE` then `READY` | S3 SSE-KMS | Media KMS | Media service + processors | `media.audit` |
| `MEDIA.DERIVATIVE` | thumbnail, OCR, transcode | `PRIVATE` per asset policy | S3 SSE-KMS | Media KMS | Per asset visibility | `media.audit` |
| `OPS.METADATA` | trace IDs, pseudonymous tenant label, request counters | `OPS` | OTel backend | Platform-managed | Operators only via RBAC | `ops.audit` |
| `AUDIT.APPENDONLY` | audit entries | `PRIVATE` to operators | Append-only store, KMS at rest | Audit KMS | Operators with DPO sign-off for export | `audit.audit` |
| `SECRET` | signing keys, DB passwords, OAuth client secrets | n/a | n/a | Vault / cloud KMS only | Workload identity | `ops.audit` |

The class is set at write time and never silently downgraded. Any
re-classification (e.g. user requests deletion → `PII.IDENTITY` becomes
`PII.REDACTED`) emits an audit event with reason.

## 6. DPIA — Tenant isolation

### 6.1 Description

The platform is multi-tenant SaaS plus shared-tenant on-premise. A single
defect in tenant context propagation could expose one tenant's records to
another. Because tenants may include minors, deceased persons whose data is
still protected, and genetic data of DNA owners, the impact rating is
**high**.

### 6.2 Necessity and proportionality

- Necessity: cross-tenant storage is required for cost-efficient SaaS; the
  alternatives (database-per-tenant, schema-per-tenant) are listed in ADR
  E0.5-02.
- Proportionality: defense in depth — application context, Istio mTLS,
  service token, database role, RLS, query guard — matches the risk
  appetite of the most sensitive class above (`GENETIC.*`).

### 6.3 Data flow

```
[Browser] ──HTTPS── Kong ──mTLS── BFF ──gRPC metadata── domain service ──jOOQ── PostgreSQL (RLS)
                                  │                                       │
                                  │                                       └── S3 (prefix per tenant)
                                  │
                                  └── Vault short-lived DB credential
```

### 6.4 Identified risks and controls

| ID | Risk | Likelihood | Impact | Residual | Control (owning epic) |
|---|---|---|---|---|---|
| T-01 | `tenant_id` forged by client | Medium | High | Low | Trusted context from BFF; service re-validates; Kong strips unknown headers (E3.5) |
| T-02 | Cross-tenant query via raw SQL bypass | Low | High | Low | jOOQ only; tenant predicate injected by repository guard (E3.5, E1.4) |
| T-03 | Cross-tenant row via missing RLS policy | Medium | High | Low | RLS on every tenant-scoped table; integration tests assert cross-tenant `select` returns zero rows (E3.5, E1.2) |
| T-04 | Cross-tenant access via shared cache | Medium | Medium | Low | Valkey key namespace includes `tenant_id`; cache wrapper enforces prefix; never cache DNA or media raw (E3.5, E2.7) |
| T-05 | Token replay across tenant after role change | Medium | High | Low | Short-lived service tokens; OpenFGA tuple invalidate path is event-driven (E3.3) |
| T-06 | On-premise misconfig drops RLS | Medium | High | Medium | Preflight check (E2.1); RLS test in CI; ADR E0.5-02 keeps schema-per-service as opt-in |
| T-07 | Operator support access leaks | Low | High | Low | JIT support requires step-up auth, scoped expiry, banner, audit (E11.5) |
| T-08 | Tenant-onboarding region misclassification | Low | High | Low | Residency locked at onboarding, immutable; Flagsmith enforcement at storage layer (E3.5, §12) |

### 6.5 Outcome

Residual risk **acceptable** subject to E3.5, E1.2 and E2.1 closing the
controls above. No additional ADR required, but ADR E0.5-02 (database-per-tenant
vs schema-per-service) and ADR E0.5-03 (residency provider) are inputs.

## 7. DPIA — Public sharing

### 7.1 Description

Visibility modes `PRIVATE`, `UNLISTED`, `PUBLIC` (R3.3) expose
genealogical records to anonymous viewers, search engines, and downstream
CDNs. The risk is that living persons or sensitive fields are leaked to
unintended audiences.

### 7.2 Necessity and proportionality

- Necessity: public sharing is a primary growth vector and a requirement
  for the partner API (E9.5).
- Proportionality: only redacted, non-living, non-sensitive data reaches
  the public projection. The same `tree.visibility` value is **never** used
  as the sole authorization signal — it is combined with field-level
  redaction, ABAC obligations and CDN `noindex` directives.

### 7.3 Data flow

```
genealogy-service ─event──> search-service ─projection──> public-projection (PostgreSQL read replica)
                                                                  │
                                                                  └── CDN (noindex on UNLISTED, edge cache only on PUBLIC non-PII)
```

### 7.4 Identified risks and controls

| ID | Risk | Likelihood | Impact | Residual | Control (owning epic) |
|---|---|---|---|---|---|
| P-01 | Living person exposed on `PUBLIC` tree | Medium | High | Low | Living-inference (E0.2 §3) plus ABAC redaction before projection; E2.10 redaction log filter; pen-test (E15.2) |
| P-02 | Minor exposed without guardian consent | Medium | High | Low | Minor class always `PRIVATE`; visibility flip requires admin role + reason + audit (E4.1, E3.4) |
| P-03 | Sensitive field (religion, health, criminal) leaks via field join | Medium | High | Low | Field-level classification in §5; ABAC policy denies projection unless explicit consent; CI scan asserts no field name from `PII.SENSITIVE` set appears in public projection migration (E3.4) |
| P-04 | `UNLISTED` token brute-forced | Low | High | Low | Token stored as salted hash (Argon2id); short TTL; rate limit at Kong + BFF; audit on validation (E4.1) |
| P-05 | Search-engine indexing of `UNLISTED` | Medium | High | Low | `noindex,nofollow` HTTP header + robots.txt; CDN strips query; CDN cache key excludes token (E8.3) |
| P-06 | Person→User link inferred from `PUBLIC` page (R2.4) | Medium | High | Low | Person and User are distinct entities; `public_profile` route never returns user identifiers; pen-test (E15.2) |
| P-07 | Cache poisoning via crafted public URL | Low | High | Low | Cache key bound to URL+accept+tenant pseudonymous ID; signed URL not used for public pages (E2.7) |
| P-08 | Public projection lags on visibility flip | Medium | Medium | Low | Outbox event drives projection rebuild; reconciliation Temporal workflow (E4.1, E8.1) |
| P-09 | `PUBLIC` tree scraped at scale | High | Medium | Medium | Kong rate limit + bot detection; ABAC watermark on high-volume routes (E2.2, E3.4) |

### 7.5 Outcome

Residual risk **acceptable** subject to E3.4, E4.1, E8.3, E10.1 and E15.2.
`PUBLIC` and `UNLISTED` for tenants that include any living persons are
**prohibited until E0.5 closes ADR-04** (residency/jurisdiction selection).
This is enforced by `legal.public_sharing.enabled` (see §12).

## 8. DPIA — Media and GEDCOM parsers

### 8.1 Description

The platform ingests untrusted media (image, audio, video, PDF) and
GEDCOM 7/5.5.1 files. Both parsers have a long history of parser-confusion,
decompression-bomb, polyglot-file and SSRF vulnerabilities. The risk is
both confidentiality (data exfiltration via crafted file) and integrity
(parser compromise leading to RCE in worker pool).

### 8.2 Necessity and proportionality

- Necessity: GEDCOM import is a foundational feature (R12, E9.2); media
  upload is a core user expectation (R9, E7).
- Proportionality: parsers run in a sandboxed worker pool with no
  egress, no internet, read-only filesystem, per-job resource quota, and
  network policy enforced by Istio (`EgressBlock`).

### 8.3 Data flow

```
Browser ──Kong── BFF ──signed URL── S3 (QUARANTINE) ──Temporal── sandboxed worker pool
                                                       │              │
                                                       │              ├── ClamAV
                                                       │              ├── libvips / ImageMagick
                                                       │              ├── Tesseract / FFmpeg / Tika
                                                       │              └── Gotenberg (PDF)
                                                       │
                                                       └── event ──> media-service (state machine)
```

### 8.4 Identified risks and controls

| ID | Risk | Likelihood | Impact | Residual | Control (owning epic) |
|---|---|---|---|---|---|
| M-01 | Decompression bomb DoSes worker | Medium | Medium | Low | libvips/ImageMagick resource limits; FFmpeg `-t` and CPU/mem caps; Tesseract timeout; max pixel count policy (E7.3) |
| M-02 | Polyglot file (e.g. image+HTML) executes in browser | Medium | High | Low | All output served with `Content-Disposition: attachment` for unrecognised type; image variants re-encoded; pen-test (E15.2) |
| M-03 | Parser CVE leading to RCE | Medium | High | Low | Pinned container version; Trivy/Grype scan in CI; Renovate updates auto-build; non-root user; read-only FS; seccomp profile; no egress (E7.2, E1.6) |
| M-04 | GEDCOM import contains malicious embedded URL (SSRF) | Medium | High | Low | Parser sandbox; deny-by-default NetworkPolicy; URL fetcher is a separate ADR-controlled component (E9.2) |
| M-05 | Malicious GEDCOM exhausts memory | Medium | High | Low | Streaming parser, per-record memory cap, depth/count limits; dry-run mode never writes domain data (E9.2) |
| M-06 | ClamAV signature outdated | Medium | Medium | Low | Daily signature update cron; freshness SLI; quarantine blocks downstream until ready (E7.2) |
| M-07 | OCR text leaks sensitive data into projection | High | Low | Low | OCR stored as `MEDIA.DERIVATIVE` with same visibility as source asset; never indexed in public projection (E7.3) |
| M-08 | Object quarantine TTL bypass exposes unscanned asset | Low | High | Low | State machine is source of truth; only `READY` assets are linkable; reconciliation workflow (E7.1) |

### 8.5 Outcome

Residual risk **acceptable** subject to E1.6, E7, E9.2 and E15.2.
`media.upload` is **disabled by default** for tenants that opt into the
`legal.parsers.restricted` flag (e.g. jurisdictions that require explicit
consent for biometric processing); see §12.

## 9. DPIA — DNA

### 9.1 Description

DNA is special-category data under GDPR Art. 9 and is governed by a growing
set of country-specific genetic-data laws. The platform's design isolates
the DNA service (E10.2) and forbids raw DNA in any other bucket, log,
trace, search index, event payload, or public projection. This DPIA
captures the residual design risks.

### 9.2 Necessity and proportionality

- Necessity: DNA matches are an explicit product differentiator (R13, E10).
- Proportionality: **opt-in only**, default-off per tenant and per
  environment (`legal.dna.enabled = false`); consent is purpose-scoped
  and re-checked at every activity, not only at submit; matching workers
  run in their own node pool with their own task queue, network policy
  and KMS key.

### 9.3 Data flow

```
DNA owner → consent UI → consent ledger → DNA-service → Vault (DEK) ──> S3 DNA bucket (envelope-encrypted raw)
                                                                  │
                                                                  └── Temporal worker (isolated) ──> match/segment
                                                                                                       │
                                                                                                       └── consent-gated read API
```

### 9.4 Identified risks and controls

| ID | Risk | Likelihood | Impact | Residual | Control (owning epic) |
|---|---|---|---|---|---|
| D-01 | Raw DNA escapes to log, trace or event | Medium | High | Low | OTel redaction filter (§11) + Apicurio payload lint + dedicated logger; CI asserts DNA column name never appears in any other service's logback config (E10.4, E13.1) |
| D-02 | DNA worker cluster shares node with media worker | Low | High | Low | Separate node pool, separate NetworkPolicy, separate task queue; admission control rejects pods without label (E10.2) |
| D-03 | Cross-region transfer of genetic data without consent | Low | High | Low | DNA bucket stays in residency cluster; residency allowlist enforced; cross-region reads require explicit consent record (E10.2, §3) |
| D-04 | Matching algorithm leak via telemetry | Medium | Medium | Low | Only `dna_match_id` exposed in telemetry; algorithm version is opaque label; no aggregate stats on matches (E13.1) |
| D-05 | Minor DNA without guardian workflow | Medium | High | Low | DNA service refuses record when subject has `minor=true` and no `guardian_consent_id`; pen-test (E15.2) |
| D-06 | Consent revocation does not stop in-flight workflow | Medium | High | Low | Consent engine re-checked at activity start; cancellation signal broadcast; matching Temporal workflow is cancellable and idempotent (E10.3, E10.5) |
| D-07 | Derived data survives deletion | Medium | High | Low | E10.5 deletion workflow verifies all derived tables cleared; reconciliation report as deletion evidence (E10.5) |
| D-08 | Jurisdiction bans genetic data processing entirely | n/a | High | n/a | Flag `legal.dna.enabled` defaults off; per-jurisdiction allowlist (DPIA §3); ADR E10.1 selects jurisdictions (E10.1) |

### 9.5 Outcome

Residual risk **acceptable** subject to E10 (entire epic) and E15.2 pen-test.
DNA MUST NOT be enabled in any tenant or environment until:

1. ADR E0.5-04 (residency) closes for the target jurisdiction, **and**
2. ADR E10.1 (DNA architecture gate) signs off on formats/providers/jurisdiction.

The Flagsmith flag `legal.dna.enabled` (§12) is the runtime gate. E0.5
remains responsible for ADR closure; this DPIA does **not** authorise DNA
rollout.

## 10. Threat model (STRIDE-lite)

The four DPIAs above cover the platform's high-risk surfaces. This section
collates cross-cutting threats that span multiple surfaces and that the
design must defend as a unit.

| ID | Category | Threat | Surfaces | Control |
|---|---|---|---|---|
| TM-01 | Spoofing | Attacker forges OIDC token | All | Keycloak signed JWT, JWKS rotation, `aud`/`iss` validation at BFF (E3.1) |
| TM-02 | Tampering | Tenant context modified in transit | All | Istio mTLS + BFF-signed context metadata + service re-validation (E3.5) |
| TM-03 | Repudiation | User denies creating a living-record update | All | Append-only audit log with hash-chain integrity (E3.6) |
| TM-04 | Information disclosure | DNA / living data leak via analytics | DNA, public projection | Strict telemetry redaction, pseudonymous tenant labels, no DNA in analytics (E13.1) |
| TM-05 | Denial of service | Malicious GEDCOM exhausts parser pool | Parser | Streaming limits, depth/count caps, sandbox resource quota, Kong rate limit (E9.2) |
| TM-06 | Elevation of privilege | Support staff escalates to DNA access | Admin/support | JIT step-up auth, DNA namespace separate in OpenFGA, impersonation flag never covers DNA (E11.5) |
| TM-07 | Side-channel | Timing attack on `tenant_id` enumeration | All | Constant-time tenant predicate, RLS denies before app-level logging (E3.5, E13.1) |
| TM-08 | Supply chain | Compromised parser image | Parser | Cosign-signed images, SBOM, pinned versions, Renovate with security SLA (E1.6) |
| TM-09 | Legal hold bypass | Hard-delete ignores legal hold | All | Hard-delete is gated by `legal_hold.active`; deletion evidence references hold id (E3.6, §13) |
| TM-10 | Cross-region DNA transfer | Worker fetches raw from another region | DNA | Worker has no egress, residency allowlist (E10.2, §3) |

Pen-test scope for E15.2 is derived directly from this table.

## 11. Telemetry and analytics minimisation

`design.md` §13 promises OTel with redaction. This DPIA spells out the
**forbidden payload classes** that the redaction filter MUST drop before
any sink (Loki, Tempo, analytics warehouse):

- `PII.IDENTITY` (email, phone, recovery codes) → replaced by pseudonymous ID
- `PII.SENSITIVE` → never sent
- `GENETIC.RAW`, `GENETIC.DERIVED` → never sent (the mere column name is a
  finding in CI)
- raw GEDCOM payload, raw media bytes, OCR text containing identifiers
- access tokens, refresh tokens, session cookies, BFF session IDs
- file paths under `dna/` prefix, bucket names, KMS key IDs
- user-supplied free-text fields (`biography`, notes) when they contain
  identifiers matching the platform's identifier regex

CI gates (`E1.6`, `E13.1`) include a regex assertion that none of the
forbidden tokens appears in `logback.xml`, `application.yaml`,
`logger.info(...)` examples or fixture payloads.

Product analytics are kept under `analytics.events.json` with an explicit
allowlist; the warehouse schema is reviewed by the DPO before activation.

## 12. Flagsmith legal-gate kill switch catalog

`design.md` §1 names OpenFeature + Flagsmith. `E2.8` makes SDK safe-default
mandatory. The flags below are the **legal gate**: a feature that ships
behind one of these flags is *off* for every tenant/environment until a
condition in §3 / §4 / §5 / §7 / §8 / §9 is satisfied.

| Flag | Default | Owner | When it becomes `true` | When it becomes `false` again |
|---|---|---|---|---|
| `legal.data_residency.allowlist` | empty list | DPO + Platform | ADR E0.5-04 selects target cluster | Jurisdiction expires (e.g. adequacy withdrawn) |
| `legal.public_sharing.enabled` | `false` | DPO | DPIA §7 controls shipped; ADR E0.5-04 closed for the residency | Pen-test finding open or DPIA change |
| `legal.dna.enabled` | `false` | DPO + Genomics lead | DPIA §9 controls shipped; ADR E10.1 signed; jurisdiction allows | Consent engine regression, revocation storm, ADR withdrawal |
| `legal.media_upload.enabled` | `true` | DPO | DPIA §8 controls shipped | CVE in critical parser without patch |
| `legal.gedcom_import.enabled` | `true` | DPO | DPIA §8 controls shipped | Streaming parser regression |
| `legal.cross_region_transfer.enabled` | `false` | DPO | ADR E0.5-04 + E0.5-05 (transfer mechanism) closed | SCC/IDTA withdrawn |
| `legal.parsers.restricted` | `false` | DPO | Set `true` per tenant in jurisdictions that ban biometric processing (e.g. some EU member-state interpretations) | Jurisdiction revoked or processor DPA amended |
| `legal.flag_bypass_allowlist` | empty list | DPO | Names flags that may be toggled for non-production troubleshooting only | Removed by default |

SDK rules (mirroring `E2.8`):

- Every consumer reads the flag from OpenFeature with an explicit typed
  default; the default value MUST match the table above.
- When Flagsmith is unreachable, the SDK MUST return the default, **never**
  the inverse, **never** `null`.
- No code path may call `setFlag(...)` from application code; the flag
  taxonomy is owned by Flagsmith admin UI / GitOps.
- E2.8 and E13.2 must surface these flags on the SLO dashboard so the
  on-call team can see which features are currently gated.

## 13. Legal hold and deletion evidence

`requirements.md` R1, R13, R16 and §6.5 require hard-delete to leave
non-content evidence behind. The mechanism is a single `legal_hold`
table (AppendOnly, write-once) plus a small state machine on each
tenant-scoped aggregate.

### 13.1 Legal hold lifecycle

1. **Create** — DPO / court order / customer request creates a
   `legal_hold` row with `subject_type`, `subject_id`, `tenant_id`,
   `scope_class[]` (e.g. `[PII.IDENTITY, MEDIA.RAW]`),
   `reason`, `issued_by`, `effective_at`, `expiry_at` (nullable).
2. **Apply** — every delete API MUST call `legal_hold.assert_clearable(...)`
   before issuing the actual delete; if any hold matches `subject` and
   `class`, the API returns `409 Conflict` with code `legal_hold_active`.
3. **Expire / release** — only DPO + second-person review can release a
   hold; release event is audit-logged.
4. **Evidence** — every successful hard-delete writes a
   `deletion_evidence` row containing: hold id (if any), tenant pseudonymous
   id, aggregate type, aggregate id, deletion class, deletion timestamp,
   actor (subject or DPO), checksum of the deleted payload (SHA-256 of
   ciphertext, never plaintext), and a pointer to the audit log entry.

### 13.2 Deletion evidence requirements

- The evidence row MUST NOT contain the content of the deleted record.
- The evidence row MUST be retained for **10 years** (configurable per
  jurisdiction; `EU` baseline is 10 years under anti-money-laundering
  analogy where applicable; `US` state laws vary; on-premise customers
  can override via tenant config).
- The evidence row is itself append-only; it is exportable in two
  formats: machine-readable JSON Lines and human-readable PDF with DPO
  signature.
- A reconciliation Temporal workflow runs nightly per region and asserts
  that no `legal_hold` row remains active for a record that has been
  deleted.

### 13.3 DNA-specific deletion (cross-ref E10.5)

For DNA, the deletion evidence MUST also assert that:

- the raw object in the DNA bucket is gone (manifest hash matches),
- the derived tables (`dna_match`, `dna_segment`, `dna_estimate`) are
  empty for that kit,
- the consent ledger retains a `REVOKED` entry with no payload content,
- the matched-notification outbox is drained.

The E10.5 workflow signs the resulting evidence bundle.

## 14. Open questions feeding E0.5 / E0.6 / E10.1

These are inputs, not decisions. They MUST remain `[ ]` until E0.5.

1. **ADR E0.5-04 residency provider selection** (drives §3).
2. **ADR E0.5-07 transfer mechanism** for cross-region data (drives §3
   cross-region rule and `legal.cross_region_transfer.enabled`).
3. **ADR E0.5-09 database topology** for shared-tenant on-premise
   (drives §6 control T-06).
4. **ADR E10.1 jurisdiction allowlist for DNA** (drives `legal.dna.enabled`).
5. **ADR E10.1 DNA format/provider and matching algorithm version** (drives
   §9 DPIA scope).
6. **Naming the platform DPO** and their on-call coverage (drives the
   owner column in §12).
7. **Customer DPA template** for on-premise deployments, referencing this
   DPIA.
8. **Breach-notification SLA** per jurisdiction (mirrors `scale-and-slo.md`
   §5.3 RTO but for privacy incidents).
9. **Pseudonymous tenant-label rotation cadence** to balance cardinality
   and re-identification risk.
10. **Standard contract clauses for subprocessors** (ClamAV signature
    feed, OCR language packs, geocoding provider).

## 15. Cross-reference map

| Concern | Source | Owning epic(s) |
|---|---|---|
| Privacy-by-default promise | `design.md` §2 | E3.4, E4.1, E10.3 |
| Append-only audit, hash chain | `requirements.md` R16, `design.md` §13 | E3.6 |
| Tenant context propagation | `design.md` §6.1 | E3.5 |
| Visibility model | `requirements.md` R3, `design.md` §6.3 | E4.1, E8.3 |
| Parser sandbox | `design.md` §11 | E7.2, E9.2 |
| DNA isolation | `design.md` §5.5, §12 | E10.1, E10.2 |
| Residency | `scale-and-slo.md` §7 | E0.5, E14.1 |
| Pseudonymous tenant labels | `scale-and-slo.md` §5, `design.md` §13 | E13.1 |
| Retention per plan | `scale-and-slo.md` §6 | E14.1 |
| RPO / RTO | `scale-and-slo.md` §5.3 | E14.1, E14.2 |
| Living / minor redaction | `glossary-and-policy-matrix.md` §3 | E3.4, E4.1 |
| Persona journey | `personas-and-journeys.md` | E1.5, E11.5 |

## 16. Change log

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1 | 2026-08-04 | AI Agent (E0.4) | Initial draft, derived from `requirements.md`, `design.md`, `personas-and-journeys.md` (E0.1), `glossary-and-policy-matrix.md` (E0.2) and `scale-and-slo.md` (E0.3). Inputs to E0.5 / E0.6 / E10.1. |