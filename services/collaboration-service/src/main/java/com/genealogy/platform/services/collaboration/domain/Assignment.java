package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Assignment record. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.assignmentRoles + assignmentStatuses + assignmentRolesAllowed`
 * (E6.4), `requirements.md` R10.5.
 *
 * <p>An assignment binds an {@link AssignmentRole} (WATCHER
 * / REVIEWER / APPROVER / GATEKEEPER / MENTIONED) to a
 * target actor + a {@link TenantScopedId} scope. The
 * application layer must re-authorize the assignment before
 * the actor accepts (per the
 * {@code assignmentReAuthorizationRequiredOnAccept}
 * toggle) and close the assignment on ABAC deny.
 */
public record Assignment(
        TenantScopedId assignmentId,
        AssignmentRole role,
        AssignmentStatus status,
        String targetPseudoId,
        TenantScopedId scopeId,
        Instant openedAt,
        Instant dueAt,
        Instant closedAt,
        CollaborationAuditAttributes audit) {

    public static final int MAX_TARGET_PSEUDO_ID_LENGTH = 128;

    public Assignment {
        Objects.requireNonNull(assignmentId, "assignmentId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(scopeId, "scopeId");
        Objects.requireNonNull(openedAt, "openedAt");
        Objects.requireNonNull(audit, "audit");
        if (targetPseudoId == null || targetPseudoId.isBlank()) {
            throw new IllegalArgumentException("targetPseudoId must not be blank");
        }
        if (targetPseudoId.length() > MAX_TARGET_PSEUDO_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "targetPseudoId exceeds " + MAX_TARGET_PSEUDO_ID_LENGTH + " characters");
        }
        if (dueAt != null && dueAt.isBefore(openedAt)) {
            throw new IllegalArgumentException("dueAt must not be before openedAt");
        }
        if (closedAt != null && closedAt.isBefore(openedAt)) {
            throw new IllegalArgumentException("closedAt must not be before openedAt");
        }
        if (status == AssignmentStatus.ACCEPTED && closedAt == null) {
            throw new IllegalArgumentException(
                    "ACCEPTED assignment requires closedAt");
        }
    }

    public Assignment accept(Instant acceptedAt) {
        Objects.requireNonNull(acceptedAt, "acceptedAt");
        return new Assignment(
                assignmentId, role, AssignmentStatus.ACCEPTED,
                targetPseudoId, scopeId, openedAt, dueAt, acceptedAt, audit);
    }

    public Assignment decline(Instant declinedAt) {
        Objects.requireNonNull(declinedAt, "declinedAt");
        return new Assignment(
                assignmentId, role, AssignmentStatus.DECLINED,
                targetPseudoId, scopeId, openedAt, dueAt, declinedAt, audit);
    }
}
