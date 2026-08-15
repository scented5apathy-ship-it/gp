package com.genealogy.platform.services.media.albums;

/**
 * Closed-set audit event for the album aggregate.
 *
 * <p>Mirrors {@code contracts/media/albums-linking-policy.yaml
 * ::spec.albumAuditEvents} (E7.5). Every event maps to a
 * dedicated {@code auditActionOn*} pair on the contract; the
 * audit class is always {@code media}.
 */
public enum AlbumAuditEvent {
    ALBUM_CREATED,
    ALBUM_RENAMED,
    ALBUM_ITEM_ADDED,
    ALBUM_ITEM_REMOVED,
    ALBUM_ITEM_REORDERED,
    ALBUM_TAGS_SET,
    ALBUM_CAPTION_SET,
    ALBUM_REFERENCE_ADDED,
    ALBUM_REFERENCE_REMOVED,
    ALBUM_REFERENCE_RESOLVED,
    ALBUM_REFERENCE_DANGLING,
    ALBUM_REFERENCE_REVOKED,
    ALBUM_RECONCILIATION_RUN,
    ALBUM_RECONCILIATION_PURGED,
    ALBUM_SOFT_DELETED,
    ALBUM_PURGED,
    ALBUM_VISIBILITY_CHANGED,
    ALBUM_DNA_BUCKET_REFUSED;

    public String wire() {
        return name();
    }

    public static AlbumAuditEvent fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (AlbumAuditEvent v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown albumAuditEvent: " + wire);
    }
}