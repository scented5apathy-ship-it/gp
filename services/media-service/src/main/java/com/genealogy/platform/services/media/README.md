# services/media-service/src/main/java/com/genealogy/platform/services/media

Canonical production code for the **MediaAsset / MediaVariant / Album** service.

Per `design.md` §4 and `ownership-catalog.md` §2.5 this
package is owned by `Media team` and implements the
aggregate, command handlers, gRPC service, REST controllers,
outbox publisher and tests for the
`MediaAsset / MediaVariant / Album` domain. Real implementation lands in later
epics (E7).

Contents:

- `package-info.java`

Boundary rules enforced by CI (`AGENTS.md` §2):

- No file under `services/media-service/` may import another service's
  `db/` or `domain/` package.
- Shared cross-cutting concerns live in
  `libs/platform-{errors, feature-flags, security, telemetry,
  spring-boot-starter}`, not here.
- Cross-service interaction happens via gRPC + Kafka events,
  never via shared database tables or domain imports.
- RLS is mandatory on every tenant-scoped query
  (`design.md` §5.1).

Ownership row from `ownership-catalog.md`:

```
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
```

Owner: `services/media-service/OWNERS`.

# E7.1 upload lifecycle

`media-service` ships the E7.1 upload lifecycle domain
under `com.genealogy.platform.services.media.domain`:

- `UploadSession` — the `REQUESTED -> SIGNED -> UPLOADING
  -> FINALIZING -> QUARANTINED -> READY / REJECTED /
  ABANDONED / FAILED` state machine with TTL + checksum +
  MIME + intent + scope metadata.
- `MultipartPart` — a received multipart part with size /
  checksum / sequence constraints.
- `QuotaLedger` — tenant-scoped bytes / items / seconds
  reservation ledger.
- `MimePolicy` — closed-set MIME allow / deny list with
  sandbox + deep-scan classification.
- `ChecksumVerifier` — deterministic constant-time
  checksum matcher.
- `QuarantineGate` — admission gate from `QUARANTINED` to
  `READY` / `REJECTED`.
- `AbandonedMultipartSweeper` — Temporal workflow helper
  that reaps unused sessions.
- `UploadAuthorizer` — pure executor that maps intent +
  media category + object key to an
  `UploadAuthorizationDecision`.
- `MediaInvariants` — pure invariant checker emitting
  `DENY` / `WARN` / `INFO` findings.
- `UploadAuthorizationPort` — port interface delegating
  to OpenFGA + ABAC at the application layer.

The contract lives at
`contracts/media/upload-lifecycle-policy.yaml`; the
mirror is at
`platform/helm/genealogy-platform/files/media-upload-lifecycle-policy.yaml`.
The linter is `scripts/lint-media-upload-lifecycle.mjs`.

E7.1 ships the pure domain + invariants + executor only:
the Flyway migration + jOOQ repository + S3 / MinIO
signed-URL adapter + Kafka producer / consumer land in
the later E7.x / E11.x sub-epics.
