package com.genealogy.platform.services.media.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of the Apache Tika extract activity. Mirrors
 * `contracts/media/malware-metadata-pipeline-policy.yaml
 * ::spec.metadataExtractOutcomes + maxMetadataFields +
 * maxMetadataFieldLength` (E7.2) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>The {@code fields} map MUST stay within
 * {@code maxMetadataFields=4096} entries + each value at
 * most {@code maxMetadataFieldLength=4096} characters.
 * {@link MetadataExtractOutcome#EMPTY} is a valid terminal
 * outcome (the asset is empty or has no extractable
 * metadata); the pipeline still reaches
 * {@link PipelineStatus#READY} in that case per
 * `requireSuccessOrEmptyMetadataForReady=true`.
 */
public record MetadataExtractResult(
        String pipelineId,
        MetadataExtractOutcome outcome,
        MetadataExtractEngine engine,
        String detectedContentType,
        String detectedLanguage,
        long extractedBytes,
        Map<String, String> fields,
        List<String> warnings) {

    public static final int MAX_PIPELINE_ID_LENGTH = 128;
    public static final int MAX_DETECTED_CONTENT_TYPE_LENGTH = 256;
    public static final int MAX_DETECTED_LANGUAGE_LENGTH = 32;
    public static final long MAX_EXTRACTED_BYTES = 268435456L;
    public static final int MAX_FIELDS = 4096;
    public static final int MAX_FIELD_KEY_LENGTH = 128;
    public static final int MAX_FIELD_VALUE_LENGTH = 4096;
    public static final int MAX_WARNINGS = 64;
    public static final int MAX_WARNING_LENGTH = 1024;

    public MetadataExtractResult {
        Objects.requireNonNull(pipelineId, "pipelineId");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(warnings, "warnings");
        if (pipelineId.isBlank() || pipelineId.length() > MAX_PIPELINE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "pipelineId length out of bounds [1, "
                            + MAX_PIPELINE_ID_LENGTH + "]");
        }
        if (detectedContentType != null
                && detectedContentType.length()
                        > MAX_DETECTED_CONTENT_TYPE_LENGTH) {
            throw new IllegalArgumentException(
                    "detectedContentType exceeds "
                            + MAX_DETECTED_CONTENT_TYPE_LENGTH + " characters");
        }
        if (detectedLanguage != null
                && detectedLanguage.length()
                        > MAX_DETECTED_LANGUAGE_LENGTH) {
            throw new IllegalArgumentException(
                    "detectedLanguage exceeds "
                            + MAX_DETECTED_LANGUAGE_LENGTH + " characters");
        }
        if (extractedBytes < 0L || extractedBytes > MAX_EXTRACTED_BYTES) {
            throw new IllegalArgumentException(
                    "extractedBytes out of bounds [0, "
                            + MAX_EXTRACTED_BYTES + "]");
        }
        Map<String, String> safeFields = Map.copyOf(fields);
        if (safeFields.size() > MAX_FIELDS) {
            throw new IllegalArgumentException(
                    "fields exceeds " + MAX_FIELDS + " entries");
        }
        for (Map.Entry<String, String> e : safeFields.entrySet()) {
            String k = e.getKey();
            String v = e.getValue();
            if (k == null || k.isBlank()
                    || k.length() > MAX_FIELD_KEY_LENGTH) {
                throw new IllegalArgumentException(
                        "field key out of bounds");
            }
            if (v != null && v.length() > MAX_FIELD_VALUE_LENGTH) {
                throw new IllegalArgumentException(
                        "field value exceeds "
                                + MAX_FIELD_VALUE_LENGTH
                                + " characters for key " + k);
            }
        }
        fields = safeFields;
        List<String> safeWarnings = List.copyOf(warnings);
        if (safeWarnings.size() > MAX_WARNINGS) {
            throw new IllegalArgumentException(
                    "warnings exceeds " + MAX_WARNINGS + " entries");
        }
        for (String w : safeWarnings) {
            if (w == null || w.isBlank()
                    || w.length() > MAX_WARNING_LENGTH) {
                throw new IllegalArgumentException(
                        "warning entry out of bounds");
            }
        }
        warnings = safeWarnings;
    }

    /**
     * Convenience constructor for short-lived tests that
     * need a SUCCESS / EMPTY result with no fields.
     */
    public static MetadataExtractResult success(
            String pipelineId,
            MetadataExtractEngine engine,
            String detectedContentType,
            String detectedLanguage,
            long extractedBytes) {
        return new MetadataExtractResult(
                pipelineId,
                MetadataExtractOutcome.SUCCESS,
                engine,
                detectedContentType,
                detectedLanguage,
                extractedBytes,
                Map.of(),
                List.of());
    }
}