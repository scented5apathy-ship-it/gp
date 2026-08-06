# services/notification-service/src/main/java/com/genealogy/platform/services/notification

Canonical production code for the **Notification / Preference / Template** service.

Per `design.md` §4 and `ownership-catalog.md` §2.9 this
package is owned by `Comms & Delivery team` and implements the
aggregate, command handlers, gRPC service, REST controllers,
outbox publisher and tests for the
`Notification / Preference / Template` domain. Real implementation lands in later
epics (E11.1, E11.2).

Contents:

- `package-info.java`

Boundary rules enforced by CI (`AGENTS.md` §2):

- No file under `services/notification-service/` may import another service's
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
### 2.9 notification-service (E11.1, E11.2)

| Field               | Value                                                                                             |
| ------------------- | ------------------------------------------------------------------------------------------------- |
| Domain owner        | Comms & Delivery team                                                                             |
| Product owner       | Cross-journey (admin, editor, guardian)                                                           |
| Privacy owner       | DPO delegate (Notification payload)                                                               |
| Security owner      | AppSec partner (Provider adapters)                                                                |
| SRE / on-call lead  | sre-secondary                                                                                     |
| Owns aggregate/data | `Notification`, `Preference`, `Template`                                                          |
| Public REST         | `services/notification-service/openapi.yaml` (preferences, inbox)                                 |
| gRPC                | `gp.notify.v1.{PreferenceService, NotificationService}`                                           |
| Events published    | `gp.notify.v1.{NotificationDispatched, NotificationFailed, SubscriptionUnsubscribe}`              |
| Events consumed     | All domain events (idempotent inbox); ABAC re-check before render per `design.md` §11.2           |
```
Owner: `services/notification-service/OWNERS`.
