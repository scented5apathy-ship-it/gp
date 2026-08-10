-- E4.1 — `tree_service` schema + `tree` aggregate.
--
-- Schema-per-service per ADR-E0.5-02. RLS defence-in-depth per
-- `design.md` §5.1. The aggregate carries visibility / lifecycle
-- state / collaboration mode / branding keys / slug / locale /
-- timezone / calendar / owner.

CREATE SCHEMA IF NOT EXISTS tree_service;

CREATE TABLE tree_service.tree (
    tree_id              UUID PRIMARY KEY,
    tenant_id            UUID NOT NULL,
    slug                 VARCHAR(40) NOT NULL,
    display_name         TEXT NOT NULL,
    visibility           VARCHAR(16) NOT NULL CHECK (visibility IN ('PRIVATE','UNLISTED','PUBLIC')),
    collaboration_mode   VARCHAR(32) NOT NULL CHECK (collaboration_mode IN ('DIRECT_EDIT','APPROVAL_REQUIRED','HYBRID_BY_ROLE')),
    lifecycle_state      VARCHAR(16) NOT NULL CHECK (lifecycle_state IN ('ACTIVE','ARCHIVED','DELETED')),
    default_locale       VARCHAR(16) NOT NULL,
    default_timezone     VARCHAR(64) NOT NULL,
    default_calendar     VARCHAR(32) NOT NULL,
    branding             JSONB NOT NULL DEFAULT '{}'::jsonb,
    owner_id             UUID NOT NULL,
    version              BIGINT NOT NULL CHECK (version >= 1),
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    CONSTRAINT tree_slug_unique_per_tenant UNIQUE (tenant_id, slug)
);

CREATE INDEX tree_tenant_idx ON tree_service.tree (tenant_id);
CREATE INDEX tree_tenant_lifecycle_idx ON tree_service.tree (tenant_id, lifecycle_state);

-- Outbox table for the transactional outbox pattern. Mirrors the
-- tenant-service outbox (`services/tenant-service/.../outbox/JdbcOutboxWriter.java`).
-- A separate relay process consumes rows where `published_at IS NULL`
-- and forwards to Kafka via the Apicurio schema. Out of scope for E4.1.
CREATE TABLE tree_service.outbox (
    event_id        UUID PRIMARY KEY,
    aggregate_id    UUID NOT NULL,
    tenant_id       UUID NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload         JSONB NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,
    correlation_id  UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX outbox_unpublished_idx ON tree_service.outbox (created_at)
    WHERE published_at IS NULL;

-- Tenant isolation — RLS per `design.md` §5.1. The application sets
-- `app.tenant_id` per request; the policy filters every row.
ALTER TABLE tree_service.tree ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.tree FORCE ROW LEVEL SECURITY;
CREATE POLICY tree_tenant_isolation ON tree_service.tree
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Application role is restricted to its own schema per ADR-E0.5-02.
-- The migration creates the role only if missing; production credentials
-- are owned by Vault / KMS.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tree_service_app') THEN
        CREATE ROLE tree_service_app NOLOGIN;
    END IF;
END$$;

GRANT USAGE ON SCHEMA tree_service TO tree_service_app;
GRANT SELECT, INSERT, UPDATE ON tree_service.tree TO tree_service_app;
GRANT SELECT, INSERT, UPDATE ON tree_service.outbox TO tree_service_app;
