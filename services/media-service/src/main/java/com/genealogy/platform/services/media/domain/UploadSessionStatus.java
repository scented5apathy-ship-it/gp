package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of {@code UploadSession} states.
 * Mirrors
 * `contracts/media/upload-lifecycle-policy.yaml
 * ::spec.uploadSessionStatuses` (E7.1),
 * `requirements.md` R9.2 and `design.md` §8.2
 * (`REQUESTED -> SIGNED -> UPLOADING -> FINALIZING ->
 * QUARANTINED -> READY`).
 *
 * <p>Adding a new state requires an ADR supersession and an
 * update to the YAML contract. The
 * {@code lint-media-upload-lifecycle} script enforces the
 * closed-set.
 */
public enum UploadSessionStatus {
    REQUESTED,
    SIGNED,
    UPLOADING,
    FINALIZING,
    QUARANTINED,
    READY,
    REJECTED,
    ABANDONED,
    FAILED;

    public static UploadSessionStatus fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return UploadSessionStatus.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown UploadSessionStatus from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}
