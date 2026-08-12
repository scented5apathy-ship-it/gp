-- ---------------------------------------------------------------------------
-- E1.4 — research-service Flyway baseline.
--
-- The research aggregate tables (Repository / Source / Citation /
-- ResearchTask / Hypothesis / Conflict) land in E6.1b. E1.4 only
-- delivers the Flyway wiring + the minimum schema for the contract
-- test suite to verify Flyway + Postgres scaffolding.
--
-- The schema-per-service rule (ADR-E0.5-02) means this migration
-- only creates the `research_service` schema; the cluster-level
-- `CREATE SCHEMA` permission is granted by the operator runbook
-- (E2.1). The migration is intentionally idempotent so a CI
-- Testcontainers spin-up that re-runs the suite does not fail.
-- ---------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS research_service;

COMMENT ON SCHEMA research_service IS
    'Schema owned by research-service per ADR-E0.5-02 (schema-per-service).'
        ' All research-scoped tables live here. RLS is added in E6.1b once'
        ' the aggregates are materialised.';

-- A minimal `_schema_history` anchor table is unnecessary because
-- Flyway creates its own history table at the cluster root; the
-- schema comment above is enough proof that the migration ran.
SELECT 1;