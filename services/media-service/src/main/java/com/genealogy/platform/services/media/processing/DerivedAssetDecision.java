package com.genealogy.platform.services.media.processing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Decision record returned by
 * {@link MediaProcessingPipeline#decide(String,
 * com.genealogy.platform.services.media.domain.PipelineStatus,
 * ProcessingTask, ProcessingEngine, ProcessingOutcome,
 * ValidationReport, boolean, boolean, String)}.
 * Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.derivedAssetStatuses + processingFailureReasons` (E7.3)
 * + `design.md` §11.
 *
 * <p>The decision carries the target
 * {@link DerivedAssetStatus}, the closed-set
 * {@link ProcessingFailureReason} (null on
 * {@link DerivedAssetStatus#DERIVED_READY}), a
 * {@code facts} map (audit evidence the application layer
 * forwards to the {@code audit-service}), and a
 * deterministic summary.
 */
public record DerivedAssetDecision(
        String processingId,
        DerivedAssetStatus status,
        ProcessingFailureReason failureReason,
        Map<String, Object> facts,
        String summary) {

    public DerivedAssetDecision {
        Objects.requireNonNull(processingId, "processingId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(summary, "summary");
        if (processingId.isBlank()) {
            throw new IllegalArgumentException(
                    "processingId must not be blank");
        }
        if (status == DerivedAssetStatus.DERIVED_READY
                && failureReason != null) {
            throw new IllegalArgumentException(
                    "DERIVED_READY decision MUST NOT carry a failureReason");
        }
        if (status != DerivedAssetStatus.DERIVED_READY
                && failureReason == null) {
            throw new IllegalArgumentException(
                    "non-DERIVED_READY decision MUST carry a failureReason (status="
                            + status.wire() + ")");
        }
        facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }

    /**
     * Build a terminal decision (DERIVED_READY or one of
     * FAILED / QUARANTINED_RETAIN).
     */
    public static DerivedAssetDecision terminal(
            String processingId,
            DerivedAssetStatus status,
            ProcessingFailureReason failureReason,
            Map<String, Object> facts,
            String summary) {
        Map<String, Object> safeFacts = facts == null
                ? Map.of()
                : facts;
        return new DerivedAssetDecision(
                processingId,
                status,
                failureReason,
                safeFacts,
                summary);
    }

    public boolean isDerivedReady() {
        return status == DerivedAssetStatus.DERIVED_READY;
    }

    public Optional<ProcessingFailureReason> failureReasonOpt() {
        return Optional.ofNullable(failureReason);
    }
}