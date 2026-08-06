# services/importexport-service/src/test/java/com/genealogy/platform/services/importexport

Canonical unit + contract tests for the **TransferJob / MappingProfile / ExportManifest** service.

Per `design.md` §4 and `ownership-catalog.md` §2.7 this
package is owned by `Interop team` and implements the
aggregate, command handlers, gRPC service, REST controllers,
outbox publisher and tests for the
`TransferJob / MappingProfile / ExportManifest` domain. Real implementation lands in later
epics (E9).

Contents:

- `UimportexportServiceSkeletonTest.java`

Boundary rules enforced by CI (`AGENTS.md` §2):

- No file under `services/importexport-service/` may import another service's
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
### 2.7 import-export-service (E9)

| Field               | Value                                                                                                                   |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Domain owner        | Interop team                                                                                                            |
| Product owner       | Power-user + Partner journeys                                                                                           |
| Privacy owner       | DPO delegate (Export redaction, DNA opt-out)                                                                            |
| Security owner      | AppSec partner (Parser sandbox, SSRF)                                                                                   |
| SRE / on-call lead  | sre-primary                                                                                                             |
| Owns aggregate/data | `TransferJob`, `MappingProfile`, `ExportManifest`                                                                       |
| Public REST         | `services/import-export-service/openapi.yaml` (job lifecycle + signed URL)                                              |
| gRPC                | `gp.interop.v1.{TransferService, MappingProfileService}`                                                                |
| Events published    | `gp.interop.v1.{TransferStarted, TransferProgressed, TransferCompleted, TransferFailed, MappingSaved, ExportDelivered}` |
| Events consumed     | `gp.tenant.v1.MembershipRevoked`                                                                                        |
```
Owner: `services/importexport-service/OWNERS`.
