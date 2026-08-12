package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Activity feed item value object. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.activityKinds + activityVisibilities + activityFeedAlwaysReproject`
 * (E6.4), `requirements.md` R10.5 + `design.md` §8.3.
 *
 * <p>The activity feed is rebuilt by re-projecting the
 * source domain event through the current permission
 * state. A snapshot may NEVER carry the raw payload (per
 * {@code activityFeedSnapshotRawPayloadAllowed=false}); the
 * {@link #redactedFieldMarker} is pinned to {@code [REDACTED]}
 * and the {@link #redactedFieldKeys} cap is enforced at
 * construction time.
 */
public record ActivityFeedItem(
        TenantScopedId itemId,
        ActivityKind kind,
        ActivityVisibility visibility,
        String actorPseudoId,
        String targetPseudoId,
        String summary,
        RedactionReason redactionReason,
        Set<String> redactedFieldKeys,
        Instant occurredAt,
        Map<String, String> metadata) {

    public static final int MAX_SUMMARY_LENGTH = 1024;
    public static final int MAX_REDACTED_KEYS = 16;
    public static final int MAX_PSEUDO_ID_LENGTH = 128;
    public static final int MAX_METADATA_KEYS = 16;
    public static final String REDACTED_FIELD_MARKER = "[REDACTED]";

    public ActivityFeedItem {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (actorPseudoId == null || actorPseudoId.isBlank()) {
            throw new IllegalArgumentException("actorPseudoId must not be blank");
        }
        if (actorPseudoId.length() > MAX_PSEUDO_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "actorPseudoId exceeds " + MAX_PSEUDO_ID_LENGTH + " characters");
        }
        if (targetPseudoId != null && targetPseudoId.length() > MAX_PSEUDO_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "targetPseudoId exceeds " + MAX_PSEUDO_ID_LENGTH + " characters");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalArgumentException(
                    "summary exceeds " + MAX_SUMMARY_LENGTH + " characters");
        }
        if (redactedFieldKeys == null) {
            redactedFieldKeys = Set.of();
        } else {
            redactedFieldKeys = Set.copyOf(redactedFieldKeys);
        }
        if (redactedFieldKeys.size() > MAX_REDACTED_KEYS) {
            throw new IllegalArgumentException(
                    "redactedFieldKeys exceeds " + MAX_REDACTED_KEYS);
        }
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        if (metadata.size() > MAX_METADATA_KEYS) {
            throw new IllegalArgumentException(
                    "metadata exceeds " + MAX_METADATA_KEYS + " entries");
        }
    }

    public static ActivityFeedItem redacted(
            TenantScopedId itemId,
            ActivityKind kind,
            ActivityVisibility visibility,
            String actorPseudoId,
            String targetPseudoId,
            RedactionReason reason,
            Instant occurredAt) {
        Objects.requireNonNull(reason, "reason");
        String summary = REDACTED_FIELD_MARKER;
        return new ActivityFeedItem(
                itemId, kind, visibility, actorPseudoId, targetPseudoId,
                summary, reason, Set.of(reason.name()), occurredAt, Map.of());
    }
}
