-- E4.5 — Life-event + claim aggregates (R3 / R4.1 / R4.4 /
-- R5 / R8 / R10 / R18 / NFR1 / NFR4).
--
-- Schema-per-service per ADR-E0.5-02. RLS defence-in-depth
-- per `design.md` §5.1.
--
-- The `life_event` table is the aggregate root for the
-- life-event sub-system that `requirements.md` R4.1 + R5
-- mandate. A life-event has many participants with roles
-- (witness / officiant / informant / subject / partner /
-- parent / child / sibling / guardian / ward), an optional
-- date (E4.3), an optional place (E4.3) and a privacy
-- classification that the ABAC layer (E3.4) may downgrade
-- further when a living person participates.
--
-- The `claim` table stores research-log assertions. Claims
-- are first-class objects: a claim can exist WITHOUT being
-- attached to an event (research hypotheses, name-spelling
-- corrections, etc.) or it can attach to one event. Every
-- claim MUST carry at least one source reference (R4.4 / R8).
--
-- Cross-table: `correctsClaimId` on a claim links back to
-- another claim. The chain is the merge service's (E4.6)
-- primary input.

CREATE TABLE tree_service.life_event (
    event_id              UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    tree_id               UUID NOT NULL,
    kind                  VARCHAR(32) NOT NULL CHECK (kind IN (
                            'BIRTH','BAPTISM','DEATH','BURIAL','CREMATION',
                            'MARRIAGE','DIVORCE','ENGAGEMENT','EDUCATION',
                            'OCCUPATION','RESIDENCE','IMMIGRATION','EMIGRATION',
                            'MILITARY_SERVICE','ILLNESS','RELIGIOUS_CEREMONY',
                            'RECURRING_MEMORIAL','CUSTOM')),
    custom_label          VARCHAR(256),
    certainty             VARCHAR(16) NOT NULL CHECK (certainty IN (
                            'HYPOTHESIS','ASSERTED','VERIFIED','DISPUTED')),
    provenance            VARCHAR(32) NOT NULL CHECK (provenance IN (
                            'USER_ENTERED','IMPORTED',
                            'VERIFIED_BY_SOURCE','CORRECTION')),
    privacy_classification VARCHAR(16) NOT NULL CHECK (privacy_classification IN (
                            'PRIVATE','UNLISTED','PUBLIC')),
    description           VARCHAR(2048),
    date_value_id         UUID,
    place_id              UUID,
    version               BIGINT NOT NULL CHECK (version >= 1),
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    created_by            UUID NOT NULL,
    CHECK (
        (kind = 'CUSTOM' AND custom_label IS NOT NULL)
        OR (kind <> 'CUSTOM')
    ),
    CHECK (
        (kind = 'RECURRING_MEMORIAL' AND date_value_id IS NOT NULL)
        OR (kind <> 'RECURRING_MEMORIAL')
    ),
    CHECK (
        provenance <> 'IMPORTED' OR certainty <> 'VERIFIED'
    )
);

CREATE INDEX life_event_tenant_idx ON tree_service.life_event (tenant_id);
CREATE INDEX life_event_tenant_tree_idx
    ON tree_service.life_event (tenant_id, tree_id);
CREATE INDEX life_event_tenant_kind_idx
    ON tree_service.life_event (tenant_id, kind);

CREATE TABLE tree_service.life_event_participant (
    event_id       UUID NOT NULL
                   REFERENCES tree_service.life_event(event_id)
                   ON DELETE CASCADE,
    participant_id UUID NOT NULL,
    role           VARCHAR(16) NOT NULL CHECK (role IN (
                     'SUBJECT','PARENT','CHILD','SIBLING','PARTNER',
                     'GUARDIAN','WARD','WITNESS','OFFICIANT','INFORMANT')),
    person_id      UUID,
    unknown_subject BOOLEAN NOT NULL DEFAULT FALSE,
    recorded_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, participant_id),
    CHECK (
        (person_id IS NOT NULL AND unknown_subject = FALSE)
        OR (person_id IS NULL AND unknown_subject = TRUE)
    )
);

CREATE INDEX life_event_part_role_idx
    ON tree_service.life_event_participant (role);
CREATE INDEX life_event_part_person_idx
    ON tree_service.life_event_participant (person_id)
    WHERE person_id IS NOT NULL;

CREATE TABLE tree_service.claim (
    claim_id          UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    tree_id           UUID NOT NULL,
    subject_kind      VARCHAR(32) NOT NULL,
    subject_id        UUID NOT NULL,
    certainty         VARCHAR(16) NOT NULL CHECK (certainty IN (
                        'HYPOTHESIS','ASSERTED','VERIFIED','DISPUTED')),
    provenance        VARCHAR(32) NOT NULL CHECK (provenance IN (
                        'USER_ENTERED','IMPORTED',
                        'VERIFIED_BY_SOURCE','CORRECTION')),
    confidence        NUMERIC(4,3) CHECK (
                        confidence IS NULL
                        OR (confidence >= 0.0 AND confidence <= 1.0)),
    statement         VARCHAR(2048),
    corrects_claim_id UUID
                      REFERENCES tree_service.claim(claim_id)
                      ON DELETE SET NULL,
    attached_event_id UUID
                      REFERENCES tree_service.life_event(event_id)
                      ON DELETE SET NULL,
    version           BIGINT NOT NULL CHECK (version >= 1),
    created_by        UUID NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    CHECK (
        provenance <> 'IMPORTED' OR certainty <> 'VERIFIED'
    ),
    CHECK (
        (provenance = 'CORRECTION' AND corrects_claim_id IS NOT NULL)
        OR (provenance <> 'CORRECTION' AND corrects_claim_id IS NULL)
    )
);

CREATE INDEX claim_tenant_idx ON tree_service.claim (tenant_id);
CREATE INDEX claim_tenant_tree_idx ON tree_service.claim (tenant_id, tree_id);
CREATE INDEX claim_tenant_subject_idx
    ON tree_service.claim (tenant_id, subject_kind, subject_id);
CREATE INDEX claim_corrects_idx ON tree_service.claim (corrects_claim_id)
    WHERE corrects_claim_id IS NOT NULL;

CREATE TABLE tree_service.claim_source (
    claim_id    UUID NOT NULL
                REFERENCES tree_service.claim(claim_id)
                ON DELETE CASCADE,
    seq         INTEGER NOT NULL,
    kind        VARCHAR(32) NOT NULL CHECK (kind IN (
                  'REPOSITORY_CITATION','DOCUMENT_CITATION',
                  'TRANSCRIPT_CITATION','PAGE_LOCATOR',
                  'URL','MEDIA_ATTACHMENT','INTERVIEW_NOTE')),
    source_id   VARCHAR(256) NOT NULL,
    locator     VARCHAR(512),
    quote       VARCHAR(2048),
    reliability NUMERIC(4,3) CHECK (
                  reliability IS NULL
                  OR (reliability >= 0.0 AND reliability <= 1.0)),
    PRIMARY KEY (claim_id, seq),
    CHECK (seq >= 1 AND seq <= 32)
);

CREATE INDEX claim_source_kind_idx ON tree_service.claim_source (kind);

-- Tenant isolation — RLS per `design.md` §5.1. The
-- participant / source tables inherit tenant scope via
-- their parent rows (no tenant_id column on their own).
ALTER TABLE tree_service.life_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.life_event FORCE ROW LEVEL SECURITY;
CREATE POLICY life_event_tenant_isolation ON tree_service.life_event
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE tree_service.life_event_participant ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.life_event_participant FORCE ROW LEVEL SECURITY;
CREATE POLICY lep_tenant_isolation ON tree_service.life_event_participant
    USING (
        EXISTS (
            SELECT 1 FROM tree_service.life_event e
            WHERE e.event_id = life_event_participant.event_id
              AND e.tenant_id = current_setting('app.tenant_id', true)::uuid
        )
    );

ALTER TABLE tree_service.claim ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.claim FORCE ROW LEVEL SECURITY;
CREATE POLICY claim_tenant_isolation ON tree_service.claim
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE tree_service.claim_source ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.claim_source FORCE ROW LEVEL SECURITY;
CREATE POLICY claim_source_tenant_isolation ON tree_service.claim_source
    USING (
        EXISTS (
            SELECT 1 FROM tree_service.claim c
            WHERE c.claim_id = claim_source.claim_id
              AND c.tenant_id = current_setting('app.tenant_id', true)::uuid
        )
    );

-- Application role scope per ADR-E0.5-02.
GRANT SELECT, INSERT, UPDATE ON tree_service.life_event
    TO tree_service_app;
GRANT SELECT, INSERT, UPDATE ON tree_service.life_event_participant
    TO tree_service_app;
GRANT SELECT, INSERT, UPDATE ON tree_service.claim
    TO tree_service_app;
GRANT SELECT, INSERT, UPDATE ON tree_service.claim_source
    TO tree_service_app;
