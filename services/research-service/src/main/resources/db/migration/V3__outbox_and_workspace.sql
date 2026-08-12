-- ---------------------------------------------------------------------------
-- E6.1d — research-service Flyway V3: outbox + workspace projection.
--
-- Adds the transactional outbox (mirrors
-- `services/genealogy-service/src/main/resources/db/migration/V8__outbox_relay.sql`)
-- and the workspace projection table that the redaction overlay
-- mutates when the consumer side receives `TreeVisibilityChanged`
-- or `PersonRedacted` events.
--
-- Scope guard (per agent-execution.md §4.4):
--   - No domain Java code, no application services, no REST changes.
--   - No gRPC stubs / Kafka client libraries (E6.1d Java work).
--   - No Avro event schemas (separate contract under
--     `contracts/events/research/v1/`).
--   - No Testcontainers / Helm / runbook (E6.1e).
--   - No research-policy.yaml changes (E6.1a locked).
--
-- Outbox pattern (per design.md §7.3 + ADR-E0.5-08):
--   - Aggregate mutation + outbox row commit in the same PostgreSQL
--     transaction. The relay polls the outbox in a separate
--     process and publishes to Kafka via the Apicurio schema.
--   - The `payload` column carries the JSON intermediate (the
--     relay converts to Avro at publish time so the schema
--     registry stays the source of truth).
--   - Partition key already includes the tenant id at the
--     application layer (`tenantId + "|" + aggregateId`); the
--     DB only stores the derived key.
--   - Status is closed-set: PENDING | FAILED | PUBLISHED |
--     DEAD_LETTERED. The relay is the only writer of the
--     non-PENDING states.
--
-- Workspace projection (re-projection workspace, R8.4 + NFR1):
--   - The consumer side maintains a denormalised per-tree
--     projection that the editor UI overlays onto the tree
--     workspace. When a tree's visibility transitions to
--     `UNLISTED` or `PUBLIC`, the projection is the source of
--     truth for the public-view rendering; when a `PersonRedacted`
--     event lands, every projection row that references the
--     person is flipped to `redacted=true` so the UI never
--     reaches the (now forbidden) redacted fields.
--   - RLS FORCE applies — the consumer binds the tenant id
--     before any UPDATE.
--   - `last_redaction_reason` is the closed-set per
--     `PersonRedacted.reason`.
--
-- Closed-set enums stay CHECK constraints (per E6.1b; this
-- migration follows the same convention).
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- Outbox.
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.outbox (
    event_id              TEXT        PRIMARY KEY,
    aggregate_id          TEXT        NOT NULL,
    tenant_id             TEXT        NOT NULL,
    event_type            TEXT        NOT NULL,
    schema_id             TEXT        NOT NULL,
    payload               JSONB       NOT NULL,
    payload_byte_size     INTEGER     NOT NULL,
    occurred_at           TIMESTAMPTZ NOT NULL,
    correlation_id        TEXT        NOT NULL,
    trace_id              TEXT,
    partition_key         TEXT        NOT NULL,
    partition_key_class   TEXT        NOT NULL,
    status                TEXT        NOT NULL DEFAULT 'PENDING',
    attempts              INTEGER     NOT NULL DEFAULT 0,
    last_attempt_at       TIMESTAMPTZ,
    next_attempt_at       TIMESTAMPTZ,
    published_at          TIMESTAMPTZ,
    claimed_at            TIMESTAMPTZ,
    claim_lease_until     TIMESTAMPTZ,
    last_error            TEXT,
    dlq_reason            TEXT,
    audit_event_id        TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT outbox_event_id_format_chk
        CHECK (event_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT outbox_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT outbox_aggregate_format_chk
        CHECK (aggregate_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT outbox_event_type_format_chk
        CHECK (event_type ~ '^gp\.[a-z0-9_.-]+\.v[0-9]+\.[A-Za-z0-9_]+$'),
    CONSTRAINT outbox_schema_id_format_chk
        CHECK (schema_id ~ '^[A-Za-z0-9._/-]{1,256}$'),
    CONSTRAINT outbox_payload_byte_size_chk
        CHECK (payload_byte_size BETWEEN 1 AND 921600),
    CONSTRAINT outbox_status_enum_chk
        CHECK (status IN ('PENDING', 'FAILED', 'PUBLISHED', 'DEAD_LETTERED')),
    CONSTRAINT outbox_partition_key_class_enum_chk
        CHECK (partition_key_class IN ('AGGREGATE_ONLY', 'TENANT_PLUS_AGGREGATE')),
    CONSTRAINT outbox_attempts_non_negative_chk
        CHECK (attempts >= 0),
    CONSTRAINT outbox_dead_lettered_reason_chk
        CHECK (
            (status = 'DEAD_LETTERED' AND dlq_reason IS NOT NULL)
            OR status <> 'DEAD_LETTERED'
        ),
    CONSTRAINT outbox_published_status_chk
        CHECK (
            (status = 'PUBLISHED' AND published_at IS NOT NULL)
            OR status <> 'PUBLISHED'
        )
);

CREATE INDEX outbox_tenant_status_idx
    ON research_service.outbox (tenant_id, status, next_attempt_at);
CREATE INDEX outbox_aggregate_idx
    ON research_service.outbox (tenant_id, aggregate_id);

ALTER TABLE research_service.outbox
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.outbox
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation
    ON research_service.outbox;
CREATE POLICY tenant_isolation
    ON research_service.outbox
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- Workspace projection.
--
-- One row per (treeId, claimReference). The UI joins the
-- projection back to the live citation rows only for the
-- non-redacted columns. Mutations are driven by the consumer
-- side (redaction overlay) and the editor (drafting).
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.workspace_projection (
    tenant_id                TEXT        NOT NULL,
    tree_id                  TEXT        NOT NULL,
    claim_reference          TEXT        NOT NULL,
    subject_reference        TEXT        NOT NULL,
    subject_kind             TEXT        NOT NULL,
    visibility               TEXT        NOT NULL,
    redacted                 BOOLEAN     NOT NULL DEFAULT FALSE,
    last_redaction_reason    TEXT,
    last_redacted_at         TIMESTAMPTZ,
    claim_verified_at        TIMESTAMPTZ,
    projection_version       BIGINT      NOT NULL DEFAULT 1,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, tree_id, claim_reference),
    CONSTRAINT wp_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT wp_tree_id_format_chk
        CHECK (tree_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT wp_claim_reference_format_chk
        CHECK (claim_reference ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT wp_subject_reference_format_chk
        CHECK (subject_reference ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT wp_visibility_enum_chk
        CHECK (visibility IN ('PRIVATE', 'UNLISTED', 'PUBLIC')),
    CONSTRAINT wp_redaction_reason_enum_chk
        CHECK (
            last_redaction_reason IS NULL
            OR last_redaction_reason IN
               ('LIVING', 'MINOR', 'CONSENT_REVOKED', 'JURISDICTION_BLOCKED')
        ),
    CONSTRAINT wp_redacted_consistency_chk
        CHECK (
            (redacted = TRUE AND last_redacted_at IS NOT NULL
                AND last_redaction_reason IS NOT NULL)
            OR redacted = FALSE
        ),
    CONSTRAINT wp_projection_version_positive_chk
        CHECK (projection_version > 0)
);

CREATE INDEX wp_tenant_tree_idx
    ON research_service.workspace_projection (tenant_id, tree_id);
CREATE INDEX wp_redacted_idx
    ON research_service.workspace_projection (tenant_id, redacted);
CREATE INDEX wp_subject_reference_idx
    ON research_service.workspace_projection (tenant_id, subject_reference);

ALTER TABLE research_service.workspace_projection
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.workspace_projection
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation
    ON research_service.workspace_projection;
CREATE POLICY tenant_isolation
    ON research_service.workspace_projection
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- Update the schema comment so the Flyway log carries the E6.1d
-- migration intent.
-- ---------------------------------------------------------------------------

COMMENT ON SCHEMA research_service IS
    'Schema owned by research-service per ADR-E0.5-02 (schema-per-service). '
    'E6.1b adds the six research aggregate tables + five bridge tables + '
    'PostgreSQL RLS (FORCE) + the research_service_app runtime role. '
    'E6.1d adds the transactional outbox (research_service.outbox) and the '
    'workspace projection table (research_service.workspace_projection) '
    'that the redaction overlay mutates. The relay polls the outbox in a '
    'separate process and publishes to Kafka via Apicurio-registered Avro '
    'schemas under contracts/events/research/v1/. Domain logic in E6.1a; '
    'REST + OpenAPI in E6.1c; gRPC + Kafka + OpenFGA/ABAC adapter in E6.1d; '
    'Testcontainers + Helm in E6.1e.';
