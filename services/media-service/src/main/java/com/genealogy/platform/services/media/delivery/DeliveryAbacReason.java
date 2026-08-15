package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of ABAC overlay reasons.
 * Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryAbacReasons` (E7.4) +
 * `requirements.md` R9.5 + R13 + `design.md` §6.2 + §12.
 *
 * <p>{@link #LIVING_MINOR_REDACT} forces a watermark
 * overlay; {@link #LIVING_RESTRICTED} forces a redaction
 * placeholder when the subject is living + restricted;
 * {@link #DNA_BUCKET_DENIED} is the closed-set bucket
 * shield (refuses any object key under {@code dna/},
 * {@code dna/match/}, {@code dna/consent/});
 * {@link #CONSENT_PURPOSE_MISSING} forces DENY when the
 * delivery scope exceeds the consented purpose;
 * {@link #JURISDICTION_BLOCKED} refuses delivery to a
 * residency that is not in the consent allowlist;
 * {@link #SCOPE_REVOKED} marks a previously-allowed scope
 * as revoked since the last issuance.
 */
public enum DeliveryAbacReason {
    LIVING_MINOR_REDACT,
    LIVING_RESTRICTED,
    DNA_BUCKET_DENIED,
    CONSENT_PURPOSE_MISSING,
    JURISDICTION_BLOCKED,
    SCOPE_REVOKED;

    public static DeliveryAbacReason fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliveryAbacReason.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliveryAbacReason from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}