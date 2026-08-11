package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;

/**
 * Pure routing executor. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.{directEditMatrix, alwaysApprovalRequired,
 * directEditPermittedResources, directEditForbiddenRoles,
 * defaultRoutingDecision}` (E6.3) and `requirements.md`
 * R10.4 (tenant / tree admin SHALL configure direct edit
 * or approval per role / branch / resource type).
 *
 * <p>The executor consults the
 * {@link DirectEditMatrix} + the hard-coded guard rails
 * (`ALWAYS_APPROVAL_REQUIRED`, `DIRECT_EDIT_FORBIDDEN_ROLES`,
 * `DIRECT_EDIT_PERMITTED_RESOURCES`) to pick the
 * {@link RoutingDecision}. The choice is then wrapped in
 * a {@link RoutingDecisionRecord} carrying the audit
 * reason code.
 */
public final class RoutingExecutor {

    private RoutingExecutor() {
    }

    public static RoutingDecisionRecord route(RoutingRequest request, DirectEditMatrix matrix) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(matrix, "matrix");
        if (DirectEditMatrix.ALWAYS_APPROVAL_REQUIRED.contains(request.resourceType())) {
            return new RoutingDecisionRecord(
                    RoutingDecision.APPROVAL_REQUIRED,
                    request.role(),
                    request.branch(),
                    request.resourceType(),
                    request.resourceId(),
                    request.proposedBaseVersion(),
                    "ROUTING_DEFAULT_REQUIRED");
        }
        if (DirectEditMatrix.DIRECT_EDIT_FORBIDDEN_ROLES.contains(request.role())) {
            RoutingDecision decision = request.role() == CollaborationRole.VIEWER
                    ? RoutingDecision.DENY
                    : RoutingDecision.APPROVAL_REQUIRED;
            return new RoutingDecisionRecord(
                    decision,
                    request.role(),
                    request.branch(),
                    request.resourceType(),
                    request.resourceId(),
                    request.proposedBaseVersion(),
                    decision == RoutingDecision.DENY
                            ? "ROUTING_FORBIDDEN_ROLE"
                            : "ROUTING_DEFAULT_REQUIRED");
        }
        if (matrix.hasEntry(request.role(), request.branch(), request.resourceType())) {
            // The matrix explicitly records a decision for this
            // (role, branch, resourceType) tuple. The hard-coded
            // guard rail on PERMITTED_RESOURCES catches the case
            // where the matrix grants DIRECT_EDIT on a resource
            // type that is never allowed (e.g. PERSON).
            if (!DirectEditMatrix.DIRECT_EDIT_PERMITTED_RESOURCES.contains(request.resourceType())) {
                return new RoutingDecisionRecord(
                        RoutingDecision.APPROVAL_REQUIRED,
                        request.role(),
                        request.branch(),
                        request.resourceType(),
                        request.resourceId(),
                        request.proposedBaseVersion(),
                        "ROUTING_FORBIDDEN_RESOURCE");
            }
            return new RoutingDecisionRecord(
                    RoutingDecision.DIRECT_EDIT,
                    request.role(),
                    request.branch(),
                    request.resourceType(),
                    request.resourceId(),
                    request.proposedBaseVersion(),
                    "ROUTING_DIRECT_EDIT_GRANTED");
        }
        return new RoutingDecisionRecord(
                RoutingDecision.APPROVAL_REQUIRED,
                request.role(),
                request.branch(),
                request.resourceType(),
                request.resourceId(),
                request.proposedBaseVersion(),
                "ROUTING_DEFAULT_REQUIRED");
    }
}
