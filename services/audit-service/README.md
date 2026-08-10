# audit-service

Append-only WORM ledger backing every audit event emitted by
every other service. E3.6 ships the ledger, the integrity hash
chain, the retention sweeper evidence and the export bundle
contract.

Source of truth:

- `contracts/audit/policy.yaml` — audit-class taxonomy + action
  catalogue + integrity fields + redaction triggers.
- `contracts/audit/retention.yaml` — per-class retention tiers +
  legal-hold enforcement.
- `contracts/audit/redaction.yaml` — field-level denyKeys +
  maskKeys + scrub patterns.
- `contracts/audit/export.yaml` — DPO / auditor bundle manifest
  - signed-URL contract.
- `platform/helm/genealogy-platform/files/audit-*.yaml` —
  byte-identical chart mirrors (enforced by
  `scripts/lint-audit-config.mjs`).

## Architecture

```
+-----------------+   JSON envelope    +--------------------+
| every service   | --Kafka audit----> | audit-service      |
| (KafkaAuditPub) |   topic            | (this module)      |
+-----------------+                    +---------+----------+
                                                  |
                                                  v
                                       +-------------------+
                                       | audit_service     |
                                       | .audit_entry      |
                                       | (append-only,     |
                                       |  RLS, hash chain) |
                                       +-------------------+
                                                  |
                                                  v
                                       +-------------------+
                                       | RetentionSweeper  |
                                       | (writes           |
                                       |  deletion_evidence|
                                       |  append-only)     |
                                       +-------------------+
                                                  |
                                                  v
                                       +-------------------+
                                       | ExportService     |
                                       | (DPO bundle +     |
                                       |  signed URL +     |
                                       |  manifest hash)   |
                                       +-------------------+
```

## Code layout

```
services/audit-service/src/main/java/com/genealogy/platform/services/audit/
├── config/ApplicationConfig.java         Spring wiring (interfaces only)
├── domain/AuditEntry.java                Value record (idempotency key + hash fields)
├── domain/HashChainComputer.java         SHA-256 + verify()
├── domain/IntegrityStatus.java           ok / INTEGRITY_BREACH result
├── export/ExportService.java             Two-person rule + manifest hash + signed URL contract
├── ingest/AuditIngestService.java        Idempotent inbox (event_id dedupe)
├── integrity/IntegrityVerifier.java      Walk-the-chain verification
├── kafka/AuditKafkaListener.java         JSON envelope -> ingest
├── persistence/AuditEntryRepository.java Interface
├── persistence/JdbcAuditEntryRepository.java jOOQ-free JdbcTemplate implementation
└── retention/RetentionSweeper.java       Per-class sweep + deletion_evidence writer

services/audit-service/src/main/resources/
├── application.yml                        audit.ingest.* + audit.retention.* + audit.integrity.*
└── db/migration/
    ├── V1__audit_ledger.sql               audit_service.audit_entry + RLS + append-only trigger
    └── V2__deletion_evidence.sql          audit_service.deletion_evidence + append-only trigger
```

## Privacy guarantees

- **Append-only.** The `audit_entry` and `deletion_evidence`
  tables grant `INSERT, SELECT` to the application role and
  revoke `UPDATE, DELETE, TRUNCATE`. The row trigger
  (`trg_audit_entry_append_only`) raises on any attempt to mutate
  an existing row.
- **Tenant isolation.** RLS is enabled and `FORCE`d so the table
  owner is also bound by `audit_tenant_isolation`. The DPO role
  (`audit_service_dpo`) can SELECT across tenants for export but
  cannot mutate anything.
- **No raw DNA / PII / tokens.** The publisher-side
  `AuditRedactor` drops `denyKeys`, masks `maskKeys` and scrubs
  free-text patterns BEFORE the event leaves the originating
  service. The audit-service is a sink — it never accepts
  unredacted payloads.
- **No claim of unbreakability.** The hash chain detects any
  tamper; it does NOT prevent it. The retention sweeper aborts
  on `INTEGRITY_BREACH` (`onTamperDetected: ABORT_AND_ALERT`)
  and the verification cron fires the same alert.

## Owner

- Domain owner: Security Engineering team.
- Privacy owner: DPO delegate (retention evidence).
- Security owner: Security Engineering (separation of duties).
- SRE / on-call lead: sre-primary (Tier-0 24x7).
