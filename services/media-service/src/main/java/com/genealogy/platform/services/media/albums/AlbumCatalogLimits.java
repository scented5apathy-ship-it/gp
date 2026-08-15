package com.genealogy.platform.services.media.albums;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Constant limits carried from
 * {@code contracts/media/albums-linking-policy.yaml::spec.*}.
 *
 * <p>The orchestrator and value-object compact constructors
 * use these constants; the linter enforces the same numbers
 * on the YAML contract so the two cannot drift.
 */
public final class AlbumCatalogLimits {

    public static final int MAX_ITEMS_PER_ALBUM = 4096;
    public static final int MAX_ALBUMS_PER_TENANT = 8192;
    public static final int MAX_ALBUMS_PER_USER = 512;
    public static final int MAX_REFERENCES_PER_ALBUM = 256;
    public static final int MAX_REFERENCES_PER_ITEM = 64;
    public static final int MAX_CAPTION_LENGTH = 4096;
    public static final int MAX_TAG_LENGTH = 64;
    public static final int MAX_TAGS_PER_ALBUM = 256;
    public static final int MAX_ALBUM_TITLE_LENGTH = 256;
    public static final int MAX_ALBUM_DESCRIPTION_LENGTH = 4096;
    public static final int SOFT_DELETE_RETENTION_DAYS = 365;
    public static final int OBJECT_LOCK_COMPLIANCE_DAYS = 30;
    public static final int RECONCILIATION_CADENCE_HOURS = 24;
    public static final int RECONCILIATION_BATCH_SIZE = 1024;
    public static final int RECONCILIATION_LOOKBACK_HOURS = 168;
    public static final int RECONCILIATION_P95_BUDGET_SECONDS = 150;
    public static final int RECONCILIATION_OUTBOX_BATCH_SIZE = 256;
    public static final int ALBUM_VERSION_FLOOR = 1;
    public static final int ALBUM_ID_LENGTH = 64;
    public static final int ALBUM_ITEM_ID_LENGTH = 64;
    public static final int ALBUM_REFERENCE_PSEUDO_ID_LENGTH = 64;
    public static final int ACTOR_PSEUDO_ID_LENGTH = 64;
    public static final int CORRELATION_ID_LENGTH = 128;
    public static final int ALBUM_OBJECT_KEY_LENGTH = 1024;
    public static final int ALBUM_BCP47_TAG_LENGTH = 64;
    public static final int ALBUM_ETAG_LENGTH = 128;
    public static final int ACTIVITY_HEARTBEAT_SECONDS = 30;
    public static final int ACTIVITY_HEARTBEAT_MULTIPLIER = 6;

    private AlbumCatalogLimits() {
    }
}