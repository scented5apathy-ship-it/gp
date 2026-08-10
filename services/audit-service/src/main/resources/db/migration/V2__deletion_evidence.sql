-- ---------------------------------------------------------------------------
-- E3.6 — audit-service Flyway V2: deletion evidence + retention sweep audit.
--
-- The retention sweeper does NOT mutate `audit_entry` (append-only is
-- enforced by the V1 trigger). Instead it writes a deletion-evidence
-- row referencing the swept batch so DPOs can audit the lifecycle.
--
-- Per `contracts/audit/retention.yaml::spec.deletionEvidence.required: true`
-- the sweeper MUST emit a row per batch.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS audit_service.deletion_evidence (
    id              BIGSERIAL PRIMARY KEY,
    sweep_run_id    TEXT NOT NULL,
    tenant_id       TEXT NOT NULL,
    audit_class     TEXT NOT NULL,
    swept_count     INTEGER NOT NULL,
    earliest_occurred_at TIMESTAMPTZ NOT NULL,
    latest_occurred_at  TIMESTAMPTZ NOT NULL,
    swept_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason_code     TEXT NOT NULL,
    integrity_hash  CHAR(64) NOT NULL,
    legal_hold_override BOOLEAN NOT NULL DEFAULT FALSE,
    performed_by    TEXT NOT NULL,
    notes           TEXT
);

COMMENT ON TABLE audit_service.deletion_evidence IS
    'Retention sweep evidence. Append-only counter-record: every batch'
        ' of audit entries that ages out of retention lands here so DPOs'
        ' can audit the lifecycle without the trigger blocking INSERT.';

CREATE INDEX IF NOT EXISTS idx_deletion_evidence_tenant_time
    ON audit_service.deletion_evidence (tenant_id, swept_at);

CREATE INDEX IF NOT EXISTS idx_deletion_evidence_run
    ON audit_service.deletion_evidence (sweep_run_id);

GRANT INSERT, SELECT ON audit_service.deletion_evidence TO audit_service_app;
GRANT USAGE, SELECT ON SEQUENCE audit_service.deletion_evidence_id_seq TO audit_service_app;
GRANT SELECT ON audit_service.deletion_evidence TO audit_service_dpo;

ALTER TABLE audit_service.deletion_evidence ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_service.deletion_evidence FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS audit_deletion_evidence_isolation ON audit_service.deletion_evidence;
CREATE POLICY audit_deletion_evidence_isolation ON audit_service.deletion_evidence
    USING (tenant_id = current_setting('app.tenant_id', true));

-- Same append-only enforcement as the main ledger.
CREATE OR REPLACE FUNCTION audit_service.reject_deletion_evidence_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_service.deletion_evidence is append-only (op=%, txid=%)',
        TG_OP, txid_current()
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_deletion_evidence_append_only ON audit_service.deletion_evidence;
CREATE TRIGGER trg_deletion_evidence_append_only
    BEFORE UPDATE OR DELETE OR TRUNCATE ON audit_service.deletion_evidence
    FOR EACH STATEMENT EXECUTE FUNCTION audit_service.reject_deletion_evidence_mutation();

SELECT 1;
