# services/genealogy-service/src/test/java/com/genealogy/platform/services/genealogy

Canonical unit + contract tests for the **Tree / Person / Relationship / LifeEvent / Claim** service.

Per `design.md` §4 and `ownership-catalog.md` §2.2 this
package is owned by `Core Genealogy team` and implements the
aggregate, command handlers, gRPC service, REST controllers,
outbox publisher and tests for the
`Tree / Person / Relationship / LifeEvent / Claim` domain. Real implementation lands in later
epics (E4.x).

Contents:

- `GenealogyServiceSkeletonTest.java`

Boundary rules enforced by CI (`AGENTS.md` §2):

- No file under `services/genealogy-service/` may import another service's
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
### 2.2 genealogy-service (E4)

| Field                   | Value                                                                                                   |
| ----------------------- | ------------------------------------------------------------------------------------------------------- |
| Domain owner            | Core Genealogy team (lead placeholder: EM-Genealogy)                                                    |
| Product owner           | Editor + Researcher journeys                                                                            |
| Privacy owner           | DPO delegate (Living/Minor redaction)                                                                   |
| Security owner          | AppSec partner (Core domain)                                                                            |
| SRE / on-call lead      | sre-primary                                                                                             |
| Owns aggregate/data     | `Tree`, `Person`, `Relationship`, `LifeEvent`, `Claim` (per `design.md` §4)                             |
| Public REST             | `services/genealogy-service/openapi.yaml` (CRUD + visibility + merge endpoints)                         |
| gRPC                    | `gp.genealogy.v1.{TreeService, PersonService, RelationshipService, ClaimService}`                       |
| Events published        | `gp.genealogy.v1.{TreeVisibilityChanged, PersonRedacted, ClaimMerged, MergeReversed}`                   |
| Events consumed         | `gp.tenant.v1.MembershipRevoked` (cache invalidation), `gp.research.v1.ClaimVerified`                   |
```
Owner: `services/genealogy-service/OWNERS`.
