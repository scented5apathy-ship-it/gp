# Reusable envelope types

Cross-surface envelopes shared by REST, gRPC and event contracts.

- `problem-details.yaml` — RFC 9457 `application/problem+json` schema,
  referenced from every REST 4xx/5xx response.
- `event-envelope.proto` — the wire shape of every Kafka event
  (duplicated as Avro at `events/envelope/v1/event-envelope.avsc`).

These envelopes are the **only** types that may be referenced across
multiple domain packages. Domain types must live in their own package
and must not be reused across domains.
