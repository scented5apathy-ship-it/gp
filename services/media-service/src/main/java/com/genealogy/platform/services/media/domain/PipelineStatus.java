package com.genealogy.platform.services.media.domain;

/**
 * Closed-set enumeration of pipeline statuses. Mirrors
 * `contracts/media/malware-metadata-pipeline-policy.yaml
 * ::spec.pipelineStatuses + pipelineStatusMatrix +
 * linkablePipelineStatuses` (E7.2) +
 * `requirements.md` R9.2 + `design.md` §8.2 / §11.
 *
 * <p>Per the E7.2 invariant "Chỉ asset {@code READY} mới
 * được liên kết", {@link #READY} is the only value that may
 * transition out of the pipeline into a linkable state. The
 * other non-terminal statuses describe the work in flight;
 * {@link #FAILED} and {@link #QUARANTINED_RETAIN} are
 * terminal.
 */
public enum PipelineStatus {
    PENDING,
    SCANNING,
    EXTRACTING,
    METADATA_READY,
    READY,
    FAILED,
    QUARANTINED_RETAIN;

    public static PipelineStatus fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return PipelineStatus.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown PipelineStatus from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}