package com.genealogy.platform.services.media.albums;

/**
 * Closed-set cross-service reference kind.
 *
 * <p>Mirrors {@code contracts/media/albums-linking-policy.yaml
 * ::spec.albumReferenceKinds} and
 * {@code spec.crossServiceReferenceKinds} (E7.5). The kind
 * is the only field that hints at the publishing service:
 * {@code PERSON} / {@code EVENT} come from
 * {@code gp.genealogy.v1.*}; {@code SOURCE} /
 * {@code CITATION} come from {@code gp.research.v1.*};
 * {@code PLACE} / {@code DATE} are opaque placeholders
 * stored as {@code place_pseudo_id} / {@code date_pseudo_id}
 * per {@code spec.placeReferenceFormat} +
 * {@code spec.dateReferenceFormat}.
 */
public enum AlbumReferenceKind {
    PERSON,
    EVENT,
    SOURCE,
    CITATION,
    PLACE,
    DATE;

    public String wire() {
        return name();
    }

    public static AlbumReferenceKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (AlbumReferenceKind v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown albumReferenceKind: " + wire);
    }
}