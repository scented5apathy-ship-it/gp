package com.genealogy.platform.services.collaboration.domain;

import java.util.List;
import java.util.Objects;

/**
 * Request to detect / classify per-field conflicts. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml`
 * (E6.3) and `requirements.md` R10.3 (the system SHALL
 * detect optimistic concurrency conflicts and provide a
 * comparison model). The executor classifies each field
 * with a {@link ConflictFieldKind} so the UI can render the
 * comparison model and the merge command factory can decide
 * whether auto-merge is safe.
 */
public record ConflictDetectionRequest(
        String baseResourceId,
        long baseVersion,
        long localVersion,
        long incomingVersion,
        List<ConflictComparison> comparisons) {

    public ConflictDetectionRequest {
        Objects.requireNonNull(baseResourceId, "baseResourceId");
        Objects.requireNonNull(comparisons, "comparisons");
        if (baseResourceId.isBlank()) {
            throw new IllegalArgumentException("baseResourceId must not be blank");
        }
        if (baseVersion <= 0) {
            throw new IllegalArgumentException(
                    "baseVersion must be positive, got " + baseVersion);
        }
        if (localVersion <= 0) {
            throw new IllegalArgumentException(
                    "localVersion must be positive, got " + localVersion);
        }
        if (incomingVersion <= 0) {
            throw new IllegalArgumentException(
                    "incomingVersion must be positive, got " + incomingVersion);
        }
        if (comparisons.size() > 64) {
            throw new IllegalArgumentException(
                    "comparisons exceeds 64 entries, got " + comparisons.size());
        }
        for (ConflictComparison c : comparisons) {
            if (!c.resourceId().equals(baseResourceId)) {
                throw new IllegalArgumentException(
                        "comparison resourceId mismatch: " + c.resourceId());
            }
        }
    }
}
