package com.genealogy.platform.services.genealogy.projection;

import java.util.Objects;
import java.util.Optional;

/**
 * Input record for {@link TreeProjectionBuilder#build}. Mirrors
 * the BFF OpenAPI query parameters + body for
 * {@code getTreeProjection}.
 */
public record ProjectionQuery(
        String treeId,
        String rootPersonId,
        ProjectionViewKind viewKind,
        ProjectionDirection direction,
        int depth,
        int maxNodes,
        int maxRelationships,
        ProjectionFilter filter,
        boolean unlistedTokenInvalid,
        long baseVersion) {

    public ProjectionQuery {
        Objects.requireNonNull(treeId, "treeId");
        Objects.requireNonNull(rootPersonId, "rootPersonId");
        Objects.requireNonNull(viewKind, "viewKind");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(filter, "filter");
        if (depth < 1 || depth > 12) {
            throw new IllegalArgumentException("depth outside [1, 12]: " + depth);
        }
        if (maxNodes < 1 || maxNodes > 1000) {
            throw new IllegalArgumentException("maxNodes outside [1, 1000]: " + maxNodes);
        }
        if (maxRelationships < 1 || maxRelationships > 2000) {
            throw new IllegalArgumentException(
                    "maxRelationships outside [1, 2000]: " + maxRelationships);
        }
    }

    public static ProjectionQuery defaultQuery(String treeId,
                                                String rootPersonId,
                                                ProjectionViewKind viewKind,
                                                ProjectionDirection direction,
                                                int depth) {
        return new ProjectionQuery(
                treeId, rootPersonId, viewKind, direction,
                depth, 250, 500, ProjectionFilter.none(), false, 0L);
    }

    public ProjectionQuery withUnlistedTokenInvalid(boolean flag) {
        return new ProjectionQuery(
                treeId, rootPersonId, viewKind, direction,
                depth, maxNodes, maxRelationships, filter, flag, baseVersion);
    }

    public Optional<Long> optionalBaseVersion() {
        return baseVersion <= 0 ? Optional.empty() : Optional.of(baseVersion);
    }
}