package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of quota denial reasons. Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.quotaDenialReasons` (E7.1) + `requirements.md`
 * R9.2 + `design.md` §8.2 (BFF yêu cầu media service tạo
 * upload session sau quota/permission check).
 */
public enum QuotaDenialReason {
    QUOTA_EXCEEDED_BYTES,
    QUOTA_EXCEEDED_COUNT,
    QUOTA_EXCEEDED_SESSION_TTL,
    QUOTA_SCOPE_NOT_PERMITTED,
    QUOTA_TENANT_HEADROOM_INSUFFICIENT;

    public static QuotaDenialReason fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return QuotaDenialReason.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown QuotaDenialReason from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}
