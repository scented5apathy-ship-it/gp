package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;

/**
 * Tenant-scoped identifier for every collaboration aggregate.
 * The tenant identity is carried by every record so the ABAC
 * layer can apply the redaction policy without second-guessing
 * the caller (per NFR1 / `requirements.md` §6.2).
 *
 * <p>The id format is opaque UUID/ULID. The {@code tenantId}
 * is the trusted-context tenant identity (decoded from the
 * Keycloak + Kong boundary, never accepted from the request
 * body).
 */
public record TenantScopedId(String tenantId, ResourceKind resourceKind, String resourceId) {

    public TenantScopedId {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(resourceKind, "resourceKind");
        Objects.requireNonNull(resourceId, "resourceId");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (resourceId.length() > 128) {
            throw new IllegalArgumentException(
                    "resourceId exceeds 128 characters");
        }
        if (!resourceId.matches("[A-Za-z0-9._\\-]+")) {
            throw new IllegalArgumentException(
                    "resourceId contains forbidden characters: " + resourceId);
        }
    }

    /**
     * Closed-set of collaboration resource kinds. Mirrors
     * `ownership-catalog.md` §2.4 (collaboration-service owns
     * {@code ChangeProposal}, {@code Review}, {@code Comment},
     * {@code ActivityFeed}).
     */
    public enum ResourceKind {
        PROPOSAL,
        REVIEW,
        COMMENT,
        ACTIVITY_FEED,
        TRACEABILITY_LINK
    }

    public static TenantScopedId of(String tenantId, ResourceKind kind, String resourceId) {
        return new TenantScopedId(tenantId, kind, resourceId);
    }
}