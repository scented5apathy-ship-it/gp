# Audit foundation (E3.6) — operator runbook

This runbook describes the audit foundation that ships with E3.6.
It is the operator-side companion to:

- `contracts/audit/policy.yaml` — the audit-class taxonomy +
  action catalogue + integrity fields.
- `contracts/audit/retention.yaml` — per-class retention tiers +
  legal-hold enforcement.
- `contracts/audit/redaction.yaml` — deny / mask / scrub lists.
- `contracts/audit/export.yaml` — DPO / auditor export bundle.
- `platform/helm/genealogy-platform/files/audit-*.yaml` — the
  byte-identical chart mirrors (enforced by
  `scripts/lint-audit-config.mjs`).

## Source of truth

| Concern                  | Source                                                                                          |
| ------------------------ | ----------------------------------------------------------------------------------------------- |
| 6 audit classes          | `policy.yaml::spec.auditClasses` (auth / authorization / policy / support / download / consent) |
| Closed-set actions       | `policy.yaml::spec.actions` (39 entries; ADRs required to add)                                  |
| Integrity hash algorithm | `policy.yaml::spec.integrity.hashAlgorithm: SHA-256`                                            |
| Genesis hash             | `policy.yaml::spec.integrity.genesisHash: 0x00..00 (64 zeros)`                                  |
| Verification cadence     | `policy.yaml::spec.integrity.verificationCadence: scheduled`                                    |
| Tamper marker            | `policy.yaml::spec.integrity.tamperEvidenceMarker: INTEGRITY_BREACH`                            |
| Retention tiers          | `retention.yaml::spec.defaultTiers` + `spec.perClassTiers`                                      |
| Legal-hold enforcement   | `retention.yaml::spec.legalHold.enforcement: HARD_BLOCK`                                        |
| Sweep cadence            | `retention.yaml::spec.sweep.cadenceCron: "0 0 2 * * *"` (daily 02:00 UTC)                       |
| Deletion evidence        | `retention.yaml::spec.deletionEvidence.required: true`                                          |
| Deny / mask / scrub      | `redaction.yaml::spec.{denyKeys, maskKeys, scrubPatterns}`                                      |
| Manifest fields          | `export.yaml::spec.responseEnvelope.manifest.fields`                                            |
| Signed URL               | `export.yaml::spec.signedUrl.{requiresDpoRole, ttlSeconds, storageClass}`                       |

Additions to the closed sets require an ADR per `agent-execution.md`
§4.4 — the catalogue is the audit contract.

## Wire format

The audit topic carries the JSON emitted by
`platform-spring-boot-starter/.../audit/AuditEventEnvelope#toJson()`:

```json
{
  "eventId": "opaque-uuid",
  "tenantId": "opaque-id",
  "actorId": "keycloak-sub",
  "auditClass": "authorization",
  "action": "tenant.created",
  "resourceType": "tenant",
  "resourceId": "opaque-id",
  "reasonCode": "policy_version_unknown",
  "correlationId": "opaque-id",
  "occurredAt": "RFC-3339",
  "metadata": { "k": "v" }
}
```

Avro encoding is reserved for a follow-up epic that wires
Apicurio; the JSON shape is identical so producer + consumer
agree on the contract regardless of transport.

## Operational playbook

### Onboarding a new producer service

1. Add the service to the `OWNERS` / CODEOWNERS for the audit
   contracts (or accept `@genealogy/identity` as a reviewer).
2. Add `implementation(project(":libs:platform-spring-boot-starter"))`
   to the service's `build.gradle.kts` so it inherits the
   `AuditPublisher` + `AuditEventValidator` + `AuditRedactor`.
3. Inject `AuditPublisher` and call
   `publisher.publish(new AuditEvent(...))` after every successful
   mutation. The pipeline:
   - Validator rejects unknown actions / classes / missing tenantId.
   - Redactor drops `denyKeys`, masks `maskKeys`, scrubs free-text
     patterns.
   - Sink forwards the JSON envelope to the Kafka audit topic.
4. Unit-test the new mutation with an in-memory `AuditEventSink`
   (see `KafkaAuditPublisherTest`) to assert redaction + counters.

### Investigating a tamper (`INTEGRITY_BREACH`)

1. Find the affected event_id from the alert
   (`platform.audit.events.rejected` counter OR a
   `INTEGRITY_BREACH` log line from `IntegrityVerifier`).
2. Query the ledger:
   ```sql
   SELECT * FROM audit_service.audit_entry WHERE event_id = '<id>';
   ```
3. Compare the stored `entry_hash` to the canonical-bytes hash.
   If they differ, an in-flight row was modified — check
   `pg_stat_statements` for the offending transaction.
4. If the stored `previous_hash` differs from the chain head at
   the time, an insert bypassed the repository contract (i.e.
   direct SQL) — open an incident with Security Engineering.
5. Append a `support.read.executed` audit entry from the DPO
   session that ran the investigation so the chain stays
   continuous.

### Running a DPO export

1. Confirm the request is signed by both `requestedBy` and
   `approvedBy` (two-person rule, enforced by
   `ExportService#validateRequest`).
2. Confirm `spec.maxTimeWindowDays: 366` is respected.
3. Call `ExportService.exportBundle(...)`; the manifest hash is
   `sha256(canonicalManifestBytes)` and the bundle is uploaded
   to the S3 WORM bucket with a Kong-signed URL (TTL 15 minutes,
   `downloadLimit: 3`).
4. The download itself is logged as
   `support.read.executed` (audit class `support`).

### Retention sweep

- Cron: `0 0 2 * * *` (daily 02:00 UTC).
- For each audit class, count entries older than the hot tier
  and write one `deletion_evidence` row per batch.
- The actual storage-tier transition (Kafka → warm S3 → cold S3)
  is staged for a follow-up epic that provisions the WORM bucket.
- Hard-delete of aged-out rows is **NOT** performed by this MVP
  — the evidence is recorded and a manual runbook step transfers
  the tier.
- Legal hold overrides anything: a `legal_hold.active` flag
  blocks the sweep and emits a marker `deletion_evidence` row
  with `reason_code = LEGAL_HOLD_BLOCK`.

### Integrity verification

- Cron: `0 0 3 * * *` (daily 03:00 UTC).
- `IntegrityVerifier.verify(tenantId, auditClass, from, to)`
  walks the chain window and emits per-event `IntegrityStatus`.
- On `INTEGRITY_BREACH`, the retention sweeper for the affected
  tenant is paused (`onTamperDetected: ABORT_AND_ALERT`) and the
  alert is routed to the on-call SRE via the standard platform
  alerting path.

### Disabling audit in a dev environment

Set `platform.audit.enabled=false`. The publisher becomes a no-op
and the `platform.audit.events` counter stops incrementing. The
validator, redactor and Kafka topic are still wired so a flip
back to `true` does not require a redeploy.

### Out of scope (deferred to a follow-up epic)

- Actual S3 / MinIO WORM bucket provisioning
  (`platform/helm/genealogy-platform/files/storage/`).
- Grafana panels for audit retention and integrity breaches
  (E2.10 + E13.2).
- Full Kafka sink wiring + `spring-kafka` listener (the
  `AuditKafkaListener.onMessage(...)` method is the entry point;
  the `@KafkaListener` annotation is staged for the catalog
  update).
- Bulk DSR / DSAR export portal UI (E11.x).
