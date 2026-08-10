package com.genealogy.platform.services.tenant.application.persistence;

import com.genealogy.platform.services.tenant.domain.ids.MembershipId;
import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.ids.UserId;
import com.genealogy.platform.services.tenant.domain.membership.Membership;
import com.genealogy.platform.services.tenant.domain.membership.MembershipRole;
import com.genealogy.platform.services.tenant.domain.membership.MembershipStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTemplate repository for the {@code memberships} aggregate table.
 * Mirrors the V2 schema constraints; the
 * {@link Membership#rehydrate} factory rejects any inconsistent row.
 */
public class MembershipRepository {

    private final JdbcTemplate jdbc;

    public MembershipRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Membership membership) {
        jdbc.update(
                "INSERT INTO tenant_service.memberships "
                        + "(id, tenant_id, user_id, role, status, invited_at, joined_at, "
                        + "suspended_at, revoked_at, version, etag, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                membership.id().getValue(),
                membership.tenantId().getValue(),
                membership.userId().getValue(),
                membership.role().name(),
                membership.status().name(),
                toTs(membership.invitedAt()),
                toTs(membership.joinedAt()),
                toTs(membership.suspendedAt()),
                toTs(membership.revokedAt()),
                membership.version(),
                TenantRepository.etagFor(membership.version()),
                Timestamp.from(membership.createdAt()),
                Timestamp.from(membership.updatedAt()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Membership membership) {
        int rows = jdbc.update(
                "UPDATE tenant_service.memberships SET "
                        + "role = ?, status = ?, invited_at = ?, joined_at = ?, "
                        + "suspended_at = ?, revoked_at = ?, version = ?, etag = ?, "
                        + "updated_at = ? WHERE id = ? AND version = ?",
                membership.role().name(),
                membership.status().name(),
                toTs(membership.invitedAt()),
                toTs(membership.joinedAt()),
                toTs(membership.suspendedAt()),
                toTs(membership.revokedAt()),
                membership.version(),
                TenantRepository.etagFor(membership.version()),
                Timestamp.from(membership.updatedAt()),
                membership.id().getValue(),
                membership.version() - 1);
        if (rows != 1) {
            throw new TenantRepository.OptimisticConcurrencyException(
                    "membership " + membership.id() + " was modified by another transaction");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Membership> findById(MembershipId id) {
        try {
            Membership m = jdbc.queryForObject(
                    "SELECT id, tenant_id, user_id, role, status, invited_at, joined_at, "
                            + "suspended_at, revoked_at, version, created_at, updated_at "
                            + "FROM tenant_service.memberships WHERE id = ?",
                    MAPPER,
                    id.getValue());
            return Optional.ofNullable(m);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Membership> findByTenantAndUser(TenantId tenantId, UserId userId) {
        try {
            Membership m = jdbc.queryForObject(
                    "SELECT id, tenant_id, user_id, role, status, invited_at, joined_at, "
                            + "suspended_at, revoked_at, version, created_at, updated_at "
                            + "FROM tenant_service.memberships WHERE tenant_id = ? AND user_id = ?",
                    MAPPER,
                    tenantId.getValue(), userId.getValue());
            return Optional.ofNullable(m);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Page of memberships scoped to the runtime tenant. RLS narrows
     * the result set; the controller layer does NOT pass an explicit
     * {@code tenant_id} filter — the {@code SET LOCAL app.tenant_id}
     * binding is the only authorization boundary. Cursor pagination
     * follows the same convention as the tenant list.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Membership> findPage(int pageSize, String cursor) {
        int limit = Math.max(1, pageSize) + 1;
        if (cursor == null || cursor.isBlank()) {
            return jdbc.query(
                    "SELECT id, tenant_id, user_id, role, status, invited_at, joined_at, "
                            + "suspended_at, revoked_at, version, created_at, updated_at "
                            + "FROM tenant_service.memberships "
                            + "ORDER BY invited_at ASC, id ASC LIMIT ?",
                    MAPPER,
                    limit);
        }
        Cursor c = Cursor.decode(cursor);
        return jdbc.query(
                "SELECT id, tenant_id, user_id, role, status, invited_at, joined_at, "
                        + "suspended_at, revoked_at, version, created_at, updated_at "
                        + "FROM tenant_service.memberships "
                        + "WHERE (invited_at, id) > (?, ?) "
                        + "ORDER BY invited_at ASC, id ASC LIMIT ?",
                MAPPER,
                Timestamp.from(c.invitedAt), c.id, limit);
    }

    private static final RowMapper<Membership> MAPPER = (rs, rowNum) -> rehydrate(rs);

    private static Membership rehydrate(ResultSet rs) throws SQLException {
        MembershipId id = new MembershipId(rs.getString("id"));
        TenantId tenantId = new TenantId(rs.getString("tenant_id"));
        UserId userId = new UserId(rs.getString("user_id"));
        MembershipRole role = MembershipRole.valueOf(rs.getString("role"));
        MembershipStatus status = MembershipStatus.valueOf(rs.getString("status"));
        java.time.Instant invitedAt = toInstant(rs.getTimestamp("invited_at"));
        java.time.Instant joinedAt = toInstant(rs.getTimestamp("joined_at"));
        java.time.Instant suspendedAt = toInstant(rs.getTimestamp("suspended_at"));
        java.time.Instant revokedAt = toInstant(rs.getTimestamp("revoked_at"));
        long version = rs.getLong("version");
        java.time.Instant createdAt = rs.getTimestamp("created_at").toInstant();
        java.time.Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        return Membership.rehydrate(id, tenantId, userId, role, status,
                invitedAt, joinedAt, suspendedAt, revokedAt,
                version, createdAt, updatedAt);
    }

    private static Timestamp toTs(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static java.time.Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    /** Opaque cursor for the membership list. See {@link TenantRepository.Cursor}. */
    public record Cursor(java.time.Instant invitedAt, String id) {

        public static String encode(java.time.Instant invitedAt, String id) {
            String raw = invitedAt.toEpochMilli() + "|" + id;
            return java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        public static Cursor decode(String encoded) {
            try {
                String raw = new String(java.util.Base64.getUrlDecoder()
                        .decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
                int sep = raw.indexOf('|');
                if (sep <= 0) {
                    throw new IllegalArgumentException("malformed cursor");
                }
                long millis = Long.parseLong(raw.substring(0, sep));
                String id = raw.substring(sep + 1);
                return new Cursor(java.time.Instant.ofEpochMilli(millis), id);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("invalid cursor: " + e.getMessage(), e);
            }
        }
    }
}
