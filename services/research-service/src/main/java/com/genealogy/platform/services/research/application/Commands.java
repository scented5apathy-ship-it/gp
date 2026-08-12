package com.genealogy.platform.services.research.application;

import com.genealogy.platform.services.research.domain.Certainty;
import com.genealogy.platform.services.research.domain.CitationQuality;
import com.genealogy.platform.services.research.domain.ConflictKind;
import com.genealogy.platform.services.research.domain.HypothesisStatus;
import com.genealogy.platform.services.research.domain.Locator;
import com.genealogy.platform.services.research.domain.ResearchAuditAttributes;
import com.genealogy.platform.services.research.domain.ResearchTaskStatus;
import com.genealogy.platform.services.research.domain.RepositoryKind;
import com.genealogy.platform.services.research.domain.SourceKind;
import com.genealogy.platform.services.research.domain.TenantScopedId;
import java.time.Instant;
import java.util.List;

/**
 * Application-layer command DTOs. These are the framework-free
 * inputs to the command services (E6.1c). The controllers and
 * the future gRPC adapters (E6.1d) map their wire types onto
 * these via {@code DraftDomainMapper} without leaking framework
 * annotations into the domain layer.
 *
 * <p>Validation that is invariant across every wire (e.g. id
 * format, value-object length caps) lives in the value objects
 * and aggregate compact constructors. The records here are just
 * transport carriers.
 */
public final class Commands {

    private Commands() {
        // utility
    }

    /** Command to create a Repository. Tenant id is supplied by the trusted context. */
    public record CreateRepository(
            String name,
            RepositoryKind kind,
            String locationLabel,
            String websiteUrl,
            String description,
            boolean privateHolding) {
    }

    /** Command to create a Source. The repository must belong to the same tenant. */
    public record CreateSource(
            TenantScopedId repositoryId,
            String title,
            SourceKind sourceKind,
            String author,
            String publisher,
            Integer publicationYear,
            String publisherLocation,
            Locator locator,
            List<com.genealogy.platform.services.research.domain.AttachmentRef> attachments,
            String description) {
    }

    /** Command to create a Citation. The source must belong to the same tenant. */
    public record CreateCitation(
            TenantScopedId sourceId,
            String claimReference,
            String claimKind,
            Locator locator,
            CitationQuality quality,
            com.genealogy.platform.services.research.domain.Citation.Disposition disposition,
            Certainty certainty,
            Double confidence,
            String quotedText,
            List<com.genealogy.platform.services.research.domain.TranscriptSegment> transcriptSegments,
            List<com.genealogy.platform.services.research.domain.AttachmentRef> attachments,
            List<String> externalUrls) {
    }

    /** Command to create a ResearchTask. */
    public record CreateResearchTask(
            String title,
            String description,
            String subjectReference,
            String subjectKind) {
    }

    /** Command to transition a ResearchTask. Tenant id is supplied by the trusted context. */
    public record TransitionResearchTask(
            com.genealogy.platform.services.research.domain.ResearchTaskStatus next,
            String blockedReason,
            String resolvedProof) {
    }

    /** Command to create a Hypothesis. */
    public record CreateHypothesis(
            String statement,
            String subjectReference,
            String subjectKind,
            Certainty certainty,
            Double confidence) {
    }

    /** Command to create a Conflict. */
    public record CreateConflict(
            String summary,
            ConflictKind kind,
            String kindNote,
            List<com.genealogy.platform.services.research.domain.Conflict.Participant> participants) {
    }

    /** Command to transition a Conflict. Tenant id is supplied by the trusted context. */
    public record TransitionConflict(
            com.genealogy.platform.services.research.domain.Conflict.ConflictStatus next,
            String resolution,
            String resolutionProof) {
    }

    /**
     * Stamp applied to every command before the aggregate is
     * persisted. The {@code actorPseudoId} is the platform
     * pseudonym derived from the Keycloak subject + Kong boundary
     * (never raw subject id / email / DNA per ADR-E0.5-05 + NFR5);
     * the {@code correlationId} is the request-scoped trace id.
     */
    public record AuditStamp(ResearchAuditAttributes attributes, Instant at) {
        public AuditStamp {
            if (attributes == null) {
                throw new IllegalArgumentException("attributes must not be null");
            }
            if (at == null) {
                throw new IllegalArgumentException("at must not be null");
            }
        }
    }
}
