package com.genealogy.platform.services.media.albums;

/**
 * Closed-set resolution outcome of an {@link AlbumReference}.
 *
 * <p>Mirrors {@code contracts/media/albums-linking-policy.yaml
 * ::spec.albumReferenceOutcomes} (E7.5). The outcome is set
 * by the reconciliation worker after re-resolving a reference
 * through the publishing service's Kafka event stream. A
 * {@code DANGLING} / {@code REVOKED} reference marks the
 * owning {@link AlbumItem} as such so the UI can show the
 * warning icon without leaking the resolved entity.
 */
public enum AlbumReferenceOutcome {
    RESOLVED,
    DANGLING,
    REVOKED,
    PUBLISHER_MISSING;

    public String wire() {
        return name();
    }

    public static AlbumReferenceOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (AlbumReferenceOutcome v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown albumReferenceOutcome: " + wire);
    }
}