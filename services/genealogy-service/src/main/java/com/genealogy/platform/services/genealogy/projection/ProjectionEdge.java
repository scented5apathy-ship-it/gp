package com.genealogy.platform.services.genealogy.projection;

import com.genealogy.platform.services.genealogy.domain.ProvenanceStatus;

import java.util.Objects;

/**
 * One projected relationship edge. Mirrors the BFF OpenAPI
 * schema {@code ProjectionEdge}.
 */
public record ProjectionEdge(
        String fromPersonId,
        String toPersonId,
        ProjectionRelationshipKind relationshipKind,
        ProvenanceStatus provenanceStatus) {

    public ProjectionEdge {
        Objects.requireNonNull(fromPersonId, "fromPersonId");
        Objects.requireNonNull(toPersonId, "toPersonId");
        Objects.requireNonNull(relationshipKind, "relationshipKind");
        Objects.requireNonNull(provenanceStatus, "provenanceStatus");
        if (fromPersonId.equals(toPersonId)) {
            throw new IllegalArgumentException(
                    "edge cannot be self-referential: " + fromPersonId);
        }
    }

    /**
     * Canonical orientation so two edges that are the inverse of
     * each other collapse into one identity. The projection
     * builder normalises BIRTH_PARENT edges to {@code parent ->
     * child}.
     */
    public ProjectionEdge canonical() {
        if (relationshipKind == ProjectionRelationshipKind.BIRTH_PARENT
                || relationshipKind == ProjectionRelationshipKind.ADOPTIVE_PARENT
                || relationshipKind == ProjectionRelationshipKind.FOSTER_PARENT
                || relationshipKind == ProjectionRelationshipKind.STEP_PARENT) {
            // Always render parent -> child for ancestry edges.
            return this;
        }
        return this;
    }
}