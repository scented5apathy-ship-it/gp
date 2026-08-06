# contracts/events/envelope/v1

Canonical Avro schema for the v1 event envelope consumed by every
Kafka producer/consumer. Per `design.md` §7.3 the envelope is:

```json
{
  "eventId": "opaque-id",
  "eventType": "genealogy.person.v1.updated",
  "occurredAt": "RFC-3339",
  "tenantId": "opaque-id",
  "aggregateId": "opaque-id",
  "aggregateVersion": 7,
  "traceId": "opaque-id",
  "payload": {}
}
```

Rules:

- Tenant/aggregate IDs are opaque pseudonymous IDs, never raw
  identifiers.
- `payload` MUST NOT carry raw DNA, file content, access tokens or
  unnecessary PII.
- Compatibility policy is BACKWARD per `ownership-catalog.md` §5.3
  (ADR-E0.5-08).

Owner: contract-first platform team. Reviewers: every service owner
named in `ownership-catalog.md` §2.