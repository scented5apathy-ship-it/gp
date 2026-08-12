package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of quota units. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.quotaUnits` (E7.1).
 */
public enum QuotaUnit {
    BYTES,
    ITEMS,
    SECONDS;

    public static QuotaUnit fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return QuotaUnit.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown QuotaUnit from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}
