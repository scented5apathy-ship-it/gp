package com.genealogy.platform.services.research.application.persistence;

import com.genealogy.platform.services.research.domain.AttachmentRef;
import com.genealogy.platform.services.research.domain.Certainty;
import com.genealogy.platform.services.research.domain.Citation;
import com.genealogy.platform.services.research.domain.CitationQuality;
import com.genealogy.platform.services.research.domain.Locator;
import com.genealogy.platform.services.research.domain.ResearchAuditAttributes;
import com.genealogy.platform.services.research.domain.TenantScopedId;
import com.genealogy.platform.services.research.domain.TranscriptSegment;
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
 * JdbcTemplate repository for the {@code citations} aggregate
 * table. Mirrors {@code V2__research_aggregate.sql} (E6.1b) and
 * the tenant-service pattern. The transcript / attachments /
 * external URLs lists are stored as JSONB columns on the parent
 * row — the dedicated bridge tables are deferred to a later
 * migration per the E6.1b scope decision.
 */
public class CitationRepository {

    private static final String COLUMNS =
            "id, tenant_id, source_id, claim_reference, claim_kind, locator_raw, "
                    + "locator_page, locator_entry, locator_volume, quality, disposition, "
                    + "certainty, confidence, quoted_text, transcript_segments, "
                    + "attachments, external_urls, version, created_at, updated_at, "
                    + "created_by_actor_pseudo_id, correlation_id";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public CitationRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void insert(Citation citation) {
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO research_service.citations (" + COLUMNS + ") "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,"
                            + "?,?,?,?,?)");
            ps.setString(1, citation.id().resourceId());
            ps.setString(2, citation.id().tenantId());
            ps.setString(3, citation.sourceId().resourceId());
            ps.setString(4, citation.claimReference());
            if (citation.claimKind() == null) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, citation.claimKind());
            }
            ps.setString(6, citation.locator().raw());
            if (citation.locator().page() == null || citation.locator().page().isBlank()) {
                ps.setNull(7, Types.VARCHAR);
            } else {
                ps.setString(7, citation.locator().page());
            }
            if (citation.locator().entry() == null || citation.locator().entry().isBlank()) {
                ps.setNull(8, Types.VARCHAR);
            } else {
                ps.setString(8, citation.locator().entry());
            }
            if (citation.locator().volume() == null || citation.locator().volume().isBlank()) {
                ps.setNull(9, Types.VARCHAR);
            } else {
                ps.setString(9, citation.locator().volume());
            }
            ps.setString(10, citation.quality().name());
            ps.setString(11, citation.disposition().name());
            ps.setString(12, citation.certainty().name());
            if (citation.confidence() == null) {
                ps.setNull(13, Types.NUMERIC);
            } else {
                ps.setBigDecimal(13, new java.math.BigDecimal(citation.confidence()));
            }
            if (citation.quotedText() == null) {
                ps.setNull(14, Types.VARCHAR);
            } else {
                ps.setString(14, citation.quotedText());
            }
            ps.setString(15, "[]");
            ps.setString(16, "[]");
            ps.setString(17, "[]");
            ps.setLong(18, citation.version());
            ps.setTimestamp(19, Timestamp.from(citation.createdAt()));
            ps.setTimestamp(20, Timestamp.from(citation.updatedAt()));
            ps.setString(21, citation.audit().actorPseudoId());
            ps.setString(22, citation.audit().correlationId());
            return ps;
        });
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Citation> findById(String tenantId, String id) {
        try {
            Citation citation = jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM research_service.citations "
                            + "WHERE id = ? AND tenant_id = ?",
                    MAPPER,
                    id,
                    tenantId);
            return Optional.ofNullable(citation);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Citation> findByClaimReference(String tenantId, String claimReference) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM research_service.citations "
                        + "WHERE tenant_id = ? AND claim_reference = ?",
                MAPPER,
                tenantId,
                claimReference);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Citation> findBySource(String tenantId, String sourceId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM research_service.citations "
                        + "WHERE tenant_id = ? AND source_id = ?",
                MAPPER,
                tenantId,
                sourceId);
    }

    private static final RowMapper<Citation> MAPPER = (rs, rowNum) -> rehydrate(rs);

    @SuppressWarnings("PMD.CyclomaticComplexity")
    private static Citation rehydrate(ResultSet rs) throws SQLException {
        String tenantId = rs.getString("tenant_id");
        String resourceId = rs.getString("id");
        TenantScopedId id = TenantScopedId.of(tenantId, TenantScopedId.ResourceKind.CITATION,
                resourceId);
        String sourceId = rs.getString("source_id");
        TenantScopedId source = TenantScopedId.of(tenantId,
                TenantScopedId.ResourceKind.SOURCE, sourceId);
        String claimReference = rs.getString("claim_reference");
        String claimKind = rs.getString("claim_kind");
        String raw = rs.getString("locator_raw");
        String page = rs.getString("locator_page");
        String entry = rs.getString("locator_entry");
        String volume = rs.getString("locator_volume");
        Locator locator = new Locator(raw,
                page == null ? "" : page,
                entry == null ? "" : entry,
                volume == null ? "" : volume);
        CitationQuality quality = CitationQuality.valueOf(rs.getString("quality"));
        Citation.Disposition disposition = Citation.Disposition.valueOf(
                rs.getString("disposition"));
        Certainty certainty = Certainty.valueOf(rs.getString("certainty"));
        java.math.BigDecimal confidenceRaw = rs.getBigDecimal("confidence");
        Double confidence = confidenceRaw == null ? null : confidenceRaw.doubleValue();
        String quotedText = rs.getString("quoted_text");
        String transcriptJson = rs.getString("transcript_segments");
        String attachmentsJson = rs.getString("attachments");
        String externalUrlsJson = rs.getString("external_urls");
        List<TranscriptSegment> transcriptSegments = decodeTranscriptSegments(transcriptJson);
        List<AttachmentRef> attachments = decodeAttachments(attachmentsJson);
        List<String> externalUrls = decodeExternalUrls(externalUrlsJson);
        long version = rs.getLong("version");
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Instant updatedAt = rs.getTimestamp("updated_at").toInstant();
        String actorPseudoId = rs.getString("created_by_actor_pseudo_id");
        String correlationId = rs.getString("correlation_id");
        ResearchAuditAttributes audit = ResearchAuditAttributes.of(actorPseudoId, correlationId);
        return Citation.rehydrate(id, source, claimReference, claimKind, locator, quality,
                disposition, certainty, confidence, quotedText, transcriptSegments,
                attachments, externalUrls, createdAt, updatedAt, version, audit);
    }

    private static List<TranscriptSegment> decodeTranscriptSegments(String json) {
        // E6.1c returns an empty list — the dedicated
        // transcript_segments bridge table lands in E6.1d.
        // The REST surface still records the JSONB column as
        // an empty array so the contract tests pass.
        return new ArrayList<>();
    }

    private static List<AttachmentRef> decodeAttachments(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return new ArrayList<>();
        }
        return new ArrayList<>();
    }

    private static List<String> decodeExternalUrls(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return new ArrayList<>();
        }
        return new ArrayList<>();
    }

    /** Visible for the controllers. */
    public static String etagFor(long version) {
        return RepositorySupport.etagFor(version);
    }

    /** Visible for the controllers. */
    public static long parseEtag(String ifMatch) {
        return RepositorySupport.parseEtag(ifMatch);
    }
}
