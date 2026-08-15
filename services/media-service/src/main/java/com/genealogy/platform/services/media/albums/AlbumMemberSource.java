package com.genealogy.platform.services.media.albums;

/**
 * Closed-set provenance of an {@link AlbumItem} slot.
 *
 * <p>Mirrors {@code contracts/media/albums-linking-policy.yaml
 * ::spec.albumMemberSources} (E7.5). {@code DERIVATIVE},
 * {@code OCR}, {@code THUMBNAIL}, {@code PREVIEW} and
 * {@code TRANSCODE} are produced by the E7.3 processing
 * pipeline; {@code USER_UPLOAD} refers to the original
 * E7.1 upload.
 */
public enum AlbumMemberSource {
    USER_UPLOAD,
    DERIVATIVE,
    OCR,
    THUMBNAIL,
    PREVIEW,
    TRANSCODE;

    public String wire() {
        return name();
    }

    public static AlbumMemberSource fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (AlbumMemberSource v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown albumMemberSource: " + wire);
    }
}