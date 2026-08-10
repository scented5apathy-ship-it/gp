-- E4.6 — Person merge + history (R4.5 / R4.6 / R5.5 / R8.5 /
-- R10 / R16 / NFR1 / NFR4).
--
-- Schema-per-service per ADR-E0.5-02. RLS defence-in-depth
-- per `design.md` §5.1.
--
-- The merge workflow lands three tables:
--
--   1. `tree_service.merge_record` — one row per merge
--      attempt (CANDIDATES_SCORED → REVIEWED → MERGED;
--      reachable REVERTED only from MERGED inside the
--      revert window). The row carries the winner / loser,
--      the score, the reviewer, the reason, the snapshot
--      hash and the canonical revert command (R4.5 + R4.6
--      + R16).
--
--   2. `tree_service.merge_candidate` — the per-pair
--      scoring breakdown emitted by the scorer. Each
--      row is append-only so the operator can audit the
--      score retroactively (R4.6).
--
--   3. `tree_service.person_merge_redirect` — an
--      indirection row for external references (share
--      tokens, public URLs). When a Person is merged
--      away, every external id attached to the loser is
--      re-keyed to the winner and the original id is
--      revoked (glossary §2.4 #5). The redirect row is
--      what external callers hit; the platform maps
--      301 → winner + records audit.

CREATE TABLE tree_service.merge_record (
    merge_id                  UUID PRIMARY KEY,
    tenant_id                 UUID NOT NULL,
    tree_id                   UUID NOT NULL,
    kind                      VARCHAR(32) NOT NULL CHECK (kind IN (
                                'DUPLICATE_PERSON_MERGE')),
    winner_person_id          UUID NOT NULL,
    loser_person_id           UUID NOT NULL,
    status                    VARCHAR(32) NOT NULL CHECK (status IN (
                                'CANDIDATES_SCORED','REVIEWED','MERGED',
                                'REVERTED','REJECTED')),
    score                     NUMERIC(5,4) NOT NULL CHECK (
                                score >= 0.0 AND score <= 1.0),
    provenance                VARCHAR(32) NOT NULL CHECK (provenance IN (
                                'USER_REVIEW','AUTOMATED_SCORER',
                                'IMPORTED','CORRECTION')),
    reviewer_user_id          UUID,
    reason                    VARCHAR(2048),
    snapshot_hash             VARCHAR(128),
    revert_command_json       VARCHAR(2048),
    rekeyed_reference_count   BIGINT NOT NULL DEFAULT 0
                              CHECK (rekeyed_reference_count >= 0),
    merged_at                 TIMESTAMPTZ,
    reverted_at               TIMESTAMPTZ,
    created_at                TIMESTAMPTZ NOT NULL,
    updated_at                TIMESTAMPTZ NOT NULL,
    created_by                UUID NOT NULL,
    version                   BIGINT NOT NULL CHECK (version >= 1),
    CHECK (winner_person_id <> loser_person_id),
    CHECK (
        (status = 'MERGED' AND reviewer_user_id IS NOT NULL
            AND reason IS NOT NULL
            AND snapshot_hash IS NOT NULL
            AND merged_at IS NOT NULL)
        OR (status <> 'MERGED')
    ),
    CHECK (
        (status = 'REVERTED' AND reviewer_user_id IS NOT NULL
            AND reason IS NOT NULL
            AND reverted_at IS NOT NULL)
        OR (status <> 'REVERTED')
    )
);

CREATE INDEX merge_record_tenant_idx ON tree_service.merge_record (tenant_id);
CREATE INDEX merge_record_tenant_tree_idx
    ON tree_service.merge_record (tenant_id, tree_id);
CREATE INDEX merge_record_winner_idx
    ON tree_service.merge_record (winner_person_id);
CREATE INDEX merge_record_loser_idx
    ON tree_service.merge_record (loser_person_id);
CREATE INDEX merge_record_status_idx
    ON tree_service.merge_record (status);

CREATE TABLE tree_service.merge_candidate (
    merge_id          UUID NOT NULL
                      REFERENCES tree_service.merge_record(merge_id)
                      ON DELETE CASCADE,
    candidate_id      VARCHAR(128) NOT NULL,
    winner_person_id  UUID NOT NULL,
    loser_person_id   UUID NOT NULL,
    name_equality     NUMERIC(5,4) NOT NULL CHECK (
                      name_equality >= 0.0 AND name_equality <= 1.0),
    date_proximity    NUMERIC(5,4) NOT NULL CHECK (
                      date_proximity >= 0.0 AND date_proximity <= 1.0),
    place_proximity   NUMERIC(5,4) NOT NULL CHECK (
                      place_proximity >= 0.0 AND place_proximity <= 1.0),
    identifier_match  NUMERIC(5,4) NOT NULL CHECK (
                      identifier_match >= 0.0 AND identifier_match <= 1.0),
    overall_score     NUMERIC(5,4) NOT NULL CHECK (
                      overall_score >= 0.0 AND overall_score <= 1.0),
    provenance        VARCHAR(32) NOT NULL CHECK (provenance IN (
                      'USER_REVIEW','AUTOMATED_SCORER',
                      'IMPORTED','CORRECTION')),
    PRIMARY KEY (merge_id, candidate_id),
    CHECK (winner_person_id <> loser_person_id)
);

CREATE INDEX merge_candidate_winner_idx
    ON tree_service.merge_candidate (winner_person_id);
CREATE INDEX merge_candidate_loser_idx
    ON tree_service.merge_candidate (loser_person_id);
CREATE INDEX merge_candidate_score_idx
    ON tree_service.merge_candidate (overall_score);

CREATE TABLE tree_service.person_merge_redirect (
    redirect_id        UUID PRIMARY KEY,
    tenant_id          UUID NOT NULL,
    tree_id            UUID NOT NULL,
    merge_id           UUID NOT NULL
                       REFERENCES tree_service.merge_record(merge_id)
                       ON DELETE CASCADE,
    original_person_id UUID NOT NULL,
    winner_person_id   UUID NOT NULL,
    external_kind      VARCHAR(32) NOT NULL CHECK (external_kind IN (
                       'SHARE_TOKEN','PUBLIC_URL')),
    external_id        VARCHAR(256) NOT NULL,
    revoked_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL,
    UNIQUE (external_kind, external_id)
);

CREATE INDEX person_merge_redirect_tenant_idx
    ON tree_service.person_merge_redirect (tenant_id);
CREATE INDEX person_merge_redirect_original_idx
    ON tree_service.person_merge_redirect (original_person_id);
CREATE INDEX person_merge_redirect_winner_idx
    ON tree_service.person_merge_redirect (winner_person_id);

-- Tenant isolation — RLS per `design.md` §5.1. The
-- candidate / redirect tables inherit tenant scope via the
-- merge_record parent row (no tenant_id column on their
-- own) so a cross-tenant merge attempt is impossible at
-- the database layer.
ALTER TABLE tree_service.merge_record ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.merge_record FORCE ROW LEVEL SECURITY;
CREATE POLICY merge_record_tenant_isolation ON tree_service.merge_record
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE tree_service.merge_candidate ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.merge_candidate FORCE ROW LEVEL SECURITY;
CREATE POLICY merge_candidate_tenant_isolation ON tree_service.merge_candidate
    USING (
        EXISTS (
            SELECT 1 FROM tree_service.merge_record r
            WHERE r.merge_id = merge_candidate.merge_id
              AND r.tenant_id = current_setting('app.tenant_id', true)::uuid
        )
    );

ALTER TABLE tree_service.person_merge_redirect ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.person_merge_redirect FORCE ROW LEVEL SECURITY;
CREATE POLICY person_merge_redirect_tenant_isolation
    ON tree_service.person_merge_redirect
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Application role scope per ADR-E0.5-02.
GRANT SELECT, INSERT, UPDATE ON tree_service.merge_record
    TO tree_service_app;
GRANT SELECT, INSERT, UPDATE ON tree_service.merge_candidate
    TO tree_service_app;
GRANT SELECT, INSERT, UPDATE ON tree_service.person_merge_redirect
    TO tree_service_app;
