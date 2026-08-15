package com.genealogy.platform.services.media.domain;

import java.util.List;
import java.util.Objects;

/**
 * Result of the ClamAV scan activity. Mirrors
 * `contracts/media/malware-metadata-pipeline-policy.yaml
 * ::spec.malwareScanOutcomes + sandboxModes` (E7.2) +
 * `requirements.md` R9.2 + `design.md` §11.
 *
 * <p>{@code signatureStatus} tracks the freshness of the
 * ClamAV signature database at the moment the scan
 * completed; {@link SignatureStatus#STALE} +
 * {@link SignatureStatus#UNKNOWN} force the worker to
 * schedule {@code MALWARE_SIGNATURE_UPDATE} before any
 * subsequent scan per the
 * `signatureStaleFailsScan=true` guard rail.
 */
public record MediaScanResult(
        String pipelineId,
        MalwareScanOutcome outcome,
        MalwareScanEngine engine,
        SignatureStatus signatureStatus,
        String signatureVersion,
        List<String> threats,
        String sandboxDigest,
        long scannedBytes) {

    public static final int MAX_PIPELINE_ID_LENGTH = 128;
    public static final int MAX_SIGNATURE_VERSION_LENGTH = 64;
    public static final int MAX_THREATS = 64;
    public static final int MAX_THREAT_LENGTH = 256;
    public static final int MAX_SANDBOX_DIGEST_LENGTH = 128;
    public static final long MAX_SCANNED_BYTES = 5497558138880L;

    public MediaScanResult {
        Objects.requireNonNull(pipelineId, "pipelineId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(signatureStatus, "signatureStatus");
        Objects.requireNonNull(signatureVersion, "signatureVersion");
        Objects.requireNonNull(sandboxDigest, "sandboxDigest");
        if (pipelineId.isBlank() || pipelineId.length() > MAX_PIPELINE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "pipelineId length out of bounds [1, "
                            + MAX_PIPELINE_ID_LENGTH + "]");
        }
        if (signatureVersion.length() > MAX_SIGNATURE_VERSION_LENGTH) {
            throw new IllegalArgumentException(
                    "signatureVersion exceeds "
                            + MAX_SIGNATURE_VERSION_LENGTH + " characters");
        }
        if (sandboxDigest.length() > MAX_SANDBOX_DIGEST_LENGTH) {
            throw new IllegalArgumentException(
                    "sandboxDigest exceeds "
                            + MAX_SANDBOX_DIGEST_LENGTH + " characters");
        }
        if (scannedBytes < 0L || scannedBytes > MAX_SCANNED_BYTES) {
            throw new IllegalArgumentException(
                    "scannedBytes out of bounds [0, "
                            + MAX_SCANNED_BYTES + "]");
        }
        List<String> safeThreats = threats == null
                ? List.of()
                : List.copyOf(threats);
        if (safeThreats.size() > MAX_THREATS) {
            throw new IllegalArgumentException(
                    "threats exceeds " + MAX_THREATS + " entries");
        }
        for (String t : safeThreats) {
            if (t == null || t.isBlank()
                    || t.length() > MAX_THREAT_LENGTH) {
                throw new IllegalArgumentException(
                        "threat entry out of bounds");
            }
        }
        threats = safeThreats;
    }

    /**
     * Convenience constructor for short-lived tests.
     */
    public static MediaScanResult clean(
            String pipelineId,
            MalwareScanEngine engine,
            SignatureStatus signatureStatus,
            String signatureVersion,
            long scannedBytes) {
        return new MediaScanResult(
                pipelineId,
                MalwareScanOutcome.CLEAN,
                engine,
                signatureStatus,
                signatureVersion,
                List.of(),
                "sandbox-digest-v1",
                scannedBytes);
    }
}