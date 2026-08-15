package com.genealogy.platform.services.media.albums;

/**
 * Closed-set ordering applied to an album's item list.
 *
 * <p>Mirrors {@code contracts/media/albums-linking-policy.yaml
 * ::spec.albumSortOrders} (E7.5). The sort order is persisted
 * per-album; a {@code MANUAL_PIN} order carries an explicit
 * {@code position} integer on each {@link AlbumItem} slot,
 * while the other orders are derived from the underlying
 * {@code capturedAt} / {@code title} / {@code addedAt}
 * timestamp.
 */
public enum AlbumSortOrder {
    MANUAL_PIN,
    CAPTURED_AT_ASC,
    CAPTURED_AT_DESC,
    TITLE_ASC,
    ADDED_AT_ASC,
    ADDED_AT_DESC;

    public String wire() {
        return name();
    }

    public static AlbumSortOrder fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (AlbumSortOrder v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown albumSortOrder: " + wire);
    }
}