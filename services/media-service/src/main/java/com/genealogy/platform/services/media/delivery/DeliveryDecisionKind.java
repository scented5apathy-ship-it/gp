package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of delivery authorization
 * decisions. Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryDecisions` (E7.4) +
 * `requirements.md` R9.5 + `design.md` §12.
 *
 * <p>{@link #ALLOW} is a clean delivery with no
 * obligation; {@link #ALLOW_WATERMARKED} is a delivery that
 * MUST carry a watermark overlay (living / minor subjects
 * or jurisdictional restriction); {@link #ALLOW_RANGE_ONLY}
 * is a partial-byte delivery with bounded range;
 * {@link #DENY} is the catch-all refuse; {@link #REDACT}
 * forces a {@code REDACTED_PLACEHOLDER} disposition instead
 * of the actual artefact.
 */
public enum DeliveryDecisionKind {
    ALLOW,
    ALLOW_WATERMARKED,
    ALLOW_RANGE_ONLY,
    DENY,
    REDACT;

    public static DeliveryDecisionKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliveryDecisionKind.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliveryDecisionKind from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}