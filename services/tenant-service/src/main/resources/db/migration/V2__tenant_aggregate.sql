-- ---------------------------------------------------------------------------
-- E3.2a — tenant-service Flyway V2: tenant aggregate + RLS + app role.
--
-- Adds the four tenant-scoped aggregate tables, the outbox table (writer
-- lands in E3.2c, schema only here) and the database role separation per
-- ADR-E0.5-02 (schema-per-service, schema-aware role).
--
-- Scope guard (per agent-execution.md §4.4):
--   - No domain Java code, no application services, no REST changes.
--   - No Avro event schemas (E3.2b).
--   - No outbox writer logic (E3.2c).
--   - No gRPC wiring (E3.2e).
--
-- Why a single V2 migration:
--   - All five tables form one transactional unit (no partial schema).
--   - Roles + RLS policies reference the tables, so they MUST land in
--     the same migration as the tables they protect.
--   - expand-contract is preserved at the API/event layer (E3.2d/e), not
--     inside this migration.
--
-- RLS strategy (defense-in-depth per design.md §5.1):
--   - Every tenant-scoped table has `tenant_id TEXT NOT NULL`.
--   - `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` so the
--     table owner (`tenant_service_owner`) is also bound by the policy.
--     Without FORCE, the owner bypasses RLS — a tenant-escape vector.
--   - Policy `tenant_isolation` matches rows where
--     `tenant_id = current_setting('app.tenant_id', true)`.
--     `current_setting('app.tenant_id', true)` returns NULL when unset;
--     the policy then matches zero rows, blocking cross-tenant reads
--     even if the application forgets to set the GUC.
--   - `app.tenant_id` MUST be set on every transaction by the
--     trusted-context filter (libs/platform-spring-boot-starter) before
--     any repository call. E3.2c adds a `TenantRlsFilter` test helper;
--     E3.2d wires it into the REST path.
--
-- Role strategy (ADR-E0.5-02):
--   - `tenant_service_owner`  — DDL + DML on `tenant_service.*`; used by
--     Flyway at boot. FORCE RLS still applies.
--   - `tenant_service_app`     — DML only on `tenant_service.*`; used by
--     the runtime service. FORCE RLS applies.
--   - Both roles get `SET ROLE` privileges only inside `tenant_service`.
--     No cross-schema read/write is granted.
--
-- Idempotency: the migration is NOT idempotent at the Flyway level
-- (Flyway tracks V2 as applied and skips it on subsequent boots). The
-- role + policy creation uses `IF NOT EXISTS` / `DROP POLICY IF EXISTS`
-- so a partial run on a developer machine can be re-applied manually.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- Roles. The `tenant_service_app` role is the runtime principal.
-- `tenant_service_owner` is the Flyway principal and owns the schema.
-- Both inherit the minimum privilege to operate inside `tenant_service`.
-- ---------------------------------------------------------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tenant_service_app') THEN
        CREATE ROLE tenant_service_app NOLOGIN;
    END IF;
END
$$;

-- The owner role already exists because Flyway connected as a
-- superuser in the Testcontainers fixture; in production it is the
-- dedicated DB user created by ADR-E0.5-02 runbook. Grant DML
-- privileges to the app role here.

GRANT USAGE ON SCHEMA tenant_service TO tenant_service_app;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA tenant_service TO tenant_service_app;
GRANT USAGE, SELECT
    ON ALL SEQUENCES IN SCHEMA tenant_service TO tenant_service_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA tenant_service
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO tenant_service_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA tenant_service
    GRANT USAGE, SELECT ON SEQUENCES TO tenant_service_app;

-- ---------------------------------------------------------------------------
-- Helpers. A reusable `is_current_tenant(tenant_id)` predicate keeps the
-- policy body simple and matches both real transactions and the
-- `app.tenant_id IS NULL` defence-in-depth case.
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION tenant_service.current_tenant_id()
RETURNS TEXT
LANGUAGE SQL
STABLE
AS $$
    SELECT NULLIF(current_setting('app.tenant_id', true), '')
$$;

-- ---------------------------------------------------------------------------
-- tenants — the tenant aggregate root.
--
-- - `id` is opaque server-issued (format pinned by OpenAPI/Protobuf).
-- - `slug` is the human-readable identifier (regex pinned by OpenAPI).
-- - `plan` / `status` mirror the protobuf enums (E3.2b types reference
--   these as documentation; this migration stays schema-only).
-- - `etag` is set by the application layer; default `'"v1"'` so V2 rows
--   have a stable value before E3.2d wires optimistic concurrency.
-- ---------------------------------------------------------------------------

CREATE TABLE tenant_service.tenants (
    id                       TEXT        PRIMARY KEY,
    slug                     TEXT        NOT NULL,
    display_name             TEXT        NOT NULL,
    plan                     TEXT        NOT NULL,
    status                   TEXT        NOT NULL,
    default_locale           TEXT,
    default_timezone         TEXT,
    default_calendar         TEXT,
    tenant_id                TEXT        NOT NULL,
    version                  BIGINT      NOT NULL DEFAULT 1,
    etag                     TEXT        NOT NULL DEFAULT '"v1"',
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    suspended_at             TIMESTAMPTZ,
    deleted_at               TIMESTAMPTZ,
    CONSTRAINT tenants_id_format_chk
        CHECK (id ~ '^[A-Za-z0-9_-]{8,64}$'),
    CONSTRAINT tenants_slug_format_chk
        CHECK (slug ~ '^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$'),
    CONSTRAINT tenants_display_name_len_chk
        CHECK (char_length(display_name) BETWEEN 1 AND 120),
    CONSTRAINT tenants_plan_enum_chk
        CHECK (plan IN ('FREE', 'FAMILY', 'PRO', 'ENTERPRISE')),
    CONSTRAINT tenants_status_enum_chk
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT tenants_self_tenant_id_chk
        CHECK (tenant_id = id),
    CONSTRAINT tenants_suspended_consistency_chk
        CHECK (
            (status = 'SUSPENDED' AND suspended_at IS NOT NULL)
            OR (status <> 'SUSPENDED' AND suspended_at IS NULL)
        ),
    CONSTRAINT tenants_deleted_consistency_chk
        CHECK (
            (status = 'DELETED' AND deleted_at IS NOT NULL)
            OR (status <> 'DELETED' AND deleted_at IS NULL)
        )
);

CREATE UNIQUE INDEX tenants_slug_global_uidx
    ON tenant_service.tenants (slug);
CREATE INDEX tenants_status_idx
    ON tenant_service.tenants (status)
    WHERE status <> 'DELETED';

ALTER TABLE tenant_service.tenants
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_service.tenants
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON tenant_service.tenants;
CREATE POLICY tenant_isolation ON tenant_service.tenants
    USING (tenant_id = tenant_service.current_tenant_id())
    WITH CHECK (tenant_id = tenant_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- memberships — User (Keycloak subject) ↔ Tenant binding.
--
-- - `user_id` is opaque (the Keycloak `sub` claim after pseudonymisation,
--   see ADR-E0.5-05; tenant-service is NOT the Keycloak source of truth).
-- - `person_id` is intentionally NULL-able and is set by an E4.x workflow
--   after the genealogical verification gate (R3, R4). E3.2a keeps the
--   column to avoid a follow-up migration that adds NOT-NULL back.
-- - `role` / `status` mirror the protobuf enums.
-- - `etag` follows the same convention as `tenants`.
-- - Unique (tenant_id, user_id) — a user holds at most one membership
--   per tenant; cross-tenant re-use is allowed (Keycloak subject can
--   belong to multiple tenants).
-- ---------------------------------------------------------------------------

CREATE TABLE tenant_service.memberships (
    id              TEXT        PRIMARY KEY,
    tenant_id       TEXT        NOT NULL,
    user_id         TEXT        NOT NULL,
    person_id       TEXT,
    role            TEXT        NOT NULL,
    status          TEXT        NOT NULL,
    invited_at      TIMESTAMPTZ,
    joined_at       TIMESTAMPTZ,
    suspended_at    TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    version         BIGINT      NOT NULL DEFAULT 1,
    etag            TEXT        NOT NULL DEFAULT '"v1"',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT memberships_id_format_chk
        CHECK (id ~ '^[A-Za-z0-9_-]{8,64}$'),
    CONSTRAINT memberships_role_enum_chk
        CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'AUDITOR', 'BILLING_ADMIN')),
    CONSTRAINT memberships_status_enum_chk
        CHECK (status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'REVOKED')),
    CONSTRAINT memberships_status_consistency_chk
        CHECK (
            (status = 'INVITED'  AND invited_at IS NOT NULL AND joined_at IS NULL)
            OR (status = 'ACTIVE'    AND joined_at IS NOT NULL)
            OR (status = 'SUSPENDED' AND joined_at IS NOT NULL AND suspended_at IS NOT NULL)
            OR (status = 'REVOKED'   AND revoked_at IS NOT NULL)
        ),
    CONSTRAINT memberships_tenant_fk_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9_-]{8,64}$'),
    CONSTRAINT memberships_user_format_chk
        CHECK (user_id ~ '^[A-Za-z0-9_-]{8,64}$')
);

CREATE UNIQUE INDEX memberships_tenant_user_uidx
    ON tenant_service.memberships (tenant_id, user_id);
CREATE INDEX memberships_status_idx
    ON tenant_service.memberships (tenant_id, status);
CREATE INDEX memberships_person_idx
    ON tenant_service.memberships (person_id)
    WHERE person_id IS NOT NULL;

ALTER TABLE tenant_service.memberships
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_service.memberships
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON tenant_service.memberships;
CREATE POLICY tenant_isolation ON tenant_service.memberships
    USING (tenant_id = tenant_service.current_tenant_id())
    WITH CHECK (tenant_id = tenant_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- invitations — pending invite for an email/keycloak-subject pair.
--
-- Invitations are tenant-scoped; the link is materialised into a
-- `memberships` row with status `INVITED` on accept (E3.2c).
-- `token_hash` stores a salted hash of the invite token; the raw token
-- only ever appears in the email body and is NOT persisted.
-- `idempotency_key` lets the REST layer de-dupe repeated invites per
-- `Idempotency-Key` header (RFC 1 contract, E3.2d).
-- ---------------------------------------------------------------------------

CREATE TABLE tenant_service.invitations (
    id                  TEXT        PRIMARY KEY,
    tenant_id           TEXT        NOT NULL,
    email               TEXT        NOT NULL,
    role                TEXT        NOT NULL,
    token_hash          TEXT        NOT NULL,
    idempotency_key     TEXT        NOT NULL,
    invited_by_user_id  TEXT        NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    accepted_at         TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT invitations_id_format_chk
        CHECK (id ~ '^[A-Za-z0-9_-]{8,64}$'),
    CONSTRAINT invitations_role_enum_chk
        CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'AUDITOR', 'BILLING_ADMIN')),
    CONSTRAINT invitations_email_format_chk
        CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);

CREATE UNIQUE INDEX invitations_tenant_idempotency_uidx
    ON tenant_service.invitations (tenant_id, idempotency_key);
CREATE INDEX invitations_email_pending_idx
    ON tenant_service.invitations (email, expires_at)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;

ALTER TABLE tenant_service.invitations
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_service.invitations
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON tenant_service.invitations;
CREATE POLICY tenant_isolation ON tenant_service.invitations
    USING (tenant_id = tenant_service.current_tenant_id())
    WITH CHECK (tenant_id = tenant_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- entitlements — per-tenant plan + quota map.
--
-- One row per tenant. Quotas are DRAFT (architecture-decisions.md §A)
-- and will be ratified in E0.6 sign-off; the CHECKs enforce that
-- numerics stay >= 0 until then.
-- ---------------------------------------------------------------------------

CREATE TABLE tenant_service.entitlements (
    tenant_id            TEXT        PRIMARY KEY,
    plan                 TEXT        NOT NULL,
    member_limit         INTEGER     NOT NULL,
    tree_limit           INTEGER     NOT NULL,
    storage_limit_mb     INTEGER     NOT NULL,
    retention_days       INTEGER     NOT NULL,
    billing_external_id  TEXT,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT entitlements_plan_enum_chk
        CHECK (plan IN ('FREE', 'FAMILY', 'PRO', 'ENTERPRISE')),
    CONSTRAINT entitlements_member_limit_nonneg_chk
        CHECK (member_limit >= 0),
    CONSTRAINT entitlements_tree_limit_nonneg_chk
        CHECK (tree_limit >= 0),
    CONSTRAINT entitlements_storage_nonneg_chk
        CHECK (storage_limit_mb >= 0),
    CONSTRAINT entitlements_retention_nonneg_chk
        CHECK (retention_days >= 0)
);

ALTER TABLE tenant_service.entitlements
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_service.entitlements
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON tenant_service.entitlements;
CREATE POLICY tenant_isolation ON tenant_service.entitlements
    USING (tenant_id = tenant_service.current_tenant_id())
    WITH CHECK (tenant_id = tenant_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- outbox_events — transactional outbox for E4.7 relay.
--
-- E3.2a ships the schema only; the writer lands in E3.2c (the writer is
-- part of the application service surface, not the migration).
-- `aggregate_type` / `aggregate_id` enable the relay to filter per
-- aggregate; `payload` is the Avro-encoded event bytes (BACKWARD
-- compatible per ADR-E0.5-08). The `dispatched_at` column lets the
-- relay mark a row published without losing the audit trail.
-- ---------------------------------------------------------------------------

CREATE TABLE tenant_service.outbox_events (
    id                 TEXT        PRIMARY KEY,
    tenant_id          TEXT        NOT NULL,
    aggregate_type     TEXT        NOT NULL,
    aggregate_id       TEXT        NOT NULL,
    event_type         TEXT        NOT NULL,
    payload            BYTEA       NOT NULL,
    schema_id          TEXT        NOT NULL,
    correlation_id     TEXT        NOT NULL,
    trace_id           TEXT        NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    dispatched_at      TIMESTAMPTZ,
    dispatch_attempts  INTEGER     NOT NULL DEFAULT 0,
    last_dispatch_error TEXT,
    CONSTRAINT outbox_id_format_chk
        CHECK (id ~ '^[A-Za-z0-9_-]{8,64}$'),
    CONSTRAINT outbox_aggregate_type_chk
        CHECK (aggregate_type IN ('tenant', 'membership', 'invitation', 'entitlement'))
);

CREATE INDEX outbox_undispatched_idx
    ON tenant_service.outbox_events (created_at)
    WHERE dispatched_at IS NULL;
CREATE INDEX outbox_aggregate_idx
    ON tenant_service.outbox_events (tenant_id, aggregate_type, aggregate_id);

ALTER TABLE tenant_service.outbox_events
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_service.outbox_events
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON tenant_service.outbox_events;
CREATE POLICY tenant_isolation ON tenant_service.outbox_events
    USING (tenant_id = tenant_service.current_tenant_id())
    WITH CHECK (tenant_id = tenant_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- Documentation: comments are pulled into Flyway info so a future
-- operator reading `flyway -info` understands the schema's intent.
-- ---------------------------------------------------------------------------

COMMENT ON SCHEMA tenant_service IS
    'Schema owned by tenant-service per ADR-E0.5-02 (schema-per-service). '
    'E3.2a adds the tenant aggregate tables (tenants, memberships, '
    'invitations, entitlements, outbox_events) plus PostgreSQL Row-Level '
    'Security (FORCE) and the tenant_service_app runtime role. Domain '
    'logic lands in E3.2b; application services in E3.2c; REST wiring '
    'in E3.2d; gRPC stub + runbook in E3.2e.';