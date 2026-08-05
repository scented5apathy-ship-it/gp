-- ---------------------------------------------------------------------------
-- E1.4 — tenant-service Flyway baseline.
--
-- The tenant aggregate, membership and OpenFGA mapping land in E3.2.
-- E1.4 only delivers the Flyway wiring + the minimum schema for
-- the contract test suite to verify jOOQ + Postgres RLS scaffolding
-- (the real aggregate tables are intentionally NOT created here).
--
-- The schema-per-service rule (ADR-E0.5-02) means this migration
-- only creates the `tenant_service` schema; the cluster-level
-- `CREATE SCHEMA` permission is granted by the operator runbook
-- (E2.1). The migration is intentionally idempotent so a CI
-- Testcontainers spin-up that re-runs the suite does not fail.
-- ---------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS tenant_service;

COMMENT ON SCHEMA tenant_service IS
    'Schema owned by tenant-service per ADR-E0.5-02 (schema-per-service).'
        ' All tenant-scoped tables live here. RLS is added in E3.2 once'
        ' the aggregate is materialised.';

-- A minimal `_schema_history` anchor table is unnecessary because
-- Flyway creates its own history table at the cluster root; the
-- schema comment above is enough proof that the migration ran.
SELECT 1;
