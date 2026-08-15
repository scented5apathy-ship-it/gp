package com.genealogy.platform.services.media.albums;

/**
 * Closed-set failure reason returned by the
 * {@link AlbumCatalog} orchestrator.
 *
 * <p>Mirrors {@code contracts/media/albums-linking-policy.yaml
 * ::spec.albumFailureReasons} (E7.5). The reason is the
 * single source of truth for the API Problem Details
 * {@code type} URI suffix; UI / BFF translate the wire
 * token to a localised message via
 * {@code i18n.album.failureReasons.*}.
 */
public enum AlbumFailureReason {
    ALBUM_NOT_FOUND,
    ALBUM_VERSION_MISMATCH,
    ALBUM_QUOTA_EXCEEDED,
    ALBUM_VISIBILITY_FORBIDDEN,
    ALBUM_ITEM_NOT_FOUND,
    ALBUM_REFERENCE_INVALID,
    ALBUM_REFERENCE_DANGLING,
    ALBUM_REFERENCE_REVOKED,
    ALBUM_REFERENCE_KIND_UNKNOWN,
    ALBUM_LIFECYCLE_FORBIDDEN,
    ALBUM_CAPTION_LANGUAGE_MISSING,
    ALBUM_TAG_TOO_LONG,
    ALBUM_CAPTION_TOO_LONG,
    ALBUM_TAG_TOO_MANY,
    ALBUM_REFERENCES_TOO_MANY,
    ALBUM_ITEMS_TOO_MANY,
    ALBUM_OBJECT_KEY_TOO_LONG,
    ALBUM_ACTOR_PSEUDO_ID_TOO_LONG,
    ALBUM_CORRELATION_ID_TOO_LONG,
    ALBUM_REFERENCE_PSEUDO_ID_TOO_LONG,
    ALBUM_DERIVED_OBJECT_KEY_NOT_READY,
    ALBUM_DNA_BUCKET_FORBIDDEN,
    ALBUM_RECONCILIATION_FAILED;

    public String wire() {
        return name();
    }

    public static AlbumFailureReason fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (AlbumFailureReason v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown albumFailureReason: " + wire);
    }
}