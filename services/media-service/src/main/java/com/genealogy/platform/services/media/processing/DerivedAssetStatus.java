package com.genealogy.platform.services.media.processing;

/**
 * Closed-set enumeration of derived asset statuses. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.derivedAssetStatuses` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>Per the E7.3 invariant "Chỉ asset `DERIVED_READY` mới
 * được liên kết vào E7.4 protected-delivery",
 * {@link #DERIVED_READY} is the only value that may
 * transition out of the processing pipeline into the E7.4
 * protected-delivery slot. {@link #FAILED} and
 * {@link #QUARANTINED_RETAIN} are terminal.
 */
public enum DerivedAssetStatus {
    PENDING,
    PROCESSING,
    VALIDATING,
    DERIVED_READY,
    FAILED,
    QUARANTINED_RETAIN;

    public static DerivedAssetStatus fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return DerivedAssetStatus.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown DerivedAssetStatus from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}