package com.genealogy.platform.services.media.domain;

/**
 * Port for the ClamAV scanner adapter. Mirrors
 * `contracts/media/malware-metadata-pipeline-policy.yaml
 * ::spec.malwareScanEngines` (E7.2) +
 * `design.md` §11 (ClamAV quét malware trong quarantine
 * network policy; binary parser không truy cập Internet,
 * chạy non-root, read-only filesystem và resource quota).
 *
 * <p>The port is the seam the Temporal worker uses to call
 * ClamAV. The implementation lives in the worker
 * subproject (E7.x / E11.x); this contract is the pure
 * interface. Calling
 * {@link #scan(MediaScanRequest) scan} on a
 * {@link MalwareScanEngine#FALLBACK_NONE} request MUST
 * return {@link MalwareScanOutcome#SCAN_ERROR} per
 * `scannerFailureRetainsInQuarantine=true`.
 */
public interface ClamAvScannerPort {

    /**
     * Synchronous scan. The adapter MUST enforce the
     * declared {@code objectSizeBytes} bound and stream
     * the object via {@code INSTREAM} for objects larger
     * than {@code maxInlineScanBytes=536870912} per the
     * `pipelineObjectTooLargeChunkedScan` invariant.
     *
     * @param request scan envelope (immutable, validated)
     * @return scan result; never {@code null}
     */
    MediaScanResult scan(MediaScanRequest request);
}