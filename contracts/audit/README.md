# contracts/audit — Audit foundation contracts (E3.6)

Source-of-truth YAML contracts for the audit foundation. Per
`agent-execution.md` §4.4 the YAML is the contract and the Java
executors mirror it. The matching implementations live in
`services/audit-service/` (ledger, retention sweeper, export
service) and `libs/platform-spring-boot-starter/.../audit/`
(redaction, validation).

| File             | Contract                                                                                                                                                                                                                               |
| ---------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `policy.yaml`    | Audit-class taxonomy (auth / authorization / policy / support / download / consent), closed-set action catalogue, integrity (SHA-256 hash chain, genesis hash, scheduled verification cron, tamper marker), redaction trigger classes. |
| `retention.yaml` | Default + per-class retention tiers (hot/warm/cold) and WORM tiers. Sweep cadence, legal-hold enforcement (HARD_BLOCK + DPO override), deletion-evidence payload.                                                                      |
| `redaction.yaml` | Field-level denyKeys / maskKeys + free-text scrub patterns. Mirrors the `contracts/abac/redaction.yaml` taxonomy with audit-specific additions (ipAddress, userAgent, token / JWT scrub).                                              |
| `export.yaml`    | DPO / auditor export bundle: request envelope, manifest fields, payload schema, signed URL contract (Kong, TTL, role gating), integrity manifest signature, access-log audit class.                                                    |

The chart mirror lives at
`platform/helm/genealogy-platform/files/audit-*.yaml` (byte-identical,
enforced by `scripts/lint-audit-config.mjs`).

## Validation

```bash
pnpm lint:audit
pnpm check:audit
```

The linter asserts:

- `policy.yaml` declares the closed-set of `auditClasses` (exactly
  the 6 from `tasks.md` line 310: auth / authorization / policy /
  support / download / consent) and the full `actions` catalogue,
  `integrity.hashAlgorithm: SHA-256`, `genesisHash` of 64 zeros,
  `verificationCadence: scheduled`.
- `retention.yaml` declares all 6 per-class tiers and the
  `legalHold.enforcement: HARD_BLOCK`.
- `redaction.yaml` declares `rawDna` + `biography` in `denyKeys`
  and `email` + `phone` in `maskKeys`.
- `export.yaml` declares `manifest.required: true`,
  `signedUrl.requiresDpoRole: true`, `accessLog.auditClass:
support`.
- No literal secret / token / password / DSN in any source-of-truth
  file.
- The chart mirror files are byte-identical to the contracts.

## Change protocol

1. Edit `contracts/audit/*.yaml`.
2. Mirror to `platform/helm/genealogy-platform/files/audit-*.yaml`
   (`scripts/lint-audit-config.mjs` fails if they drift).
3. Update the matching Java executor
   (`AuditClassRegistry` / `RetentionPolicy` /
   `AuditRedactor` / `ExportService`) + tests when the contract
   changes.
4. Bump `spec.policyId` minor version (e.g. `default-audit/v1` →
   `default-audit/v2`) when the change is breaking; otherwise
   append a note in the file header.

## Owner

- Domain owner: Security Engineering team (`identity` per
  `ownership-catalog.md` §2.11).
- Privacy owner: DPO delegate (retention evidence).
- Security owner: Security Engineering (separation of duties).
