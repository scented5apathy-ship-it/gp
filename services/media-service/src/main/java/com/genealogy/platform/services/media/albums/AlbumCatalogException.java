package com.genealogy.platform.services.media.albums;

import java.util.Map;

/**
 * Thrown when {@link AlbumCatalog#apply} refuses an
 * operation. Carries the canonical
 * {@link AlbumFailureReason} + an immutable {@code facts}
 * map so the API Problem Details response can populate the
 * {@code type} URI + the {@code properties} map without
 * leaking any raw payload.
 */
public class AlbumCatalogException extends RuntimeException {

    private final AlbumFailureReason failureReason;
    private final Map<String, String> facts;

    public AlbumCatalogException(
            AlbumFailureReason failureReason,
            String summary,
            Map<String, String> facts) {
        super(summary);
        this.failureReason = failureReason;
        this.facts = facts == null ? Map.of() : Map.copyOf(facts);
    }

    public AlbumFailureReason failureReason() {
        return failureReason;
    }

    public Map<String, String> facts() {
        return facts;
    }
}