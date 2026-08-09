# S3/MinIO + Valkey (E2.7) — source-of-truth

This directory holds the source-of-truth configuration for the
platform's S3-compatible object storage (MinIO on-prem + dev,
AWS S3 on SaaS) and the Valkey (Redis-compatible) cache layer.

Per `tasks.md` E2.7 + `design.md` §11 + §13 + ADR-E0.5-03, the
declarative files in this directory are:

| File                                            | Purpose                                                                                          |
| ----------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| `s3-config.yaml`                                | MinIO server posture (image pin, region, TLS, CORS, versioning, replication, SSE-KMS, audit)    |
| `bucket-policy.yaml`                            | 4 buckets (`media`, `media-quarantine`, `dna-raw`, `import-export`) with per-bucket lifecycle / KMS / IAM / CORS / object lock / signed URL |
| `compatibility-matrix.yaml`                     | S3 API operations that must work identically on AWS S3 + MinIO (the E2.7 compatibility test)     |
| `valkey-config.yaml`                            | Valkey server posture (image pin, region, TLS, ACL, persistence, TTL ceilings, required users)  |
| `OWNERS`                                        | Mirrors `config/teams.yaml` — primary, secondary, on-call for `storage` + `cache`                |

The deep linter (`scripts/lint-s3-config.mjs`) enforces:

- Every bucket has versioning + lifecycle + KMS key alias + IAM
  policy + CORS allowlist (where applicable).
- Every bucket prefix template includes `{tenant_pseudo_id}`.
- No raw tenant UUID / raw PII / raw DNA in any prefix template.
- No public READ ACL on the `media` bucket.
- Signed URL TTL ≤ 1 hour (export bucket overrides to 15 min).
- Valkey ACL forbids `@admin` for service users (only `operator`).
- Valkey key patterns forbid raw DNA / password / token storage.
- Valkey `maxmemory-policy` is `allkeys-lru` (no `noeviction`).
- Every per-class TTL ceiling is enforced.
- The S3 compatibility matrix declares every operation the
  domain code needs.

The chart mirrors these files byte-identical into
`platform/helm/genealogy-platform/files/storage/`. The
`scripts/lint-s3-config.mjs` linter rejects drift.

The smoke probe (`scripts/smoke-s3.mjs`) brings up the MinIO
dev server on a disposable kind cluster (or runs structural-only
when kind / kubectl / helm are not on PATH) and asserts the
posture is intact.

The runbook (`runbook/s3.md` + `runbook/valkey.md`) covers the
operator-facing procedures: bucket recovery, replication lag,
Valkey failover, ACL reset and signature-URL rotation.

See also:

- `docs/platform-setup.md` §4.7 — local + cluster setup
- `docs/platform-setup.md` §4.8 — E2.7 verification commands
- `.kiro/specs/genealogy-platform/ownership-catalog.md` §3 —
  ownership + SLO
- `.kiro/specs/genealogy-platform/evidence/E2.7.md` — completion
  evidence
