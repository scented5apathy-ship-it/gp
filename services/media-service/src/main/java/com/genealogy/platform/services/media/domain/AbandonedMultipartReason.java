package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of abandoned multipart reasons.
 * Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.abandonedMultipartReasons` (E7.1) + `design.md`
 * §8.2 (Dọn abandoned multipart bằng lifecycle / workflow).
 *
 * <p>The {@code AbandonedMultipartSweeper} reaps sessions
 * whose TTL has expired or whose finalize was never called
 * and emits the reason code to the audit log.
 */
public enum AbandonedMultipartReason {
    SESSION_TTL_EXPIRED,
    CALLER_ABORTED_FINALIZE,
    NO_PART_RECEIVED_IN_TTL,
    CHECKSUM_FINALIZE_TIMEOUT,
    QUOTA_REVOKED_MID_FLIGHT;

    public static AbandonedMultipartReason fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return AbandonedMultipartReason.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown AbandonedMultipartReason from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}
