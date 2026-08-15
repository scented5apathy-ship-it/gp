package com.genealogy.platform.services.media.albums;

/**
 * Closed-set album visibility scope.
 *
 * <p>Mirrors {@code contracts/media/albums-linking-policy.yaml
 * ::spec.albumVisibilities} (E7.5). The visibility scope is the
 * row-level authorisation classifier on the {@link AlbumRecord};
 * every item-level operation goes through the
 * {@link AlbumCatalog} orchestrator which checks the
 * album's lifecycle + visibility before delegating to the
 * OpenFGA port.
 *
 * <p>{@code LEGAL_HOLD} is a closed-set state that pins S3 / MinIO
 * Object Lock to {@code COMPLIANCE} mode for 30 days per
 * {@code spec.objectLockComplianceDays=30}; the
 * {@code objectLockComplianceRequiredForLegalHold=true} guard
 * rail refuses the visibility flip if the bucket does not
 * support compliance-mode object lock.
 */
public enum AlbumVisibility {
    PRIVATE,
    UNLISTED,
    PUBLIC,
    INTERNAL_TENANT,
    LEGAL_HOLD;

    public String wire() {
        return name();
    }

    public static AlbumVisibility fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (AlbumVisibility v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown albumVisibility: " + wire);
    }
}