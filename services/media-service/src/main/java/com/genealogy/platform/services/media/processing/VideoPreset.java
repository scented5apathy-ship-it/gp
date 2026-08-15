package com.genealogy.platform.services.media.processing;

/**
 * Closed-set enumeration of video transcode presets. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.videoPresets` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>{@link #AUDIO_ONLY} extracts the audio track only
 * (used by the genealogy audio archive). VIDEO_4K is the
 * highest preset; the linter enforces the
 * {@code videoMaxBitrateKbps=20000} ceiling so 4K output
 * is capped at 20 Mbps.
 */
public enum VideoPreset {
    AUDIO_ONLY,
    VIDEO_360P,
    VIDEO_720P,
    VIDEO_1080P,
    VIDEO_4K;

    public static VideoPreset fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return VideoPreset.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown VideoPreset from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}