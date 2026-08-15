package com.genealogy.platform.services.media.albums;

/**
 * Closed-set album lifecycle state.
 *
 * <p>Mirrors {@code contracts/media/albums-linking-policy.yaml
 * ::spec.albumLifecycleStates} (E7.5). The lifecycle state is
 * the only row-level transition source of truth: a soft-deleted
 * album is retained for {@code spec.softDeleteRetentionDays=365}
 * days before the object garbage collector purges its
 * derived objects.
 *
 * <p>{@code FAILED} is reserved for the reconciliation worker
 * to flag an album whose references could not be resolved; the
 * state is terminal until a manual operator intervenes via the
 * Operations UI.
 */
public enum AlbumLifecycleState {
    ACTIVE,
    SOFT_DELETED,
    LEGAL_HOLD,
    PURGED,
    FAILED;

    public String wire() {
        return name();
    }

    public static AlbumLifecycleState fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (AlbumLifecycleState v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown albumLifecycleState: " + wire);
    }
}