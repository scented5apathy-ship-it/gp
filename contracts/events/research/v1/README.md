# contracts/events/research/v1

Avro event schemas for the research-service producer side (and
the redaction / visibility events the consumer side reads). Per
ADR-E0.5-08 the wire schema is Avro; the JSON intermediate
encoding is what the transactional outbox stores (the relay
converts to Avro at publish time).

## Producer schemas (research-service → broker)

- `citation-created.avsc` — `gp.research.v1.CitationCreated`.
  Emitted in the same transaction as the `citation` INSERT.
  Closed-set fields (`quality`, `disposition`, `certainty`)
  match the `ResearchInvariants` enum lockstep.

- `claim-verified.avsc` — `gp.research.v1.ClaimVerified`.
  Emitted when a citation crosses the verification threshold
  (disposition=SUPPORTING + certainty≥LIKELY). Triggers
  downstream propagation (search re-index, public projection
  refresh).

- `conflict-detected.avsc` — `gp.research.v1.ConflictDetected`.
  Emitted when a Conflict aggregate is created. Triggers
  collaboration-service review notification.

## Consumer schemas (genealogy-service → research-service)

- `tree-visibility-changed.avsc` — already published by
  `genealogy-service` under `contracts/events/genealogy/v1/`.
  The research consumer re-broadcasts the new visibility onto
  the workspace projection.

- `person-redacted-mirror.avsc` — mirror of the genealogy
  `PersonRedacted` event. The research consumer applies the
  redaction overlay (R8.4 + NFR1) to every workspace
  projection that references the redacted person.

Topic naming follows ADR-E0.5-08:

- `research.citation.v1.v1`
- `research.claim-verified.v1.v1`
- `research.conflict.v1.v1`
- (consumer) `genealogy.tree-visibility.v1.v1`
- (consumer) `genealogy.person-redacted.v1.v1`

Partition key = `aggregateId` (per-tenancy is already enforced
on the publisher side by the platform `OutboxRelay`).
