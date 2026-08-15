package com.genealogy.platform.services.media.albums;

/**
 * Closed-set kind of an {@link AlbumItem} slot.
 *
 * <p>Mirrors {@code contracts/media/albums-linking-policy.yaml
 * ::spec.albumMemberKinds} (E7.5). The kind is the structural
 * classifier: an {@code ASSET} slot points to a {@code MediaAsset}
 * (or {@code MediaVariant}); a {@code FOLDER} slot groups items
 * by hand; a {@code COLLECTION} slot is a hand-curated pointer
 * to another album.
 */
public enum AlbumMemberKind {
    ASSET,
    VARIANT,
    FOLDER,
    COLLECTION;

    public String wire() {
        return name();
    }

    public static AlbumMemberKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (AlbumMemberKind v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown albumMemberKind: " + wire);
    }
}