package com.genealogy.platform.services.media.albums;

/**
 * Closed-set outcome of the reconciliation worker for an
 * individual album item reference.
 */
public enum AlbumItemOutcome {
    HEALTHY,
    DANGLING_REFERENCES,
    REVOKED_REFERENCES,
    ORPHAN_ASSETS,
    QUOTA_EXCEEDED,
    PURGED,
    FAILED;

    public String wire() {
        return name();
    }

    public static AlbumItemOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (AlbumItemOutcome v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown albumItemOutcome: " + wire);
    }
}