package com.genealogy.platform.services.media.albums;

import java.util.Map;

/**
 * Closed-set verdict returned by
 * {@link AlbumReferenceResolverPort}.
 */
public record AlbumReferenceVerdict(
        AlbumReferenceOutcome outcome,
        AlbumFailureReason failureReason,
        Map<String, String> facts) {

    public AlbumReferenceVerdict {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
        if (outcome == AlbumReferenceOutcome.RESOLVED
                && failureReason != null) {
            throw new IllegalArgumentException(
                    "RESOLVED verdict MUST NOT carry failureReason");
        }
        if (outcome != AlbumReferenceOutcome.RESOLVED
                && failureReason == null) {
            throw new IllegalArgumentException(
                    "non-RESOLVED verdict requires failureReason");
        }
    }

    public static AlbumReferenceVerdict resolved() {
        return new AlbumReferenceVerdict(
                AlbumReferenceOutcome.RESOLVED, null, Map.of());
    }

    public static AlbumReferenceVerdict unresolved(
            AlbumReferenceOutcome outcome,
            AlbumFailureReason reason) {
        return new AlbumReferenceVerdict(outcome, reason, Map.of());
    }
}