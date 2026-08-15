package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of delivery failure reasons.
 * Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryFailureReasons` (E7.4) +
 * `requirements.md` R9.5 + `design.md` §12.
 *
 * <p>{@link #OPENFGA_DENY} is the relationship-graph
 * reject; {@link #ABAC_DENY} is the attribute-based
 * overlay reject (living / minor / DNA-adjacent);
 * {@link #CONSENT_REVOKED} /
 * {@link #MEMBERSHIP_REVOKED} /
 * {@link #TENANT_DELETED} are the revocation-source
 * rejects; {@link #OBJECT_NOT_READY} is the upstream
 * E7.3 linkability reject; {@link #OBJECT_TAMPERED} is
 * the integrity-checksum mismatch reject;
 * {@link #TTL_EXPIRED} / {@link #SIGNATURE_INVALID} are
 * the post-issuance rejects when the requester tries to
 * use a revoked or tampered ticket.
 */
public enum DeliveryFailureReason {
    POLICY_DENIED,
    OPENFGA_DENY,
    ABAC_DENY,
    CONSENT_REVOKED,
    MEMBERSHIP_REVOKED,
    TENANT_DELETED,
    OBJECT_NOT_READY,
    OBJECT_TAMPERED,
    TTL_EXPIRED,
    SIGNATURE_INVALID;

    public static DeliveryFailureReason fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliveryFailureReason.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliveryFailureReason from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}