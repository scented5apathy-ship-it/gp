package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of upload guard deny reasons.
 * Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.uploadGuardDenyReasons` (E7.1) + `requirements.md`
 * R9.2 + `design.md` §8.2.
 *
 * <p>The constructor of {@code UploadSession} refuses to
 * advance to {@code SIGNED} / {@code UPLOADING} unless every
 * guard passes; the {@code UploadGuard} executor maps the
 * failure mode to the closed-set reason code.
 */
public enum UploadGuardDenyReason {
    MIME_NOT_PERMITTED,
    CHECKSUM_MISMATCH,
    DECLARED_SIZE_MISMATCH,
    MULTIPART_PART_NUMBER_INVALID,
    MULTIPART_PART_COUNT_OVERFLOW,
    MULTIPART_PART_SEQUENCE_GAP,
    SESSION_NOT_OWNED_BY_CALLER,
    SESSION_ABANDONED,
    SESSION_ALREADY_FINALIZED,
    RATE_LIMITED,
    PAYLOAD_DNA_BUCKET_FORBIDDEN;

    public static UploadGuardDenyReason fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return UploadGuardDenyReason.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown UploadGuardDenyReason from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}
