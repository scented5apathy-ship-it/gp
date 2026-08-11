package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;

/**
 * Routing request: the inputs to the mixed-collaboration
 * routing decision. The executor consults the
 * {@code DirectEditMatrix} + the guard rails to choose
 * between DIRECT_EDIT, APPROVAL_REQUIRED and DENY.
 *
 * <p>Mirrors `contracts/collaboration/mixed-collaboration-
 * policy.yaml` ::spec.{collaborationRoles, treeBranches,
 * resourceTypes} (E6.3) and `requirements.md` R10.4.
 *
 * <p>Tenant boundary is enforced by `TenantScopedId`
 * (E6.2) — the compact constructor rejects blank tenant
 * ids.
 */
public record RoutingRequest(
        TenantScopedId proposer,
        CollaborationRole role,
        TreeBranch branch,
        RoutingResourceType resourceType,
        String resourceId,
        long proposedBaseVersion) {

    public RoutingRequest {
        Objects.requireNonNull(proposer, "proposer");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (resourceId.length() > 128) {
            throw new IllegalArgumentException("resourceId exceeds 128 characters");
        }
        if (!resourceId.matches("[A-Za-z0-9._\\-]+")) {
            throw new IllegalArgumentException(
                    "resourceId contains forbidden characters: " + resourceId);
        }
        if (proposedBaseVersion <= 0) {
            throw new IllegalArgumentException(
                    "proposedBaseVersion must be positive, got " + proposedBaseVersion);
        }
    }
}
