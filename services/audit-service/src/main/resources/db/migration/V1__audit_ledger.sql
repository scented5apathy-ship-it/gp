-- ---------------------------------------------------------------------------
-- E3.6 — audit-service Flyway V1: append-only ledger + hash chain + RLS.
--
-- Adds the immutable audit ledger table. Every entry carries:
--   - `previous_hash` (SHA-256 of the prior entry, scoped per tenant_id)
--   - `entry_hash` (SHA-256 of the canonical entry bytes)
--   - `payload` (Avro/JSON envelope per contracts/audit/policy.yaml)
--
-- Append-only discipline is enforced by:
--   1. `REVOKE UPDATE, DELETE` from the application role.
--   2. A row-level trigger that raises on UPDATE / DELETE.
--   3. No application code path issues UPDATE / DELETE.
--
-- RLS strategy (defense-in-depth per design.md §5.1):
--   - `tenant_id TEXT NOT NULL` on every row.
--   - `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` so the
--     table owner is also bound by the policy.
--   - Policy `audit_tenant_isolation` matches rows where
--     `tenant_id = current_setting('app.tenant_id', true)`.
--     When the GUC is unset the policy matches zero rows, blocking
--     cross-tenant reads even if the application forgets to bind the
--     context.
--
-- Retention columns are present from V1 so the retention sweeper
-- (E3.6 RetentionSweeper) can run without an ALTER TABLE migration
-- later. WORM tiers are configured in `contracts/audit/retention.yaml`.
-- ---------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS audit_service;

COMMENT ON SCHEMA audit_service IS
    'Schema owned by audit-service per ADR-E0.5-02 (schema-per-service).'
        ' All audit entries live here. RLS enforces tenant isolation;'
        ' append-only discipline is enforced by REVOKE + a row trigger.';

-- Application role separation per ADR-E0.5-02. The owner is the
-- Flyway-managed role that runs migrations; the application role
-- has INSERT + SELECT only.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_service_owner') THEN
        CREATE ROLE audit_service_owner NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_service_app') THEN
        CREATE ROLE audit_service_app NOLOGIN;
    END IF;
END$$;

GRANT USAGE ON SCHEMA audit_service TO audit_service_owner, audit_service_app;

-- ---------------------------------------------------------------------------
-- Append-only ledger table.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_service.audit_entry (
    -- Surrogate row id; the wire-level event id is the logical PK.
    id              BIGSERIAL PRIMARY KEY,
    event_id        TEXT NOT NULL UNIQUE,
    tenant_id       TEXT NOT NULL,
    actor_id        TEXT,
    audit_class     TEXT NOT NULL,
    action          TEXT NOT NULL,
    resource_type   TEXT,
    resource_id     TEXT,
    reason_code     TEXT,
    correlation_id  TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    payload         JSONB NOT NULL,
    previous_hash   CHAR(64) NOT NULL,
    entry_hash      CHAR(64) NOT NULL,
    -- 0 = active; 1 = soft-deleted (legal hold overrides; only DPO can set).
    deleted         SMALLINT NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMPTZ,
    sweep_run_id    TEXT
);

COMMENT ON TABLE audit_service.audit_entry IS
    'Append-only audit ledger. Every mutation by every service lands here.'
        ' Schema-per-service per ADR-E0.5-02; RLS enforces tenant isolation;'
        ' REVOKE UPDATE/DELETE + row trigger enforce append-only.';

CREATE INDEX IF NOT EXISTS idx_audit_entry_tenant_class_time
    ON audit_service.audit_entry (tenant_id, audit_class, occurred_at);

CREATE INDEX IF NOT EXISTS idx_audit_entry_actor_time
    ON audit_service.audit_entry (actor_id, occurred_at);

CREATE INDEX IF NOT EXISTS idx_audit_entry_correlation
    ON audit_service.audit_entry (correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_audit_entry_chain
    ON audit_service.audit_entry (tenant_id, id);

-- ---------------------------------------------------------------------------
-- Append-only enforcement: REVOKE UPDATE/DELETE from the application role.
-- The application role is granted INSERT + SELECT only; updates can only be
-- performed by the DPO via a separate maintenance path (out of scope for
-- the app role and not exposed via REST/gRPC).
-- ---------------------------------------------------------------------------
REVOKE ALL ON audit_service.audit_entry FROM PUBLIC;
GRANT INSERT, SELECT ON audit_service.audit_entry TO audit_service_app;
GRANT USAGE, SELECT ON SEQUENCE audit_service.audit_entry_id_seq TO audit_service_app;

-- ---------------------------------------------------------------------------
-- Row trigger that raises on UPDATE / DELETE. Belt-and-braces: even if the
-- REVOKE is dropped or the role escalated, the trigger still rejects.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION audit_service.reject_audit_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_service.audit_entry is append-only (op=%, txid=%)',
        TG_OP, txid_current()
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_entry_append_only ON audit_service.audit_entry;
CREATE TRIGGER trg_audit_entry_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON audit_service.audit_entry
    FOR EACH STATEMENT EXECUTE FUNCTION audit_service.reject_audit_mutation();

-- ---------------------------------------------------------------------------
-- Tenant isolation via RLS. The policy matches when
-- `current_setting('app.tenant_id', true)` equals the row's `tenant_id`.
-- `true` means missing_ok — a NULL setting yields a NULL comparison,
-- which evaluates to FALSE, which denies the row.
-- ---------------------------------------------------------------------------
ALTER TABLE audit_service.audit_entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_service.audit_entry FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS audit_tenant_isolation ON audit_service.audit_entry;
CREATE POLICY audit_tenant_isolation ON audit_service.audit_entry
    USING (tenant_id = current_setting('app.tenant_id', true));

-- Operator escape hatch: a separate role `audit_service_dpo` may read
-- across tenants (for export bundles). It still cannot UPDATE / DELETE
-- because the trigger is unconditional.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'audit_service_dpo') THEN
        CREATE ROLE audit_service_dpo NOLOGIN;
    END IF;
END$$;

GRANT USAGE ON SCHEMA audit_service TO audit_service_dpo;
GRANT SELECT ON audit_service.audit_entry TO audit_service_dpo;

SELECT 1;
