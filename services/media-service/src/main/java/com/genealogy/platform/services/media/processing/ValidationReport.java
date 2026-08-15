package com.genealogy.platform.services.media.processing;

import java.util.Map;
import java.util.Objects;

/**
 * Validation gate report. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.validationChecks + validationCheckResults` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>The report is a deterministic snapshot of every
 * {@link ValidationCheck} the worker ran against the
 * derived artefact. A check that returns
 * {@link ValidationCheckResult#FAIL} forces
 * {@link DerivedAssetStatus#FAILED} with
 * {@link ProcessingFailureReason#VALIDATION_FAILED} per the
 * {@code validationFailNeverYieldsDerivedReady=true} guard
 * rail.
 */
public record ValidationReport(
        String processingId,
        Map<ValidationCheck, ValidationCheckResult> results,
        Map<ValidationCheck, String> messages) {

    public static final int MAX_PROCESSING_ID_LENGTH = 128;
    public static final int MAX_CHECKS = 16;
    public static final int MAX_MESSAGE_LENGTH = 1024;

    public ValidationReport {
        Objects.requireNonNull(processingId, "processingId");
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(messages, "messages");
        if (processingId.isBlank()
                || processingId.length() > MAX_PROCESSING_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "processingId length out of bounds");
        }
        if (results.size() > MAX_CHECKS) {
            throw new IllegalArgumentException(
                    "results exceeds " + MAX_CHECKS + " entries");
        }
        for (Map.Entry<ValidationCheck, ValidationCheckResult> e
                : results.entrySet()) {
            Objects.requireNonNull(e.getKey(), "check key");
            Objects.requireNonNull(e.getValue(), "check result");
        }
        for (Map.Entry<ValidationCheck, String> e : messages.entrySet()) {
            String m = e.getValue();
            if (m != null && m.length() > MAX_MESSAGE_LENGTH) {
                throw new IllegalArgumentException(
                        "message exceeds "
                                + MAX_MESSAGE_LENGTH
                                + " characters for check "
                                + e.getKey());
            }
        }
        results = Map.copyOf(results);
        messages = Map.copyOf(messages);
    }

    /**
     * Whether every {@link ValidationCheck} returned
     * {@link ValidationCheckResult#PASS}.
     */
    public boolean allPassed() {
        for (ValidationCheckResult r : results.values()) {
            if (r != ValidationCheckResult.PASS) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether any {@link ValidationCheck} returned
     * {@link ValidationCheckResult#FAIL}.
     */
    public boolean anyFailed() {
        for (ValidationCheckResult r : results.values()) {
            if (r == ValidationCheckResult.FAIL) {
                return true;
            }
        }
        return false;
    }
}