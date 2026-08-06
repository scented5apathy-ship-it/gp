# contracts/events/envelope

Canonical Kafka event envelope (single source of truth, versioned).
Per `design.md` §7.3 every domain event MUST wrap its payload with
the shared envelope so consumers can rely on the same identity,
ordering and tracing metadata regardless of source service.

Sub-directories:

- `v1/` — `event-envelope.avsc` (current). Adding a new envelope
  version creates `v2/` and follows the BACKWARD compatibility
  policy from `ownership-catalog.md` §5.3.

Owner: contract-first platform team. Reviewers: every service
emitting events.