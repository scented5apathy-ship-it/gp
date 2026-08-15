package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of delivery content types.
 * Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryContentTypes` (E7.4) +
 * `requirements.md` R9.5.
 *
 * <p>The seven values cover the canonical derived
 * artefacts the E7.4 worker hands out:
 * <ul>
 *   <li>{@link #IMAGE_WEBP} / {@link #IMAGE_AVIF} /
 *       {@link #IMAGE_JPEG} — E7.3 image transcode.</li>
 *   <li>{@link #APPLICATION_PDF} — E7.3 Gotenberg PDF
 *       preview.</li>
 *   <li>{@link #VIDEO_MP4} — E7.3 FFmpeg video transcode.</li>
 *   <li>{@link #TEXT_PLAIN} — E7.3 Tesseract OCR
 *       fulltext.</li>
 *   <li>{@link #APPLICATION_OCTET_STREAM} — opaque
 *       download of a non-derived artefact (capped at the
 *       E7.3 byte ceiling).</li>
 * </ul>
 */
public enum DeliveryContentType {
    IMAGE_WEBP,
    IMAGE_AVIF,
    IMAGE_JPEG,
    APPLICATION_PDF,
    VIDEO_MP4,
    TEXT_PLAIN,
    APPLICATION_OCTET_STREAM;

    public static DeliveryContentType fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliveryContentType.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliveryContentType from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}