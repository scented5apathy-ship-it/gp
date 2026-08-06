# services/dna-service/src/main/java/com/genealogy/platform/services/dna

Canonical production code for the **DnaKit / Consent / DnaMatch / Segment** service.

Per `design.md` §4 and `ownership-catalog.md` §2.8 this
package is owned by `DNA team` and implements the
aggregate, command handlers, gRPC service, REST controllers,
outbox publisher and tests for the
`DnaKit / Consent / DnaMatch / Segment` domain. Real implementation lands in later
epics (E10).

Contents:

- `package-info.java`

Boundary rules enforced by CI (`AGENTS.md` §2):

- No file under `services/dna-service/` may import another service's
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
### 2.8 dna-service (E10)

| Field               | Value                                                                                                                                                                                      |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Domain owner        | DNA team                                                                                                                                                                                   |
| Product owner       | DNA owner + Guardian journeys                                                                                                                                                              |
| Privacy owner       | DPO delegate (DNA module)                                                                                                                                                                  |
| Security owner      | AppSec partner (DNA isolation); Security on-call lead during incidents                                                                                                                     |
| SRE / on-call lead  | sre-primary + privacy-secondary (dual on-call)                                                                                                                                             |
| Owns aggregate/data | `DnaKit`, `Consent`, `DnaMatch`, `Segment` in dedicated schema/bucket/KMS key per ADR-E0.5-15                                                                                              |
| Public REST         | `services/dna-service/openapi.yaml` (consent + upload session + match query)                                                                                                               |
| gRPC                | `gp.dna.v1.{ConsentService, KitService, MatchService}`                                                                                                                                     |
| Events published    | `gp.dna.v1.{ConsentGranted, ConsentRevoked, KitUploaded, MatchProduced, KitDeleted}`                                                                                                       |
| Events consumed     | `gp.tenant.v1.MembershipRevoked` (mandatory revocation across all DNA flows)                                                                                                               |
```
Owner: `services/dna-service/OWNERS`.
