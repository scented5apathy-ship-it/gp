package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of content disposition modes.
 * Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryDispositions` (E7.4) +
 * `design.md` §12 ("content disposition an toàn").
 *
 * <p>{@link #INLINE} renders the artefact in the browser;
 * {@link #ATTACHMENT} forces download with the supplied
 * filename; {@link #REDACTED_PLACEHOLDER} replaces the
 * artefact body with a redaction placeholder PNG + a
 * 410 Gone explanation.
 */
public enum DeliveryDisposition {
    INLINE,
    ATTACHMENT,
    REDACTED_PLACEHOLDER;

    public static DeliveryDisposition fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliveryDisposition.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliveryDisposition from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}