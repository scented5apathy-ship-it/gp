package com.genealogy.platform.services.research.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Repository aggregate root. A repository groups the
 * {@code Source} records that share a physical or digital
 * home (an archive, a parish, a digital platform). Mirrors
 * `requirements.md` R8.1 (repository) + `design.md` §5.5
 * (research log home) + `contracts/research/research-policy.
 * yaml::spec.repositorySchema` (E6.1).
 *
 * <p>Invariants enforced by the compact constructor:
 *
 * <ul>
 *   <li>Tenant scope is mandatory (NFR1).
 *   <li>{@code name} is non-blank and ≤ 256 characters.
 *   <li>{@code locationLabel} is optional but ≤ 1024
 *       characters when set.
 *   <li>{@code websiteUrl} is optional but ≤ 2048 characters
 *       when set.
 *   <li>{@link RepositoryKind#FAMILY_HOLDING} and
 *       {@link RepositoryKind#OTHER} are forced-private for
 *       default visibility.
 *   <li>Audit attributes are mandatory (NFR5).
 * </ul>
 *
 * Soft-delete is supported via {@link #archivedAt()}; once
 * archived, the repository is hidden from the default search
 * projection but the audit chain remains intact.
 */
public record Repository(
        TenantScopedId id,
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
        ResearchAuditAttributes audit,
        Map<String, String> metadata) {

    public Repository {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(audit, "audit");
        if (id.resourceKind() != TenantScopedId.ResourceKind.REPOSITORY) {
            throw new IllegalArgumentException(
                    "TenantScopedId resourceKind must be REPOSITORY, got "
                            + id.resourceKind());
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.length() > 256) {
            throw new IllegalArgumentException("name exceeds 256 characters");
        }
        if (locationLabel != null && locationLabel.length() > 1024) {
            throw new IllegalArgumentException(
                    "locationLabel exceeds 1024 characters");
        }
        if (locationLabel != null && locationLabel.isBlank()) {
            locationLabel = null;
        }
        if (websiteUrl != null && websiteUrl.length() > 2048) {
            throw new IllegalArgumentException(
                    "websiteUrl exceeds 2048 characters");
        }
        if (websiteUrl != null && websiteUrl.isBlank()) {
            websiteUrl = null;
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
        if (kind == RepositoryKind.FAMILY_HOLDING || kind == RepositoryKind.OTHER) {
            privateHolding = true;
        }
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        if (metadata.size() > 32) {
            throw new IllegalArgumentException(
                    "metadata exceeds 32 entries: " + metadata.size());
        }
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public Repository archive(Instant at, ResearchAuditAttributes archiveAudit) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(archiveAudit, "audit");
        if (archivedAt != null) {
            throw new IllegalStateException("repository already archived");
        }
        return new Repository(id, name, kind, locationLabel, websiteUrl, description,
                privateHolding, createdAt, at, at, version + 1, archiveAudit, metadata);
    }

    public static Repository create(
            TenantScopedId id,
            String name,
            RepositoryKind kind,
            String locationLabel,
            String websiteUrl,
            String description,
            ResearchAuditAttributes audit) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(audit, "audit");
        return new Repository(id, name, kind, locationLabel, websiteUrl, description,
                false, Instant.now(), Instant.now(), null, 1L, audit, Map.of());
    }
}
