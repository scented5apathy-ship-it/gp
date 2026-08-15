package com.genealogy.platform.services.media.delivery;

/**
 * Closed-set enumeration of revocation event sources that
 * MUST propagate to in-flight signed URLs within
 * {@code revokePropagationSeconds=60}. Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliveryRevocationSources` (E7.4) +
 * `requirements.md` R9.5 + R13 + `design.md` §12 +
 * ADR-E0.5-15 (consent revocation propagation).
 *
 * <p>The media-service consumes
 * {@code gp.tenant.v1.MembershipRevoked} +
 * {@code gp.tenant.v1.TenantDeleted}; the ABAC overlay
 * also feeds {@code CONSENT_REVOKED} +
 * {@code POLICY_VERSION_BUMPED} from the
 * consent-ledger / dna-service event streams.
 */
public enum DeliveryRevocationSource {
    MEMBERSHIP_REVOKED,
    TENANT_DELETED,
    CONSENT_REVOKED,
    POLICY_VERSION_BUMPED;

    public static DeliveryRevocationSource fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DeliveryRevocationSource.valueOf(
                    wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DeliveryRevocationSource from wire: "
                            + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}