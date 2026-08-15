package com.genealogy.platform.services.media.albums;

import java.util.Map;

/**
 * Closed-set verdict returned by {@link AlbumOpenFgaPort}.
 *
 * <p>{@code ALLOW} requires an empty {@code facts} map;
 * {@code DENY} requires at least one fact. The compact
 * constructor enforces the invariant so the orchestrator
 * cannot silently accept a partially-populated verdict.
 */
public record AlbumOpenFgaVerdict(
        AlbumOpenFgaOutcome outcome,
        AlbumFailureReason failureReason,
        String reasonCode,
        Map<String, String> facts) {

    public AlbumOpenFgaVerdict {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
        if (outcome == AlbumOpenFgaOutcome.ALLOW) {
            if (failureReason != null) {
                throw new IllegalArgumentException(
                        "ALLOW outcome MUST NOT carry failureReason");
            }
            if (!facts.isEmpty()) {
                throw new IllegalArgumentException(
                        "ALLOW outcome MUST NOT carry facts");
            }
        } else {
            if (failureReason == null) {
                throw new IllegalArgumentException(
                        "DENY outcome requires failureReason");
            }
        }
    }

    public static AlbumOpenFgaVerdict allow() {
        return new AlbumOpenFgaVerdict(
                AlbumOpenFgaOutcome.ALLOW, null, null, Map.of());
    }

    public static AlbumOpenFgaVerdict deny(
            AlbumFailureReason reason, String code) {
        return new AlbumOpenFgaVerdict(
                AlbumOpenFgaOutcome.DENY, reason, code, Map.of());
    }
}