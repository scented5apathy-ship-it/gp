package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of range request units. Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryRangeUnit` (E7.4) + `requirements.md`
 * R9.5 + `design.md` §12.
 *
 * <p>{@link #BYTES} is the canonical HTTP
 * {@code Range: bytes=start-end} unit;
 * {@link #NONE} is the no-range baseline.
 */
public enum DeliveryRangeUnit {
    BYTES,
    NONE;

    public static DeliveryRangeUnit fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliveryRangeUnit.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliveryRangeUnit from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}