# libs/platform-spring-boot-starter/.../spring/audit

Audit-event publishing glue. Per `ownership-catalog.md` §2.11 the
audit service is a sink (never a source of business events); every
other service writes via this starter, which fans into the Kafka
audit topic that `audit-service` reads and persists in its
append-only WORM bucket.

Classes (E3.6 additions marked `+`):

- `AuditEvent` — value object carrying the pseudonymous subject,
  resource type, action and tenant pseudonymous ID.
- `AuditPublisher` — interface implemented by services to emit
  audit events.
- `MicrometerAuditPublisher` — default implementation that
  increments the `platform.audit.events` counter and emits a
  structured log line (used when no Kafka sink is registered).
- `+ AuditEventEnvelope` — wire-format JSON envelope consumed by
  `audit-service`. Carries the closed-set `auditClass` and
  `action` fields plus `correlationId` and metadata.
- `+ AuditClassRegistry` — closed-set taxonomy (6 classes / 39
  actions). Mirrors `contracts/audit/policy.yaml`.
- `+ AuditEventValidator` — publisher-side guard that rejects
  unknown actions / classes / missing tenantId before the event
  leaves the service.
- `+ AuditRedactor` — drops `denyKeys`, masks `maskKeys`, scrubs
  free-text patterns (email, JWT, IPv4/IPv6, bearer token, SSN,
  DNA sequence). Mirrors `contracts/audit/redaction.yaml`.
- `+ AuditEventSink` — pluggable transport interface; the default
  no-op sink keeps DI consumers compiling until the service
  registers a Kafka sink.
- `+ KafkaAuditPublisher` — replaces `MicrometerAuditPublisher`
  when an `AuditEventSink` bean exists. Pipeline:
  validator → redactor → sink → counters (`platform.audit.events.published`
  / `.rejected` / `.redacted`).

Privacy: AuditEvent MUST NOT contain raw DNA, PII or secrets; only
opaque IDs per `ownership-catalog.md` §2.11. The redactor
defends in depth by dropping `rawDna`, `biography`, tokens and
masks `email`, `phone`, `ipAddress`, `userAgent` regardless of
the caller.

Owner: platform-secondary. Reviewers: Security Engineering.
