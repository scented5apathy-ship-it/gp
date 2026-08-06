# libs/platform-spring-boot-starter/.../spring/audit

Audit-event publishing glue. Per `ownership-catalog.md` §2.11 the
audit service is a sink (never a source of business events); every
other service writes via this starter, which fans into the Kafka
audit topic that `audit-service` reads and persists in its
append-only WORM bucket.

Classes:

- `AuditEvent` — value object carrying the pseudonymous subject,
  resource type, action, reason code and tenant pseudonymous ID.
- `AuditPublisher` — interface implemented by services to emit
  audit events.
- `MicrometerAuditPublisher` — default implementation that writes
  to the Kafka audit topic and emits a Micrometer counter
  (`gp.audit.events.published`) tagged with pseudonymous IDs only.

Privacy: AuditEvent MUST NOT contain raw DNA, PII or secrets; only
opaque IDs per `ownership-catalog.md` §2.11.

Owner: platform-secondary. Reviewers: Security Engineering.