# services/collaboration-service/src/test/java/com/genealogy/platform/services/collaboration

Canonical unit + contract tests for the **ChangeProposal / Review / Comment / ActivityFeed** service.

Per `design.md` §4 and `ownership-catalog.md` §2.4 this
package is owned by `Collaboration team` and implements the
aggregate, command handlers, gRPC service, REST controllers,
outbox publisher and tests for the
`ChangeProposal / Review / Comment / ActivityFeed` domain. Real implementation lands in later
epics (E6.2, E6.4).

Contents:

- `UcollaborationServiceSkeletonTest.java`

Boundary rules enforced by CI (`AGENTS.md` §2):

- No file under `services/collaboration-service/` may import another service's
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
### 2.4 collaboration-service (E6.2, E6.4)

| Field               | Value                                                                                 |
| ------------------- | ------------------------------------------------------------------------------------- |
| Domain owner        | Collaboration team                                                                    |
| Product owner       | Reviewer journey                                                                      |
| Privacy owner       | DPO delegate (Comments/metadata)                                                      |
| Security owner      | AppSec partner (Comments)                                                             |
| SRE / on-call lead  | sre-secondary                                                                         |
| Owns aggregate/data | `ChangeProposal`, `Review`, `Comment`, `ActivityFeed`                                 |
| Public REST         | `services/collaboration-service/openapi.yaml`                                         |
| gRPC                | `gp.collab.v1.{ProposalService, CommentService}`                                      |
| Events published    | `gp.collab.v1.{ProposalSubmitted, ProposalApproved, ProposalRejected, PartialMerged}` |
| Events consumed     | All domain events for activity aggregation; ABAC redacted at projection               |
```
Owner: `services/collaboration-service/OWNERS`.
