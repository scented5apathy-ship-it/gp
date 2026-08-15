package com.genealogy.platform.services.media.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Decision record returned by
 * {@link MalwareMetadataPipeline#decide(MediaScanResult, MetadataExtractResult)}.
 * Mirrors
 * `contracts/media/malware-metadata-pipeline-policy.yaml
 * ::spec.pipelineStatuses + pipelineFailureReasons` (E7.2)
 * + `design.md` §11.
 *
 * <p>The decision carries the target {@link PipelineStatus},
 * the closed-set {@link PipelineFailureReason} (null on
 * {@link PipelineStatus#READY}), a {@code facts} map (audit
 * evidence the application layer forwards to the
 * {@code audit-service}), and a deterministic summary.
 */
public record PipelineDecision(
        String pipelineId,
        PipelineStatus status,
        PipelineFailureReason failureReason,
        Map<String, Object> facts,
        String summary) {

    public PipelineDecision {
        Objects.requireNonNull(pipelineId, "pipelineId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(summary, "summary");
        if (pipelineId.isBlank()) {
            throw new IllegalArgumentException(
                    "pipelineId must not be blank");
        }
        if (status == PipelineStatus.READY && failureReason != null) {
            throw new IllegalArgumentException(
                    "READY decision MUST NOT carry a failureReason");
        }
        if (status != PipelineStatus.READY
                && failureReason == null
                && status != PipelineStatus.READY) {
            throw new IllegalArgumentException(
                    "non-READY decision MUST carry a failureReason (status="
                            + status.wire() + ")");
        }
        facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }

    /**
     * Build a terminal decision (READY or one of
     * FAILED / QUARANTINED_RETAIN).
     */
    public static PipelineDecision terminal(
            String pipelineId,
            PipelineStatus status,
            PipelineFailureReason failureReason,
            Map<String, Object> facts,
            String summary) {
        Map<String, Object> safeFacts = facts == null
                ? Map.of()
                : facts;
        return new PipelineDecision(
                pipelineId,
                status,
                failureReason,
                safeFacts,
                summary);
    }

    /**
     * Whether this decision is {@link PipelineStatus#READY}.
     */
    public boolean isReady() {
        return status == PipelineStatus.READY;
    }

    public Optional<PipelineFailureReason> failureReasonOpt() {
        return Optional.ofNullable(failureReason);
    }
}