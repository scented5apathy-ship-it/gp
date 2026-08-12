package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Activity feed projection. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.activityFeedAlwaysReproject + activityFeedSnapshotRawPayloadAllowed=false`
 * (E6.4), `requirements.md` R10.5 + `design.md` §8.3.
 *
 * <p>The feed is a paginated projection of
 * {@link ActivityFeedItem} records. The
 * {@link #reproject(Instant)} method stamps the projection
 * time so the application layer can detect a stale
 * snapshot; the snapshot itself never carries the raw
 * payload (enforced by {@link ActivityFeedItem}).
 */
public record ActivityFeed(
        TenantScopedId feedId,
        String ownerPseudoId,
        Map<String, ActivityFeedItem> items,
        Instant projectedAt,
        boolean hasMore) {

    public static final int MAX_ITEMS_PER_PAGE = 100;

    public ActivityFeed {
        Objects.requireNonNull(feedId, "feedId");
        Objects.requireNonNull(projectedAt, "projectedAt");
        if (ownerPseudoId == null || ownerPseudoId.isBlank()) {
            throw new IllegalArgumentException("ownerPseudoId must not be blank");
        }
        items = items == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(items));
        if (items.size() > MAX_ITEMS_PER_PAGE) {
            throw new IllegalArgumentException(
                    "items exceeds " + MAX_ITEMS_PER_PAGE + " entries");
        }
    }

    public ActivityFeed reproject(Instant now) {
        Objects.requireNonNull(now, "now");
        return new ActivityFeed(feedId, ownerPseudoId, items, now, hasMore);
    }
}
