-- E4.1 — UNLISTED-visibility token table.
--
-- Per `contracts/genealogy/unlisted-token.yaml` the plaintext token
-- NEVER leaves the issuing service. Only the SHA-256 fingerprint
-- (hex lower-case, 64 chars) is stored. Scope is closed-set
-- (FULL_TREE / BRANCH). TTL + revocation are tracked.

CREATE TABLE tree_service.unlisted_token (
    token_id            UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    tree_id             UUID NOT NULL,
    fingerprint         CHAR(64) NOT NULL,
    scope               VARCHAR(16) NOT NULL CHECK (scope IN ('FULL_TREE','BRANCH')),
    branch_id           UUID,
    issued_at           TIMESTAMPTZ NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    issued_by           UUID NOT NULL,
    revoked_by          UUID,
    revocation_reason   TEXT,
    CONSTRAINT unlisted_token_fingerprint_unique_per_tenant UNIQUE (tenant_id, fingerprint),
    CONSTRAINT unlisted_token_expires_after_issued CHECK (expires_at > issued_at),
    CONSTRAINT unlisted_token_branch_scope_aligned CHECK (
        (scope = 'BRANCH' AND branch_id IS NOT NULL)
        OR (scope = 'FULL_TREE' AND branch_id IS NULL)
    )
);

CREATE INDEX unlisted_token_tenant_tree_idx ON tree_service.unlisted_token (tenant_id, tree_id);
CREATE INDEX unlisted_token_active_idx ON tree_service.unlisted_token (expires_at)
    WHERE revoked_at IS NULL;

-- Tenant isolation — RLS per `design.md` §5.1.
ALTER TABLE tree_service.unlisted_token ENABLE ROW LEVEL SECURITY;
ALTER TABLE tree_service.unlisted_token FORCE ROW LEVEL SECURITY;
CREATE POLICY unlisted_token_tenant_isolation ON tree_service.unlisted_token
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE ON tree_service.unlisted_token TO tree_service_app;
