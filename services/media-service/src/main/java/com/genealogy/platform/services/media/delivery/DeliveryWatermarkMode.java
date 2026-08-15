package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of delivery watermark modes.
 * Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryWatermarkModes` (E7.4) +
 * `requirements.md` R9.5 + `design.md` §12.
 *
 * <p>{@link #NONE} is the no-watermark baseline (the
 * default for HISTORICAL subjects in PRIVATE /
 * INTERNAL_TENANT scopes); {@link #TEXT_OVERLAY} draws
 * the actor's {@code actorPseudoId} + request timestamp
 * as a single line in the bottom-right corner;
 * {@link #DIAGONAL_REPEAT} tiles the same overlay across
 * the artefact body for high-risk subjects;
 * {@link #VISIBLE_DOI} draws a digital-object-identifier
 * barcode overlay for documents / PDFs.
 */
public enum DeliveryWatermarkMode {
    NONE,
    TEXT_OVERLAY,
    DIAGONAL_REPEAT,
    VISIBLE_DOI;

    public static DeliveryWatermarkMode fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliveryWatermarkMode.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliveryWatermarkMode from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}