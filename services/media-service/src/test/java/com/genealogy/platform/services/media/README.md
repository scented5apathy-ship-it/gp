# services/media-service/src/test/java/com/genealogy/platform/services/media

Canonical unit + contract tests for the **MediaAsset / MediaVariant / Album** service.

Per `design.md` §4 and `ownership-catalog.md` §2.5 this
package is owned by `Media team` and implements the
aggregate, command handlers, gRPC service, REST controllers,
outbox publisher and tests for the
`MediaAsset / MediaVariant / Album` domain. Real implementation lands in later
epics (E7).

Contents:

- `UmediaServiceSkeletonTest.java`

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
