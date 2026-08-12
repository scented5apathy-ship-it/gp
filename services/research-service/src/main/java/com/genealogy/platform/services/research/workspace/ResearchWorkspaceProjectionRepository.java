package com.genealogy.platform.services.research.workspace;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction over the workspace projection table.
 * The production implementation is JdbcTemplate-backed; the
 * unit tests use an in-memory fake. The interface is
 * intentionally narrow so the redaction-overlay service stays
 * framework-free (per AGENTS.md).
 */
public interface ResearchWorkspaceProjectionRepository {

    Optional<ResearchWorkspaceProjection> find(
            String tenantId, String treeId, String claimReference);

    void upsert(ResearchWorkspaceProjection row);

    List<ResearchWorkspaceProjection> findBySubject(
            String tenantId, String subjectReference);

    List<ResearchWorkspaceProjection> findByTree(
            String tenantId, String treeId);

    /** Touches every projection row that references the supplied subject. */
    int applyRedactionOverlay(
            String tenantId,
            String subjectReference,
            ResearchWorkspaceProjection.RedactionReason reason,
            Instant at);
}
