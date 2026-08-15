package com.genealogy.platform.services.media.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Request envelope sent to the Apache Tika worker. Mirrors
 * `contracts/media/malware-metadata-pipeline-policy.yaml
 * ::spec.pipelineActivityNames` (E7.2) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>{@code maxBytes} is the bound the worker must enforce
 * before starting Tika (the
 * `maxMetadataBytes=268435456` contract bound); Tika is
 * told to abort once the bound is reached. Going over the
 * bound transitions the pipeline to {@link PipelineStatus#FAILED}
 * with {@link PipelineFailureReason#EXTRACT_TIMEOUT}.
 */
public record MetadataExtractRequest(
        String pipelineId,
        MediaTenantScopedId assetId,
        String objectKey,
        long objectSizeBytes,
        long maxBytes,
        MetadataExtractEngine engine,
        Map<String, String> labels) {

    public static final int MAX_PIPELINE_ID_LENGTH = 128;
    public static final int MAX_OBJECT_KEY_LENGTH = 1024;
    public static final long MAX_OBJECT_SIZE_BYTES = 5497558138880L;
    public static final long MAX_EXTRACT_BYTES = 268435456L;
    public static final int MAX_LABELS = 16;
    public static final int MAX_LABEL_KEY_LENGTH = 64;
    public static final int MAX_LABEL_VALUE_LENGTH = 1024;

    public MetadataExtractRequest {
        Objects.requireNonNull(pipelineId, "pipelineId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(objectKey, "objectKey");
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
        if (maxBytes <= 0L || maxBytes > MAX_EXTRACT_BYTES) {
            throw new IllegalArgumentException(
                    "maxBytes out of bounds (1, "
                            + MAX_EXTRACT_BYTES + "]");
        }
        if (maxBytes > objectSizeBytes) {
            throw new IllegalArgumentException(
                    "maxBytes must be <= objectSizeBytes");
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