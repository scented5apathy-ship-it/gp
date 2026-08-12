package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Activity feed collector. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.activityFeedAlwaysReproject +
 * activityFeedSnapshotRawPayloadAllowed=false +
 * activityFeedRedactedFieldMarker` (E6.4), `requirements.md`
 * R10.5 + `design.md` §8.3.
 *
 * <p>The collector is a pure function that takes a list of
 * candidate {@link ActivityFeedItem} records and:
 *
 * <ul>
 *   <li>filters out items whose {@link ActivityVisibility}
 *       is not in the allowed-visibility set (per
 *       {@code commentScopes});</li>
 *   <li>replaces any item that references a sensitive field
 *       with a {@link ActivityFeedItem#redacted} variant
 *       carrying the {@code SOMETHING_REDACTED} marker
 *       + a {@link RedactionReason};</li>
 *   <li>returns a fresh {@link ActivityFeed} stamped with
 *       the projection time.</li>
 * </ul>
 *
 * <p>The raw payload is never copied into the feed; the
 * {@link ActivityFeedItem} compact constructor enforces the
 * size cap + the {@code [REDACTED]} marker.
 */
public final class ActivityFeedCollector {

    private final Set<String> allowedVisibilities;

    public ActivityFeedCollector(Set<String> allowedVisibilities) {
        Objects.requireNonNull(allowedVisibilities, "allowedVisibilities");
        if (allowedVisibilities.isEmpty()) {
            throw new IllegalArgumentException("allowedVisibilities must not be empty");
        }
        this.allowedVisibilities = Set.copyOf(allowedVisibilities);
    }

    public ActivityFeed collect(
            TenantScopedId feedId,
            String ownerPseudoId,
            List<ActivityFeedItem> candidates,
            Instant now,
            boolean hasMore) {
        Objects.requireNonNull(feedId, "feedId");
        Objects.requireNonNull(ownerPseudoId, "ownerPseudoId");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(now, "now");
        if (ownerPseudoId.isBlank()) {
            throw new IllegalArgumentException("ownerPseudoId must not be blank");
        }
        if (candidates.size() > ActivityFeed.MAX_ITEMS_PER_PAGE) {
            throw new IllegalArgumentException(
                    "candidates exceeds " + ActivityFeed.MAX_ITEMS_PER_PAGE);
        }
        Map<String, ActivityFeedItem> accepted = new LinkedHashMap<>();
        List<ActivityFeedItem> redacted = new ArrayList<>();
        for (ActivityFeedItem item : candidates) {
            if (!allowedVisibilities.contains(item.visibility().name())) {
                continue;
            }
            if (item.redactedFieldKeys() != null
                    && !item.redactedFieldKeys().isEmpty()) {
                redacted.add(item);
            }
            accepted.put(item.itemId().resourceId(), item);
        }
        if (!redacted.isEmpty()) {
            for (ActivityFeedItem r : redacted) {
                ActivityFeedItem redactedItem = ActivityFeedItem.redacted(
                        r.itemId(),
                        r.kind(),
                        r.visibility(),
                        r.actorPseudoId(),
                        r.targetPseudoId(),
                        r.redactionReason() == null
                                ? RedactionReason.RAW_PII_DETECTED
                                : r.redactionReason(),
                        r.occurredAt());
                accepted.put(redactedItem.itemId().resourceId(), redactedItem);
            }
        }
        return new ActivityFeed(
                feedId, ownerPseudoId,
                Collections.unmodifiableMap(accepted),
                now, hasMore);
    }
}
