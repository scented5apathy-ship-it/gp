package com.genealogy.platform.services.media.albums;

/**
 * Cross-service reference inside an {@link AlbumItemRequest}.
 *
 * <p>Only opaque ids live here — the publishing service owns
 * the truth and the reconciliation worker re-resolves the
 * reference through the publishing service's Kafka event
 * stream per
 * {@code spec.crossServiceReferencesResolveThrough=
 * [gp.genealogy.v1, gp.research.v1]}.
 */
public record AlbumReferenceRequest(
        AlbumReferenceKind kind,
        String referencePseudoId,
        String publisherScope,
        AlbumReferenceOutcome outcome) {

    public AlbumReferenceRequest {
        if (kind == null) {
            throw new IllegalArgumentException("kind is null");
        }
        if (referencePseudoId == null) {
            throw new IllegalArgumentException("referencePseudoId is null");
        }
        if (outcome == null) {
            throw new IllegalArgumentException("outcome is null");
        }
        if (referencePseudoId.isBlank()) {
            throw new IllegalArgumentException("referencePseudoId blank");
        }
        if (referencePseudoId.length()
                > AlbumCatalogLimits.ALBUM_REFERENCE_PSEUDO_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "referencePseudoId too long: "
                            + referencePseudoId.length());
        }
    }
}