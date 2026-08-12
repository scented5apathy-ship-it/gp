package com.genealogy.platform.services.research.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Citation aggregate root. A citation asserts "this claim is
 * supported (or refuted) by this source at this locator with
 * this quality". Mirrors `requirements.md` R8.2 (claim must
 * carry multiple citations with confidence grade) + R8.4
 * (provenance from fact to source) + `design.md` §5.5
 * (CITATION_LINK supports) + `contracts/research/research-
 * policy.yaml::spec.citationSchema` (E6.1).
 *
 * <p>Invariants enforced by the compact constructor:
 *
 * <ul>
 *   <li>Tenant scope is mandatory (NFR1).
 *   <li>{@code claimReference} is an opaque id (typically a
 *       genealogy-service Claim id from E4.5) — the research
 *       service never dereferences the claim, only carries
 *       the pointer.
 *   <li>{@code quality} is closed-set (see
 *       {@link CitationQuality}).
 *   <li>When {@code quality == TRANSCRIPT}, at least one
 *       transcript segment is mandatory.
 *   <li>At most 64 transcript segments + 64 attachments +
 *       64 URL references.
 *   <li>{@code disposition} is closed-set (SUPPORTS, REFUTES,
 *       MENTIONS, UNCERTAIN).
 * </ul>
 */
public record Citation(
        TenantScopedId id,
        TenantScopedId sourceId,
        String claimReference,
        String claimKind,
        Locator locator,
        CitationQuality quality,
        Disposition disposition,
        Certainty certainty,
        Double confidence,
        String quotedText,
        List<TranscriptSegment> transcriptSegments,
        List<AttachmentRef> attachments,
        List<String> externalUrls,
        Instant createdAt,
        Instant updatedAt,
        long version,
        ResearchAuditAttributes audit) {

    public Citation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(claimReference, "claimReference");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(certainty, "certainty");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(audit, "audit");
        if (id.resourceKind() != TenantScopedId.ResourceKind.CITATION) {
            throw new IllegalArgumentException(
                    "TenantScopedId resourceKind must be CITATION, got "
                            + id.resourceKind());
        }
        if (sourceId.resourceKind() != TenantScopedId.ResourceKind.SOURCE) {
            throw new IllegalArgumentException(
                    "sourceId resourceKind must be SOURCE, got "
                            + sourceId.resourceKind());
        }
        if (!id.tenantId().equals(sourceId.tenantId())) {
            throw new IllegalArgumentException(
                    "citation tenant must match source tenant");
        }
        if (claimReference.isBlank()) {
            throw new IllegalArgumentException("claimReference must not be blank");
        }
        if (claimReference.length() > 128) {
            throw new IllegalArgumentException(
                    "claimReference exceeds 128 characters");
        }
        if (claimKind != null && claimKind.length() > 64) {
            throw new IllegalArgumentException(
                    "claimKind exceeds 64 characters");
        }
        if (claimKind != null && claimKind.isBlank()) {
            claimKind = null;
        }
        confidence = Confidence.requireInRange(confidence);
        if (quotedText != null && quotedText.length() > 4096) {
            throw new IllegalArgumentException(
                    "quotedText exceeds 4096 characters");
        }
        if (quotedText != null && quotedText.isBlank()) {
            quotedText = null;
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        transcriptSegments = transcriptSegments == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(transcriptSegments));
        attachments = attachments == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(attachments));
        externalUrls = externalUrls == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(externalUrls));
        if (transcriptSegments.size() > 64) {
            throw new IllegalArgumentException(
                    "transcriptSegments exceeds 64: " + transcriptSegments.size());
        }
        if (attachments.size() > 64) {
            throw new IllegalArgumentException(
                    "attachments exceeds 64: " + attachments.size());
        }
        if (externalUrls.size() > 64) {
            throw new IllegalArgumentException(
                    "externalUrls exceeds 64: " + externalUrls.size());
        }
    }

    /**
     * Closed-set disposition of a citation relative to the
     * claim it points at. Mirrors `contracts/research/research-
     * policy.yaml::spec.citationDispositions`.
     */
    public enum Disposition {
        SUPPORTS,
        REFUTES,
        MENTIONS,
        UNCERTAIN
    }

    public static Citation create(
            TenantScopedId id,
            TenantScopedId sourceId,
            String claimReference,
            String claimKind,
            Locator locator,
            CitationQuality quality,
            Disposition disposition,
            Certainty certainty,
            Double confidence,
            String quotedText,
            List<TranscriptSegment> transcriptSegments,
            List<AttachmentRef> attachments,
            List<String> externalUrls,
            ResearchAuditAttributes audit) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(claimReference, "claimReference");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(certainty, "certainty");
        Objects.requireNonNull(audit, "audit");
        return new Citation(id, sourceId, claimReference, claimKind, locator, quality,
                disposition, certainty, confidence, quotedText,
                transcriptSegments == null ? List.of() : List.copyOf(transcriptSegments),
                attachments == null ? List.of() : List.copyOf(attachments),
                externalUrls == null ? List.of() : List.copyOf(externalUrls),
                Instant.now(), Instant.now(), 1L, audit);
    }

    /**
     * Rehydrate a Citation from a persisted row. The aggregate
     * the JSONB {@code transcript_segments} / {@code attachments} /
     * {@code external_urls} columns back into the immutable
     * domain lists; the {@code at} / {@code updatedAt} timestamps
     * are restored as-is so the version history stays intact.
     */
    public static Citation rehydrate(
            TenantScopedId id,
            TenantScopedId sourceId,
            String claimReference,
            String claimKind,
            Locator locator,
            CitationQuality quality,
            Disposition disposition,
            Certainty certainty,
            Double confidence,
            String quotedText,
            List<TranscriptSegment> transcriptSegments,
            List<AttachmentRef> attachments,
            List<String> externalUrls,
            Instant createdAt,
            Instant updatedAt,
            long version,
            ResearchAuditAttributes audit) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(claimReference, "claimReference");
        Objects.requireNonNull(quality, "quality");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(certainty, "certainty");
        Objects.requireNonNull(audit, "audit");
        return new Citation(id, sourceId, claimReference, claimKind, locator, quality,
                disposition, certainty, confidence, quotedText,
                transcriptSegments == null ? List.of() : List.copyOf(transcriptSegments),
                attachments == null ? List.of() : List.copyOf(attachments),
                externalUrls == null ? List.of() : List.copyOf(externalUrls),
                createdAt, updatedAt, version, audit);
    }
}
