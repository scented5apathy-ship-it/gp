package com.genealogy.platform.services.genealogy.projection;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable projection snapshot. Mirrors the BFF OpenAPI schema
 * {@code TreeProjection}. The {@code version} field is
 * monotonic per {@code treeId} + {@code viewKind}; the
 * {@code generatedAt} field is the wall-clock timestamp the
 * projection was assembled (used together with
 * {@code freshnessTtlSeconds} for cache staleness).
 *
 * <p>{@link #etag()} is a stable hash that the client sends
 * back via {@code If-None-Match} (304 Not Modified path).
 */
public record TreeProjection(
        String treeId,
        ProjectionViewKind viewKind,
        ProjectionDirection direction,
        int depth,
        long version,
        Instant generatedAt,
        List<ProjectionNode> nodes,
        List<ProjectionEdge> edges,
        RedactionSummary redaction,
        boolean hasMore,
        String nextCursor,
        String etag) {

    public TreeProjection {
        Objects.requireNonNull(treeId, "treeId");
        Objects.requireNonNull(viewKind, "viewKind");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(redaction, "redaction");
        Objects.requireNonNull(etag, "etag");
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        if (depth < 1 || depth > 12) {
            throw new IllegalArgumentException("depth outside [1, 12]: " + depth);
        }
        if (nodes.size() > 1000) {
            throw new IllegalArgumentException(
                    "nodes exceed spec.maxNeighborhoodNodes=1000: " + nodes.size());
        }
        if (edges.size() > 2000) {
            throw new IllegalArgumentException(
                    "edges exceed spec.maxRelationshipsPerResponse=2000: " + edges.size());
        }
    }

    public Set<String> personIds() {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for (ProjectionNode n : nodes) {
            ids.add(n.personId());
        }
        return ids;
    }
}