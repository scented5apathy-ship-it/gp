package com.genealogy.platform.services.genealogy.projection;

import java.util.Objects;
import java.util.Set;

/**
 * Input filter for a projection query. Mirrors the BFF OpenAPI
 * query parameters {@code filter[relationshipKinds]} +
 * {@code filter[livingStatus]}. The filter is closed-set; the
 * builder raises {@link IllegalArgumentException} on unknown
 * values.
 */
public record ProjectionFilter(
        Set<ProjectionRelationshipKind> relationshipKinds,
        Set<com.genealogy.platform.services.genealogy.domain.LivingStatus> livingStatuses) {

    public ProjectionFilter {
        Objects.requireNonNull(relationshipKinds, "relationshipKinds");
        Objects.requireNonNull(livingStatuses, "livingStatuses");
        relationshipKinds = Set.copyOf(relationshipKinds);
        livingStatuses = Set.copyOf(livingStatuses);
    }

    public static ProjectionFilter none() {
        return new ProjectionFilter(Set.of(), Set.of());
    }

    public boolean matchesRelationship(ProjectionRelationshipKind kind) {
        return relationshipKinds.isEmpty() || relationshipKinds.contains(kind);
    }

    public boolean matchesLivingStatus(com.genealogy.platform.services.genealogy.domain.LivingStatus status) {
        return livingStatuses.isEmpty() || livingStatuses.contains(status);
    }
}