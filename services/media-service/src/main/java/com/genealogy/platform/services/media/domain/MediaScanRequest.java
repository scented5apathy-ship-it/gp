package com.genealogy.platform.services.media.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Request envelope sent to the ClamAV worker. Mirrors
 * `contracts/media/malware-metadata-pipeline-policy.yaml
 * ::spec.pipelineActivityNames` (E7.2) +
 * `requirements.md` R9.2 + `design.md` §11.
 *
 * <p>The {@code pipelineId} is the workflow-scoped
 * idempotency key (see
 * `pipelineIdempotentOnPipelineId=true` guard rail). The
 * {@code expectedSha256} is the declared checksum captured at
 * finalize time and re-verified before the scan activity
 * starts (`integrityChecksumRequiredBeforeScan=true`).
 */
public record MediaScanRequest(
        String pipelineId,
        MediaTenantScopedId assetId,
        String objectKey,
        long objectSizeBytes,
        String expectedSha256,
        ChecksumAlgorithm checksumAlgorithm,
        MalwareScanEngine engine,
        Map<String, String> labels) {

    public static final int MAX_PIPELINE_ID_LENGTH = 128;
    public static final int MAX_OBJECT_KEY_LENGTH = 1024;
    public static final int MAX_CHECKSUM_LENGTH = 256;
    public static final long MAX_OBJECT_SIZE_BYTES = 5497558138880L;
    public static final int MAX_LABELS = 16;
    public static final int MAX_LABEL_KEY_LENGTH = 64;
    public static final int MAX_LABEL_VALUE_LENGTH = 1024;

    public MediaScanRequest {
        Objects.requireNonNull(pipelineId, "pipelineId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(objectKey, "objectKey");
        Objects.requireNonNull(expectedSha256, "expectedSha256");
        Objects.requireNonNull(checksumAlgorithm, "checksumAlgorithm");
        Objects.requireNonNull(engine, "engine");
        if (pipelineId.isBlank() || pipelineId.length() > MAX_PIPELINE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "pipelineId length out of bounds [1, "
                            + MAX_PIPELINE_ID_LENGTH + "]");
        }
        if (objectKey.isBlank() || objectKey.length() > MAX_OBJECT_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "objectKey length out of bounds [1, "
                            + MAX_OBJECT_KEY_LENGTH + "]");
        }
        if (objectSizeBytes <= 0L || objectSizeBytes > MAX_OBJECT_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "objectSizeBytes out of bounds (1, "
                            + MAX_OBJECT_SIZE_BYTES + "]");
        }
        if (expectedSha256.isBlank()
                || expectedSha256.length() > MAX_CHECKSUM_LENGTH) {
            throw new IllegalArgumentException(
                    "expectedSha256 length out of bounds");
        }
        Map<String, String> safeLabels = labels == null
                ? Map.of()
                : Map.copyOf(labels);
        if (safeLabels.size() > MAX_LABELS) {
            throw new IllegalArgumentException(
                    "labels exceeds " + MAX_LABELS + " entries");
        }
        for (Map.Entry<String, String> e : safeLabels.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k == null || k.isBlank()
                    || k.length() > MAX_LABEL_KEY_LENGTH) {
                throw new IllegalArgumentException(
                        "label key out of bounds");
            }
            if (v != null && v.length() > MAX_LABEL_VALUE_LENGTH) {
                throw new IllegalArgumentException(
                        "label value exceeds "
                                + MAX_LABEL_VALUE_LENGTH
                                + " characters for key " + k);
            }
        }
        labels = safeLabels;
    }
}