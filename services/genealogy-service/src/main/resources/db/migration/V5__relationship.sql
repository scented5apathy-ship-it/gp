-- E4.4 — Relationship graph and invariants (R3 / R4 / R4.4 /
-- R8 / R10 / R18 / NFR4).
--
-- Schema-per-service per ADR-E0.5-02. RLS defence-in-depth per
-- `design.md` §5.1.
--
-- The `relationship` table is the aggregate root; the
-- `relationship_participant` table is the explicit
-- participant/role model that `design.md` §5.2 mandates
-- instead of denormalised `father_id` / `mother_id` columns.
-- A relationship MUST have 1..8 participants; the cap
-- mirrors `relationship-graph-policy.yaml::
-- spec.maxParticipantsPerRelationship` (default 8).
--
-- The `temporal_validity` window is a tstzrange-like pair of
-- TIMESTAMPTZ columns (valid_from, valid_until). An open-
-- ended relationship (still active) has valid_until = NULL.
-- The application layer (Relationship domain) checks overlap;
-- PostgreSQL keeps the pair ordered at write time.
--
-- Self-link / cycle prevention is enforced at the domain
-- layer (Relationship compact constructor +
-- RelationshipInvariants). Database CHECK guards only the
-- basic structural invariants because a full cycle check
-- would require recursive CTE at insert time and the platform
-- prefers application-layer enforcement (E4.4 / design.md
-- §5.2).

CREATE TABLE tree_service.relationship (
    relationship_id  UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    tree_id          UUID NOT NULL,
    kind             VARCHAR(32) NOT NULL CHECK (kind IN (
                        'BIOLOGICAL_PARENT','ADOPTIVE_PARENT','FOSTER_PARENT',
                        'STEP_PARENT','SURROGATE_PARENT','GUARDIAN',
                        'GODPARENT','PARTNER','SIBLING','HALF_SIBLING',
                        'STEP_SIBLING','CUSTOM')),
    partner_sub_kind VARCHAR(16) CHECK (partner_sub_kind IS NULL OR partner_sub_kind IN (
                        'MARRIED','CIVIL_UNION','COMMON_LAW','UNMARRIED',
                        'DIVORCED','WIDOWED','ANNULLED','UNKNOWN')),
    custom_label     VARCHAR(256),
    certainty        VARCHAR(16) NOT NULL CHECK (certainty IN (
                        'HYPOTHESIS','ASSERTED','VERIFIED','DISPUTED')),
    provenance       VARCHAR(32) NOT NULL CHECK (provenance IN (
                        'USER_ENTERED','IMPORTED','VERIFIED_BY_SOURCE','CORRECTION')),
    valid_from       TIMESTAMPTZ NOT NULL,
    valid_until      TIMESTAMPTZ,
    version          BIGINT NOT NULL CHECK (version >= 1),
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    created_by       UUID NOT NULL,
    CHECK (valid_from <= COALESCE(valid_until, valid_from)),
    CHECK (custom_label IS NULL OR length(custom_label) BETWEEN 1 AND 256),
    CHECK (
        (kind = 'PARTNER' AND partner_sub_kind IS NOT NULL)
        OR (kind <> 'PARTNER' AND partner_sub_kind IS NULL)
    ),
    CHECK (
        (kind = 'CUSTOM' AND custom_label IS NOT NULL)
        OR (kind <> 'CUSTOM')
    )
);

CREATE INDEX relationship_tenant_idx ON tree_service.relationship (tenant_id);
CREATE INDEX relationship_tenant_tree_idx
    ON tree_service.relationship (tenant_id, tree_id);
CREATE INDEX relationship_tenant_kind_idx
    ON tree_service.relationship (tenant_id, kind);
CREATE INDEX relationship_tenant_validity_idx
    ON tree_service.relationship (tenant_id, valid_from, valid_until);

CREATE TABLE tree_service.relationship_participant (
    relationship_id  UUID NOT NULL
                     REFERENCES tree_service.relationship(relationship_id)
                     ON DELETE CASCADE,
    participant_id   UUID NOT NULL,
    role             VARCHAR(16) NOT NULL CHECK (role IN (
                        'PARENT','CHILD','SIBLING','PARTNER',
                        'SUBJECT','GUARDIAN','WARD')),
    person_id        UUID,
    unknown_subject  BOOLEAN NOT NULL DEFAULT FALSE,
    recorded_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (relationship_id, participant_id),
    CHECK (
        (person_id IS NOT NULL AND unknown_subject = FALSE)
        OR (person_id IS NULL AND unknown_subject = TRUE)
    ),
    CHECK (
        unknown_subject = TRUE OR person_id IS NOT NULL
    )
);

CREATE INDEX rel_part_role_idx
    ON tree_service.relationship_participant (role);
CREATE INDEX rel_part_person_idx
    ON tree_service.relationship_participant (person_id)
    WHERE person_id IS NOT NULL;

-- Tenant isolation — RLS per `design.md` §5.1. The
-- relationship_participant table inherits tenant scope via
-- its parent row (no tenant_id column on its own).
ALTER TABLE tree_service.relationship ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.relationship FORCE ROW LEVEL SECURITY;
CREATE POLICY relationship_tenant_isolation ON tree_service.relationship
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE tree_service.relationship_participant ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.relationship_participant FORCE ROW LEVEL SECURITY;
CREATE POLICY rel_part_tenant_isolation ON tree_service.relationship_participant
    USING (
        EXISTS (
            SELECT 1 FROM tree_service.relationship r
            WHERE r.relationship_id = relationship_participant.relationship_id
              AND r.tenant_id = current_setting('app.tenant_id', true)::uuid
        )
    );

-- Application role scope per ADR-E0.5-02.
GRANT SELECT, INSERT, UPDATE ON tree_service.relationship
    TO tree_service_app;
GRANT SELECT, INSERT, UPDATE ON tree_service.relationship_participant
    TO tree_service_app;
