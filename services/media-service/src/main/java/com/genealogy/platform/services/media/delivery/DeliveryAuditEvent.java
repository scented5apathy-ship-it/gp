package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of delivery audit event types.
 * Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryAuditEvents` (E7.4) +
 * `requirements.md` R9.5 + R16 + NFR5 + `design.md` §13.
 *
 * <p>Every {@link #DELIVERY_GRANTED} /
 * {@link #DELIVERY_WATERMARKED} /
 * {@link #DELIVERY_RANGE_SERVED} /
 * {@link #DELIVERY_DENIED} / {@link #DELIVERY_REVOKED}
 * event carries the actor's {@code actorPseudoId} +
 * {@code correlationId}; raw user id, email, IP and DNA
 * are NEVER carried per the
 * {@code DELIVERY_PSEUDONYM_IN_AUDIT} invariant.
 */
public enum DeliveryAuditEvent {
    DELIVERY_GRANTED,
    DELIVERY_WATERMARKED,
    DELIVERY_DENIED,
    DELIVERY_REVOKED,
    DELIVERY_RANGE_SERVED;

    public static DeliveryAuditEvent fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliveryAuditEvent.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliveryAuditEvent from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}