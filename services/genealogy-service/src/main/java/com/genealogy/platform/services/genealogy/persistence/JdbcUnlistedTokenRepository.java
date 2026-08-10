package com.genealogy.platform.services.genealogy.persistence;

import com.genealogy.platform.services.genealogy.domain.UnlistedToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link UnlistedTokenRepository}. The
 * SHA-256 fingerprint is the natural primary key (per tenant);
 * the plaintext token never leaves the issuing service.
 */
public class JdbcUnlistedTokenRepository implements UnlistedTokenRepository {

    private final JdbcTemplate jdbc;

    public JdbcUnlistedTokenRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void insert(UnlistedToken token) {
        jdbc.update(
                "INSERT INTO tree_service.unlisted_token ("
                        + " token_id, tenant_id, tree_id, fingerprint, scope, branch_id,"
                        + " issued_at, expires_at, revoked_at, issued_by, revoked_by, revocation_reason"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                token.tokenId(),
                token.tenantId(),
                token.treeId(),
                token.fingerprint(),
                token.scope().wire(),
                token.branchId(),
                Timestamp.from(token.issuedAt()),
                Timestamp.from(token.expiresAt()),
                token.revokedAt() == null ? null : Timestamp.from(token.revokedAt()),
                token.issuedBy(),
                token.revokedBy(),
                token.revocationReason());
    }

    @Override
    public Optional<UnlistedToken> findByFingerprint(String tenantId, String fingerprint) {
        List<UnlistedToken> rows = jdbc.query(
                "SELECT * FROM tree_service.unlisted_token WHERE tenant_id = ? AND fingerprint = ?",
                ROW_MAPPER,
                tenantId,
                fingerprint);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<UnlistedToken> listActive(String tenantId, String treeId, Instant now) {
        return jdbc.query(
                "SELECT * FROM tree_service.unlisted_token"
                        + " WHERE tenant_id = ? AND tree_id = ? AND revoked_at IS NULL"
                        + " AND expires_at > ?",
                ROW_MAPPER,
                tenantId,
                treeId,
                Timestamp.from(now));
    }

    @Override
    public List<UnlistedToken> findExpired(Instant now, int limit) {
        return jdbc.query(
                "SELECT * FROM tree_service.unlisted_token"
                        + " WHERE revoked_at IS NULL AND expires_at <= ?"
                        + " ORDER BY expires_at ASC LIMIT ?",
                ROW_MAPPER,
                Timestamp.from(now),
                limit);
    }

    @Override
    public void revoke(String tenantId, String fingerprint, String revokedBy, String reason, Instant at) {
        jdbc.update(
                "UPDATE tree_service.unlisted_token SET revoked_at = ?, revoked_by = ?,"
                        + " revocation_reason = ?"
                        + " WHERE tenant_id = ? AND fingerprint = ? AND revoked_at IS NULL",
                Timestamp.from(at),
                revokedBy,
                reason,
                tenantId,
                fingerprint);
    }

    private static final RowMapper<UnlistedToken> ROW_MAPPER = (rs, rowNum) -> new UnlistedToken(
            rs.getString("token_id"),
            rs.getString("tree_id"),
            rs.getString("tenant_id"),
            rs.getString("fingerprint"),
            UnlistedToken.Scope.fromWire(rs.getString("scope")),
            rs.getString("branch_id"),
            rs.getTimestamp("issued_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(),
            rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
            rs.getString("issued_by"),
            rs.getString("revoked_by"),
            rs.getString("revocation_reason"));
}
