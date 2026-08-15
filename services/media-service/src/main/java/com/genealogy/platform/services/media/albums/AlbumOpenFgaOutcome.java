package com.genealogy.platform.services.media.albums;

/**
 * Closed-set OpenFGA verdict outcome (per ADR-E0.5-06).
 */
public enum AlbumOpenFgaOutcome {
    ALLOW,
    DENY;

    public String wire() {
        return name();
    }

    public static AlbumOpenFgaOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (AlbumOpenFgaOutcome v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown albumOpenFgaOutcome: " + wire);
    }
}