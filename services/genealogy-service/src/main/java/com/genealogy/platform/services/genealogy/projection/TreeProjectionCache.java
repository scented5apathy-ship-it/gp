package com.genealogy.platform.services.genealogy.projection;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory projection cache that mirrors the Valkey contract
 * declared in {@code contracts/genealogy/tree-projection-cache.yaml}.
 *
 * <ul>
 *   <li>Tenant-aware key:
 *       {@code gp:{tenant_pseudo_id}:genealogy:projection:{treeId}:{viewKind}:{direction}:{depth}:{filterHash}}.
 *       The filter hash is the SHA-256 hex of the canonical
 *       filter representation; the BFF rebuilds the key for
 *       every request.
 *   <li>{@code freshnessTtlSeconds} is the upper bound for
 *       emergency staleness (default 300s). Every cache entry
 *       carries the projection version + the ETag.
 *   <li>{@code invalidate(treeId)} wipes every entry whose
 *       prefix matches the treeId. The Java executor subscribes
 *       to the {@code gp.genealogy.v1.*} Kafka topics and calls
 *       this method on every event listed in
 *       {@code tree-projection-policy.yaml::spec.invalidators}.
 *   <li>{@code read-through}: a miss calls the
 *       {@link TreeProjectionBuilder} and stores the snapshot.
 * </ul>
 *
 * <p>The cache is NEVER the source of truth. The cache contract
 * is mandatory but the read model is always rebuildable from
 * the JDBC-backed store. Per ADR-E0.5-06 the cache invalidation
 * is mandatory on every Write — TTL-only caching is forbidden.
 */
public final class TreeProjectionCache {

    private final TreeProjectionBuilder builder;
    private final long freshnessTtlSeconds;
    private final long freshnessTtlSecondsCeiling;
    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public TreeProjectionCache(TreeProjectionBuilder builder,
                                long freshnessTtlSeconds,
                                long freshnessTtlSecondsCeiling) {
        this(builder, freshnessTtlSeconds, freshnessTtlSecondsCeiling, Clock.systemUTC());
    }

    public TreeProjectionCache(TreeProjectionBuilder builder,
                                long freshnessTtlSeconds,
                                long freshnessTtlSecondsCeiling,
                                Clock clock) {
        this.builder = Objects.requireNonNull(builder, "builder");
        if (freshnessTtlSeconds <= 0) {
            throw new IllegalArgumentException(
                    "freshnessTtlSeconds must be > 0: " + freshnessTtlSeconds);
        }
        if (freshnessTtlSecondsCeiling < freshnessTtlSeconds) {
            throw new IllegalArgumentException(
                    "freshnessTtlSecondsCeiling < freshnessTtlSeconds");
        }
        if (freshnessTtlSecondsCeiling > 1800) {
            throw new IllegalArgumentException(
                    "freshnessTtlSecondsCeiling > 1800: " + freshnessTtlSecondsCeiling);
        }
        this.freshnessTtlSeconds = freshnessTtlSeconds;
        this.freshnessTtlSecondsCeiling = freshnessTtlSecondsCeiling;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Build (or reuse) a projection. The cache key encodes
     * every parameter that influences the response payload.
     */
    public TreeProjection get(String tenantPseudoId, ProjectionQuery query) {
        Objects.requireNonNull(tenantPseudoId, "tenantPseudoId");
        Objects.requireNonNull(query, "query");
        String key = key(tenantPseudoId, query);
        Instant now = clock.instant();
        Entry existing = entries.get(key);
        if (existing != null && existing.snapshot.version() >= query.baseVersion()
                && existing.expiresAt.isAfter(now)) {
            return existing.snapshot;
        }
        TreeProjection snapshot = builder.build(query);
        entries.put(key, new Entry(snapshot, now.plusSeconds(freshnessTtlSeconds)));
        return snapshot;
    }

    /** Invalidate every entry that references {@code treeId}. */
    public void invalidate(String treeId) {
        Objects.requireNonNull(treeId, "treeId");
        entries.entrySet().removeIf(e -> e.getKey().contains(":" + treeId + ":"));
    }

    /** Drop the entire cache (tenant sweep, debug, etc.). */
    public void invalidateAll() {
        entries.clear();
    }

    /** Read-only view of the cache contents (debug + tests). */
    public Map<String, Instant> debugExpiry() {
        Map<String, Instant> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            snapshot.put(e.getKey(), e.getValue().expiresAt);
        }
        return snapshot;
    }

    public long freshnessTtlSeconds() {
        return freshnessTtlSeconds;
    }

    public long freshnessTtlSecondsCeiling() {
        return freshnessTtlSecondsCeiling;
    }

    public Optional<TreeProjection> peek(String tenantPseudoId, ProjectionQuery query) {
        String key = key(tenantPseudoId, query);
        Entry existing = entries.get(key);
        return existing == null ? Optional.empty() : Optional.of(existing.snapshot);
    }

    /**
     * Compose the tenant-aware key. Pattern mirrors
     * {@code tree-projection-cache.yaml::spec.aclKeyPattern}.
     */
    public static String key(String tenantPseudoId, ProjectionQuery query) {
        return "gp:" + tenantPseudoId
                + ":genealogy:projection:"
                + query.treeId() + ":"
                + query.viewKind().wire() + ":"
                + query.direction().wire() + ":"
                + query.depth() + ":"
                + filterHash(query.filter());
    }

    private static String filterHash(ProjectionFilter filter) {
        StringBuilder sb = new StringBuilder();
        filter.relationshipKinds().stream().sorted()
                .forEach(k -> sb.append(k.wire()).append(','));
        sb.append('|');
        filter.livingStatuses().stream().sorted()
                .forEach(s -> sb.append(s.wire()).append(','));
        return Integer.toHexString(sb.toString().hashCode());
    }

    private record Entry(TreeProjection snapshot, Instant expiresAt) {
        Entry {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }
}