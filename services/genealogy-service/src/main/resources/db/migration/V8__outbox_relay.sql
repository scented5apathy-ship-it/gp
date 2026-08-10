-- E4.7 — Outbox relay + event publishing (R4.6 + R10 + R16 +
-- NFR1 + NFR4 + NFR7). Mirrors
-- `contracts/genealogy/outbox-relay-policy.yaml` +
-- `design.md` §7.3 + ADR-E0.5-08.
--
-- The migration is BACKWARD-compatible per ADR-E0.5-08 +
-- `agent-execution.md` §4.4: it ONLY ADDS columns to the
-- existing `tree_service.outbox` table and adds a new
-- `tree_service.inbox_idempotency` table. The existing
-- `INSERT` path continues to work; new callers can opt-in
-- to the richer status / attempt / lease columns. The
-- column defaults are picked so an outbox row inserted by
-- the old writer still transitions correctly under the
-- new relay.
--
-- Tenant isolation (design.md §5.1) is enforced at the
-- RLS layer on every new table; the application role
-- (`tree_service_app`) is granted only the privileges the
-- relay + writer + consumer actually need.

-- ---------------------------------------------------------------------------
-- 1) Extend tree_service.outbox with the relay contract
-- ---------------------------------------------------------------------------

ALTER TABLE tree_service.outbox
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED', 'DEAD_LETTERED')),
    ADD COLUMN IF NOT EXISTS schema_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS partition_key VARCHAR(255),
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS attempts INTEGER NOT NULL DEFAULT 0
        CHECK (attempts >= 0),
    ADD COLUMN IF NOT EXISTS last_error TEXT,
    ADD COLUMN IF NOT EXISTS dlq_reason VARCHAR(64),
    ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS claim_lease_until TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS audit_event_id UUID,
    ADD COLUMN IF NOT EXISTS payload_byte_size INTEGER NOT NULL DEFAULT 0
        CHECK (payload_byte_size >= 0 AND payload_byte_size <= 1048576);

-- Backfill `status` for rows that already exist (created by
-- the E3.x / E4.1 writer). A row with `published_at IS NOT
-- NULL` is already PUBLISHED; everything else is PENDING.
UPDATE tree_service.outbox
   SET status = CASE
       WHEN published_at IS NOT NULL THEN 'PUBLISHED'
       ELSE 'PENDING'
   END
 WHERE status = 'PENDING';

-- Replace the old "unpublished by created_at" index with the
-- relay-aware index that also includes status, attempts and
-- the next-attempt-at timestamp. The relay claims rows in
-- (status = 'PENDING' AND (next_attempt_at IS NULL OR
-- next_attempt_at <= NOW())) order, ignoring rows whose lease
-- is still alive.
DROP INDEX IF EXISTS tree_service.outbox_unpublished_idx;
CREATE INDEX outbox_pending_idx ON tree_service.outbox (next_attempt_at NULLS FIRST, created_at)
    WHERE status = 'PENDING';
CREATE INDEX outbox_failed_idx ON tree_service.outbox (next_attempt_at NULLS FIRST, attempts)
    WHERE status = 'FAILED';
CREATE INDEX outbox_dlq_idx ON tree_service.outbox (dlq_reason, created_at DESC)
    WHERE status = 'DEAD_LETTERED';

-- ---------------------------------------------------------------------------
-- 2) Inbox / idempotency table for the consumer side
-- ---------------------------------------------------------------------------
-- The consumer side is the responsibility of every consumer
-- service; the genealogy-service inbox table is the local
-- idempotency cache for the genealogy consumer (e.g. the
-- projection rebuild worker that consumes PersonMerged).
-- Strategy = EVENT_ID by default (per outbox-relay-policy
-- defaultIdempotencyStrategy); the strategy column lets a
-- future migration switch to AGGREGATE_VERSION without
-- breaking the table.
CREATE TABLE tree_service.inbox_idempotency (
    event_id        UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    aggregate_id    UUID,
    strategy        VARCHAR(32) NOT NULL DEFAULT 'EVENT_ID'
        CHECK (strategy IN ('EVENT_ID', 'AGGREGATE_VERSION', 'SCHEMA_HASH')),
    schema_id       VARCHAR(255) NOT NULL,
    payload_hash    VARCHAR(128) NOT NULL,
    correlation_id  UUID,
    trace_id        VARCHAR(64),
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX inbox_expires_idx ON tree_service.inbox_idempotency (expires_at);
CREATE INDEX inbox_tenant_strategy_idx ON tree_service.inbox_idempotency (tenant_id, strategy);

-- ---------------------------------------------------------------------------
-- 3) Tenant isolation (design.md §5.1)
-- ---------------------------------------------------------------------------

ALTER TABLE tree_service.inbox_idempotency ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.inbox_idempotency FORCE ROW LEVEL SECURITY;
CREATE POLICY inbox_tenant_isolation ON tree_service.inbox_idempotency
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Defense-in-depth on the outbox table (already enforced via
-- the writer's RLS context but added here so any future bulk
-- script cannot bypass).
ALTER TABLE tree_service.outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.outbox FORCE ROW LEVEL SECURITY;
CREATE POLICY outbox_tenant_isolation ON tree_service.outbox
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- ---------------------------------------------------------------------------
-- 4) Privileges for the application role (ADR-E0.5-02)
-- ---------------------------------------------------------------------------

GRANT SELECT, INSERT, UPDATE ON tree_service.inbox_idempotency TO tree_service_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON tree_service.outbox TO tree_service_app;
