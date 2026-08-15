package com.genealogy.platform.services.media.processing;

/**
 * Closed-set enumeration of derived asset formats. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.derivedAssetFormats` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>Thumbnails (THUMBNAIL_WEBP / THUMBNAIL_AVIF /
 * THUMBNAIL_JPEG) come from {@link ProcessingTask#IMAGE_TRANSCODE}
 * (libvips). PDF_PREVIEW comes from
 * {@link ProcessingTask#DOCUMENT_RENDER} (Gotenberg).
 * VIDEO_360P / VIDEO_720P / VIDEO_1080P come from
 * {@link ProcessingTask#VIDEO_TRANSCODE} (FFmpeg).
 * OCR_TEXT comes from {@link ProcessingTask#TEXT_OCR}
 * (Tesseract).
 */
public enum DerivedAssetFormat {
    THUMBNAIL_WEBP,
    THUMBNAIL_AVIF,
    THUMBNAIL_JPEG,
    PDF_PREVIEW,
    VIDEO_360P,
    VIDEO_720P,
    VIDEO_1080P,
    OCR_TEXT;

    public static DerivedAssetFormat fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DerivedAssetFormat.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DerivedAssetFormat from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}