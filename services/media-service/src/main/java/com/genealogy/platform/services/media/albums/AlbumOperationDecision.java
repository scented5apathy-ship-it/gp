package com.genealogy.platform.services.media.albums;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Result of an {@link AlbumCatalog} operation.
 *
 * <p>Decision tree (mirrors the
 * {@code spec.albumAuthorizationMatrix} state machine):
 * <ul>
 *   <li>{@code ALLOWED} — write / read approved.</li>
 *   <li>{@code DENIED} — domain authorisation or contract
 *       guard rail failed; {@link #failureReason()} carries
 *       the canonical {@link AlbumFailureReason}.</li>
 *   <li>{@code SOFT_DELETED} — caller requested
 *       {@code SOFT_DELETED} lifecycle and the operation
 *       succeeded.</li>
 *   <li>{@code PURGED} — caller requested {@code PURGED}
 *       lifecycle and the operation succeeded.</li>
 * </ul>
 */
public record AlbumOperationDecision(
        String albumId,
        AlbumOperationOutcome outcome,
        AlbumFailureReason failureReason,
        Long newAlbumVersion,
        String etag,
        Map<AlbumFailureReason, String> reasonFacts,
        String summary) {

    public AlbumOperationDecision {
        Objects.requireNonNull(albumId, "albumId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reasonFacts, "reasonFacts");
        reasonFacts = Map.copyOf(reasonFacts);
        if (outcome == AlbumOperationOutcome.ALLOWED
                || outcome == AlbumOperationOutcome.SOFT_DELETED) {
            if (newAlbumVersion == null || etag == null) {
                throw new IllegalArgumentException(
                        "ALLOWED/SOFT_DELETED outcome requires newAlbumVersion + etag");
            }
        }
        if (outcome == AlbumOperationOutcome.DENIED) {
            if (failureReason == null) {
                throw new IllegalArgumentException(
                        "DENIED outcome requires failureReason");
            }
        }
    }

    public static AlbumOperationDecision allowed(
            String albumId,
            long newAlbumVersion,
            String etag,
            String summary) {
        return new AlbumOperationDecision(
                albumId,
                AlbumOperationOutcome.ALLOWED,
                null,
                newAlbumVersion,
                etag,
                Map.of(),
                summary);
    }

    public static AlbumOperationDecision denied(
            String albumId,
            AlbumFailureReason reason,
            Map<AlbumFailureReason, String> facts,
            String summary) {
        if (reason == null) {
            throw new IllegalArgumentException(
                    "DENIED outcome requires failureReason");
        }
        Map<AlbumFailureReason, String> map = new LinkedHashMap<>();
        if (facts != null) {
            map.putAll(facts);
        }
        map.putIfAbsent(reason, summary);
        return new AlbumOperationDecision(
                albumId,
                AlbumOperationOutcome.DENIED,
                reason,
                null,
                null,
                map,
                summary);
    }

    public static AlbumOperationDecision softDeleted(
            String albumId,
            long newAlbumVersion,
            String etag,
            String summary) {
        return new AlbumOperationDecision(
                albumId,
                AlbumOperationOutcome.SOFT_DELETED,
                null,
                newAlbumVersion,
                etag,
                Map.of(),
                summary);
    }

    public static AlbumOperationDecision purged(
            String albumId,
            String summary) {
        return new AlbumOperationDecision(
                albumId,
                AlbumOperationOutcome.PURGED,
                null,
                null,
                null,
                Map.of(),
                summary);
    }
}