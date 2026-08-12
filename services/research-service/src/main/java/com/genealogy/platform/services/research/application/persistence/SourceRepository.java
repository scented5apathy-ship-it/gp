package com.genealogy.platform.services.research.application.persistence;

import com.genealogy.platform.services.research.domain.AttachmentRef;
import com.genealogy.platform.services.research.domain.Locator;
import com.genealogy.platform.services.research.domain.ResearchAuditAttributes;
import com.genealogy.platform.services.research.domain.Source;
import com.genealogy.platform.services.research.domain.SourceKind;
import com.genealogy.platform.services.research.domain.TenantScopedId;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTemplate repository for the {@code sources} aggregate
 * table. Mirrors {@code V2__research_aggregate.sql} (E6.1b) and
 * the tenant-service pattern. The locator is split into the
 * four columns (raw, page, entry, volume) so the unique
 * constraint on the page locator still works for the high-value
 * lookup path.
 */
public class SourceRepository {

    private static final String COLUMNS =
            "id, tenant_id, repository_id, title, source_kind, author, publisher, "
                    + "publication_year, publisher_location, locator_raw, locator_page, "
                    + "locator_entry, locator_volume, description, version, "
                    + "created_at, updated_at, archived_at, "
                    + "created_by_actor_pseudo_id, correlation_id";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SourceRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Source source) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO research_service.sources (" + COLUMNS + ") "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
            bindSource(ps, source);
            return ps;
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Source> findById(String tenantId, String id) {
        try {
            Source source = jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM research_service.sources "
                            + "WHERE id = ? AND tenant_id = ?",
                    MAPPER,
                    id,
                    tenantId);
            return Optional.ofNullable(source);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<AttachmentRef> findAttachments(String tenantId, String sourceId) {
        // The V2 schema denormalises attachments as JSONB on the
        // citation row; the source attachments list is built by
        // the dedicated E6.1d outbox-relay path. E6.1c returns an
        // empty list so the REST view stays JSON-friendly until the
        // bridge table arrives in a later migration.
        return new ArrayList<>();
    }

    private static void bindSource(PreparedStatement ps, Source source) throws SQLException {
        ps.setString(1, source.id().resourceId());
        ps.setString(2, source.id().tenantId());
        ps.setString(3, source.repositoryId().resourceId());
        ps.setString(4, source.title());
        ps.setString(5, source.sourceKind().name());
        if (source.author() == null) {
            ps.setNull(6, Types.VARCHAR);
        } else {
            ps.setString(6, source.author());
        }
        if (source.publisher() == null) {
            ps.setNull(7, Types.VARCHAR);
        } else {
            ps.setString(7, source.publisher());
        }
        if (source.publicationYear() == null) {
            ps.setNull(8, Types.INTEGER);
        } else {
            ps.setInt(8, source.publicationYear());
        }
        if (source.publisherLocation() == null) {
            ps.setNull(9, Types.VARCHAR);
        } else {
            ps.setString(9, source.publisherLocation());
        }
        ps.setString(10, source.locator().raw());
        if (source.locator().page() == null || source.locator().page().isBlank()) {
            ps.setNull(11, Types.VARCHAR);
        } else {
            ps.setString(11, source.locator().page());
        }
        if (source.locator().entry() == null || source.locator().entry().isBlank()) {
            ps.setNull(12, Types.VARCHAR);
        } else {
            ps.setString(12, source.locator().entry());
        }
        if (source.locator().volume() == null || source.locator().volume().isBlank()) {
            ps.setNull(13, Types.VARCHAR);
        } else {
            ps.setString(13, source.locator().volume());
        }
        if (source.description() == null) {
            ps.setNull(14, Types.VARCHAR);
        } else {
            ps.setString(14, source.description());
        }
        ps.setLong(15, source.version());
        ps.setTimestamp(16, Timestamp.from(source.createdAt()));
        ps.setTimestamp(17, Timestamp.from(source.updatedAt()));
        if (source.archivedAt() == null) {
            ps.setNull(18, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            ps.setTimestamp(18, Timestamp.from(source.archivedAt()));
        }
        ps.setString(19, source.audit().actorPseudoId());
        ps.setString(20, source.audit().correlationId());
    }

    private static final RowMapper<Source> MAPPER = (rs, rowNum) -> rehydrate(rs);

    private static Source rehydrate(ResultSet rs) throws SQLException {
        String tenantId = rs.getString("tenant_id");
        String resourceId = rs.getString("id");
        TenantScopedId id = TenantScopedId.of(tenantId, TenantScopedId.ResourceKind.SOURCE,
                resourceId);
        String repositoryId = rs.getString("repository_id");
        TenantScopedId repository = TenantScopedId.of(tenantId,
                TenantScopedId.ResourceKind.REPOSITORY, repositoryId);
        String title = rs.getString("title");
        SourceKind sourceKind = SourceKind.valueOf(rs.getString("source_kind"));
        String author = rs.getString("author");
        String publisher = rs.getString("publisher");
        int publicationYear = rs.getInt("publication_year");
        Integer publicationYearBoxed = rs.wasNull() ? null : publicationYear;
        String publisherLocation = rs.getString("publisher_location");
        String raw = rs.getString("locator_raw");
        String page = rs.getString("locator_page");
        String entry = rs.getString("locator_entry");
        String volume = rs.getString("locator_volume");
        Locator locator = new Locator(raw,
                page == null ? "" : page,
                entry == null ? "" : entry,
                volume == null ? "" : volume);
        String description = rs.getString("description");
        long version = rs.getLong("version");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        Instant archivedAt = rs.getTimestamp("archived_at") == null
                ? null : rs.getTimestamp("archived_at").toInstant();
        String actorPseudoId = rs.getString("created_by_actor_pseudo_id");
        String correlationId = rs.getString("correlation_id");
        ResearchAuditAttributes audit = ResearchAuditAttributes.of(actorPseudoId, correlationId);
        return Source.rehydrate(id, repository, title, sourceKind, author, publisher,
                publicationYearBoxed, publisherLocation, locator, List.of(),
                description, createdAt, updatedAt, archivedAt, version, audit);
    }

    /** Visible for the controllers. */
    public static String etagFor(long version) {
        return RepositorySupport.etagFor(version);
    }

    /** Visible for the controllers. */
    public static long parseEtag(String ifMatch) {
        return RepositorySupport.parseEtag(ifMatch);
    }

    /**
     * Convenience for the REST surface; the attachment ref
     * shape is documented in the contract. Empty list when no
     * attachments are present (matches the V2 default).
     */
    public static List<AttachmentRef> emptyAttachments() {
        return new ArrayList<>();
    }
}
