# E0.1 — Personas and Journeys

Status: Accepted for downstream discovery (E0.2, E0.4, E0.6, E1, E3–E15).
Scope: Define every user archetype that touches the Genealogy Platform and the end-to-end journeys they perform. Provide minimum permissions, visible data, failure paths, success metrics and requirements traceability so every later epic has a stable anchor.
Out of scope: Authorization model details, glossary/policy matrix (E0.2), scale/SLO numbers (E0.3), legal gate (E0.4), ADR closure (E0.5), ownership RACI (E0.6). Cross-references to those deliverables are noted but not duplicated here.

## 1. Personas

Personas are described as archetypes, never as identity records. A single human (User) can hold more than one persona, and a persona can shift over time (an editor becomes a viewer after their membership expires). All server-side enforcement must treat the persona's current effective role, not the user's history (R10.4, R16.4).

### 1.1 Owner (`OWNER` tenant role)

A person who creates a tenant and bears contractual and legal responsibility for it. There is exactly one primary owner; ownership can be transferred but never co-owned by default.

- Accountability: billing, legal hold, deletion approvals, tenant suspension, change of plan.
- Data access: full read/write inside the tenant, including audit exports and DSR (data subject request) tooling.
- Typical actions: invite admins, change plan, suspend tenant, approve destructive workflows, view audit trail, export for portability.
- Anti-patterns: direct day-to-day editing of trees (delegated to editors); impersonating other users (forbidden by R16.4).

### 1.2 Admin (`ADMIN` tenant role)

A trusted operator the owner delegates to. They manage membership, invitations, role assignment and tenant-wide configuration but cannot dissolve the tenant or transfer ownership without owner approval.

- Accountability: invitation lifecycle, role grants, feature flag toggles within their tenant, support of editors.
- Data access: full read/write to membership, invitations, entitlements, audit; limited destructive capability on trees (still scoped by tree role).
- Anti-patterns: see audit logs of other tenants, change ownership, mutate DNA consent.

### 1.3 Editor (`TREE_ADMIN` or `EDITOR` per tree)

Builds and curates the tree. Edits facts, attaches sources, merges duplicates, resolves proposals and configures visibility per branch.

- Accountability: provenance quality, citation completeness, merge decisions, visibility choices for branches they own.
- Data access: read/write on the trees they administer, read-only on branches they do not own, never read on DNA raw data unless explicitly granted through DNA consent.
- Anti-patterns: modify verification status of imported claims without re-checking sources (R5.5, R8.5); reveal living-person details to public projection (R3.6, R6.5).

### 1.4 Contributor (`CONTRIBUTOR` per tree)

Suggests changes through proposals. Cannot apply directly unless the tree policy allows it.

- Accountability: quality and accuracy of proposals, attaching citations, responding to review feedback.
- Data access: read the tree they contribute to; write only through the proposal workflow; never edit audit or membership.
- Anti-patterns: bypass the proposal workflow by abusing direct-edit APIs; flood proposal queue (R10).

### 1.5 Viewer (`VIEWER` or `GUEST` per tree)

Read-only consumer. Viewers can be invited members or holders of unlisted tokens; guests have token-bounded scope and expiry.

- Accountability: none beyond the token terms (noexport, no redistribution).
- Data access: only the redacted slice the policy allows; living persons and DNA must be hidden (R3.4, R3.6, R13.7).
- Anti-patterns: scrape the public projection; share unlisted tokens publicly.

### 1.6 Genealogist (advanced role, can be combined with `EDITOR` or stand alone)

A specialist who runs long-running research tasks, manages sources, hypothesises relationships and imports/exports data.

- Accountability: research log hygiene, source verification status, import reproducibility.
- Data access: full research artifact history; import/export jobs; bulk proposal creation.
- Anti-patterns: treat unverified imports as verified; duplicate the entire tree to circumvent access review.

### 1.7 Operator (platform-level `OPERATOR`, outside any single tenant)

Runs the SaaS control plane or the on-premise installation. They manage clusters, observability, support tickets and feature flags across tenants.

- Accountability: incident response, restore drills, license compliance, platform upgrades.
- Data access: pseudonymous telemetry and aggregated dashboards by default; tenant content only via JIT support access with owner/admin approval, time-bounded, bannered and audited (R16.3, E11.5).
- Anti-patterns: read tenant data without JIT; bypass DNA isolation; share raw logs externally.

### 1.8 Auditor (platform-level or tenant-level `AUDITOR`)

Reviews audit entries, retention evidence and compliance reports. They never mutate business data.

- Accountability: integrity of the audit chain, retention enforcement, export attestation.
- Data access: append-only audit, deletion evidence, configuration; never raw DNA, never business PII beyond what the audit entry already captures.
- Anti-patterns: edit or delete audit entries; use audit tool to reconstruct content for unauthorised users.

### 1.9 DNA Owner / DNA Subject

A Person (not a User) whose genetic data is uploaded and processed. The DNA owner may be the same human as a User, or a minor/incapacitated person represented by a guardian.

- Accountability: granting or revoking consent; providing accurate metadata; triggering deletion.
- Data access: own DNA kit metadata, consent ledger, match list, segment notes; never raw genotype outside the secure DNA worker (R13.3, R13.6).
- Anti-patterns: share DNA results outside consented purposes; coerce other subjects.

### 1.10 Guardian

A User who acts on behalf of a DNA owner who cannot give consent themselves (minor, legally incapacitated, deceased-with-active-research consent). Guardian status is jurisdiction-bound and time-bound.

- Accountability: best-interest decisions, renewal of consent, eventual handover or deletion when the subject regains capacity or reaches majority.
- Data access: scoped by the consent they grant, never wider than the subject would have had.
- Anti-patterns: extend consent past jurisdictionally defined age; copy DNA data into a non-DNA service.

### 1.11 Cross-cutting: Anonymous Visitor

A public projection consumer who has no User identity. They only see what the public projection surfaces, never private or unlisted records.

- Accountability: none contractual; rate limits and abuse protections apply.
- Data access: public projection only, with redaction applied.
- Anti-patterns: probe for unlisted tokens; scrape aggressively.

## 2. Journey catalogue

A journey is an end-to-end flow with a defined start trigger, observable outcome and measurable success metric. Each journey names its primary personas, minimum permissions, visible data, failure path and success metric. Journeys are grouped by lifecycle phase: Onboarding, Tree building, Collaboration, Sharing & publication, Media, Source, DNA, Data subject rights, Platform operations.

### 2.1 Onboarding journeys

#### J-ONB-1: Tenant and owner creation (SaaS)

- Trigger: Anonymous visitor signs up for the SaaS.
- Personas: Owner, Anonymous Visitor.
- Pre-conditions: email reachable, ToS accepted.
- Minimum permissions: anonymous → authenticated via Keycloak; Keycloak subject `sub` recorded; tenant created with `OWNER` role; default policy and quota applied.
- Data visible: own membership, own audit, billing adapter stubs.
- Failure path: email verification fails, billing adapter unavailable, terms unaccepted; the tenant must remain inactive and the user prompted.
- Success metric: `time_to_first_tree` p75 ≤ 10 minutes for SaaS; ≥ 80% of created tenants reach the first person milestone in 7 days.
- References: R1.1, R1.4, R2.1, R2.5.

#### J-ONB-2: Tenant creation (on-premise)

- Trigger: Operator bootstraps a tenant via Helm-released config-as-code or admin console.
- Personas: Operator, Owner.
- Pre-conditions: license file or feature flags loaded; operator has the platform role.
- Minimum permissions: Operator JIT access with banner; Owner auto-receives local credentials.
- Data visible: tenant metadata, license/plan.
- Failure path: license missing, DB unreachable, IdP unavailable; install fails closed.
- Success metric: install-to-owner-ready time ≤ 30 minutes; restore from snapshot restores owner login.
- References: R1.5, NFR6, E2, E14.

#### J-ONB-3: Member invitation

- Trigger: Owner or Admin invites a User.
- Personas: Owner/Admin (inviter), Viewer/Editor/Contributor (invitee).
- Minimum permissions: inviter has `ADMIN+`; invitee token bounded.
- Data visible: invitee sees only the scoped tree summary until invitation accepted.
- Failure path: inviter revoked before acceptance; invite expired; tenant quota full.
- Success metric: invite-to-acceptance median ≤ 24 h; revocation effective ≤ 5 s downstream.
- References: R1.2, R3.1, E3.2.

### 2.2 Tree building journeys

#### J-TREE-1: Create first tree

- Trigger: Owner/Editor enters the workspace.
- Personas: Owner/Editor.
- Minimum permissions: `TREE_ADMIN` on a new tree, defaults to `PRIVATE`.
- Data visible: empty tree shell, default calendar/locale.
- Failure path: tenant quota exceeded; concurrent tree creation race resolved with optimistic versioning.
- Success metric: `time_to_first_person` p75 ≤ 5 minutes.
- References: R3.1, R3.2, R3.3.

#### J-TREE-2: Manual person creation

- Trigger: Editor adds a person with names, dates, places, events.
- Personas: Editor.
- Minimum permissions: `TREE_ADMIN`/`EDITOR` on the branch.
- Data visible: existing tree graph, alias dictionary, place authority suggestions.
- Failure path: invalid calendar conversion, ambiguous date, cycle attempt detected and blocked (R5.4), duplicate candidate surfaced.
- Success metric: median input-to-save latency ≤ 600 ms; 0 cycles introduced; duplicate detection recall ≥ 0.85.
- References: R4.1–R4.6, R5.

#### J-TREE-3: Relationship entry with invariants

- Trigger: Editor connects two persons.
- Personas: Editor.
- Minimum permissions: branch-scoped write.
- Data visible: impacted sub-graph only.
- Failure path: cycle detected, chronological conflict, dispute with existing asserted relationship.
- Success metric: ≥ 99% of attempted valid relationships accepted without rework; chronological conflicts surface as warnings, not silent failures.
- References: R5.1–R5.4, E4.4.

#### J-TREE-4: Merge duplicate candidates

- Trigger: Editor selects a candidate from duplicate detection.
- Personas: Editor, Auditor (read).
- Minimum permissions: branch admin; review re-authorized at approval time (E6.2).
- Data visible: comparison view, sources supporting each side.
- Failure path: conflicting citations, third-party references that would be lost, jurisdictional lock on merging deceased living-protection records.
- Success metric: merge reversal exercised in tests; ≥ 99% merges preserve citations.
- References: R4.5, R5, E4.6.

### 2.3 Import and bulk journeys

#### J-IMP-1: GEDCOM import

- Trigger: Editor/Genealogist uploads GEDCOM file.
- Personas: Editor or Genealogist.
- Minimum permissions: tree write + import quota entitlement.
- Data visible: dry-run report, mapping suggestions, duplicate candidates, missing citation flags.
- Failure path: malformed file, malicious payload, large file exceeding tenant quota; importer streams with progress and checkpoints.
- Success metric: import completes for 95% of valid files ≤ 1 M records without restart; resume-from-checkpoint works; 0 domain rows written before dry-run confirmation.
- References: R8.5, R12.1–R12.2, E9.2, E9.3.

#### J-IMP-2: Export bundle

- Trigger: Owner/Editor requests export.
- Personas: Owner, Editor, Auditor.
- Minimum permissions: explicit export scope, redaction preview before commit.
- Data visible: preview shows counts and sample after redaction.
- Failure path: redaction fails on a living person field; export aborted for that branch; user prompted to adjust scope.
- Success metric: 0 living persons leak; checksum manifest verifies; signed URL expires per policy.
- References: R12.3–R12.4, E9.4.

### 2.4 Collaboration journeys

#### J-COL-1: Submit proposal

- Trigger: Contributor proposes a change.
- Personas: Contributor, Editor (reviewer).
- Minimum permissions: `CONTRIBUTOR` write; reviewer `EDITOR`/`TREE_ADMIN`.
- Data visible: normalized diff, source attachments, comparison against base version.
- Failure path: base version outdated, re-authorization fails on review, scope conflict.
- Success metric: ≤ 24 h median review SLA for active tenants; ≥ 95% review re-authorization matches submit-time intent.
- References: R10, E6.2.

#### J-COL-2: Mixed direct-edit / approval

- Trigger: Editor edits a branch where policy allows direct edit.
- Personas: Editor, Auditor.
- Minimum permissions: branch-scoped write; Flagsmith flag determines mode per role/resource.
- Data visible: same as proposal view.
- Failure path: flag temporarily off, concurrent conflicting edit, policy change mid-edit.
- Success metric: 0% of direct-edit actions bypass ABAC re-check.
- References: R3.7, R10.4, E6.3.

#### J-COL-3: Comment, mention, watch, activity feed

- Trigger: User interacts with a tree artifact.
- Personas: any authenticated tree user.
- Minimum permissions: read or write per resource.
- Data visible: permission-filtered activity; sensitive content replaced by generic placeholder.
- Failure path: revoked user; mention to non-member; cross-tenant mention attempt.
- Success metric: 0 PII leak in notifications; activity latency p95 ≤ 1 s for in-app inbox.
- References: R10.5, R14.3, E6.4, E11.2.

### 2.5 Sharing and publication journeys

#### J-SHARE-1: Unlisted share

- Trigger: Owner/Editor issues a share link.
- Personas: Owner/Editor (issuer), Viewer/Guest (recipient).
- Minimum permissions: token creator has branch write; token scoped to subtree + expiry.
- Data visible: redacted subtree; living persons redacted; noindex true; access logged.
- Failure path: revocation mid-share, quota exceeded, attempted public index discovery.
- Success metric: revocation effective ≤ 60 s; 0 living-person leaks after redaction; crawler scraping attempt blocked at rate-limit.
- References: R3.4–R3.5, E8.3.

#### J-SHARE-2: Public tree publication

- Trigger: Owner/Editor toggles branch to `PUBLIC`.
- Personas: Owner/Editor, Anonymous Visitor.
- Minimum permissions: branch admin.
- Data visible: public projection only; living data redacted; DNA never included.
- Failure path: redaction job fails, projection stale, downstream cache poisoning.
- Success metric: ≤ 5 min re-projection lag; 0 living/minor/DNA in public index.
- References: R3.6, R13.7, E8.1, E8.3.

#### J-SHARE-3: Print and PDF export

- Trigger: Viewer requests printable view.
- Personas: Viewer, Owner.
- Minimum permissions: read on the subtree; PDF generation via Temporal + Gotenberg.
- Data visible: same redacted data as on-screen; watermark with token hash.
- Failure path: Gotenberg unavailable, long generation, expired signed URL.
- Success metric: report ready p95 ≤ 30 s for ≤ 200 pages; redaction verified before dispatch.
- References: R12.4, E5.6, E7.3, E11.3.

### 2.6 Media journeys

#### J-MED-1: Upload photo

- Trigger: Editor selects file.
- Personas: Editor.
- Minimum permissions: tree write + media quota + signed URL entitlement.
- Data visible: upload progress, quarantine status, processing pipeline.
- Failure path: MIME mismatch, malware detected, malware scanner unavailable, quota exhausted.
- Success metric: 0% of un-scanned assets ever become linkable; ≤ 60 s from upload to READY for typical JPEG.
- References: R9.1–R9.6, E7.1, E7.2.

#### J-MED-2: Link media to person

- Trigger: Editor attaches asset to person or event.
- Personas: Editor.
- Minimum permissions: write on asset, write on person, ABAC re-check at link time.
- Data visible: candidate persons; permission-filtered album suggestions.
- Failure path: asset still quarantined; subject has no permission.
- Success metric: media asset references enforced; soft-delete cascade correct.
- References: R9.5, E7.5.

#### J-MED-3: DNA raw upload (isolated)

- Trigger: DNA owner or guardian uploads raw genotype.
- Personas: DNA Owner, Guardian.
- Minimum permissions: explicit DNA opt-in flag enabled by tenant, dedicated consent, envelope encryption key fetched from Vault/KMS.
- Data visible: consent receipt, kit metadata only; raw data opaque to non-DNA services.
- Failure path: KMS unavailable, sandbox worker crash, invalid format; quarantine state held.
- Success metric: 0 raw genotype bytes in non-DNA buckets, logs, events, traces or search; consent receipt issued ≤ 2 s.
- References: R13.1–R13.8, E10.2, E10.4.

### 2.7 Source and research journeys

#### J-RES-1: Add citation

- Trigger: Editor or Contributor attaches a citation to a claim.
- Personas: Editor, Contributor.
- Minimum permissions: claim visibility; citation write.
- Data visible: source metadata, transcription snippet (redacted), quality assessment.
- Failure path: source already archived, repository quota full, conflicting citation.
- Success metric: ≥ 95% of new claims include ≥ 1 citation; provenance query latency p95 ≤ 200 ms.
- References: R8, E6.1.

#### J-RES-2: Hypothesis tracking

- Trigger: Genealogist creates a hypothesis with alternatives.
- Personas: Genealogist, Editor (reviewer).
- Minimum permissions: research write.
- Data visible: hypothesis tree, alternative states, evidence list.
- Failure path: reviewer disputed; new evidence invalidates hypothesis.
- Success metric: 0% of hypotheses treated as verified facts in downstream projections.
- References: R5.5, E6.1.

### 2.8 DNA journeys

#### J-DNA-1: Consent grant

- Trigger: DNA Owner grants a purpose-bound consent.
- Personas: DNA Owner, Guardian (when applicable), Auditor.
- Minimum permissions: subject-bound consent grant; policy version captured.
- Data visible: consent ledger entry; never the data the consent will unlock yet.
- Failure path: policy version mismatch, guardian workflow blocked, jurisdiction restriction.
- Success metric: consent receipt issued for 100% of grants; revocation propagated ≤ 60 s.
- References: R13.3, E10.3.

#### J-DNA-2: Match request

- Trigger: DNA Owner or researcher requests match.
- Personas: DNA Owner, Researcher (DNA-scoped), Operator (just observes telemetry).
- Minimum permissions: matching purpose consented, isolated Temporal worker.
- Data visible: list of candidate matches with relationship estimate; never raw genotype.
- Failure path: consent revoked mid-flight, worker failure, queue overflow.
- Success metric: 0 raw genotype leaked to non-DNA workers; revocation aborts running activity within heartbeat.
- References: R13.5, E10.4.

#### J-DNA-3: DNA revoke/export/delete

- Trigger: DNA Owner or Guardian requests revocation.
- Personas: DNA Owner, Guardian, Auditor.
- Minimum permissions: revocation requires step-up auth; deletion evidence required.
- Data visible: revocation ledger entry; deletion report without content.
- Failure path: legal hold conflict, ongoing court order, derived data sprawl.
- Success metric: 100% of derived caches purged or quarantined within target RPO; legal-hold precedence respected.
- References: R13.4, R16, E10.5.

### 2.9 Data subject rights journeys

#### J-DSR-1: Data subject access request

- Trigger: User or DNA Owner requests copy of their data.
- Personas: Subject, Owner (tenant approval), Auditor.
- Minimum permissions: subject identity verified; tenant admin authorises release if request crosses subjects.
- Data visible: machine-readable export bundle; redaction preview if shared with sponsor.
- Failure path: identity verification fails, legal hold, third-party data impossible to extract.
- Success metric: ≤ 30 d turnaround (configurable per jurisdiction); 100% of requests audited.
- References: R16.5, E11.5, E14.

#### J-DSR-2: Correction / restriction / deletion

- Trigger: Subject requests change to their record.
- Personas: Subject, Editor/Owner, Auditor.
- Minimum permissions: identity verification, edit per branch, audit evidence.
- Data visible: change diff and/or tombstone.
- Failure path: historical record conflict, legal hold, sourced citation must be preserved.
- Success metric: every change traces to subject request; legal-hold items correctly excluded.
- References: R4.5, R5, R16.5.

### 2.10 Platform operations journeys

#### J-OPS-1: Support access with approval

- Trigger: Operator opens a support ticket; tenant Owner/Admin approves.
- Personas: Operator, Owner/Admin, Auditor.
- Minimum permissions: JIT, step-up auth, banner visible, action log signed.
- Data visible: only the data scope approved.
- Failure path: approval revoked; banner bypass attempt; timer expires.
- Success metric: 100% of JIT sessions logged with reason and TTL; 0% bypass DNA/consent.
- References: R16.3–R16.4, E11.5.

#### J-OPS-2: Restore drill

- Trigger: Operator schedules a quarterly restore drill.
- Personas: Operator, Auditor.
- Minimum permissions: backup admin role, isolated environment.
- Data visible: catalog of artifacts restored; reconciliation diff (counts only).
- Failure path: backup missing, restore order violated, projection rebuild fails.
- Success metric: RPO ≤ 15 min, RTO ≤ 4 h measured; reconciliation passes.
- References: NFR3, E13, E14.

#### J-OPS-3: Feature flag rollout / kill switch

- Trigger: Product owner toggles Flagsmith flag.
- Personas: Owner (flag authority), Operator (deploys).
- Minimum permissions: Flagsmith role-bound; per-tenant scope; safe fallback coded.
- Data visible: flag audit entry.
- Failure path: Flagsmith outage → SDK uses safe fallback; flag with security implication disabled by default.
- Success metric: 0% security/consent bypass via flag.
- References: E2.8.

## 3. Failure mode summary

| Failure event | Impacted journeys | Required handling |
|---|---|---|
| Lost tenant owner | J-ONB-1, J-ONB-3, J-DSR-1 | Transfer workflow, admin hold, owner recovery with re-verification |
| Tenant suspension | All tenant journeys | API return `tenant_suspended` problem; queued jobs paused |
| Living person falsely labelled deceased | J-TREE-2, J-TREE-4, J-SHARE-2 | Living re-inference + audit reversal; public projection rebuild |
| DNA consent revoked mid-job | J-DNA-2, J-DNA-3 | Cancellation + purge; legal hold may override purge |
| GEDCOM parser exploit | J-IMP-1 | Sandbox, resource cap, kill switch via Flagsmith |
| Media malware detected | J-MED-1, J-MED-2 | Quarantine, alert, CVEs tracked in audit |
| Flagsmith or OIDC outage | many journeys | Safe default fallbacks (degraded UX, fail-closed for auth) |
| Audit tampering attempt | J-OPS-1, J-DSR-* | Append-only chain evidence; auditor alerted; restore from immutable archive |

## 4. Success metrics roll-up

| Metric group | Target | Anchor |
|---|---|---|
| Activation | time_to_first_tree; first-person creation rate | J-ONB-1, J-TREE-1 |
| Collaboration throughput | proposal review latency, merge reversal success | J-COL-1, J-TREE-4 |
| Sharing safety | 0 living-person leaks, 0 DNA leaks | J-SHARE-1..2, J-MED-3, J-DNA-2 |
| Data integrity | 0 unsourced critical claims in production | J-RES-1 |
| Privacy & DSR | DSAR turnaround, revocation propagation | J-DSR-1, J-DNA-3 |
| Platform reliability | RPO/RTO adherence; service availability | J-OPS-2 |

## 5. Downstream traceability

- E0.2 (glossary & policy matrix) consumes §1 personas and §2 permission minima to assemble decision tables.
- E0.3 (scale & SLO) consumes §4 success metrics as seed objectives.
- E0.4 (privacy/legal gate) consumes §2.8–§2.10 and §3 for DPIA scope.
- E0.5 (ADR closure) is independent but E0.1 feeds it product/operator constraints.
- E0.6 (ownership catalog) consumes §1 personas to assign service/platform owners.
- Epic E3 (identity) and E4 (genealogy) implement J-ONB-*, J-TREE-*, J-COL-*.
- Epic E5/E7/E8 implement J-SHARE-*, J-MED-*, J-SHARE-2 respectively.
- Epic E9 implements J-IMP-*.
- Epic E10 implements §2.8 journeys end to end.
- Epic E11 implements §2.10 journeys plus notification guarantees.
- Epic E14/E15 verify J-OPS-2 and J-OPS-3.

## 6. Open questions handed to other E0 tasks

- Confirm exact entitlement keys for each role (E0.2).
- Confirm legal/jurisdiction scope before enabling DNA journeys (E0.4).
- Confirm SaaS/on-premise plan names and quotas that gate J-ONB-* (E0.3/E0.5).
- Confirm which consent templates are required for first release of DNA journeys (E0.4).
