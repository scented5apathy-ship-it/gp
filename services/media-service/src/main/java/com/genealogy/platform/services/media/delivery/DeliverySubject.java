package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of delivery request subjects.
 * Mirrors `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliverySubjects` (E7.4) +
 * `requirements.md` R9.5 + `design.md` §12.
 *
 * <p>The protected-delivery contract only accepts these six
 * subjects; {@link #DOWNLOAD} is the canonical full-object
 * download, {@link #THUMBNAIL} / {@link #PREVIEW} are
 * linkable derived artefacts from E7.3,
 * {@link #OCR_TEXT} is the Tesseract fulltext,
 * {@link #RANGE_PART} is a partial-byte request, and
 * {@link #METADATA} is the sidecar metadata probe.
 */
public enum DeliverySubject {
    DOWNLOAD,
    THUMBNAIL,
    PREVIEW,
    OCR_TEXT,
    RANGE_PART,
    METADATA;

    public static DeliverySubject fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliverySubject.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliverySubject from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}