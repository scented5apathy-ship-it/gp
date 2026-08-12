package com.genealogy.platform.services.research.application.persistence;

import com.genealogy.platform.services.research.domain.ResearchAuditAttributes;
import com.genealogy.platform.services.research.domain.Repository;
import com.genealogy.platform.services.research.domain.RepositoryKind;
import com.genealogy.platform.services.research.domain.TenantScopedId;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTemplate repository for the {@code repositories} aggregate
 * table. Mirrors the tenant-service pattern (E3.2c) and the
 * {@code V2__research_aggregate.sql} migration (E6.1b).
 *
 * <p>Every method runs with {@link Propagation#MANDATORY} so it
 * MUST participate in a caller-managed transaction (the
 * {@code *CommandService} methods). RLS is enforced by the
 * {@code ResearchRlsTxInterceptor} on the same JDBC connection.
 */
public class RepositoryRepository {

    private static final String COLUMNS =
            "id, tenant_id, name, kind, location_label, website_url, description, "
                    + "private_holding, metadata, version, created_at, updated_at, archived_at, "
                    + "created_by_actor_pseudo_id, correlation_id";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RepositoryRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Repository repository) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO research_service.repositories (" + COLUMNS + ") "
                            + "VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?)");
            ps.setString(1, repository.id().resourceId());
            ps.setString(2, repository.id().tenantId());
            ps.setString(3, repository.name());
            ps.setString(4, repository.kind().name());
            if (repository.locationLabel() == null) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, repository.locationLabel());
            }
            if (repository.websiteUrl() == null) {
                ps.setNull(6, Types.VARCHAR);
            } else {
                ps.setString(6, repository.websiteUrl());
            }
            if (repository.description() == null) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, repository.description());
            }
            ps.setBoolean(8, repository.privateHolding());
            ps.setString(9, serializeMetadata(repository.metadata()));
            ps.setLong(10, repository.version());
            ps.setTimestamp(11, Timestamp.from(repository.createdAt()));
            ps.setTimestamp(12, Timestamp.from(repository.updatedAt()));
            if (repository.archivedAt() == null) {
                ps.setNull(13, Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setTimestamp(13, Timestamp.from(repository.archivedAt()));
            }
            ps.setString(14, repository.audit().actorPseudoId());
            ps.setString(15, repository.audit().correlationId());
            return ps;
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void update(Repository repository) {
        int rows = jdbc.update(
                "UPDATE research_service.repositories SET "
                        + "name = ?, kind = ?, location_label = ?, website_url = ?, "
                        + "description = ?, private_holding = ?, metadata = ?::jsonb, "
                        + "version = ?, updated_at = ?, archived_at = ?, "
                        + "created_by_actor_pseudo_id = ?, correlation_id = ? "
                        + "WHERE id = ? AND tenant_id = ? AND version = ?",
                repository.name(),
                repository.kind().name(),
                repository.locationLabel(),
                repository.websiteUrl(),
                repository.description(),
                repository.privateHolding(),
                serializeMetadata(repository.metadata()),
                repository.version(),
                Timestamp.from(repository.updatedAt()),
                repository.archivedAt() == null ? null : Timestamp.from(repository.archivedAt()),
                repository.audit().actorPseudoId(),
                repository.audit().correlationId(),
                repository.id().resourceId(),
                repository.id().tenantId(),
                repository.version() - 1);
        if (rows != 1) {
            throw new OptimisticConcurrencyException(
                    "repository " + repository.id().resourceId()
                            + " was modified by another transaction");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Repository> findById(String tenantId, String id) {
        try {
            Repository repository = jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM research_service.repositories "
                            + "WHERE id = ? AND tenant_id = ?",
                    MAPPER,
                    id,
                    tenantId);
            return Optional.ofNullable(repository);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static final RowMapper<Repository> MAPPER = (rs, rowNum) -> rehydrate(rs);

    private static Repository rehydrate(ResultSet rs) throws SQLException {
        String tenantId = rs.getString("tenant_id");
        String resourceId = rs.getString("id");
        TenantScopedId id = TenantScopedId.of(tenantId, TenantScopedId.ResourceKind.REPOSITORY,
                resourceId);
        String name = rs.getString("name");
        RepositoryKind kind = RepositoryKind.valueOf(rs.getString("kind"));
        String locationLabel = rs.getString("location_label");
        String websiteUrl = rs.getString("website_url");
        String description = rs.getString("description");
        boolean privateHolding = rs.getBoolean("private_holding");
        Map<String, String> metadata = deserializeMetadata(rs.getString("metadata"));
        long version = rs.getLong("version");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        Instant archivedAt = rs.getTimestamp("archived_at") == null
                ? null : rs.getTimestamp("archived_at").toInstant();
        String actorPseudoId = rs.getString("created_by_actor_pseudo_id");
        String correlationId = rs.getString("correlation_id");
        ResearchAuditAttributes audit = ResearchAuditAttributes.of(actorPseudoId, correlationId);
        return Repository.rehydrate(id, name, kind, locationLabel, websiteUrl, description,
                privateHolding, createdAt, updatedAt, archivedAt, version, audit, metadata);
    }

    private static String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
        }
        sb.append('}');
        return sb.toString();
    }

    private static Map<String, String> deserializeMetadata(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return Map.of();
        }
        // E6.1c persistence keeps the metadata map as an opaque JSON
        // object — the contract only enforces ≤ 32 entries and the
        // value-object string cap (1024). A full JSON parser is
        // introduced in E6.1d when the outbox relay needs to embed
        // metadata in the event payload. For now we keep the map
        // unparsed so the REST surface stays JSON-friendly.
        Map<String, String> out = new LinkedHashMap<>();
        String body = json.trim();
        if (body.startsWith("{") && body.endsWith("}")) {
            body = body.substring(1, body.length() - 1);
        }
        if (body.isBlank()) {
            return out;
        }
        for (String pair : body.split(",")) {
            int colon = pair.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = unquote(pair.substring(0, colon).trim());
            String value = unquote(pair.substring(colon + 1).trim());
            out.put(key, value);
        }
        return out;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String escape(String value) {
        return value == null
                ? ""
                : value.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "\\r");
    }

    /** Visible for the controllers; the etag format is fixed. */
    public static String etagFor(long version) {
        return RepositorySupport.etagFor(version);
    }

    /** Visible for the controllers; the etag round-trip is the same. */
    public static long parseEtag(String ifMatch) {
        return RepositorySupport.parseEtag(ifMatch);
    }

    public static class OptimisticConcurrencyException extends RuntimeException {
        public OptimisticConcurrencyException(String message) {
            super(message);
        }
    }
}
