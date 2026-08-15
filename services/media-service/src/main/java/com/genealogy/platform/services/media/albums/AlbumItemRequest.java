package com.genealogy.platform.services.media.albums;

import java.util.List;
import java.util.Objects;

/**
 * Per-item request payload inside an
 * {@link AlbumOperationRequest}.
 *
 * <p>The compact constructor enforces:
 * <ul>
 *   <li>{@code itemId} blank / oversized check.</li>
 *   <li>{@code derivedObjectKey} length + DNA bucket shield
 *       pre-screen via {@link AlbumCatalog#isDnaBucketKey}.</li>
 *   <li>{@code references.size() <= MAX_REFERENCES_PER_ITEM}.</li>
 *   <li>{@code tags.size() <= MAX_TAGS_PER_ALBUM} + per-tag
 *       length cap.</li>
 *   <li>{@code captionBcp47Language} presence + length cap.</li>
 * </ul>
 */
public record AlbumItemRequest(
        String itemId,
        AlbumMemberKind kind,
        AlbumMemberSource source,
        String derivedObjectKey,
        boolean derivedReady,
        Integer position,
        List<AlbumReferenceRequest> references,
        List<String> tags,
        String caption,
        String captionBcp47Language) {

    public AlbumItemRequest {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(derivedObjectKey, "derivedObjectKey");
        if (itemId.isBlank()) {
            throw new IllegalArgumentException("itemId blank");
        }
        if (itemId.length() > AlbumCatalogLimits.ALBUM_ITEM_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "itemId too long: " + itemId.length());
        }
        if (derivedObjectKey.length()
                > AlbumCatalogLimits.ALBUM_OBJECT_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "derivedObjectKey too long: "
                            + derivedObjectKey.length());
        }
        if (caption != null
                && caption.length()
                        > AlbumCatalogLimits.MAX_CAPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "caption too long: " + caption.length());
        }
        if (caption != null && captionBcp47Language == null) {
            throw new IllegalArgumentException(
                    "captionBcp47Language missing for caption");
        }
        if (captionBcp47Language != null
                && captionBcp47Language.length()
                        > AlbumCatalogLimits.ALBUM_BCP47_TAG_LENGTH) {
            throw new IllegalArgumentException(
                    "captionBcp47Language too long: "
                            + captionBcp47Language.length());
        }
        references = references == null ? List.of() : List.copyOf(references);
        tags = tags == null ? List.of() : List.copyOf(tags);
        if (references.size()
                > AlbumCatalogLimits.MAX_REFERENCES_PER_ITEM) {
            throw new IllegalArgumentException(
                    "references too many per item: " + references.size());
        }
        if (tags.size() > AlbumCatalogLimits.MAX_TAGS_PER_ALBUM) {
            throw new IllegalArgumentException(
                    "tags too many per item: " + tags.size());
        }
        for (String tag : tags) {
            if (tag == null) {
                throw new IllegalArgumentException("tag null");
            }
            if (tag.length() > AlbumCatalogLimits.MAX_TAG_LENGTH) {
                throw new IllegalArgumentException(
                        "tag too long: " + tag.length());
            }
        }
        for (AlbumReferenceRequest r : references) {
            Objects.requireNonNull(r, "reference");
        }
    }
}