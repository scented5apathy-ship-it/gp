package com.genealogy.platform.services.media.processing;

/**
 * Port for the FFmpeg video-encoder adapter. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingEngines + videoPresets` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>The port is the seam the Temporal worker uses to call
 * FFmpeg. The implementation lives in the worker
 * subproject (E7.x / E11.x); this contract is the pure
 * interface. The worker MUST honour the
 * {@code videoMinBitrateKbps=200 / videoMaxBitrateKbps=20000}
 * bound and the deterministic + versioned output key
 * derivation.
 */
public interface FfmpegEncoderPort {

    /**
     * Synchronous video transcode. The adapter MUST
     * enforce the {@code preset} closed-set + the bitrate
     * ceiling + the deterministic + versioned output key
     * derivation.
     *
     * @param processingId workflow-scoped idempotency key
     * @param objectKey input S3 / MinIO object key
     * @param objectSizeBytes declared input size (bytes)
     * @param expectedSha256 E7.2-declared checksum
     * @param preset video preset (AUDIO_ONLY / VIDEO_360P /
     *              VIDEO_720P / VIDEO_1080P / VIDEO_4K)
     * @param format derived format (VIDEO_360P / VIDEO_720P
     *              / VIDEO_1080P)
     * @param engineVersion FFmpeg version baked into the
     *                       deterministic key
     * @return transcode result; never {@code null}
     */
    VideoTranscodeResult transcode(
            String processingId,
            String objectKey,
            long objectSizeBytes,
            String expectedSha256,
            VideoPreset preset,
            DerivedAssetFormat format,
            String engineVersion);
}