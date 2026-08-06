# services/audit-service/src/main/java/com/genealogy/platform/services/audit

Canonical production code for the **AuditEntry / AuditExport** service.

Per `design.md` §4 and `ownership-catalog.md` §2.11 this
package is owned by `Security Engineering team` and implements the
aggregate, command handlers, gRPC service, REST controllers,
outbox publisher and tests for the
`AuditEntry / AuditExport` domain. Real implementation lands in later
epics (E3.6).

Contents:

- `README.md`
- `package-info.java`

Boundary rules enforced by CI (`AGENTS.md` §2):

- No file under `services/audit-service/` may import another service's
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
### 2.11 audit-service (E3.6)

| Field               | Value                                                                             |
| ------------------- | --------------------------------------------------------------------------------- |
| Domain owner        | Security Engineering team                                                         |
| Product owner       | Compliance & Operator journeys                                                    |
| Privacy owner       | DPO delegate (Retention evidence)                                                 |
| Security owner      | Security Engineering (separation of duties)                                       |
| SRE / on-call lead  | sre-primary                                                                       |
| Owns aggregate/data | `AuditEntry`, `AuditExport` (append-only WORM bucket) per `scale-and-slo.md` §5.3 |
| Public REST         | internal only via BFF / admin shell; external audit export via Kong-signed URL    |
| gRPC                | `gp.audit.v1.{AuditService}`                                                      |
| Events published    | none (audit is a sink, never a source of business events)                         |
| Events consumed     | none — every service writes via gateway API or Kafka audit topic                  |
```
Owner: `services/audit-service/OWNERS`.
