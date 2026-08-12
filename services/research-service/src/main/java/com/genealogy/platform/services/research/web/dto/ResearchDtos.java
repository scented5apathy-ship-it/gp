package com.genealogy.platform.services.research.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Wire DTOs for the research-service REST surface. Every
 * record is {@code @JsonInclude(NON_NULL)} so the response
 * never carries an empty {@code null} payload — the contract
 * tests confirm the shape byte-for-byte.
 */
public final class ResearchDtos {

    private ResearchDtos() {
        // utility
    }

    /* ---------------- Repository ---------------- */

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateRepositoryRequest(
            String name,
            String kind,
            String locationLabel,
            String websiteUrl,
            String description,
            @JsonProperty("privateHolding") Boolean privateHolding) {
    }

    public record RepositoryResponse(
            String id,
            @JsonProperty("tenantId") String tenantId,
            String name,
            String kind,
            String locationLabel,
            String websiteUrl,
            String description,
            @JsonProperty("privateHolding") boolean privateHolding,
            @JsonProperty("createdAt") String createdAt,
            @JsonProperty("updatedAt") String updatedAt,
            @JsonProperty("archivedAt") String archivedAt,
            long version,
            String etag,
            Map<String, String> metadata) {
    }

    /* ---------------- Source ---------------- */

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LocatorDto(
            String raw,
            String page,
            String entry,
            String volume) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttachmentDto(
            String kind,
            @JsonProperty("mediaObjectId") String mediaObjectId,
            @JsonProperty("canonicalUrl") String canonicalUrl,
            String caption,
            String locale) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateSourceRequest(
            @JsonProperty("repositoryId") String repositoryId,
            String title,
            @JsonProperty("sourceKind") String sourceKind,
            String author,
            String publisher,
            @JsonProperty("publicationYear") Integer publicationYear,
            @JsonProperty("publisherLocation") String publisherLocation,
            LocatorDto locator,
            List<AttachmentDto> attachments,
            String description) {
    }

    public record SourceResponse(
            String id,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("repositoryId") String repositoryId,
            String title,
            @JsonProperty("sourceKind") String sourceKind,
            String author,
            String publisher,
            @JsonProperty("publicationYear") Integer publicationYear,
            @JsonProperty("publisherLocation") String publisherLocation,
            LocatorDto locator,
            List<AttachmentDto> attachments,
            String description,
            @JsonProperty("createdAt") String createdAt,
            @JsonProperty("updatedAt") String updatedAt,
            @JsonProperty("archivedAt") String archivedAt,
            long version,
            String etag) {
    }

    /* ---------------- Citation ---------------- */

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TranscriptSegmentDto(
            @JsonProperty("lineNumber") int lineNumber,
            String text,
            @JsonProperty("originalScript") String originalScript,
            @JsonProperty("translationLocale") String translationLocale,
            String speaker) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateCitationRequest(
            @JsonProperty("sourceId") String sourceId,
            @JsonProperty("claimReference") String claimReference,
            @JsonProperty("claimKind") String claimKind,
            LocatorDto locator,
            String quality,
            String disposition,
            String certainty,
            Double confidence,
            @JsonProperty("quotedText") String quotedText,
            @JsonProperty("transcriptSegments") List<TranscriptSegmentDto> transcriptSegments,
            List<AttachmentDto> attachments,
            @JsonProperty("externalUrls") List<String> externalUrls) {
    }

    public record CitationResponse(
            String id,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("sourceId") String sourceId,
            @JsonProperty("claimReference") String claimReference,
            @JsonProperty("claimKind") String claimKind,
            LocatorDto locator,
            String quality,
            String disposition,
            String certainty,
            Double confidence,
            @JsonProperty("quotedText") String quotedText,
            @JsonProperty("transcriptSegments") List<TranscriptSegmentDto> transcriptSegments,
            List<AttachmentDto> attachments,
            @JsonProperty("externalUrls") List<String> externalUrls,
            @JsonProperty("createdAt") String createdAt,
            @JsonProperty("updatedAt") String updatedAt,
            long version,
            String etag) {
    }

    /* ---------------- ResearchTask ---------------- */

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateResearchTaskRequest(
            String title,
            String description,
            @JsonProperty("subjectReference") String subjectReference,
            @JsonProperty("subjectKind") String subjectKind) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransitionResearchTaskRequest(
            String toStatus,
            @JsonProperty("blockedReason") String blockedReason,
            @JsonProperty("resolvedProof") String resolvedProof) {
    }

    public record AssignmentResponse(
            @JsonProperty("assigneePseudoId") String assigneePseudoId,
            @JsonProperty("assigneeRole") String assigneeRole,
            @JsonProperty("assignedAt") String assignedAt,
            @JsonProperty("releasedAt") String releasedAt,
            String note) {
    }

    public record ResearchTaskResponse(
            String id,
            @JsonProperty("tenantId") String tenantId,
            String title,
            String description,
            @JsonProperty("subjectReference") String subjectReference,
            @JsonProperty("subjectKind") String subjectKind,
            String status,
            List<AssignmentResponse> assignments,
            @JsonProperty("linkedCitationIds") List<String> linkedCitationIds,
            @JsonProperty("blockedReason") String blockedReason,
            @JsonProperty("resolvedProof") String resolvedProof,
            @JsonProperty("createdAt") String createdAt,
            @JsonProperty("updatedAt") String updatedAt,
            @JsonProperty("resolvedAt") String resolvedAt,
            long version,
            String etag) {
    }

    /* ---------------- Hypothesis ---------------- */

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateHypothesisRequest(
            String statement,
            @JsonProperty("subjectReference") String subjectReference,
            @JsonProperty("subjectKind") String subjectKind,
            String certainty,
            Double confidence) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransitionHypothesisRequest(
            String toStatus) {
    }

    public record HypothesisResponse(
            String id,
            @JsonProperty("tenantId") String tenantId,
            String statement,
            @JsonProperty("subjectReference") String subjectReference,
            @JsonProperty("subjectKind") String subjectKind,
            String certainty,
            Double confidence,
            String status,
            @JsonProperty("corroboratingCitations") List<String> corroboratingCitations,
            @JsonProperty("refutingCitations") List<String> refutingCitations,
            @JsonProperty("supersededByHypothesisId") String supersededByHypothesisId,
            @JsonProperty("assignedTo") String assignedTo,
            @JsonProperty("createdAt") String createdAt,
            @JsonProperty("updatedAt") String updatedAt,
            @JsonProperty("resolvedAt") String resolvedAt,
            long version,
            String etag) {
    }

    /* ---------------- Conflict ---------------- */

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ParticipantDto(
            String reference,
            @JsonProperty("referenceKind") String referenceKind,
            String interpretation,
            @JsonProperty("supportingCitations") List<String> supportingCitations) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CreateConflictRequest(
            String summary,
            String kind,
            @JsonProperty("kindNote") String kindNote,
            List<ParticipantDto> participants) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransitionConflictRequest(
            String toStatus,
            String resolution,
            @JsonProperty("resolutionProof") String resolutionProof) {
    }

    public record ParticipantResponse(
            String reference,
            @JsonProperty("referenceKind") String referenceKind,
            String interpretation,
            @JsonProperty("supportingCitations") List<String> supportingCitations) {
    }

    public record ConflictResponse(
            String id,
            @JsonProperty("tenantId") String tenantId,
            String summary,
            String kind,
            @JsonProperty("kindNote") String kindNote,
            List<ParticipantResponse> participants,
            @JsonProperty("linkedCitationIds") List<String> linkedCitationIds,
            String status,
            String resolution,
            @JsonProperty("resolutionProof") String resolutionProof,
            @JsonProperty("createdAt") String createdAt,
            @JsonProperty("updatedAt") String updatedAt,
            @JsonProperty("resolvedAt") String resolvedAt,
            long version,
            String etag) {
    }

    /* ---------------- Provenance ---------------- */

    public record ProvenanceHopResponse(
            @JsonProperty("citationId") String citationId,
            @JsonProperty("sourceId") String sourceId,
            @JsonProperty("sourceTitle") String sourceTitle,
            @JsonProperty("sourceKind") String sourceKind,
            @JsonProperty("repositoryId") String repositoryId,
            @JsonProperty("repositoryName") String repositoryName,
            @JsonProperty("repositoryKind") String repositoryKind,
            String quality,
            String disposition,
            String certainty,
            Double confidence,
            @JsonProperty("locatorRaw") String locatorRaw,
            @JsonProperty("quotedText") String quotedText) {
    }

    public record ProvenanceChainResponse(
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("claimReference") String claimReference,
            List<ProvenanceHopResponse> hops) {
    }
}
