# services/research-service/src/test/java/com/genealogy/platform/services/research

Canonical unit + contract tests for the **Source / Citation / ResearchTask / Hypothesis** service.

Per `design.md` §4 and `ownership-catalog.md` §2.3 this
package is owned by `Research & Evidence team` and implements the
aggregate, command handlers, gRPC service, REST controllers,
outbox publisher and tests for the
`Source / Citation / ResearchTask / Hypothesis` domain. Real implementation lands in later
epics (E6.1).

Contents:

- `UresearchServiceSkeletonTest.java`

Boundary rules enforced by CI (`AGENTS.md` §2):

- No file under `services/research-service/` may import another service's
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
### 2.3 research-service (E6.1)

| Field               | Value                                                                                           |
| ------------------- | ----------------------------------------------------------------------------------------------- |
| Domain owner        | Research & Evidence team                                                                        |
| Product owner       | Genealogist journey                                                                             |
| Privacy owner       | DPO delegate (Citation metadata)                                                                |
| Security owner      | AppSec partner (Evidence)                                                                       |
| SRE / on-call lead  | sre-secondary                                                                                   |
| Owns aggregate/data | `Source`, `Citation`, `ResearchTask`, `Hypothesis`                                              |
| Public REST         | `services/research-service/openapi.yaml`                                                        |
| gRPC                | `gp.research.v1.{RepositoryService, CitationService, ResearchTaskService}`                      |
| Events published    | `gp.research.v1.{CitationCreated, ClaimVerified, ConflictDetected}`                             |
| Events consumed     | `gp.genealogy.v1.{TreeVisibilityChanged, PersonRedacted}`                                       |
```
Owner: `services/research-service/OWNERS`.
