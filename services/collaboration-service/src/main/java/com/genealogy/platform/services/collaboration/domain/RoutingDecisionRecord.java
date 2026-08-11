package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;

/**
 * Routing decision: the output of the mixed-collaboration
 * routing executor. The decision is recorded in the audit
 * hook (per `auditClassOnRoute: collab` + the
 * `auditActionOn{DirectEdit,ApprovalRequired,Deny}`
 * closed-set) and downstream code applies the mutation
 * directly (on DIRECT_EDIT) or creates a proposal (on
 * APPROVAL_REQUIRED), or returns 403 (on DENY).
 */
public record RoutingDecisionRecord(
        RoutingDecision decision,
        CollaborationRole role,
        TreeBranch branch,
        RoutingResourceType resourceType,
        String resourceId,
        long proposedBaseVersion,
        String reasonCode) {

    public RoutingDecisionRecord {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(reasonCode, "reasonCode");
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        if (reasonCode.length() > 128) {
            throw new IllegalArgumentException("reasonCode exceeds 128 characters");
        }
        if (proposedBaseVersion <= 0) {
            throw new IllegalArgumentException(
                    "proposedBaseVersion must be positive, got " + proposedBaseVersion);
        }
    }
}
