package com.genealogy.platform.services.media.processing;

/**
 * Closed-set enumeration of image transcode presets. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.imagePresets` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>{@link #ORIGINAL} is a passthrough that copies the
 * input to the derived bucket without re-encoding (used by
 * the search index when the upload is already a JPEG /
 * WebP / AVIF).
 */
public enum ImagePreset {
    THUMBNAIL_128,
    THUMBNAIL_256,
    PREVIEW_1024,
    PREVIEW_2048,
    ORIGINAL;

    public static ImagePreset fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return ImagePreset.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown ImagePreset from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}