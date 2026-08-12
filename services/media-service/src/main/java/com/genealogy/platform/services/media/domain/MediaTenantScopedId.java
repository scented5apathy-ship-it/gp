package com.genealogy.platform.services.media.domain;

import java.util.Objects;

/**
 * Tenant-scoped opaque identifier for every media
 * aggregate. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.auditRequiredKeys` (E7.1) and the
 * {@code TenantScopedId} convention from
 * `services/collaboration-service` (E6.2).
 *
 * <p>The {@code tenantId} is the trusted-context tenant
 * identity decoded from the Keycloak + Kong boundary, never
 * accepted from the request body.
 */
public record MediaTenantScopedId(
        String tenantId, MediaResourceKind resourceKind, String resourceId) {

    public static final int MAX_RESOURCE_ID_LENGTH = 128;

    public MediaTenantScopedId {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(resourceKind, "resourceKind");
        Objects.requireNonNull(resourceId, "resourceId");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (resourceId.length() > MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "resourceId exceeds " + MAX_RESOURCE_ID_LENGTH + " characters");
        }
        if (!resourceId.matches("[A-Za-z0-9._\\-]+")) {
            throw new IllegalArgumentException(
                    "resourceId contains forbidden characters: " + resourceId);
        }
    }

    public static MediaTenantScopedId of(
            String tenantId, MediaResourceKind kind, String resourceId) {
        return new MediaTenantScopedId(tenantId, kind, resourceId);
    }

    /**
     * Closed-set of media resource kinds. Mirrors
     * `ownership-catalog.md` §2.5 (media-service owns
     * `MediaAsset`, `MediaVariant`, `Album`).
     */
    public enum MediaResourceKind {
        UPLOAD_SESSION,
        MULTIPART_PART,
        MEDIA_ASSET,
        MEDIA_VARIANT,
        ALBUM,
        QUOTA_LEDGER
    }
}
