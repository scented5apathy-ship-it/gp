package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of delivery visibility scopes.
 * Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryVisibilityScopes` (E7.4) +
 * `requirements.md` R3 + R9.5 + `design.md` §6.3.
 *
 * <p>{@link #PRIVATE} is invitation-only;
 * {@link #UNLISTED} is token-gated + noindex;
 * {@link #PUBLIC} is redaction-aware search-indexed;
 * {@link #INTERNAL_TENANT} is shared within a tenant
 * without a public projection.
 */
public enum DeliveryVisibilityScope {
    PRIVATE,
    UNLISTED,
    PUBLIC,
    INTERNAL_TENANT;

    public static DeliveryVisibilityScope fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliveryVisibilityScope.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliveryVisibilityScope from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}