package com.genealogy.platform.services.media.albums;

/**
 * Closed-set operation outcome of an
 * {@link AlbumCatalog} write / read attempt.
 */
public enum AlbumOperationOutcome {
    ALLOWED,
    DENIED,
    SOFT_DELETED,
    PURGED;

    public String wire() {
        return name();
    }

    public static AlbumOperationOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (AlbumOperationOutcome v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown albumOperationOutcome: " + wire);
    }
}