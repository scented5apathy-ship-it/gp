package com.genealogy.platform.services.research.application;

import com.genealogy.platform.services.research.domain.AttachmentKind;
import com.genealogy.platform.services.research.domain.Certainty;
import com.genealogy.platform.services.research.domain.CitationQuality;
import com.genealogy.platform.services.research.domain.ConflictKind;
import com.genealogy.platform.services.research.domain.HypothesisStatus;
import com.genealogy.platform.services.research.domain.ResearchTaskStatus;
import com.genealogy.platform.services.research.domain.RepositoryKind;
import com.genealogy.platform.services.research.domain.SourceKind;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Application-layer result DTOs. Returned by the command and
 * query services so the controllers (E6.1c) and the future gRPC
 * adapters (E6.1d) do not rehydrate the aggregate just to render
 * a view.
 *
 * <p>All timestamps are {@link Instant}; the wire layer (REST /
 * gRPC) is responsible for RFC 3339 serialisation.
 */
public final class Results {

    private Results() {
        // utility
    }

    public record AttachmentView(
            AttachmentKind kind,
            String mediaObjectId,
            String canonicalUrl,
            String caption,
            String locale) {
    }

    public record TranscriptSegmentView(
            int lineNumber,
            String text,
            String originalScript,
            String translationLocale,
            String speaker) {
    }

    public record LocatorView(
            String raw,
            String page,
            String entry,
            String volume) {
    }

    public record RepositoryView(
            String id,
            String tenantId,
            String name,
            RepositoryKind kind,
            String locationLabel,
            String websiteUrl,
            String description,
            boolean privateHolding,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt,
            long version,
            String etag,
            Map<String, String> metadata) {
    }

    public record SourceView(
            String id,
            String tenantId,
            String repositoryId,
            String title,
            SourceKind sourceKind,
            String author,
            String publisher,
            Integer publicationYear,
            String publisherLocation,
            LocatorView locator,
            List<AttachmentView> attachments,
            String description,
            Instant createdAt,
            Instant updatedAt,
            Instant archivedAt,
            long version,
            String etag) {
    }

    public record CitationView(
            String id,
            String tenantId,
            String sourceId,
            String claimReference,
            String claimKind,
            LocatorView locator,
            CitationQuality quality,
            Disposition disposition,
            Certainty certainty,
            Double confidence,
            String quotedText,
            List<TranscriptSegmentView> transcriptSegments,
            List<AttachmentView> attachments,
            List<String> externalUrls,
            Instant createdAt,
            Instant updatedAt,
            long version,
            String etag) {
    }

    public record ResearchTaskView(
            String id,
            String tenantId,
            String title,
            String description,
            String subjectReference,
            String subjectKind,
            ResearchTaskStatus status,
            List<AssignmentView> assignments,
            List<String> linkedCitationIds,
            String blockedReason,
            String resolvedProof,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt,
            long version,
            String etag) {
    }

    public record AssignmentView(
            String assigneePseudoId,
            String assigneeRole,
            Instant assignedAt,
            Instant releasedAt,
            String note) {
    }

    public record HypothesisView(
            String id,
            String tenantId,
            String statement,
            String subjectReference,
            String subjectKind,
            Certainty certainty,
            Double confidence,
            HypothesisStatus status,
            List<String> corroboratingCitations,
            List<String> refutingCitations,
            String supersededByHypothesisId,
            String assignedTo,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt,
            long version,
            String etag) {
    }

    public record ConflictView(
            String id,
            String tenantId,
            String summary,
            ConflictKind kind,
            String kindNote,
            List<ParticipantView> participants,
            List<String> linkedCitationIds,
            com.genealogy.platform.services.research.domain.Conflict.ConflictStatus status,
            String resolution,
            String resolutionProof,
            Instant createdAt,
            Instant updatedAt,
            Instant resolvedAt,
            long version,
            String etag) {
    }

    public record ParticipantView(
            String reference,
            String referenceKind,
            String interpretation,
            List<String> supportingCitations) {
    }

    public record ProvenanceHopView(
            String citationId,
            String sourceId,
            String sourceTitle,
            SourceKind sourceKind,
            String repositoryId,
            String repositoryName,
            RepositoryKind repositoryKind,
            CitationQuality quality,
            Disposition disposition,
            Certainty certainty,
            Double confidence,
            String locatorRaw,
            String quotedText) {
    }

    public record ProvenanceChainView(
            String tenantId,
            String claimReference,
            List<ProvenanceHopView> hops) {
    }

    /**
     * Mirrors {@code Citation.Disposition} on the wire. The
     * domain enum is {@code com.genealogy.platform.services.research.domain.Citation.Disposition}
     * but the wire is stringified so no internal enum constant
     * can leak across the JSON boundary.
     */
    public enum Disposition {
        SUPPORTS,
        REFUTES,
        MENTIONS,
        UNCERTAIN
    }
}
