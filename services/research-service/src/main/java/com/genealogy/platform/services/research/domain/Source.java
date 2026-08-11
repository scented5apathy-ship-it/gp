package com.genealogy.platform.services.research.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Source aggregate root. A source is a single record (or a
 * small group of records treated as one) that anchors one or
 * more {@code Citation} instances. Mirrors
 * `requirements.md` R8.1 (source) + `design.md` §5.5
 * (CLAIM ||--o{ CITATION_LINK supported_by, SOURCE ||--o{
 * CITATION contains) + `contracts/research/research-policy.
 * yaml::spec.sourceSchema` (E6.1).
 *
 * <p>Invariants enforced by the compact constructor:
 *
 * <ul>
 *   <li>The source MUST belong to a {@code Repository} (the
 *       containment anchor).
 *   <li>{@code title} is non-blank and ≤ 512 characters.
 *   <li>{@code sourceKind} is closed-set (see
 *       {@link SourceKind}).
 *   <li>At most 64 attachments + 256 citations.
 * </ul>
 *
 * Pointer-only sources ({@link SourceKind#ARCHIVE} +
 * {@link SourceKind#FINDING_AID}) require at least one
 * attachment; the invariant service emits a hard DENY
 * otherwise.
 */
public record Source(
        TenantScopedId id,
        TenantScopedId repositoryId,
        String title,
        SourceKind sourceKind,
        String author,
        String publisher,
        Integer publicationYear,
        String publisherLocation,
        Locator locator,
        List<AttachmentRef> attachments,
        List<Citation> citations,
        String description,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt,
        long version,
        ResearchAuditAttributes audit) {

    public Source {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(audit, "audit");
        if (id.resourceKind() != TenantScopedId.ResourceKind.SOURCE) {
            throw new IllegalArgumentException(
                    "TenantScopedId resourceKind must be SOURCE, got "
                            + id.resourceKind());
        }
        if (repositoryId.resourceKind() != TenantScopedId.ResourceKind.REPOSITORY) {
            throw new IllegalArgumentException(
                    "repositoryId resourceKind must be REPOSITORY, got "
                            + repositoryId.resourceKind());
        }
        if (!id.tenantId().equals(repositoryId.tenantId())) {
            throw new IllegalArgumentException(
                    "source tenant must match repository tenant");
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > 512) {
            throw new IllegalArgumentException("title exceeds 512 characters");
        }
        if (publicationYear != null && (publicationYear < 1000 || publicationYear > 9999)) {
            throw new IllegalArgumentException(
                    "publicationYear must be 4-digit: " + publicationYear);
        }
        if (author != null && author.length() > 256) {
            throw new IllegalArgumentException("author exceeds 256 characters");
        }
        if (author != null && author.isBlank()) {
            author = null;
        }
        if (publisher != null && publisher.length() > 256) {
            throw new IllegalArgumentException("publisher exceeds 256 characters");
        }
        if (publisher != null && publisher.isBlank()) {
            publisher = null;
        }
        if (publisherLocation != null && publisherLocation.length() > 256) {
            throw new IllegalArgumentException(
                    "publisherLocation exceeds 256 characters");
        }
        if (publisherLocation != null && publisherLocation.isBlank()) {
            publisherLocation = null;
        }
        if (description != null && description.length() > 4096) {
            throw new IllegalArgumentException(
                    "description exceeds 4096 characters");
        }
        if (description != null && description.isBlank()) {
            description = null;
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        attachments = attachments == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(attachments));
        citations = citations == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(citations));
        if (attachments.size() > 64) {
            throw new IllegalArgumentException(
                    "attachments exceeds 64: " + attachments.size());
        }
        if (citations.size() > 256) {
            throw new IllegalArgumentException(
                    "citations exceeds 256: " + citations.size());
        }
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public boolean isPointerOnly() {
        return sourceKind == SourceKind.ARCHIVE
                || sourceKind == SourceKind.FINDING_AID;
    }

    public Source withCitations(List<Citation> next) {
        Objects.requireNonNull(next, "next");
        return new Source(id, repositoryId, title, sourceKind, author, publisher,
                publicationYear, publisherLocation, locator, attachments, next,
                description, createdAt, Instant.now(), archivedAt, version + 1, audit);
    }

    public static Source create(
            TenantScopedId id,
            TenantScopedId repositoryId,
            String title,
            SourceKind sourceKind,
            String author,
            String publisher,
            Integer publicationYear,
            String publisherLocation,
            Locator locator,
            List<AttachmentRef> attachments,
            String description,
            ResearchAuditAttributes audit) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(repositoryId, "repositoryId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(sourceKind, "sourceKind");
        Objects.requireNonNull(audit, "audit");
        return new Source(id, repositoryId, title, sourceKind, author, publisher,
                publicationYear, publisherLocation, locator,
                attachments == null ? List.of() : List.copyOf(attachments),
                List.of(), description, Instant.now(), Instant.now(), null, 1L, audit);
    }
}
