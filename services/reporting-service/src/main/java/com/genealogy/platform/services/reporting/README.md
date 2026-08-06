# services/reporting-service/src/main/java/com/genealogy/platform/services/reporting

Canonical production code for the **ReportJob / ReportTemplate / AnalyticsProjection** service.

Per `design.md` §4 and `ownership-catalog.md` §2.10 this
package is owned by `Reporting team` and implements the
aggregate, command handlers, gRPC service, REST controllers,
outbox publisher and tests for the
`ReportJob / ReportTemplate / AnalyticsProjection` domain. Real implementation lands in later
epics (E11.3).

Contents:

- `package-info.java`

Boundary rules enforced by CI (`AGENTS.md` §2):

- No file under `services/reporting-service/` may import another service's
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
### 2.10 reporting-service (E11.3)

| Field               | Value                                                                                                         |
| ------------------- | ------------------------------------------------------------------------------------------------------------- |
| Domain owner        | Reporting team                                                                                                |
| Product owner       | Power-user + Operator dashboards                                                                              |
| Privacy owner       | DPO delegate (Report redaction)                                                                               |
| Security owner      | AppSec partner (Generated PDFs)                                                                               |
| SRE / on-call lead  | sre-secondary                                                                                                 |
| Owns aggregate/data | `ReportJob`, `ReportTemplate`, `AnalyticsProjection`                                                          |
| Public REST         | `services/reporting-service/openapi.yaml` (job status + signed download URL)                                  |
| gRPC                | `gp.report.v1.{ReportService, AnalyticsService}`                                                              |
| Events published    | `gp.report.v1.{ReportRequested, ReportCompleted, ReportFailed, AnalyticsRefreshed}`                           |
| Events consumed     | Domain events for projection rebuild                                                                          |
```
Owner: `services/reporting-service/OWNERS`.
