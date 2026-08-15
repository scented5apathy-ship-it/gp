-- ---------------------------------------------------------------------------
-- E6.1e — research-service Flyway V4: consumer durable inbox.
--
-- Adds the per-tenant inbox that backs the consumer-side
-- idempotency contract. The motivation is the E6.1d Gap 5:
--
--   "design.md §7.3 + ADR-E0.5-08 require a per-tenant inbox
--    row written in the same transaction as the projection
--    mutation (so a re-delivery does not fork the projection)."
--
-- The relay publishes three research events; the upstream
-- genealogy events flow the other way. The downstream
-- consumer (TreeVisibilityChanged + PersonRedacted) is what
-- this migration protects. The upstream producer side
-- already has the transactional outbox in V3.
--
-- Schema:
--
--   PK = (tenant_id, source_topic, event_id)
--
--   - `event_id` is the canonical Avro event id, encoded
--     into the `EventEnvelope.eventId` field. The Spring
--     Kafka listener computes the same hash and calls
--     `INSERT ... ON CONFLICT DO NOTHING` to claim the
--     row before it mutates the projection. A second
--     delivery finds the row and skips.
--   - `source_topic` lets the same table back both the
--     `genealogy.tree-visibility.v1.v1` and
--     `genealogy.person-redacted.v1.v1` consumers.
--   - `payload_hash` is the SHA-256 of the deserialised
--     envelope so a re-delivery with a tampered body fails
--     the closed-set guards even if the id collides.
--   - `processed_at` is the wall-clock at which the
--     projection mutation committed; an event with
--     `processed_at IS NULL` is in flight.
--   - `outcome` is a closed-set (PROCESSED | FAILED |
--     SKIPPED_DUPLICATE) so audit + SLO reports can split
--     "happy path re-delivery skip" from "real failure".
--   - `last_error` is mandatory whenever `outcome = FAILED`
--     so the on-call runbook can grep the table.
--
-- RLS (FORCE) policy matches the V3 outbox / V2 aggregate
-- posture — the consumer never reads another tenant's inbox
-- even though the workload owns the cross-tenant topic ACL.
--
-- The audit columns (`correlation_id` + `created_at`) let
-- the on-call trace a re-delivery loop back to the original
-- request.
--
-- Scope guard (per agent-execution.md §4.4):
--   - No new domain Java records; no application services.
--   - No outbox / workspace projection changes (V3 is locked).
--   - No REST / OpenAPI / gRPC stubs.
--   - No new Kafka topic or ACL (E6.1d Gap 8 lands in a
--     follow-up; this migration is read-by-id only on the
--     existing consumer topics).
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.consumer_inbox (
    tenant_id        TEXT        NOT NULL,
    source_topic     TEXT        NOT NULL,
    event_id         TEXT        NOT NULL,
    event_type       TEXT        NOT NULL,
    payload_hash     TEXT        NOT NULL,
    received_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at     TIMESTAMPTZ,
    outcome          TEXT        NOT NULL DEFAULT 'IN_FLIGHT',
    last_error       TEXT,
    actor_pseudo_id  TEXT,
    correlation_id   TEXT,
    PRIMARY KEY (tenant_id, source_topic, event_id),
    CONSTRAINT inbox_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT inbox_source_topic_format_chk
        CHECK (source_topic ~ '^[A-Za-z0-9._/-]{1,256}$'),
    CONSTRAINT inbox_event_id_format_chk
        CHECK (event_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT inbox_event_type_format_chk
        CHECK (event_type ~ '^gp\.[a-z0-9_.-]+\.v[0-9]+\.[A-Za-z0-9_]+$'),
    CONSTRAINT inbox_payload_hash_format_chk
        CHECK (payload_hash ~ '^[A-Fa-f0-9]{64}$'),
    CONSTRAINT inbox_outcome_enum_chk
        CHECK (outcome IN ('IN_FLIGHT', 'PROCESSED', 'FAILED', 'SKIPPED_DUPLICATE')),
    CONSTRAINT inbox_processed_consistency_chk
        CHECK (
            (outcome = 'PROCESSED' AND processed_at IS NOT NULL)
            OR outcome <> 'PROCESSED'
        ),
    CONSTRAINT inbox_failed_consistency_chk
        CHECK (
            (outcome = 'FAILED' AND last_error IS NOT NULL)
            OR outcome <> 'FAILED'
        )
);

-- The claim-by-tenant query (the consumer first filters by
-- the trusted tenant id from the envelope). The composite
-- PK already covers the per-row lookup; this index supports
-- the on-call grep path (find recent failures for a tenant).
CREATE INDEX inbox_tenant_outcome_idx
    ON research_service.consumer_inbox (tenant_id, outcome, received_at);

-- Replay protection: if a malformed event keeps arriving,
-- the on-call can list every `event_id` for a given
-- `source_topic` to spot the runaway producer.
CREATE INDEX inbox_source_topic_idx
    ON research_service.consumer_inbox (source_topic, received_at);

ALTER TABLE research_service.consumer_inbox
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.consumer_inbox
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation
    ON research_service.consumer_inbox;
CREATE POLICY tenant_isolation
    ON research_service.consumer_inbox
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- Update the schema comment so the Flyway log carries the E6.1e
-- migration intent.
-- ---------------------------------------------------------------------------

COMMENT ON SCHEMA research_service IS
    'Schema owned by research-service per ADR-E0.5-02 (schema-per-service). '
    'E6.1b adds the six research aggregate tables + five bridge tables + '
    'PostgreSQL RLS (FORCE) + the research_service_app runtime role. '
    'E6.1d adds the transactional outbox (research_service.outbox) and the '
    'workspace projection table (research_service.workspace_projection) '
    'that the redaction overlay mutates. E6.1e adds the consumer durable '
    'inbox (research_service.consumer_inbox) that backs idempotent '
    'TreeVisibilityChanged + PersonRedacted re-delivery handling. Domain '
    'logic in E6.1a; REST + OpenAPI in E6.1c; gRPC + Kafka + OpenFGA/ABAC '
    'adapter in E6.1d; Testcontainers + Helm in E6.1e.';
