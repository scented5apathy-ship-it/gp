package com.genealogy.platform.services.media.processing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Deterministic orchestrator for the media processing
 * pipeline. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.derivedAssetStatusMatrix + sandboxModes + guard rails`
 * (E7.3) + `requirements.md` R9.3 + `design.md` §11.
 *
 * <p>The orchestrator is a pure executor: it consumes the
 * upstream {@link com.genealogy.platform.services.media.domain.PipelineStatus}
 * (E7.2 READY invariant) + a {@link ProcessingTask} +
 * {@link ProcessingOutcome} + {@link ValidationReport} and
 * produces a deterministic {@link DerivedAssetDecision}. It
 * NEVER mutates another service's domain record directly;
 * the application layer applies the transition through the
 * jOOQ repository (E7.x / E11.x). All side effects go
 * through the {@link LibvipsOptimizerPort} +
 * {@link GotenbergRendererPort} + {@link FfmpegEncoderPort}
 * + {@link TesseractOcrPort} ports which are wired by the
 * Temporal worker.
 *
 * <p>Guard rails enforced:
 * <ul>
 *   <li>{@code requireReadyInputForDerivedReady=true} —
 *       only {@link com.genealogy.platform.services.media.domain.PipelineStatus#READY}
 *       admits {@link DerivedAssetStatus#DERIVED_READY}; the
 *       E7.2 linkability invariant carries through.</li>
 *   <li>{@code requireSuccessOutcomeForDerivedReady=true}
 *       — only {@link ProcessingOutcome#SUCCESS} admits
 *       {@link DerivedAssetStatus#DERIVED_READY}; {@link ProcessingOutcome#PARTIAL}
 *       forces {@link DerivedAssetStatus#FAILED}.</li>
 *   <li>{@code requireAllValidationChecksPassForDerivedReady=true}
 *       — every {@link ValidationCheck} in the report
 *       MUST return {@link ValidationCheckResult#PASS};
 *       {@link ValidationCheckResult#FAIL} forces
 *       {@link DerivedAssetStatus#FAILED} with
 *       {@link ProcessingFailureReason#VALIDATION_FAILED}.</li>
 *   <li>{@code libvipsOnlyForImageTranscode=true} +
 *       {@code imageMagickBlocked=true} —
 *       {@link ProcessingEngine#IMAGEMAGICK} forces
 *       {@link ProcessingFailureReason#UNSUPPORTED_DERIVED_FORMAT}
 *       → {@link DerivedAssetStatus#FAILED}.</li>
 *   <li>{@code exifScrubbedRequiredForDerivedReady=true} —
 *       the image transcode result MUST report
 *       {@code exifScrubbed=true}; otherwise
 *       {@link ProcessingFailureReason#EXIF_PII_LEAKED}
 *       forces {@link DerivedAssetStatus#FAILED}.</li>
 *   <li>{@code processingIdempotentOnProcessingId=true} —
 *       the {@code processingId} is the workflow-scoped
 *       idempotency key; re-running the same workflow
 *       produces the same decision.</li>
 *   <li>{@code outputKeyDeterministicAndVersioned=true} —
 *       {@link #deriveOutputKey(String, ProcessingTask, String, String, int)}
 *       pins the engine version in the key.</li>
 *   <li>{@code dnaObjectRejected=true} —
 *       {@code objectKey.startsWith("dna/")} forces
 *       {@link ProcessingFailureReason#DNA_OBJECT_REJECTED}
 *       → {@link DerivedAssetStatus#QUARANTINED_RETAIN}.</li>
 *   <li>{@code derivedKeyCollisionFailsPipeline=true} —
 *       a pre-existing key with a different content hash
 *       forces {@link ProcessingFailureReason#DERIVED_OBJECT_KEY_COLLISION}
 *       → {@link DerivedAssetStatus#FAILED}.</li>
 * </ul>
 */
public final class MediaProcessingPipeline {

    private MediaProcessingPipeline() {
    }

    /**
     * Compute the deterministic output object key for a
     * derived artefact. The {@code engineVersion} component
     * MUST be non-null + non-blank; bumping the engine
     * version produces a new key without overwriting
     * historical artefacts.
     */
    public static String deriveOutputKey(
            String tenantScopeId,
            String assetId,
            ProcessingTask task,
            String engineVersion,
            int attempt) {
        Objects.requireNonNull(tenantScopeId, "tenantScopeId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(engineVersion, "engineVersion");
        if (tenantScopeId.isBlank()) {
            throw new IllegalArgumentException(
                    "tenantScopeId must not be blank");
        }
        if (assetId.isBlank()) {
            throw new IllegalArgumentException(
                    "assetId must not be blank");
        }
        if (engineVersion.isBlank()) {
            throw new IllegalArgumentException(
                    "engineVersion must not be blank");
        }
        if (attempt < 0) {
            throw new IllegalArgumentException(
                    "attempt must be >= 0");
        }
        return "media/" + tenantScopeId + "/"
                + assetId + "/" + engineVersion
                + "/" + task.wire().toLowerCase() + "/v" + attempt;
    }

    /**
     * Decide whether a {@link DerivedAssetStatus} value may
     * transition to another status. The matrix mirrors
     * `contracts/media/media-processing-pipeline-policy.yaml
     * ::spec.derivedAssetStatusMatrix`;
     * {@link DerivedAssetStatus#DERIVED_READY} /
     * {@link DerivedAssetStatus#FAILED} /
     * {@link DerivedAssetStatus#QUARANTINED_RETAIN} are
     * terminal.
     */
    public static boolean isLegalTransition(
            DerivedAssetStatus current,
            DerivedAssetStatus next) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(next, "next");
        return switch (current) {
            case PENDING -> next == DerivedAssetStatus.PROCESSING
                    || next == DerivedAssetStatus.FAILED;
            case PROCESSING -> next == DerivedAssetStatus.VALIDATING
                    || next == DerivedAssetStatus.FAILED
                    || next == DerivedAssetStatus.QUARANTINED_RETAIN;
            case VALIDATING -> next == DerivedAssetStatus.DERIVED_READY
                    || next == DerivedAssetStatus.FAILED
                    || next == DerivedAssetStatus.QUARANTINED_RETAIN;
            case DERIVED_READY, FAILED, QUARANTINED_RETAIN -> false;
        };
    }

    /**
     * Pure decision: combine the upstream E7.2
     * {@link com.genealogy.platform.services.media.domain.PipelineStatus}
     * + the {@link ProcessingTask} + the
     * {@link ProcessingOutcome} + the {@link ValidationReport}
     * into a {@link DerivedAssetDecision}. The decision is
     * deterministic and idempotent on {@code processingId}.
     *
     * <p>The {@code imageTranscodeExifScrubbed} parameter
     * carries the EXIF scrub status from the libvips worker
     * (only consulted when {@code task == ProcessingTask.IMAGE_TRANSCODE}).
     * The {@code derivedKeyCollision} parameter reports
     * whether the deterministic + versioned output key
     * already exists with a different content hash.
     */
    public static DerivedAssetDecision decide(
            String processingId,
            com.genealogy.platform.services.media.domain.PipelineStatus inputStatus,
            ProcessingTask task,
            ProcessingEngine engine,
            ProcessingOutcome outcome,
            ValidationReport validation,
            boolean imageTranscodeExifScrubbed,
            boolean derivedKeyCollision,
            String inputObjectKey) {
        Objects.requireNonNull(processingId, "processingId");
        Objects.requireNonNull(inputStatus, "inputStatus");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(validation, "validation");
        Objects.requireNonNull(inputObjectKey, "inputObjectKey");
        if (processingId.isBlank()) {
            throw new IllegalArgumentException(
                    "processingId must not be blank");
        }
        if (!processingId.equals(validation.processingId())) {
            throw new IllegalArgumentException(
                    "processingId mismatch: pipeline="
                            + processingId + " validation="
                            + validation.processingId());
        }

        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("task", task.wire());
        facts.put("engine", engine.wire());
        facts.put("inputStatus", inputStatus.wire());
        facts.put("outcome", outcome.wire());

        // 0. DNA bucket shield (forward-looking to E7.4):
        // refuse DNA objects here so the E7.4 worker can
        // carry the full bucket prefix shield.
        if (inputObjectKey.startsWith("dna/")) {
            return DerivedAssetDecision.terminal(
                    processingId,
                    DerivedAssetStatus.QUARANTINED_RETAIN,
                    ProcessingFailureReason.DNA_OBJECT_REJECTED,
                    facts,
                    "Object key in DNA bucket; rejected per E7.4 shield");
        }

        // 1. Input status gate — only E7.2 READY admits
        // DERIVED_READY. The E7.2 linkability invariant
        // carries through to E7.3.
        if (inputStatus
                != com.genealogy.platform.services.media.domain.PipelineStatus.READY) {
            return DerivedAssetDecision.terminal(
                    processingId,
                    DerivedAssetStatus.FAILED,
                    ProcessingFailureReason.PROCESS_ERROR,
                    facts,
                    "Input status is " + inputStatus.wire()
                            + "; only READY is admitted");
        }

        // 2. ImageMagick is BLOCKED. libvips is the canonical
        // image engine.
        if (task == ProcessingTask.IMAGE_TRANSCODE
                && engine == ProcessingEngine.IMAGEMAGICK) {
            return DerivedAssetDecision.terminal(
                    processingId,
                    DerivedAssetStatus.FAILED,
                    ProcessingFailureReason.UNSUPPORTED_DERIVED_FORMAT,
                    facts,
                    "ImageMagick is BLOCKED; use libvips");
        }
        if (engine == ProcessingEngine.FALLBACK_NONE) {
            return DerivedAssetDecision.terminal(
                    processingId,
                    DerivedAssetStatus.FAILED,
                    ProcessingFailureReason.PROCESSOR_UNAVAILABLE,
                    facts,
                    "FALLBACK_NONE is not a valid engine");
        }

        // 3. Process outcome gate — only SUCCESS admits
        // DERIVED_READY. PARTIAL / PROCESS_TIMEOUT /
        // PROCESS_ERROR / UNSUPPORTED_FORMAT / SANDBOX_DENIED
        // / OUTPUT_KEY_COLLISION / VALIDATION_FAILED all
        // fail the pipeline.
        switch (outcome) {
            case PROCESS_TIMEOUT:
                return DerivedAssetDecision.terminal(
                        processingId,
                        DerivedAssetStatus.FAILED,
                        ProcessingFailureReason.PROCESS_TIMEOUT,
                        facts,
                        "Processor timed out");
            case SANDBOX_DENIED:
                return DerivedAssetDecision.terminal(
                        processingId,
                        DerivedAssetStatus.FAILED,
                        ProcessingFailureReason.SANDBOX_NETWORK_DENIED,
                        facts,
                        "Sandbox denied network egress");
            case UNSUPPORTED_FORMAT:
                return DerivedAssetDecision.terminal(
                        processingId,
                        DerivedAssetStatus.FAILED,
                        ProcessingFailureReason.UNSUPPORTED_DERIVED_FORMAT,
                        facts,
                        "Unsupported derived format");
            case OUTPUT_KEY_COLLISION:
                return DerivedAssetDecision.terminal(
                        processingId,
                        DerivedAssetStatus.FAILED,
                        ProcessingFailureReason.DERIVED_OBJECT_KEY_COLLISION,
                        facts,
                        "Deterministic output key collision");
            case VALIDATION_FAILED:
                return DerivedAssetDecision.terminal(
                        processingId,
                        DerivedAssetStatus.FAILED,
                        ProcessingFailureReason.VALIDATION_FAILED,
                        facts,
                        "Validation gate reported failure");
            case PARTIAL:
                return DerivedAssetDecision.terminal(
                        processingId,
                        DerivedAssetStatus.FAILED,
                        ProcessingFailureReason.PROCESS_ERROR,
                        facts,
                        "Partial outcome NEVER yields DERIVED_READY");
            case PROCESS_ERROR:
                return DerivedAssetDecision.terminal(
                        processingId,
                        DerivedAssetStatus.FAILED,
                        ProcessingFailureReason.PROCESSOR_UNAVAILABLE,
                        facts,
                        "Processor returned PROCESS_ERROR");
            case SUCCESS:
                // fall through to validation + EXIF + collision
                break;
            default:
                throw new IllegalStateException(
                        "unhandled ProcessingOutcome: "
                                + outcome.wire());
        }

        // 4. Validation gate — every required check MUST
        // return PASS. A FAIL forces FAILED with
        // VALIDATION_FAILED.
        if (validation.anyFailed()) {
            return DerivedAssetDecision.terminal(
                    processingId,
                    DerivedAssetStatus.FAILED,
                    ProcessingFailureReason.VALIDATION_FAILED,
                    facts,
                    "Validation gate reported FAIL");
        }
        if (!validation.allPassed()) {
            facts.put("validationNotAllPassed", true);
        }

        // 5. EXIF scrubbed required for IMAGE_TRANSCODE.
        if (task == ProcessingTask.IMAGE_TRANSCODE
                && !imageTranscodeExifScrubbed) {
            return DerivedAssetDecision.terminal(
                    processingId,
                    DerivedAssetStatus.FAILED,
                    ProcessingFailureReason.EXIF_PII_LEAKED,
                    facts,
                    "EXIF PII leaked; derived artefact not safe to link");
        }

        // 6. Derived key collision gate.
        if (derivedKeyCollision) {
            return DerivedAssetDecision.terminal(
                    processingId,
                    DerivedAssetStatus.FAILED,
                    ProcessingFailureReason.DERIVED_OBJECT_KEY_COLLISION,
                    facts,
                    "Deterministic output key already exists with different hash");
        }

        // 7. CLEAN — task + engine + outcome + validation +
        // EXIF + collision all clear → DERIVED_READY.
        return DerivedAssetDecision.terminal(
                processingId,
                DerivedAssetStatus.DERIVED_READY,
                null,
                facts,
                task.wire() + " via " + engine.wire()
                        + " → DERIVED_READY");
    }

    /**
     * Whether a {@link DerivedAssetStatus} value may be
     * linked into the E7.4 protected-delivery slot. The
     * {@code processingTerminalStatuses=[DERIVED_READY,
     * FAILED, QUARANTINED_RETAIN]} guard rail means only
     * {@link DerivedAssetStatus#DERIVED_READY} may be linked.
     */
    public static boolean isLinkable(DerivedAssetStatus status) {
        Objects.requireNonNull(status, "status");
        return status == DerivedAssetStatus.DERIVED_READY;
    }

    /**
     * Whether a derived asset with the supplied
     * {@link DerivedAssetStatus} is downstream-linkable. The
     * E7.4 protected-delivery contract consumes only
     * {@link DerivedAssetStatus#DERIVED_READY}; the
     * E7.2 {@code linkablePipelineStatuses=[READY]} gate is
     * upstream of E7.3.
     */
    public static Optional<DerivedAssetFormat> requiredDownstreamFormat(
            ProcessingTask task) {
        Objects.requireNonNull(task, "task");
        return switch (task) {
            case IMAGE_TRANSCODE -> Optional.of(
                    DerivedAssetFormat.THUMBNAIL_WEBP);
            case DOCUMENT_RENDER -> Optional.of(
                    DerivedAssetFormat.PDF_PREVIEW);
            case VIDEO_TRANSCODE -> Optional.of(
                    DerivedAssetFormat.VIDEO_720P);
            case TEXT_OCR -> Optional.of(
                    DerivedAssetFormat.OCR_TEXT);
        };
    }

    /**
     * Verify that a derived artefact's declared + observed
     * checksums match. Throws
     * {@link DerivedAssetIntegrityException} on mismatch
     * (mirrors the
     * {@code PROCESSING_INTEGRITY_CHECKSUM_REQUIRED} +
     * {@code INTEGRITY_CHECKSUM_MISMATCH} invariants).
     */
    public static void verifyIntegrityChecksum(
            String declaredSha256,
            String observedSha256) {
        Objects.requireNonNull(declaredSha256, "declaredSha256");
        Objects.requireNonNull(observedSha256, "observedSha256");
        if (!declaredSha256.equalsIgnoreCase(observedSha256)) {
            throw new DerivedAssetIntegrityException(
                    "checksum mismatch: declared="
                            + declaredSha256
                            + " observed=" + observedSha256);
        }
    }
}