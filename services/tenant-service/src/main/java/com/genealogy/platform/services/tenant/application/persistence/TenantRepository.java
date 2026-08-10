package com.genealogy.platform.services.tenant.application.persistence;

import com.genealogy.platform.services.tenant.domain.ids.TenantId;
import com.genealogy.platform.services.tenant.domain.tenant.CalendarType;
import com.genealogy.platform.services.tenant.domain.tenant.Locale;
import com.genealogy.platform.services.tenant.domain.tenant.Slug;
import com.genealogy.platform.services.tenant.domain.tenant.Tenant;
import com.genealogy.platform.services.tenant.domain.tenant.TenantDisplayName;
import com.genealogy.platform.services.tenant.domain.tenant.TenantPlan;
import com.genealogy.platform.services.tenant.domain.tenant.TenantStatus;
import com.genealogy.platform.services.tenant.domain.tenant.Timezone;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTemplate repository for the {@code tenants} aggregate table.
 *
 * <p>Every method runs with {@link Propagation#MANDATORY} so it
 * MUST participate in a caller-managed transaction
 * (the {@code *CommandService} methods). RLS is enforced by the
 * {@code TenantRlsTxInterceptor} on the same JDBC connection.
 */
public class TenantRepository {

    private static final String COLUMNS =
            "id, slug, display_name, plan, status, default_locale, default_timezone, "
                    + "default_calendar, version, etag, created_at, updated_at, suspended_at, deleted_at";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TenantRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Tenant tenant) {
        jdbc.update(
                "INSERT INTO tenant_service.tenants ("
                        + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                tenant.id().getValue(),
                tenant.slug().value(),
                tenant.displayName().value(),
                tenant.plan().name(),
                tenant.status().name(),
                tenant.locale() == null ? null : tenant.locale().tag(),
                tenant.timezone() == null ? null : tenant.timezone().id(),
                tenant.calendar() == null ? null : tenant.calendar().name(),
                tenant.version(),
                etagFor(tenant.version()),
                Timestamp.from(tenant.createdAt()),
                Timestamp.from(tenant.updatedAt()),
                toTs(tenant.suspendedAt()),
                toTs(tenant.deletedAt()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Tenant tenant) {
        int rows = jdbc.update(
                "UPDATE tenant_service.tenants SET "
                        + "slug = ?, display_name = ?, plan = ?, status = ?, "
                        + "default_locale = ?, default_timezone = ?, default_calendar = ?, "
                        + "version = ?, etag = ?, updated_at = ?, "
                        + "suspended_at = ?, deleted_at = ? "
                        + "WHERE id = ? AND version = ?",
                tenant.slug().value(),
                tenant.displayName().value(),
                tenant.plan().name(),
                tenant.status().name(),
                tenant.locale() == null ? null : tenant.locale().tag(),
                tenant.timezone() == null ? null : tenant.timezone().id(),
                tenant.calendar() == null ? null : tenant.calendar().name(),
                tenant.version(),
                etagFor(tenant.version()),
                Timestamp.from(tenant.updatedAt()),
                toTs(tenant.suspendedAt()),
                toTs(tenant.deletedAt()),
                tenant.id().getValue(),
                tenant.version() - 1);
        if (rows != 1) {
            throw new OptimisticConcurrencyException(
                    "tenant " + tenant.id() + " was modified by another transaction");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Tenant> findById(TenantId id) {
        try {
            Tenant tenant = jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM tenant_service.tenants WHERE id = ?",
                    MAPPER,
                    id.getValue());
            return Optional.ofNullable(tenant);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Page of tenants the runtime can see. RLS narrows the result set
     * to {@code app.tenant_id}; the {@code pageSize + 1} trick returns
     * one extra row so the caller can derive {@code nextCursor}.
     *
     * <p>Cursor encoding: base64-encoded {@code "<createdAtMillis>|<id>"}
     * so the next page resumes from the row after the cursor in a
     * stable order. The cursor is opaque to the caller.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Tenant> findPage(int pageSize, String cursor) {
        int limit = Math.max(1, pageSize) + 1;
        if (cursor == null || cursor.isBlank()) {
            return jdbc.query(
                    "SELECT " + COLUMNS + " FROM tenant_service.tenants "
                            + "ORDER BY created_at ASC, id ASC LIMIT ?",
                    MAPPER,
                    limit);
        }
        Cursor c = Cursor.decode(cursor);
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM tenant_service.tenants "
                        + "WHERE (created_at, id) > (?, ?) "
                        + "ORDER BY created_at ASC, id ASC LIMIT ?",
                MAPPER,
                Timestamp.from(c.createdAt), c.id, limit);
    }

    private static final RowMapper<Tenant> MAPPER = (rs, rowNum) -> rehydrate(rs);

    private static Tenant rehydrate(ResultSet rs) throws SQLException {
        TenantId id = new TenantId(rs.getString("id"));
        Slug slug = new Slug(rs.getString("slug"));
        TenantDisplayName name = new TenantDisplayName(rs.getString("display_name"));
        TenantPlan plan = TenantPlan.valueOf(rs.getString("plan"));
        TenantStatus status = TenantStatus.valueOf(rs.getString("status"));
        Locale locale = rs.getString("default_locale") == null
                ? null : new Locale(rs.getString("default_locale"));
        Timezone tz = rs.getString("default_timezone") == null
                ? null : new Timezone(rs.getString("default_timezone"));
        CalendarType calendar = rs.getString("default_calendar") == null
                ? null : CalendarType.valueOf(rs.getString("default_calendar"));
        long version = rs.getLong("version");
        java.time.Instant createdAt = rs.getTimestamp("created_at").toInstant();
        java.time.Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        java.time.Instant suspendedAt = toInstant(rs.getTimestamp("suspended_at"));
        java.time.Instant deletedAt = toInstant(rs.getTimestamp("deleted_at"));
        return Tenant.rehydrate(id, slug, name, plan, status,
                locale, tz, calendar, version,
                createdAt, updatedAt, suspendedAt, deletedAt,
                java.time.Clock.systemUTC());
    }

    public static String etagFor(long version) {
        return "\"v" + version + "\"";
    }

    private static java.time.Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Timestamp toTs(java.time.Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    public static class OptimisticConcurrencyException extends RuntimeException {
        public OptimisticConcurrencyException(String message) {
            super(message);
        }
    }

    /**
     * Opaque cursor decoded back into the tuple used to resume a
     * tenant list. Internal helper; production code never sees the
     * tuple.
     */
    public record Cursor(java.time.Instant createdAt, String id) {

        public static String encode(java.time.Instant createdAt, String id) {
            String raw = createdAt.toEpochMilli() + "|" + id;
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
