package com.genealogy.platform.services.media.processing;

import java.util.Map;
import java.util.Objects;

/**
 * Request envelope sent to the libvips image-optimizer
 * worker. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingActivityNames + imagePresets` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>The {@code processingId} is the workflow-scoped
 * idempotency key (see
 * {@code processingIdempotentOnProcessingId=true} guard
 * rail). The {@code expectedSha256} is the E7.2-declared
 * checksum re-verified before the activity starts
 * (mirrors the E7.2
 * {@code integrityChecksumRequiredBeforeScan} invariant).
 * The {@code engineVersion} is the libvips version baked
 * into the deterministic + versioned output key.
 */
public record ImageTranscodeRequest(
        String processingId,
        String assetId,
        String tenantScopeId,
        String objectKey,
        long objectSizeBytes,
        String expectedSha256,
        ImagePreset preset,
        DerivedAssetFormat format,
        String engineVersion,
        Map<String, String> labels) {

    public static final int MAX_PROCESSING_ID_LENGTH = 128;
    public static final int MAX_ASSET_ID_LENGTH = 128;
    public static final int MAX_OBJECT_KEY_LENGTH = 1024;
    public static final int MAX_CHECKSUM_LENGTH = 256;
    public static final long MAX_OBJECT_SIZE_BYTES = 5497558138880L;
    public static final int MAX_LABELS = 16;
    public static final int MAX_LABEL_KEY_LENGTH = 64;
    public static final int MAX_LABEL_VALUE_LENGTH = 1024;
    public static final int MAX_ENGINE_VERSION_LENGTH = 64;

    public ImageTranscodeRequest {
        Objects.requireNonNull(processingId, "processingId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(tenantScopeId, "tenantScopeId");
        Objects.requireNonNull(objectKey, "objectKey");
        Objects.requireNonNull(expectedSha256, "expectedSha256");
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(engineVersion, "engineVersion");
        if (processingId.isBlank()
                || processingId.length() > MAX_PROCESSING_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "processingId length out of bounds [1, "
                            + MAX_PROCESSING_ID_LENGTH + "]");
        }
        if (assetId.isBlank() || assetId.length() > MAX_ASSET_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "assetId length out of bounds [1, "
                            + MAX_ASSET_ID_LENGTH + "]");
        }
        if (tenantScopeId.isBlank()) {
            throw new IllegalArgumentException(
                    "tenantScopeId must not be blank");
        }
        if (objectKey.isBlank()
                || objectKey.length() > MAX_OBJECT_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "objectKey length out of bounds");
        }
        if (objectSizeBytes <= 0L
                || objectSizeBytes > MAX_OBJECT_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "objectSizeBytes out of bounds");
        }
        if (expectedSha256.isBlank()
                || expectedSha256.length() > MAX_CHECKSUM_LENGTH) {
            throw new IllegalArgumentException(
                    "expectedSha256 length out of bounds");
        }
        if (engineVersion.isBlank()
                || engineVersion.length() > MAX_ENGINE_VERSION_LENGTH) {
            throw new IllegalArgumentException(
                    "engineVersion length out of bounds");
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