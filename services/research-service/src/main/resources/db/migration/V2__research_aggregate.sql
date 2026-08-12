-- ---------------------------------------------------------------------------
-- E6.1b — research-service Flyway V2: research aggregate + RLS + app role.
--
-- Adds the six research-scoped aggregate tables, the five bridge
-- tables, the `research_service_app` runtime role, the
-- `current_tenant_id()` helper and Row-Level Security (FORCE) per
-- ADR-E0.5-02 (schema-per-service) + design.md §5.1
-- (tenant-scoped RLS as defence-in-depth).
--
-- Scope guard (per agent-execution.md §4.4):
--   - No domain Java code, no application services, no REST changes.
--   - No Avro event schemas (E6.1d).
--   - No outbox writer / Kafka publisher (E6.1d).
--   - No gRPC wiring (E6.1d).
--   - No Testcontainers / Helm / runbook (E6.1e).
--   - No research-policy.yaml contract changes (already locked in
--     E6.1a; the linter enforces byte-equality).
--
-- Why a single V2 migration:
--   - All eleven tables + the helper + the role + the policies form
--     one transactional unit (no partial schema). The roles + RLS
--     policies reference the tables, so they MUST land in the same
--     migration as the tables they protect.
--   - expand-contract is preserved at the API/event layer (E6.1c/d),
--     not inside this migration.
--
-- Closed-set enums are pinned as CHECK constraints rather than
-- PostgreSQL ENUM types. CHECK constraints compose with the existing
-- `scripts/lint-research-config.mjs` validator (which already
-- alphabetises the Java enum constants) so the SQL CHECK and the
-- Java enum stay in lockstep without a schema-per-enum break.
--
-- Audit columns (R16.2 + NFR5 + agent-execution.md §4.4):
--   - `created_at`, `updated_at`, `archived_at`, `version`,
--     `created_by_actor_pseudo_id`, `correlation_id`.
--   - `actor_pseudo_id` is the platform pseudonym derived from the
--     Keycloak subject + Kong boundary (never raw subject id /
--     email / DNA per ADR-E0.5-05 + NFR5).
--   - `correlation_id` is the request-scoped trace id from Kong;
--     required on every row so the audit log can rebuild the
--     transaction context.
--
-- RLS strategy (defence-in-depth per design.md §5.1):
--   - Every research-scoped table has `tenant_id TEXT NOT NULL`.
--   - `ENABLE ROW LEVEL SECURITY` + `FORCE ROW LEVEL SECURITY` so
--     the table owner (`research_service_owner`) is also bound by
--     the policy. Without FORCE, the owner bypasses RLS — a
--     tenant-escape vector.
--   - Policy `tenant_isolation` matches rows where
--     `tenant_id = research_service.current_tenant_id()`.
--     `current_setting('app.tenant_id', true)` returns NULL when
--     unset; the policy then matches zero rows, blocking
--     cross-tenant reads even if the application forgets to set
--     the GUC.
--   - `app.tenant_id` MUST be set on every transaction by the
--     trusted-context filter (libs/platform-spring-boot-starter)
--     before any repository call. The `ResearchRlsTxInterceptor`
--     wires that filter in E6.1c; E6.1b only proves the schema
--     + RLS behaviour via `RlsNegativeIT`.
--
-- Role strategy (ADR-E0.5-02):
--   - `research_service_owner`  — DDL + DML on
--     `research_service.*`; used by Flyway at boot. FORCE RLS
--     still applies.
--   - `research_service_app`     — DML only on
--     `research_service.*`; used by the runtime service. FORCE
--     RLS applies.
--   - Both roles get `SET ROLE` privileges only inside
--     `research_service`. No cross-schema read/write is granted.
--
-- Idempotency: the migration is NOT idempotent at the Flyway level
-- (Flyway tracks V2 as applied and skips it on subsequent boots).
-- The role + policy creation uses `IF NOT EXISTS` /
-- `DROP POLICY IF EXISTS` so a partial run on a developer machine
-- can be re-applied manually.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- Roles. The `research_service_app` role is the runtime principal.
-- `research_service_owner` is the Flyway principal and owns the
-- schema. Both inherit the minimum privilege to operate inside
-- `research_service`.
-- ---------------------------------------------------------------------------

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'research_service_app') THEN
        CREATE ROLE research_service_app NOLOGIN;
    END IF;
END
$$;

-- The owner role already exists because Flyway connected as a
-- superuser in the Testcontainers fixture; in production it is the
-- dedicated DB user created by ADR-E0.5-02 runbook. Grant DML
-- privileges to the app role here.

GRANT USAGE ON SCHEMA research_service TO research_service_app;
GRANT SELECT, INSERT, UPDATE, DELETE
    ON ALL TABLES IN SCHEMA research_service TO research_service_app;
GRANT USAGE, SELECT
    ON ALL SEQUENCES IN SCHEMA research_service TO research_service_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA research_service
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO research_service_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA research_service
    GRANT USAGE, SELECT ON SEQUENCES TO research_service_app;

-- ---------------------------------------------------------------------------
-- Helpers. A reusable `current_tenant_id()` predicate keeps the
-- policy body simple and matches both real transactions and the
-- `app.tenant_id IS NULL` defence-in-depth case.
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION research_service.current_tenant_id()
RETURNS TEXT
LANGUAGE SQL
STABLE
AS $$
    SELECT NULLIF(current_setting('app.tenant_id', true), '')
$$;

-- ---------------------------------------------------------------------------
-- repositories — the Repository aggregate root (1 of 6).
--
-- Mirrors `domain/Repository.java`. `name` is the human-readable
-- label; `kind` is the closed-set from `research-policy.yaml::
-- spec.repositoryKinds`. `private_holding` is forced for
-- `FAMILY_HOLDING` / `OTHER` (R8.1 default-private).
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.repositories (
    id                          TEXT        PRIMARY KEY,
    tenant_id                   TEXT        NOT NULL,
    name                        TEXT        NOT NULL,
    kind                        TEXT        NOT NULL,
    location_label              TEXT,
    website_url                 TEXT,
    description                 TEXT,
    private_holding             BOOLEAN     NOT NULL DEFAULT FALSE,
    metadata                    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    version                     BIGINT      NOT NULL DEFAULT 1,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    archived_at                 TIMESTAMPTZ,
    created_by_actor_pseudo_id  TEXT        NOT NULL,
    correlation_id              TEXT        NOT NULL,
    CONSTRAINT repositories_id_format_chk
        CHECK (id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT repositories_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT repositories_name_len_chk
        CHECK (char_length(name) BETWEEN 1 AND 256),
    CONSTRAINT repositories_kind_enum_chk
        CHECK (kind IN ('ARCHIVE', 'LIBRARY', 'CHURCH', 'CIVIL_REGISTRY',
                         'CEMETERY', 'FAMILY_HOLDING', 'DIGITAL_PLATFORM', 'OTHER')),
    CONSTRAINT repositories_location_len_chk
        CHECK (location_label IS NULL OR char_length(location_label) <= 1024),
    CONSTRAINT repositories_website_url_len_chk
        CHECK (website_url IS NULL OR char_length(website_url) <= 2048),
    CONSTRAINT repositories_description_len_chk
        CHECK (description IS NULL OR char_length(description) <= 4096),
    CONSTRAINT repositories_version_positive_chk
        CHECK (version > 0),
    CONSTRAINT repositories_archived_consistency_chk
        CHECK (
            (archived_at IS NOT NULL AND updated_at >= archived_at)
            OR archived_at IS NULL
        ),
    CONSTRAINT repositories_private_holding_consistency_chk
        CHECK (
            (kind IN ('FAMILY_HOLDING', 'OTHER') AND private_holding = TRUE)
            OR kind NOT IN ('FAMILY_HOLDING', 'OTHER')
        ),
    CONSTRAINT repositories_actor_pseudo_id_len_chk
        CHECK (char_length(created_by_actor_pseudo_id) BETWEEN 1 AND 128),
    CONSTRAINT repositories_correlation_id_len_chk
        CHECK (char_length(correlation_id) BETWEEN 1 AND 128)
);

CREATE INDEX repositories_tenant_idx
    ON research_service.repositories (tenant_id);
CREATE INDEX repositories_tenant_kind_idx
    ON research_service.repositories (tenant_id, kind);
CREATE INDEX repositories_tenant_archived_idx
    ON research_service.repositories (tenant_id, archived_at);

ALTER TABLE research_service.repositories
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.repositories
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON research_service.repositories;
CREATE POLICY tenant_isolation ON research_service.repositories
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- sources — the Source aggregate root (2 of 6).
--
-- Mirrors `domain/Source.java`. A source belongs to exactly one
-- repository; `repository_id` is an opaque id. Tenant ↔ repository
-- consistency is enforced at the application layer (Source compact
-- constructor) because a CHECK constraint cannot use a subquery in
-- PostgreSQL.
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.sources (
    id                          TEXT        PRIMARY KEY,
    tenant_id                   TEXT        NOT NULL,
    repository_id               TEXT        NOT NULL,
    title                       TEXT        NOT NULL,
    source_kind                 TEXT        NOT NULL,
    author                      TEXT,
    publisher                   TEXT,
    publication_year            INTEGER,
    publisher_location          TEXT,
    locator_raw                 TEXT        NOT NULL,
    locator_page                TEXT,
    locator_entry               TEXT,
    locator_volume              TEXT,
    description                 TEXT,
    version                     BIGINT      NOT NULL DEFAULT 1,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    archived_at                 TIMESTAMPTZ,
    created_by_actor_pseudo_id  TEXT        NOT NULL,
    correlation_id              TEXT        NOT NULL,
    CONSTRAINT sources_id_format_chk
        CHECK (id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT sources_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT sources_repository_id_format_chk
        CHECK (repository_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT sources_title_len_chk
        CHECK (char_length(title) BETWEEN 1 AND 512),
    CONSTRAINT sources_kind_enum_chk
        CHECK (source_kind IN ('PRIMARY', 'SECONDARY', 'DERIVED', 'ARCHIVE',
                                'FINDING_AID', 'OTHER')),
    CONSTRAINT sources_author_len_chk
        CHECK (author IS NULL OR char_length(author) <= 256),
    CONSTRAINT sources_publisher_len_chk
        CHECK (publisher IS NULL OR char_length(publisher) <= 256),
    CONSTRAINT sources_publication_year_chk
        CHECK (publication_year IS NULL
               OR (publication_year BETWEEN 1000 AND 9999)),
    CONSTRAINT sources_publisher_location_len_chk
        CHECK (publisher_location IS NULL OR char_length(publisher_location) <= 256),
    CONSTRAINT sources_locator_raw_len_chk
        CHECK (char_length(locator_raw) BETWEEN 1 AND 256),
    CONSTRAINT sources_description_len_chk
        CHECK (description IS NULL OR char_length(description) <= 4096),
    CONSTRAINT sources_version_positive_chk
        CHECK (version > 0),
    CONSTRAINT sources_archived_consistency_chk
        CHECK (
            (archived_at IS NOT NULL AND updated_at >= archived_at)
            OR archived_at IS NULL
        ),
    CONSTRAINT sources_actor_pseudo_id_len_chk
        CHECK (char_length(created_by_actor_pseudo_id) BETWEEN 1 AND 128),
    CONSTRAINT sources_correlation_id_len_chk
        CHECK (char_length(correlation_id) BETWEEN 1 AND 128)
);

CREATE INDEX sources_tenant_idx
    ON research_service.sources (tenant_id);
CREATE INDEX sources_tenant_repository_idx
    ON research_service.sources (tenant_id, repository_id);
CREATE INDEX sources_tenant_kind_idx
    ON research_service.sources (tenant_id, source_kind);
CREATE INDEX sources_tenant_archived_idx
    ON research_service.sources (tenant_id, archived_at);

ALTER TABLE research_service.sources
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.sources
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON research_service.sources;
CREATE POLICY tenant_isolation ON research_service.sources
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- citations — the Citation aggregate root (3 of 6).
--
-- Mirrors `domain/Citation.java`. `claim_reference` is an opaque
-- pointer to a genealogy-service Claim id (not dereferenced here).
-- `disposition` + `quality` + `certainty` are closed-set enums.
-- Inline transcript_segments / attachments / external_urls live as
-- JSONB on the row; the E6.1b scope contract lists exactly five
-- bridges (see tasks.md §E6.1b), so these lists stay denormalised
-- in V2. A later sub-task (E6.1d + E11 reports) can promote them
-- to bridge tables in a V3 migration when indexed access is needed.
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.citations (
    id                          TEXT        PRIMARY KEY,
    tenant_id                   TEXT        NOT NULL,
    source_id                   TEXT        NOT NULL,
    claim_reference             TEXT        NOT NULL,
    claim_kind                  TEXT,
    locator_raw                 TEXT        NOT NULL,
    locator_page                TEXT,
    locator_entry               TEXT,
    locator_volume              TEXT,
    quality                     TEXT        NOT NULL,
    disposition                 TEXT        NOT NULL,
    certainty                   TEXT        NOT NULL,
    confidence                  NUMERIC(4,3),
    quoted_text                 TEXT,
    transcript_segments         JSONB       NOT NULL DEFAULT '[]'::jsonb,
    attachments                 JSONB       NOT NULL DEFAULT '[]'::jsonb,
    external_urls               JSONB       NOT NULL DEFAULT '[]'::jsonb,
    version                     BIGINT      NOT NULL DEFAULT 1,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    archived_at                 TIMESTAMPTZ,
    created_by_actor_pseudo_id  TEXT        NOT NULL,
    correlation_id              TEXT        NOT NULL,
    CONSTRAINT citations_id_format_chk
        CHECK (id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT citations_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT citations_source_id_format_chk
        CHECK (source_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT citations_claim_reference_len_chk
        CHECK (char_length(claim_reference) BETWEEN 1 AND 128),
    CONSTRAINT citations_claim_kind_len_chk
        CHECK (claim_kind IS NULL OR char_length(claim_kind) <= 64),
    CONSTRAINT citations_locator_raw_len_chk
        CHECK (char_length(locator_raw) BETWEEN 1 AND 256),
    CONSTRAINT citations_quality_enum_chk
        CHECK (quality IN ('ORIGINAL', 'TRANSCRIPT', 'ABSTRACT', 'IMAGE',
                            'COPY', 'UNKNOWN')),
    CONSTRAINT citations_disposition_enum_chk
        CHECK (disposition IN ('SUPPORTS', 'REFUTES', 'MENTIONS', 'UNCERTAIN')),
    CONSTRAINT citations_certainty_enum_chk
        CHECK (certainty IN ('HYPOTHESIS', 'ASSERTED', 'VERIFIED', 'DISPUTED')),
    CONSTRAINT citations_confidence_range_chk
        CHECK (confidence IS NULL OR (confidence >= 0.000 AND confidence <= 1.000)),
    CONSTRAINT citations_quoted_text_len_chk
        CHECK (quoted_text IS NULL OR char_length(quoted_text) <= 4096),
    CONSTRAINT citations_version_positive_chk
        CHECK (version > 0),
    CONSTRAINT citations_actor_pseudo_id_len_chk
        CHECK (char_length(created_by_actor_pseudo_id) BETWEEN 1 AND 128),
    CONSTRAINT citations_correlation_id_len_chk
        CHECK (char_length(correlation_id) BETWEEN 1 AND 128)
);

CREATE INDEX citations_tenant_idx
    ON research_service.citations (tenant_id);
CREATE INDEX citations_tenant_source_idx
    ON research_service.citations (tenant_id, source_id);
CREATE INDEX citations_tenant_claim_idx
    ON research_service.citations (tenant_id, claim_reference);
CREATE INDEX citations_tenant_archived_idx
    ON research_service.citations (tenant_id, archived_at);

ALTER TABLE research_service.citations
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.citations
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON research_service.citations;
CREATE POLICY tenant_isolation ON research_service.citations
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- research_tasks — the ResearchTask aggregate root (4 of 6).
--
-- Mirrors `domain/ResearchTask.java`. `subject_reference` is an
-- opaque pointer (Claim id, Person id, or free-form note).
-- `linked_citation_ids` are denormalised as JSONB; the dedicated
-- `research_task_assignments` bridge table (bridge 1 of 5) handles
-- the assignment list, which is the only bridge the tasks.md §E6.1b
-- scope names explicitly.
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.research_tasks (
    id                          TEXT        PRIMARY KEY,
    tenant_id                   TEXT        NOT NULL,
    title                       TEXT        NOT NULL,
    description                 TEXT,
    subject_reference           TEXT        NOT NULL,
    subject_kind                TEXT,
    status                      TEXT        NOT NULL DEFAULT 'OPEN',
    blocked_reason              TEXT,
    resolved_proof              TEXT,
    linked_citation_ids         JSONB       NOT NULL DEFAULT '[]'::jsonb,
    version                     BIGINT      NOT NULL DEFAULT 1,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at                 TIMESTAMPTZ,
    archived_at                 TIMESTAMPTZ,
    created_by_actor_pseudo_id  TEXT        NOT NULL,
    correlation_id              TEXT        NOT NULL,
    CONSTRAINT research_tasks_id_format_chk
        CHECK (id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT research_tasks_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT research_tasks_title_len_chk
        CHECK (char_length(title) BETWEEN 1 AND 256),
    CONSTRAINT research_tasks_description_len_chk
        CHECK (description IS NULL OR char_length(description) <= 4096),
    CONSTRAINT research_tasks_subject_reference_len_chk
        CHECK (char_length(subject_reference) BETWEEN 1 AND 128),
    CONSTRAINT research_tasks_subject_kind_len_chk
        CHECK (subject_kind IS NULL OR char_length(subject_kind) <= 64),
    CONSTRAINT research_tasks_status_enum_chk
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'BLOCKED', 'RESOLVED', 'ABANDONED')),
    CONSTRAINT research_tasks_blocked_reason_len_chk
        CHECK (blocked_reason IS NULL OR char_length(blocked_reason) <= 1024),
    CONSTRAINT research_tasks_resolved_proof_len_chk
        CHECK (resolved_proof IS NULL OR char_length(resolved_proof) <= 128),
    CONSTRAINT research_tasks_status_consistency_chk
        CHECK (
            (status = 'BLOCKED'  AND blocked_reason IS NOT NULL)
            OR status <> 'BLOCKED'
        ),
    CONSTRAINT research_tasks_resolved_consistency_chk
        CHECK (
            (status = 'RESOLVED' AND resolved_at IS NOT NULL
                                  AND resolved_proof IS NOT NULL)
            OR status <> 'RESOLVED'
        ),
    CONSTRAINT research_tasks_version_positive_chk
        CHECK (version > 0),
    CONSTRAINT research_tasks_actor_pseudo_id_len_chk
        CHECK (char_length(created_by_actor_pseudo_id) BETWEEN 1 AND 128),
    CONSTRAINT research_tasks_correlation_id_len_chk
        CHECK (char_length(correlation_id) BETWEEN 1 AND 128)
);

CREATE INDEX research_tasks_tenant_idx
    ON research_service.research_tasks (tenant_id);
CREATE INDEX research_tasks_tenant_status_idx
    ON research_service.research_tasks (tenant_id, status);
CREATE INDEX research_tasks_tenant_subject_idx
    ON research_service.research_tasks (tenant_id, subject_reference);

ALTER TABLE research_service.research_tasks
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.research_tasks
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON research_service.research_tasks;
CREATE POLICY tenant_isolation ON research_service.research_tasks
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- hypotheses — the Hypothesis aggregate root (5 of 6).
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.hypotheses (
    id                          TEXT        PRIMARY KEY,
    tenant_id                   TEXT        NOT NULL,
    statement                   TEXT        NOT NULL,
    subject_reference           TEXT        NOT NULL,
    subject_kind                TEXT,
    certainty                   TEXT        NOT NULL,
    confidence                  NUMERIC(4,3),
    status                      TEXT        NOT NULL DEFAULT 'DRAFT',
    superseded_by_hypothesis_id TEXT,
    assigned_to                 TEXT,
    version                     BIGINT      NOT NULL DEFAULT 1,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at                 TIMESTAMPTZ,
    archived_at                 TIMESTAMPTZ,
    created_by_actor_pseudo_id  TEXT        NOT NULL,
    correlation_id              TEXT        NOT NULL,
    CONSTRAINT hypotheses_id_format_chk
        CHECK (id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT hypotheses_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT hypotheses_statement_len_chk
        CHECK (char_length(statement) BETWEEN 1 AND 1024),
    CONSTRAINT hypotheses_subject_reference_len_chk
        CHECK (char_length(subject_reference) BETWEEN 1 AND 128),
    CONSTRAINT hypotheses_subject_kind_len_chk
        CHECK (subject_kind IS NULL OR char_length(subject_kind) <= 64),
    CONSTRAINT hypotheses_certainty_enum_chk
        CHECK (certainty IN ('HYPOTHESIS', 'ASSERTED', 'VERIFIED', 'DISPUTED')),
    CONSTRAINT hypotheses_confidence_range_chk
        CHECK (confidence IS NULL OR (confidence >= 0.000 AND confidence <= 1.000)),
    CONSTRAINT hypotheses_status_enum_chk
        CHECK (status IN ('DRAFT', 'ACTIVE', 'CORROBORATED', 'REFUTED', 'SUPERSEDED')),
    CONSTRAINT hypotheses_superseded_by_len_chk
        CHECK (superseded_by_hypothesis_id IS NULL
               OR char_length(superseded_by_hypothesis_id) <= 128),
    CONSTRAINT hypotheses_superseded_by_consistency_chk
        CHECK (
            (status = 'SUPERSEDED' AND superseded_by_hypothesis_id IS NOT NULL)
            OR status <> 'SUPERSEDED'
        ),
    CONSTRAINT hypotheses_assigned_to_len_chk
        CHECK (assigned_to IS NULL OR char_length(assigned_to) <= 128),
    CONSTRAINT hypotheses_version_positive_chk
        CHECK (version > 0),
    CONSTRAINT hypotheses_actor_pseudo_id_len_chk
        CHECK (char_length(created_by_actor_pseudo_id) BETWEEN 1 AND 128),
    CONSTRAINT hypotheses_correlation_id_len_chk
        CHECK (char_length(correlation_id) BETWEEN 1 AND 128)
);

CREATE INDEX hypotheses_tenant_idx
    ON research_service.hypotheses (tenant_id);
CREATE INDEX hypotheses_tenant_status_idx
    ON research_service.hypotheses (tenant_id, status);
CREATE INDEX hypotheses_tenant_subject_idx
    ON research_service.hypotheses (tenant_id, subject_reference);

ALTER TABLE research_service.hypotheses
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.hypotheses
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON research_service.hypotheses;
CREATE POLICY tenant_isolation ON research_service.hypotheses
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- conflicts — the Conflict aggregate root (6 of 6).
-- `linked_citation_ids` is denormalised JSONB (top-level
-- citations linked to the conflict); the per-participant
-- supporting citations land in `conflict_participant_supporting_citations`
-- (bridge 5 of 5). The participant list itself lives in
-- `conflict_participants` (bridge 4 of 5).
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.conflicts (
    id                          TEXT        PRIMARY KEY,
    tenant_id                   TEXT        NOT NULL,
    summary                     TEXT        NOT NULL,
    kind                        TEXT        NOT NULL,
    kind_note                   TEXT,
    status                      TEXT        NOT NULL DEFAULT 'OPEN',
    resolution                  TEXT,
    resolution_proof            TEXT,
    linked_citation_ids         JSONB       NOT NULL DEFAULT '[]'::jsonb,
    version                     BIGINT      NOT NULL DEFAULT 1,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at                 TIMESTAMPTZ,
    archived_at                 TIMESTAMPTZ,
    created_by_actor_pseudo_id  TEXT        NOT NULL,
    correlation_id              TEXT        NOT NULL,
    CONSTRAINT conflicts_id_format_chk
        CHECK (id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT conflicts_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT conflicts_summary_len_chk
        CHECK (char_length(summary) BETWEEN 1 AND 1024),
    CONSTRAINT conflicts_kind_enum_chk
        CHECK (kind IN ('SOURCE_DISAGREES', 'CITATION_DISAGREES',
                         'CLAIM_CONTRADICTS_SOURCE', 'HYPOTHESIS_COLLIDES',
                         'OTHER')),
    CONSTRAINT conflicts_kind_note_len_chk
        CHECK (kind_note IS NULL OR char_length(kind_note) <= 1024),
    CONSTRAINT conflicts_status_enum_chk
        CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'ABANDONED')),
    CONSTRAINT conflicts_resolution_len_chk
        CHECK (resolution IS NULL OR char_length(resolution) <= 4096),
    CONSTRAINT conflicts_resolution_proof_len_chk
        CHECK (resolution_proof IS NULL OR char_length(resolution_proof) <= 128),
    CONSTRAINT conflicts_resolved_consistency_chk
        CHECK (
            (status = 'RESOLVED' AND resolution IS NOT NULL
                                  AND resolution_proof IS NOT NULL
                                  AND resolved_at IS NOT NULL)
            OR status <> 'RESOLVED'
        ),
    CONSTRAINT conflicts_version_positive_chk
        CHECK (version > 0),
    CONSTRAINT conflicts_actor_pseudo_id_len_chk
        CHECK (char_length(created_by_actor_pseudo_id) BETWEEN 1 AND 128),
    CONSTRAINT conflicts_correlation_id_len_chk
        CHECK (char_length(correlation_id) BETWEEN 1 AND 128)
);

CREATE INDEX conflicts_tenant_idx
    ON research_service.conflicts (tenant_id);
CREATE INDEX conflicts_tenant_status_idx
    ON research_service.conflicts (tenant_id, status);
CREATE INDEX conflicts_tenant_kind_idx
    ON research_service.conflicts (tenant_id, kind);

ALTER TABLE research_service.conflicts
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.conflicts
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON research_service.conflicts;
CREATE POLICY tenant_isolation ON research_service.conflicts
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ===========================================================================
-- BRIDGE TABLES (5 of 5, per tasks.md §E6.1b scope contract).
-- ===========================================================================

-- ---------------------------------------------------------------------------
-- Bridge 1 of 5 — research_task_assignments.
-- Assignments of research tasks to platform users (pseudonymised).
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.research_task_assignments (
    task_id                     TEXT        NOT NULL,
    tenant_id                   TEXT        NOT NULL,
    assignee_pseudo_id          TEXT        NOT NULL,
    assignee_role               TEXT,
    assigned_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at                 TIMESTAMPTZ,
    note                        TEXT,
    PRIMARY KEY (task_id, assignee_pseudo_id, assigned_at),
    CONSTRAINT rta_task_id_format_chk
        CHECK (task_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT rta_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT rta_assignee_pseudo_id_len_chk
        CHECK (char_length(assignee_pseudo_id) BETWEEN 1 AND 128),
    CONSTRAINT rta_assignee_role_len_chk
        CHECK (assignee_role IS NULL OR char_length(assignee_role) <= 64),
    CONSTRAINT rta_released_at_consistency_chk
        CHECK (released_at IS NULL OR released_at >= assigned_at),
    CONSTRAINT rta_note_len_chk
        CHECK (note IS NULL OR char_length(note) <= 1024)
);

CREATE INDEX rta_tenant_idx
    ON research_service.research_task_assignments (tenant_id);
CREATE INDEX rta_tenant_task_idx
    ON research_service.research_task_assignments (tenant_id, task_id);
CREATE INDEX rta_tenant_assignee_idx
    ON research_service.research_task_assignments (tenant_id, assignee_pseudo_id);

ALTER TABLE research_service.research_task_assignments
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.research_task_assignments
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation
    ON research_service.research_task_assignments;
CREATE POLICY tenant_isolation
    ON research_service.research_task_assignments
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- Bridge 2 of 5 — hypothesis_corroborating_citations.
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.hypothesis_corroborating_citations (
    hypothesis_id               TEXT        NOT NULL,
    citation_id                 TEXT        NOT NULL,
    tenant_id                   TEXT        NOT NULL,
    linked_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (hypothesis_id, citation_id),
    CONSTRAINT hcc_hypothesis_id_format_chk
        CHECK (hypothesis_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT hcc_citation_id_format_chk
        CHECK (citation_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT hcc_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$')
);

CREATE INDEX hcc_tenant_idx
    ON research_service.hypothesis_corroborating_citations (tenant_id);
CREATE INDEX hcc_tenant_hypothesis_idx
    ON research_service.hypothesis_corroborating_citations (tenant_id, hypothesis_id);
CREATE INDEX hcc_tenant_citation_idx
    ON research_service.hypothesis_corroborating_citations (tenant_id, citation_id);

ALTER TABLE research_service.hypothesis_corroborating_citations
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.hypothesis_corroborating_citations
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation
    ON research_service.hypothesis_corroborating_citations;
CREATE POLICY tenant_isolation
    ON research_service.hypothesis_corroborating_citations
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- Bridge 3 of 5 — hypothesis_refuting_citations.
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.hypothesis_refuting_citations (
    hypothesis_id               TEXT        NOT NULL,
    citation_id                 TEXT        NOT NULL,
    tenant_id                   TEXT        NOT NULL,
    linked_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (hypothesis_id, citation_id),
    CONSTRAINT hrc_hypothesis_id_format_chk
        CHECK (hypothesis_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT hrc_citation_id_format_chk
        CHECK (citation_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT hrc_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$')
);

CREATE INDEX hrc_tenant_idx
    ON research_service.hypothesis_refuting_citations (tenant_id);
CREATE INDEX hrc_tenant_hypothesis_idx
    ON research_service.hypothesis_refuting_citations (tenant_id, hypothesis_id);
CREATE INDEX hrc_tenant_citation_idx
    ON research_service.hypothesis_refuting_citations (tenant_id, citation_id);

ALTER TABLE research_service.hypothesis_refuting_citations
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.hypothesis_refuting_citations
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation
    ON research_service.hypothesis_refuting_citations;
CREATE POLICY tenant_isolation
    ON research_service.hypothesis_refuting_citations
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- Bridge 4 of 5 — conflict_participants.
-- At least two participants per conflict is enforced by the
-- application-layer invariant `CONFLICT_REQUIRE_MULTIPLE_PARTICIPANTS`
-- (DENY). The ordinal column positions each side so the SQL
-- CHECK `ordinal BETWEEN 1 AND 16` matches the domain cap
-- (`maxConflictParticipants: 16`).
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.conflict_participants (
    conflict_id                 TEXT        NOT NULL,
    ordinal                     SMALLINT    NOT NULL,
    tenant_id                   TEXT        NOT NULL,
    reference                   TEXT        NOT NULL,
    reference_kind              TEXT,
    interpretation              TEXT,
    PRIMARY KEY (conflict_id, ordinal),
    CONSTRAINT cp_conflict_id_format_chk
        CHECK (conflict_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT cp_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT cp_ordinal_positive_chk
        CHECK (ordinal BETWEEN 1 AND 16),
    CONSTRAINT cp_reference_len_chk
        CHECK (char_length(reference) BETWEEN 1 AND 128),
    CONSTRAINT cp_reference_kind_len_chk
        CHECK (reference_kind IS NULL OR char_length(reference_kind) <= 64),
    CONSTRAINT cp_interpretation_len_chk
        CHECK (interpretation IS NULL OR char_length(interpretation) <= 1024)
);

CREATE INDEX cp_tenant_idx
    ON research_service.conflict_participants (tenant_id);
CREATE INDEX cp_tenant_conflict_idx
    ON research_service.conflict_participants (tenant_id, conflict_id);

ALTER TABLE research_service.conflict_participants
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.conflict_participants
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation
    ON research_service.conflict_participants;
CREATE POLICY tenant_isolation
    ON research_service.conflict_participants
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- Bridge 5 of 5 — conflict_participant_supporting_citations.
-- Per-participant supporting citations. Composite PK
-- (conflict_id, ordinal, citation_id) mirrors the
-- `conflict_participants` ordering.
-- ---------------------------------------------------------------------------

CREATE TABLE research_service.conflict_participant_supporting_citations (
    conflict_id                 TEXT        NOT NULL,
    ordinal                     SMALLINT    NOT NULL,
    citation_id                 TEXT        NOT NULL,
    tenant_id                   TEXT        NOT NULL,
    linked_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (conflict_id, ordinal, citation_id),
    CONSTRAINT cpsc_conflict_id_format_chk
        CHECK (conflict_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT cpsc_ordinal_chk
        CHECK (ordinal BETWEEN 1 AND 16),
    CONSTRAINT cpsc_citation_id_format_chk
        CHECK (citation_id ~ '^[A-Za-z0-9._-]{1,128}$'),
    CONSTRAINT cpsc_tenant_format_chk
        CHECK (tenant_id ~ '^[A-Za-z0-9._-]{1,128}$')
);

CREATE INDEX cpsc_tenant_idx
    ON research_service.conflict_participant_supporting_citations (tenant_id);
CREATE INDEX cpsc_tenant_conflict_idx
    ON research_service.conflict_participant_supporting_citations (tenant_id, conflict_id);

ALTER TABLE research_service.conflict_participant_supporting_citations
    ENABLE ROW LEVEL SECURITY;
ALTER TABLE research_service.conflict_participant_supporting_citations
    FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation
    ON research_service.conflict_participant_supporting_citations;
CREATE POLICY tenant_isolation
    ON research_service.conflict_participant_supporting_citations
    USING (tenant_id = research_service.current_tenant_id())
    WITH CHECK (tenant_id = research_service.current_tenant_id());

-- ---------------------------------------------------------------------------
-- Documentation: comments are pulled into Flyway info so a future
-- operator reading `flyway -info` understands the schema's intent.
-- ---------------------------------------------------------------------------

COMMENT ON SCHEMA research_service IS
    'Schema owned by research-service per ADR-E0.5-02 (schema-per-service). '
    'E6.1b adds the six research aggregate tables (repositories, '
    'sources, citations, research_tasks, hypotheses, conflicts) plus '
    'five bridge tables (research_task_assignments, '
    'hypothesis_corroborating_citations, hypothesis_refuting_citations, '
    'conflict_participants, conflict_participant_supporting_citations) '
    'plus PostgreSQL Row-Level Security (FORCE) and the '
    'research_service_app runtime role. Audit columns '
    '(created_at, updated_at, archived_at, version, '
    'created_by_actor_pseudo_id, correlation_id) live on every '
    'aggregate row. Closed-set vocabularies are pinned as CHECK '
    'constraints so they stay in lockstep with the Java enum '
    'constants and the research-policy.yaml contract. Inline '
    'list payloads (transcript_segments / attachments / external_urls '
    'on citations; linked_citation_ids on research_tasks / conflicts) '
    'live as JSONB on the parent row; promoting them to dedicated '
    'bridge tables is a future V3 migration when indexed access is '
    'needed. Domain logic lands in E6.1a; REST + OpenAPI in E6.1c; '
    'gRPC + Kafka in E6.1d; Testcontainers + Helm in E6.1e.';