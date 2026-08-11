package com.genealogy.platform.services.collaboration.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Direct edit matrix: the role × branch × resource-type
 * routing decision grid. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.directEditMatrix` (E6.3) and `requirements.md`
 * R10.4.
 *
 * <p>The matrix is immutable: the lookup goes through
 * {@link #decide(CollaborationRole, TreeBranch, RoutingResourceType)}
 * which falls back to {@code defaultRoutingDecision} when
 * the (role, branch, resourceType) tuple is not present.
 *
 * <p>Hard-coded guard rails never relax:
 * <ul>
 *   <li>`RoutingResourceType.TREE_VISIBILITY` / `CLAIM` /
 *       `SOURCE` are NEVER DIRECT_EDIT (see
 *       `spec.alwaysApprovalRequired`).
 *   <li>`CollaborationRole.VIEWER` / `CONTRIBUTOR` are NEVER
 *       DIRECT_EDIT (see `spec.directEditForbiddenRoles`).
 *   <li>`directEditPermittedResources` (LIFE_EVENT / CITATION)
 *       is the positive list of resource types that allow
 *       direct edit for any role.
 * </ul>
 *
 * <p>The matrix is loaded from the YAML contract at startup;
 * the Java type is the in-memory mirror that the executor
 * consults.
 */
public final class DirectEditMatrix {

    public static final Set<RoutingResourceType> ALWAYS_APPROVAL_REQUIRED = Set.of(
            RoutingResourceType.TREE_VISIBILITY,
            RoutingResourceType.CLAIM,
            RoutingResourceType.SOURCE);

    public static final Set<RoutingResourceType> DIRECT_EDIT_PERMITTED_RESOURCES = Set.of(
            RoutingResourceType.LIFE_EVENT,
            RoutingResourceType.CITATION);

    public static final Set<CollaborationRole> DIRECT_EDIT_FORBIDDEN_ROLES = Set.of(
            CollaborationRole.VIEWER,
            CollaborationRole.CONTRIBUTOR);

    private final RoutingDecision defaultRoutingDecision;
    private final Map<CollaborationRole, Map<TreeBranch, Map<RoutingResourceType, RoutingDecision>>> grid;

    public DirectEditMatrix(
            RoutingDecision defaultRoutingDecision,
            Map<CollaborationRole, Map<TreeBranch, Map<RoutingResourceType, RoutingDecision>>> grid) {
        this.defaultRoutingDecision = Objects.requireNonNull(
                defaultRoutingDecision, "defaultRoutingDecision");
        Objects.requireNonNull(grid, "grid");
        Map<CollaborationRole, Map<TreeBranch, Map<RoutingResourceType, RoutingDecision>>> copy =
                new HashMap<>();
        for (Map.Entry<CollaborationRole, Map<TreeBranch, Map<RoutingResourceType, RoutingDecision>>> e
                : grid.entrySet()) {
            Map<TreeBranch, Map<RoutingResourceType, RoutingDecision>> branchCopy = new HashMap<>();
            for (Map.Entry<TreeBranch, Map<RoutingResourceType, RoutingDecision>> be
                    : e.getValue().entrySet()) {
                branchCopy.put(be.getKey(), Map.copyOf(be.getValue()));
            }
            copy.put(e.getKey(), Map.copyOf(branchCopy));
        }
        this.grid = Map.copyOf(copy);
    }

    public RoutingDecision defaultRoutingDecision() {
        return defaultRoutingDecision;
    }

    public RoutingDecision decide(
            CollaborationRole role,
            TreeBranch branch,
            RoutingResourceType resourceType) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(branch, "branch");
        Objects.requireNonNull(resourceType, "resourceType");
        if (ALWAYS_APPROVAL_REQUIRED.contains(resourceType)) {
            return RoutingDecision.APPROVAL_REQUIRED;
        }
        if (DIRECT_EDIT_FORBIDDEN_ROLES.contains(role)) {
            return role == CollaborationRole.VIEWER
                    ? RoutingDecision.DENY
                    : RoutingDecision.APPROVAL_REQUIRED;
        }
        Map<TreeBranch, Map<RoutingResourceType, RoutingDecision>> branchMap = grid.get(role);
        if (branchMap == null) {
            return defaultRoutingDecision;
        }
        Map<RoutingResourceType, RoutingDecision> resourceMap = branchMap.get(branch);
        if (resourceMap == null) {
            return defaultRoutingDecision;
        }
        RoutingDecision decision = resourceMap.get(resourceType);
        if (decision == null) {
            return defaultRoutingDecision;
        }
        if (decision == RoutingDecision.DIRECT_EDIT
                && !DIRECT_EDIT_PERMITTED_RESOURCES.contains(resourceType)) {
            return RoutingDecision.APPROVAL_REQUIRED;
        }
        return decision;
    }

    public boolean hasEntry(
            CollaborationRole role,
            TreeBranch branch,
            RoutingResourceType resourceType) {
        Map<TreeBranch, Map<RoutingResourceType, RoutingDecision>> branchMap = grid.get(role);
        if (branchMap == null) {
            return false;
        }
        Map<RoutingResourceType, RoutingDecision> resourceMap = branchMap.get(branch);
        if (resourceMap == null) {
            return false;
        }
        return resourceMap.containsKey(resourceType);
    }

    public Set<CollaborationRole> roles() {
        return Collections.unmodifiableSet(new HashSet<>(grid.keySet()));
    }
}
