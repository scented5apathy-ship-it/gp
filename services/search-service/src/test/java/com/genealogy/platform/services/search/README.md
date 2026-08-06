# services/search-service/src/test/java/com/genealogy/platform/services/search

Canonical unit + contract tests for the **SearchDocument / SavedSearch / PublicProjection** service.

Per `design.md` §4 and `ownership-catalog.md` §2.6 this
package is owned by `Search team` and implements the
aggregate, command handlers, gRPC service, REST controllers,
outbox publisher and tests for the
`SearchDocument / SavedSearch / PublicProjection` domain. Real implementation lands in later
epics (E8).

Contents:

- `UsearchServiceSkeletonTest.java`

Boundary rules enforced by CI (`AGENTS.md` §2):

- No file under `services/search-service/` may import another service's
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
### 2.6 search-service (E8)

| Field               | Value                                                                                                                         |
| ------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Domain owner        | Search team                                                                                                                   |
| Product owner       | Researcher + Public discovery journeys                                                                                        |
| Privacy owner       | DPO delegate (Public projection, redaction)                                                                                   |
| Security owner      | AppSec partner (Projection poisoning)                                                                                         |
| SRE / on-call lead  | sre-primary                                                                                                                   |
| Owns aggregate/data | `SearchDocument`, `SavedSearch`, `PublicProjection`                                                                           |
| Public REST         | `services/search-service/openapi.yaml` (authorised search only; public read goes via public-api + Kong)                       |
| gRPC                | `gp.search.v1.{AuthorizedSearchService, SavedSearchService}`                                                                  |
| Events published    | `gp.search.v1.{ProjectionRebuilt, SavedSearchEvaluated}`                                                                      |
| Events consumed     | `gp.genealogy.v1.*`, `gp.research.v1.*`, `gp.collab.v1.*`, `gp.media.v1.{AssetReady, AssetRevoked}` (idempotent inbox)        |
```
Owner: `services/search-service/OWNERS`.
