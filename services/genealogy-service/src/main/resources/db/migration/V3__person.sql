-- E4.2 — Person aggregate (R4 / R13.6 / NFR4).
--
-- Schema-per-service per ADR-E0.5-02. RLS defence-in-depth per
-- `design.md` §5.1. The aggregate carries the closed-set
-- privacy + living status + lifecycle; the names / pronouns /
-- identifiers live on child tables with composite foreign keys.
--
-- The `person_history` table is an append-only audit trail per
-- `requirements.md` R4.6 / `design.md` §6.2 obligations; once
-- written, history rows MUST NOT be UPDATEd or DELETEd
-- (enforced via trigger + REVOKE). The audit ledger (E3.6)
-- records the audit-class and audit-action from
-- `contracts/genealogy/person-policy.yaml`.

CREATE TABLE tree_service.person (
    person_id           UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    tree_id             UUID NOT NULL,
    living_status       VARCHAR(16) NOT NULL CHECK (living_status IN
                            ('LIVING','DECEASED','UNKNOWN','INFERRED_LIVING')),
    privacy_level       VARCHAR(16) NOT NULL CHECK (privacy_level IN
                            ('PRIVATE','TREE_DEFAULT','UNLISTED','PUBLIC')),
    gender_description  VARCHAR(32) CHECK (gender_description IS NULL
                            OR gender_description IN
                            ('FEMALE','MALE','NONBINARY','UNDISCLOSED','SELF_DESCRIBED')),
    biography           TEXT CHECK (biography IS NULL OR length(biography) <= 8192),
    verified_user_id    UUID,
    lifecycle_state     VARCHAR(16) NOT NULL CHECK (lifecycle_state IN
                            ('ACTIVE','MERGED','DELETED')),
    version             BIGINT NOT NULL CHECK (version >= 1),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    created_by          UUID NOT NULL
);

CREATE INDEX person_tenant_idx ON tree_service.person (tenant_id);
CREATE INDEX person_tenant_tree_idx ON tree_service.person (tenant_id, tree_id);
CREATE INDEX person_tenant_tree_lifecycle_idx
    ON tree_service.person (tenant_id, tree_id, lifecycle_state);

CREATE TABLE tree_service.person_name (
    name_id     UUID PRIMARY KEY,
    person_id   UUID NOT NULL REFERENCES tree_service.person (person_id) ON DELETE CASCADE,
    tenant_id   UUID NOT NULL,
    kind        VARCHAR(16) NOT NULL CHECK (kind IN
                    ('BIRTH','PREFERRED','MARRIED','RELIGIOUS','PROFESSIONAL','ALIAS','NICKNAME')),
    script_tag  VARCHAR(16) NOT NULL CHECK (script_tag IN
                    ('Latn','Cyrl','Hans','Hant','Jpan','Kana','Hang','Hebr','Thai','Arab')),
    locale_tag  VARCHAR(35),
    display     TEXT NOT NULL CHECK (length(display) BETWEEN 1 AND 512),
    romanised   TEXT CHECK (romanised IS NULL OR length(romanised) <= 512),
    preferred   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (person_id, kind, display)
);

CREATE INDEX person_name_person_idx ON tree_service.person_name (person_id);
CREATE INDEX person_name_tenant_idx ON tree_service.person_name (tenant_id);

CREATE TABLE tree_service.person_pronoun (
    person_id   UUID NOT NULL REFERENCES tree_service.person (person_id) ON DELETE CASCADE,
    tenant_id   UUID NOT NULL,
    pronoun     VARCHAR(16) NOT NULL CHECK (pronoun IN
                    ('HE_HIM','SHE_HER','THEY_THEM','ZE_ZIR','XE_XEM','SELF_DESCRIBED','NOT_SPECIFIED')),
    free_text   TEXT CHECK (free_text IS NULL OR length(free_text) <= 256),
    declared_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (person_id, pronoun)
);

CREATE INDEX person_pronoun_tenant_idx ON tree_service.person_pronoun (tenant_id);

CREATE TABLE tree_service.person_identifier (
    identifier_id  UUID PRIMARY KEY,
    person_id      UUID NOT NULL REFERENCES tree_service.person (person_id) ON DELETE CASCADE,
    tenant_id      UUID NOT NULL,
    kind           VARCHAR(32) NOT NULL CHECK (kind IN
                       ('WIKIDATA_QID','FAMILYSEARCH_ID','ANCESTRY_ID',
                        'FINDAGRAVE_ID','GENI_ID','LOCAL_SLUG','GEDCOM_XREF')),
    value          TEXT NOT NULL CHECK (length(value) BETWEEN 1 AND 256),
    source_system  TEXT,
    verified       BOOLEAN NOT NULL DEFAULT FALSE,
    verified_at    TIMESTAMPTZ,
    attached_at    TIMESTAMPTZ NOT NULL,
    UNIQUE (person_id, kind, value)
);

CREATE INDEX person_identifier_tenant_idx ON tree_service.person_identifier (tenant_id);

-- Append-only audit history. The composite PK on (person_id,
-- version) records one row per committed mutation. The
-- application MUST never UPDATE / DELETE these rows.
CREATE TABLE tree_service.person_history (
    person_id        UUID NOT NULL,
    tenant_id        UUID NOT NULL,
    tree_id          UUID NOT NULL,
    version          BIGINT NOT NULL,
    actor_id         UUID NOT NULL,
    reason           TEXT,
    changed_fields   TEXT[] NOT NULL,
    snapshot         JSONB NOT NULL,
    recorded_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (person_id, version)
);

CREATE INDEX person_history_tenant_idx ON tree_service.person_history (tenant_id);
CREATE INDEX person_history_recorded_at_idx ON tree_service.person_history (recorded_at);

-- Tenant isolation — RLS per `design.md` §5.1.
ALTER TABLE tree_service.person ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.person FORCE ROW LEVEL SECURITY;
CREATE POLICY person_tenant_isolation ON tree_service.person
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE tree_service.person_name ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.person_name FORCE ROW LEVEL SECURITY;
CREATE POLICY person_name_tenant_isolation ON tree_service.person_name
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE tree_service.person_pronoun ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.person_pronoun FORCE ROW LEVEL SECURITY;
CREATE POLICY person_pronoun_tenant_isolation ON tree_service.person_pronoun
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE tree_service.person_identifier ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.person_identifier FORCE ROW LEVEL SECURITY;
CREATE POLICY person_identifier_tenant_isolation ON tree_service.person_identifier
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- person_history participates in tenant isolation too; the
-- audit service and reversal workflows need cross-tenant
-- reads, so the application role for that service uses a
-- dedicated read-only grant (created in the audit-service
-- migration per ADR-E0.5-02).
ALTER TABLE tree_service.person_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.person_history FORCE ROW LEVEL SECURITY;
CREATE POLICY person_history_tenant_isolation ON tree_service.person_history
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Append-only discipline on person_history. Even the
-- application role can never UPDATE or DELETE history rows.
CREATE OR REPLACE FUNCTION tree_service.person_history_append_only()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'person_history is append-only';
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER person_history_no_update
    BEFORE UPDATE OR DELETE ON tree_service.person_history
    FOR EACH ROW EXECUTE FUNCTION tree_service.person_history_append_only();

-- Application role scope per ADR-E0.5-02.
GRANT SELECT, INSERT, UPDATE ON tree_service.person TO tree_service_app;
GRANT SELECT, INSERT ON tree_service.person_name TO tree_service_app;
GRANT SELECT, INSERT, DELETE ON tree_service.person_pronoun TO tree_service_app;
GRANT SELECT, INSERT, UPDATE ON tree_service.person_identifier TO tree_service_app;
GRANT SELECT, INSERT ON tree_service.person_history TO tree_service_app;
