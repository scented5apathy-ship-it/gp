package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of OpenFGA verdict outcomes
 * (port payload). Mirrors the E7.4 contract that OpenFGA
 * decides the relationship + ABAC decides the attribute
 * overlay.
 */
public enum DeliveryOpenFgaOutcome {
    ALLOW,
    DENY;

    public static DeliveryOpenFgaOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliveryOpenFgaOutcome.valueOf(
                    wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliveryOpenFgaOutcome from wire: "
                            + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}