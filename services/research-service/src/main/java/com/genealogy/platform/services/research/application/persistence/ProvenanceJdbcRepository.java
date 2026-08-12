package com.genealogy.platform.services.research.application.persistence;

import com.genealogy.platform.services.research.domain.Certainty;
import com.genealogy.platform.services.research.domain.Citation;
import com.genealogy.platform.services.research.domain.CitationQuality;
import com.genealogy.platform.services.research.domain.RepositoryKind;
import com.genealogy.platform.services.research.domain.SourceKind;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JdbcTemplate-backed provenance executor. E6.1c ships a
 * relational variant that joins {@code citations} ↔
 * {@code sources} ↔ {@code repositories} under the RLS bound
 * issued by the {@code ResearchRlsTxInterceptor}. E6.1a
 * shipped the in-memory {@code ProvenanceQueryService} for the
 * domain unit tests; this class is the production-side wire to
 * the persisted schema.
 *
 * <p>Bulk loads happen inside the caller's transaction; the
 * executor applies the same ordering contract as the in-memory
 * variant (certainty DESC, citation id ASC) so the REST response
 * stays byte-stable when the same claim id is queried twice.
 */
public class ProvenanceJdbcRepository {

    private static final String CITATION_COLUMNS =
            "id, tenant_id, source_id, claim_reference, quality, disposition, "
                    + "certainty, confidence, locator_raw, quoted_text";

    private static final String SOURCE_COLUMNS =
            "id, tenant_id, repository_id, title, source_kind";

    private static final String REPOSITORY_COLUMNS =
            "id, tenant_id, name, kind";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProvenanceJdbcRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Trace every citation that points at the given
     * {@code claimReference}. The executor emits one
     * {@link ProvenanceHop} per candidate, ordered by certainty
     * (DESC) then citation id (ASC) so the UI does not have to
     * sort the chain client-side.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<ProvenanceHop> traverseByClaim(String tenantId, String claimReference) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(claimReference, "claimReference");
        List<CitationRow> citations = jdbc.query(
                "SELECT " + CITATION_COLUMNS + " FROM research_service.citations "
                        + "WHERE tenant_id = ? AND claim_reference = ?",
                (rs, rowNum) -> new CitationRow(
                        rs.getString("id"),
                        rs.getString("tenant_id"),
                        rs.getString("source_id"),
                        rs.getString("claim_reference"),
                        CitationQuality.valueOf(rs.getString("quality")),
                        Citation.Disposition.valueOf(rs.getString("disposition")),
                        Certainty.valueOf(rs.getString("certainty")),
                        rs.getBigDecimal("confidence") == null
                                ? null : rs.getBigDecimal("confidence").doubleValue(),
                        rs.getString("locator_raw"),
                        rs.getString("quoted_text")),
                tenantId,
                claimReference);
        if (citations.isEmpty()) {
            return Collections.emptyList();
        }
        List<SourceRow> sources = loadSources(tenantId, citations);
        List<RepositoryRow> repositories = loadRepositories(tenantId, citations, sources);
        List<ProvenanceHop> hops = new ArrayList<>();
        for (CitationRow citation : citations) {
            SourceRow source = findSource(sources, citation.sourceId);
            RepositoryRow repository = source == null
                    ? null : findRepository(repositories, source.repositoryId);
            hops.add(new ProvenanceHop(
                    citation.id,
                    citation.sourceId,
                    source == null ? null : source.title,
                    source == null ? null : SourceKind.valueOf(source.sourceKind),
                    source == null ? null : source.repositoryId,
                    repository == null ? null : repository.name,
                    repository == null ? null : RepositoryKind.valueOf(repository.kind),
                    citation.quality,
                    citation.disposition,
                    citation.certainty,
                    citation.confidence,
                    citation.locatorRaw,
                    citation.quotedText));
        }
        hops.sort((a, b) -> {
            int c = b.certainty.compareTo(a.certainty);
            if (c != 0) {
                return c;
            }
            return a.citationId.compareTo(b.citationId);
        });
        return hops;
    }

    private List<SourceRow> loadSources(String tenantId, List<CitationRow> citations) {
        List<String> sourceIds = new ArrayList<>();
        for (CitationRow citation : citations) {
            if (!sourceIds.contains(citation.sourceId)) {
                sourceIds.add(citation.sourceId);
            }
        }
        if (sourceIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(sourceIds.size(), "?"));
        return jdbc.query(
                "SELECT " + SOURCE_COLUMNS + " FROM research_service.sources "
                        + "WHERE tenant_id = ? AND id IN (" + placeholders + ")",
                (rs, rowNum) -> new SourceRow(
                        rs.getString("id"),
                        rs.getString("tenant_id"),
                        rs.getString("repository_id"),
                        rs.getString("title"),
                        rs.getString("source_kind")),
                concat(tenantId, sourceIds));
    }

    private List<RepositoryRow> loadRepositories(
            String tenantId, List<CitationRow> citations, List<SourceRow> sources) {
        List<String> repositoryIds = new ArrayList<>();
        for (SourceRow source : sources) {
            if (!repositoryIds.contains(source.repositoryId)) {
                repositoryIds.add(source.repositoryId);
            }
        }
        if (repositoryIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(",", Collections.nCopies(repositoryIds.size(), "?"));
        return jdbc.query(
                "SELECT " + REPOSITORY_COLUMNS + " FROM research_service.repositories "
                        + "WHERE tenant_id = ? AND id IN (" + placeholders + ")",
                (rs, rowNum) -> new RepositoryRow(
                        rs.getString("id"),
                        rs.getString("tenant_id"),
                        rs.getString("name"),
                        rs.getString("kind")),
                concat(tenantId, repositoryIds));
    }

    private static Object[] concat(String tenantId, List<String> ids) {
        Object[] args = new Object[ids.size() + 1];
        args[0] = tenantId;
        for (int i = 0; i < ids.size(); i++) {
            args[i + 1] = ids.get(i);
        }
        return args;
    }

    private static SourceRow findSource(List<SourceRow> sources, String id) {
        for (SourceRow source : sources) {
            if (source.id.equals(id)) {
                return source;
            }
        }
        return null;
    }

    private static RepositoryRow findRepository(List<RepositoryRow> repositories, String id) {
        for (RepositoryRow repository : repositories) {
            if (repository.id.equals(id)) {
                return repository;
            }
        }
        return null;
    }

    /**
     * One hop in the chain. Mirrors
     * {@code ProvenanceQueryService.ProvenanceHop} but the
     * JDBC counterpart uses native attribute types so the wire
     * mapping is done by the controller / view.
     */
    public record ProvenanceHop(
            String citationId,
            String sourceId,
            String sourceTitle,
            SourceKind sourceKind,
            String repositoryId,
            String repositoryName,
            RepositoryKind repositoryKind,
            CitationQuality quality,
            Citation.Disposition disposition,
            Certainty certainty,
            Double confidence,
            String locatorRaw,
            String quotedText) {
    }

    private record CitationRow(
            String id,
            String tenantId,
            String sourceId,
            String claimReference,
            CitationQuality quality,
            Citation.Disposition disposition,
            Certainty certainty,
            Double confidence,
            String locatorRaw,
            String quotedText) {
    }

    private record SourceRow(
            String id,
            String tenantId,
            String repositoryId,
            String title,
            String sourceKind) {
    }

    private record RepositoryRow(
            String id,
            String tenantId,
            String name,
            String kind) {
    }
}
