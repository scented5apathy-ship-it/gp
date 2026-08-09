package com.genealogy.platform.services.tenant.application.persistence;

import com.genealogy.platform.services.tenant.domain.ids.InvitationId;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.ids.UserId;
import com.genealogy.platform.services.tenant.domain.invitation.Email;
import com.genealogy.platform.services.tenant.domain.invitation.Invitation;
import com.genealogy.platform.services.tenant.domain.invitation.TokenHash;
import com.genealogy.platform.services.tenant.domain.membership.MembershipRole;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTemplate repository for the {@code invitations} aggregate table.
 *
 * <p>The unique index on {@code (tenant_id, idempotency_key)} is
 * the de-dup boundary; the writer translates the
 * {@link DuplicateKeyException} into
 * {@link DuplicateIdempotencyKeyException} so the REST layer can
 * answer 409 instead of leaking the database vendor code.
 */
public class InvitationRepository {

    private final JdbcTemplate jdbc;

    public InvitationRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Invitation invitation) {
        try {
            jdbc.update(
                    "INSERT INTO tenant_service.invitations "
                            + "(id, tenant_id, email, role, token_hash, idempotency_key, "
                            + "invited_by_user_id, expires_at, accepted_at, revoked_at, "
                            + "created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    invitation.id().getValue(),
                    invitation.tenantId().getValue(),
                    invitation.email().value(),
                    invitation.role().name(),
                    invitation.tokenHash().value(),
                    invitation.idempotencyKey(),
                    invitation.invitedByUserId().getValue(),
                    Timestamp.from(invitation.expiresAt()),
                    toTs(invitation.acceptedAt()),
                    toTs(invitation.revokedAt()),
                    Timestamp.from(invitation.createdAt()),
                    Timestamp.from(invitation.updatedAt()));
        } catch (DuplicateKeyException e) {
            throw new DuplicateIdempotencyKeyException(
                    "invitation with idempotency_key="
                            + invitation.idempotencyKey()
                            + " already exists for tenant " + invitation.tenantId());
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markAccepted(Invitation invitation) {
        jdbc.update(
                "UPDATE tenant_service.invitations SET accepted_at = ?, updated_at = ? "
                        + "WHERE id = ? AND accepted_at IS NULL AND revoked_at IS NULL",
                Timestamp.from(invitation.acceptedAt()),
                Timestamp.from(invitation.updatedAt()),
                invitation.id().getValue());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revoke(Invitation invitation) {
        jdbc.update(
                "UPDATE tenant_service.invitations SET revoked_at = ?, updated_at = ? "
                        + "WHERE id = ? AND accepted_at IS NULL AND revoked_at IS NULL",
                Timestamp.from(invitation.revokedAt()),
                Timestamp.from(invitation.updatedAt()),
                invitation.id().getValue());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Invitation> findById(InvitationId id) {
        try {
            Invitation i = jdbc.queryForObject(
                    "SELECT id, tenant_id, email, role, token_hash, idempotency_key, "
                            + "invited_by_user_id, expires_at, accepted_at, revoked_at, "
                            + "created_at, updated_at "
                            + "FROM tenant_service.invitations WHERE id = ?",
                    MAPPER,
                    id.getValue());
            return Optional.ofNullable(i);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Invitation> findByIdempotencyKey(TenantId tenantId, String idempotencyKey) {
        try {
            Invitation i = jdbc.queryForObject(
                    "SELECT id, tenant_id, email, role, token_hash, idempotency_key, "
                            + "invited_by_user_id, expires_at, accepted_at, revoked_at, "
                            + "created_at, updated_at "
                            + "FROM tenant_service.invitations "
                            + "WHERE tenant_id = ? AND idempotency_key = ?",
                    MAPPER,
                    tenantId.getValue(), idempotencyKey);
            return Optional.ofNullable(i);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static final RowMapper<Invitation> MAPPER = (rs, rowNum) -> rehydrate(rs);

    private static Invitation rehydrate(ResultSet rs) throws SQLException {
        InvitationId id = new InvitationId(rs.getString("id"));
        TenantId tenantId = new TenantId(rs.getString("tenant_id"));
        Email email = new Email(rs.getString("email"));
        MembershipRole role = MembershipRole.valueOf(rs.getString("role"));
        TokenHash hash = new TokenHash(rs.getString("token_hash"));
        String idempotencyKey = rs.getString("idempotency_key");
        UserId invitedByUserId = new UserId(rs.getString("invited_by_user_id"));
        java.time.Instant expiresAt = rs.getTimestamp("expires_at").toInstant();
        java.time.Instant acceptedAt = toInstant(rs.getTimestamp("accepted_at"));
        java.time.Instant revokedAt = toInstant(rs.getTimestamp("revoked_at"));
        java.time.Instant createdAt = rs.getTimestamp("created_at").toInstant();
        java.time.Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        return Invitation.rehydrate(id, tenantId, email, role, hash,
                idempotencyKey, invitedByUserId, expiresAt, acceptedAt, revokedAt,
                createdAt, updatedAt);
    }

    private static Timestamp toTs(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static java.time.Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public static class DuplicateIdempotencyKeyException extends RuntimeException {
        public DuplicateIdempotencyKeyException(String message) {
            super(message);
        }
    }
}
