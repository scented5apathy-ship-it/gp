package com.genealogy.platform.services.media.domain;

/**
 * Port for the Apache Tika extractor adapter. Mirrors
 * `contracts/media/malware-metadata-pipeline-policy.yaml
 * ::spec.metadataExtractEngines` (E7.2) +
 * `design.md` §11 (Apache Tika trích metadata/text tài
 * liệu).
 *
 * <p>The port is the seam the Temporal worker uses to call
 * Tika. The implementation lives in the worker subproject
 * (E7.x / E11.x); this contract is the pure interface.
 * Calling {@link #extract(MetadataExtractRequest) extract}
 * on a {@link MetadataExtractEngine#FALLBACK_NONE} request
 * MUST return {@link MetadataExtractOutcome#EXTRACT_ERROR}
 * per `extractorFailureRetainsInQuarantine=true`.
 */
public interface TikaExtractorPort {

    /**
     * Synchronous extract. The adapter MUST enforce the
     * {@code maxBytes} bound; Tika is told to abort once
     * the bound is reached. Over-budget runs MUST return
     * {@link MetadataExtractOutcome#EXTRACT_TIMEOUT}.
     *
     * @param request extract envelope (immutable, validated)
     * @return extract result; never {@code null}
     */
    MetadataExtractResult extract(MetadataExtractRequest request);
}