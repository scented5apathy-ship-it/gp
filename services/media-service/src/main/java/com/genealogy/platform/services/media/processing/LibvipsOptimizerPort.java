package com.genealogy.platform.services.media.processing;

/**
 * Port for the libvips image-optimizer adapter. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingEngines` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>The port is the seam the Temporal worker uses to call
 * libvips. The implementation lives in the worker
 * subproject (E7.x / E11.x); this contract is the pure
 * interface. Calling
 * {@link #transcode(ImageTranscodeRequest) transcode} on a
 * {@link ProcessingEngine#FALLBACK_NONE} request MUST return
 * {@link ProcessingOutcome#PROCESS_ERROR} per
 * {@code imageMagickFallbackPolicy=NEVER} +
 * {@code libvipsOnlyForImageTranscode=true}.
 */
public interface LibvipsOptimizerPort {

    /**
     * Synchronous image transcode. The adapter MUST enforce
     * the declared {@code objectSizeBytes} bound + the
     * {@code preset.longestEdgePx} range + the
     * {@code format} closed-set, and strip EXIF GPS
     * coordinates + camera serial numbers from the derived
     * artefact.
     *
     * @param request transcode envelope (immutable, validated)
     * @return transcode result; never {@code null}
     */
    ImageTranscodeResult transcode(ImageTranscodeRequest request);
}