package com.genealogy.platform.services.media.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;

import com.genealogy.platform.services.media.domain.PipelineStatus;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@link MediaProcessingPipeline} deterministic
 * orchestrator + its associated value objects. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.derivedAssetStatusMatrix + processingInputs +
 * processingFailureReasons + guard rails` (E7.3) +
 * `requirements.md` R9.3 + `design.md` §11.
 */
class MediaProcessingPipelineTest {

    private static ValidationReport allPassedReport(String processingId) {
        Map<ValidationCheck, ValidationCheckResult> results =
                new EnumMap<>(ValidationCheck.class);
        for (ValidationCheck c : ValidationCheck.values()) {
            results.put(c, ValidationCheckResult.PASS);
        }
        return new ValidationReport(processingId, results, Map.of());
    }

    private static ValidationReport failedReport(String processingId) {
        Map<ValidationCheck, ValidationCheckResult> results =
                new EnumMap<>(ValidationCheck.class);
        for (ValidationCheck c : ValidationCheck.values()) {
            results.put(c, ValidationCheckResult.PASS);
        }
        results.put(ValidationCheck.EXIF_SCRUBBED,
                ValidationCheckResult.FAIL);
        return new ValidationReport(processingId, results, Map.of());
    }

    @Test
    void deriveOutputKeyIsDeterministicAndVersioned() {
        String k1 = MediaProcessingPipeline.deriveOutputKey(
                "tenant-1", "asset-abc",
                ProcessingTask.IMAGE_TRANSCODE, "v8.15.0", 0);
        String k2 = MediaProcessingPipeline.deriveOutputKey(
                "tenant-1", "asset-abc",
                ProcessingTask.IMAGE_TRANSCODE, "v8.15.0", 0);
        String k3 = MediaProcessingPipeline.deriveOutputKey(
                "tenant-1", "asset-abc",
                ProcessingTask.IMAGE_TRANSCODE, "v8.16.0", 0);
        assertEquals(k1, k2);
        assertEquals(
                "media/tenant-1/asset-abc/v8.15.0/image_transcode/v0",
                k1);
        assertEquals(
                "media/tenant-1/asset-abc/v8.16.0/image_transcode/v0",
                k3);
    }

    @Test
    void deriveOutputKeyRejectsBlanks() {
        assertThrows(IllegalArgumentException.class,
                () -> MediaProcessingPipeline.deriveOutputKey(
                        "", "asset",
                        ProcessingTask.IMAGE_TRANSCODE, "v1", 0));
        assertThrows(IllegalArgumentException.class,
                () -> MediaProcessingPipeline.deriveOutputKey(
                        "tenant", "",
                        ProcessingTask.IMAGE_TRANSCODE, "v1", 0));
        assertThrows(IllegalArgumentException.class,
                () -> MediaProcessingPipeline.deriveOutputKey(
                        "tenant", "asset",
                        ProcessingTask.IMAGE_TRANSCODE, "", 0));
        assertThrows(IllegalArgumentException.class,
                () -> MediaProcessingPipeline.deriveOutputKey(
                        "tenant", "asset",
                        ProcessingTask.IMAGE_TRANSCODE, "v1", -1));
        assertThrows(NullPointerException.class,
                () -> MediaProcessingPipeline.deriveOutputKey(
                        "tenant", "asset", null, "v1", 0));
    }

    @Test
    void isLegalTransitionMatrixMatchesContract() {
        assertTrue(MediaProcessingPipeline.isLegalTransition(
                DerivedAssetStatus.PENDING,
                DerivedAssetStatus.PROCESSING));
        assertTrue(MediaProcessingPipeline.isLegalTransition(
                DerivedAssetStatus.PENDING,
                DerivedAssetStatus.FAILED));
        assertFalse(MediaProcessingPipeline.isLegalTransition(
                DerivedAssetStatus.PENDING,
                DerivedAssetStatus.DERIVED_READY));
        assertTrue(MediaProcessingPipeline.isLegalTransition(
                DerivedAssetStatus.PROCESSING,
                DerivedAssetStatus.VALIDATING));
        assertTrue(MediaProcessingPipeline.isLegalTransition(
                DerivedAssetStatus.PROCESSING,
                DerivedAssetStatus.QUARANTINED_RETAIN));
        assertTrue(MediaProcessingPipeline.isLegalTransition(
                DerivedAssetStatus.VALIDATING,
                DerivedAssetStatus.DERIVED_READY));
        assertFalse(MediaProcessingPipeline.isLegalTransition(
                DerivedAssetStatus.DERIVED_READY,
                DerivedAssetStatus.FAILED));
        assertFalse(MediaProcessingPipeline.isLegalTransition(
                DerivedAssetStatus.FAILED,
                DerivedAssetStatus.DERIVED_READY));
        assertFalse(MediaProcessingPipeline.isLegalTransition(
                DerivedAssetStatus.QUARANTINED_RETAIN,
                DerivedAssetStatus.DERIVED_READY));
    }

    @Test
    void onlyDerivedReadyIsLinkable() {
        assertTrue(MediaProcessingPipeline.isLinkable(
                DerivedAssetStatus.DERIVED_READY));
        assertFalse(MediaProcessingPipeline.isLinkable(
                DerivedAssetStatus.PENDING));
        assertFalse(MediaProcessingPipeline.isLinkable(
                DerivedAssetStatus.PROCESSING));
        assertFalse(MediaProcessingPipeline.isLinkable(
                DerivedAssetStatus.VALIDATING));
        assertFalse(MediaProcessingPipeline.isLinkable(
                DerivedAssetStatus.FAILED));
        assertFalse(MediaProcessingPipeline.isLinkable(
                DerivedAssetStatus.QUARANTINED_RETAIN));
    }

    @Test
    void successOutcomeAndPassedValidationYieldsDerivedReady() {
        DerivedAssetDecision d = MediaProcessingPipeline.decide(
                "proc-1",
                PipelineStatus.READY,
                ProcessingTask.IMAGE_TRANSCODE,
                ProcessingEngine.LIBVIPS,
                ProcessingOutcome.SUCCESS,
                allPassedReport("proc-1"),
                true,
                false,
                "media/tenant-1/asset-abc/upload.jpg");
        assertEquals(
                DerivedAssetStatus.DERIVED_READY, d.status());
        assertNull(d.failureReason());
        assertTrue(d.isDerivedReady());
    }

    @Test
    void nonReadyInputYieldsFailed() {
        DerivedAssetDecision d = MediaProcessingPipeline.decide(
                "proc-1",
                PipelineStatus.METADATA_READY,
                ProcessingTask.IMAGE_TRANSCODE,
                ProcessingEngine.LIBVIPS,
                ProcessingOutcome.SUCCESS,
                allPassedReport("proc-1"),
                true,
                false,
                "media/tenant-1/asset-abc/upload.jpg");
        assertEquals(DerivedAssetStatus.FAILED, d.status());
        assertEquals(ProcessingFailureReason.PROCESS_ERROR,
                d.failureReason());
    }

    @Test
    void imagemagickIsBlocked() {
        DerivedAssetDecision d = MediaProcessingPipeline.decide(
                "proc-1",
                PipelineStatus.READY,
                ProcessingTask.IMAGE_TRANSCODE,
                ProcessingEngine.IMAGEMAGICK,
                ProcessingOutcome.SUCCESS,
                allPassedReport("proc-1"),
                true,
                false,
                "media/tenant-1/asset-abc/upload.jpg");
        assertEquals(DerivedAssetStatus.FAILED, d.status());
        assertEquals(
                ProcessingFailureReason.UNSUPPORTED_DERIVED_FORMAT,
                d.failureReason());
    }

    @Test
    void fallbackNoneYieldsProcessorUnavailable() {
        DerivedAssetDecision d = MediaProcessingPipeline.decide(
                "proc-1",
                PipelineStatus.READY,
                ProcessingTask.VIDEO_TRANSCODE,
                ProcessingEngine.FALLBACK_NONE,
                ProcessingOutcome.SUCCESS,
                allPassedReport("proc-1"),
                true,
                false,
                "media/tenant-1/asset-abc/upload.mp4");
        assertEquals(DerivedAssetStatus.FAILED, d.status());
        assertEquals(
                ProcessingFailureReason.PROCESSOR_UNAVAILABLE,
                d.failureReason());
    }

    @Test
    void partialOutcomeNeverYieldsDerivedReady() {
        DerivedAssetDecision d = MediaProcessingPipeline.decide(
                "proc-1",
                PipelineStatus.READY,
                ProcessingTask.DOCUMENT_RENDER,
                ProcessingEngine.GOTENBERG,
                ProcessingOutcome.PARTIAL,
                allPassedReport("proc-1"),
                true,
                false,
                "media/tenant-1/asset-abc/upload.pdf");
        assertEquals(DerivedAssetStatus.FAILED, d.status());
        assertEquals(ProcessingFailureReason.PROCESS_ERROR,
                d.failureReason());
    }

    @Test
    void processTimeoutYieldsProcessTimeout() {
        DerivedAssetDecision d = MediaProcessingPipeline.decide(
                "proc-1",
                PipelineStatus.READY,
                ProcessingTask.TEXT_OCR,
                ProcessingEngine.TESSERACT,
                ProcessingOutcome.PROCESS_TIMEOUT,
                allPassedReport("proc-1"),
                true,
                false,
                "media/tenant-1/asset-abc/upload.pdf");
        assertEquals(DerivedAssetStatus.FAILED, d.status());
        assertEquals(ProcessingFailureReason.PROCESS_TIMEOUT,
                d.failureReason());
    }

    @Test
    void sandboxDeniedYieldsSandboxNetworkDenied() {
        DerivedAssetDecision d = MediaProcessingPipeline.decide(
                "proc-1",
                PipelineStatus.READY,
                ProcessingTask.VIDEO_TRANSCODE,
                ProcessingEngine.FFMPEG,
                ProcessingOutcome.SANDBOX_DENIED,
                allPassedReport("proc-1"),
                true,
                false,
                "media/tenant-1/asset-abc/upload.mp4");
        assertEquals(DerivedAssetStatus.FAILED, d.status());
        assertEquals(
                ProcessingFailureReason.SANDBOX_NETWORK_DENIED,
                d.failureReason());
    }

    @Test
    void validationFailureYieldsValidationFailed() {
        DerivedAssetDecision d = MediaProcessingPipeline.decide(
                "proc-1",
                PipelineStatus.READY,
                ProcessingTask.IMAGE_TRANSCODE,
                ProcessingEngine.LIBVIPS,
                ProcessingOutcome.SUCCESS,
                failedReport("proc-1"),
                true,
                false,
                "media/tenant-1/asset-abc/upload.jpg");
        assertEquals(DerivedAssetStatus.FAILED, d.status());
        assertEquals(ProcessingFailureReason.VALIDATION_FAILED,
                d.failureReason());
    }

    @Test
    void exifPiiLeakedYieldsExifPiiLeaked() {
        DerivedAssetDecision d = MediaProcessingPipeline.decide(
                "proc-1",
                PipelineStatus.READY,
                ProcessingTask.IMAGE_TRANSCODE,
                ProcessingEngine.LIBVIPS,
                ProcessingOutcome.SUCCESS,
                allPassedReport("proc-1"),
                false,
                false,
                "media/tenant-1/asset-abc/upload.jpg");
        assertEquals(DerivedAssetStatus.FAILED, d.status());
        assertEquals(ProcessingFailureReason.EXIF_PII_LEAKED,
                d.failureReason());
    }

    @Test
    void derivedKeyCollisionYieldsDerivedObjectKeyCollision() {
        DerivedAssetDecision d = MediaProcessingPipeline.decide(
                "proc-1",
                PipelineStatus.READY,
                ProcessingTask.VIDEO_TRANSCODE,
                ProcessingEngine.FFMPEG,
                ProcessingOutcome.SUCCESS,
                allPassedReport("proc-1"),
                true,
                true,
                "media/tenant-1/asset-abc/upload.mp4");
        assertEquals(DerivedAssetStatus.FAILED, d.status());
        assertEquals(
                ProcessingFailureReason.DERIVED_OBJECT_KEY_COLLISION,
                d.failureReason());
    }

    @Test
    void dnaObjectKeyYieldsDnaObjectRejected() {
        DerivedAssetDecision d = MediaProcessingPipeline.decide(
                "proc-1",
                PipelineStatus.READY,
                ProcessingTask.IMAGE_TRANSCODE,
                ProcessingEngine.LIBVIPS,
                ProcessingOutcome.SUCCESS,
                allPassedReport("proc-1"),
                true,
                false,
                "dna/raw/sample.fastq");
        assertEquals(
                DerivedAssetStatus.QUARANTINED_RETAIN, d.status());
        assertEquals(
                ProcessingFailureReason.DNA_OBJECT_REJECTED,
                d.failureReason());
    }

    @Test
    void processingIdMismatchBetweenPipelineAndReportRejected() {
        Map<ValidationCheck, ValidationCheckResult> results =
                new EnumMap<>(ValidationCheck.class);
        for (ValidationCheck c : ValidationCheck.values()) {
            results.put(c, ValidationCheckResult.PASS);
        }
        ValidationReport wrong = new ValidationReport(
                "different-id", results, Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> MediaProcessingPipeline.decide(
                        "proc-1",
                        PipelineStatus.READY,
                        ProcessingTask.IMAGE_TRANSCODE,
                        ProcessingEngine.LIBVIPS,
                        ProcessingOutcome.SUCCESS,
                        wrong,
                        true,
                        false,
                        "media/tenant-1/asset-abc/upload.jpg"));
    }

    @Test
    void verifyIntegrityChecksumAcceptAndReject() {
        MediaProcessingPipeline.verifyIntegrityChecksum(
                "abc123", "ABC123");
        assertThrows(DerivedAssetIntegrityException.class,
                () -> MediaProcessingPipeline.verifyIntegrityChecksum(
                        "abc123", "def456"));
        assertThrows(NullPointerException.class,
                () -> MediaProcessingPipeline.verifyIntegrityChecksum(
                        null, "abc"));
        assertThrows(NullPointerException.class,
                () -> MediaProcessingPipeline.verifyIntegrityChecksum(
                        "abc", null));
    }

    @Test
    void requiredDownstreamFormatMatchesTask() {
        assertEquals(DerivedAssetFormat.THUMBNAIL_WEBP,
                MediaProcessingPipeline.requiredDownstreamFormat(
                        ProcessingTask.IMAGE_TRANSCODE).orElseThrow());
        assertEquals(DerivedAssetFormat.PDF_PREVIEW,
                MediaProcessingPipeline.requiredDownstreamFormat(
                        ProcessingTask.DOCUMENT_RENDER).orElseThrow());
        assertEquals(DerivedAssetFormat.VIDEO_720P,
                MediaProcessingPipeline.requiredDownstreamFormat(
                        ProcessingTask.VIDEO_TRANSCODE).orElseThrow());
        assertEquals(DerivedAssetFormat.OCR_TEXT,
                MediaProcessingPipeline.requiredDownstreamFormat(
                        ProcessingTask.TEXT_OCR).orElseThrow());
    }

    @Test
    void successAcrossAllTasksYieldsDerivedReady() {
        for (ProcessingTask t : ProcessingTask.values()) {
            ProcessingEngine engine = switch (t) {
                case IMAGE_TRANSCODE -> ProcessingEngine.LIBVIPS;
                case DOCUMENT_RENDER -> ProcessingEngine.GOTENBERG;
                case VIDEO_TRANSCODE -> ProcessingEngine.FFMPEG;
                case TEXT_OCR -> ProcessingEngine.TESSERACT;
            };
            DerivedAssetDecision d = MediaProcessingPipeline.decide(
                    "proc-" + t.wire(),
                    PipelineStatus.READY,
                    t,
                    engine,
                    ProcessingOutcome.SUCCESS,
                    allPassedReport("proc-" + t.wire()),
                    true,
                    false,
                    "media/tenant-1/asset-abc/upload.jpg");
            assertEquals(DerivedAssetStatus.DERIVED_READY,
                    d.status(),
                    "task=" + t + " did not yield DERIVED_READY");
            assertNull(d.failureReason());
        }
    }

    @Test
    void decisionIsImmutableFacts() {
        DerivedAssetDecision d = MediaProcessingPipeline.decide(
                "proc-1",
                PipelineStatus.READY,
                ProcessingTask.IMAGE_TRANSCODE,
                ProcessingEngine.LIBVIPS,
                ProcessingOutcome.SUCCESS,
                allPassedReport("proc-1"),
                true,
                false,
                "media/tenant-1/asset-abc/upload.jpg");
        assertNotNull(d.facts());
        assertThrows(UnsupportedOperationException.class,
                () -> d.facts().put("extra", "value"));
    }

    @Test
    void imageTranscodeResultCompactConstructorRejections() {
        assertThrows(NullPointerException.class,
                () -> ImageTranscodeResult.success(
                        null, DerivedAssetFormat.THUMBNAIL_WEBP, 256));
        assertThrows(IllegalArgumentException.class,
                () -> ImageTranscodeResult.success(
                        "", DerivedAssetFormat.THUMBNAIL_WEBP, 256));
        assertThrows(IllegalArgumentException.class,
                () -> ImageTranscodeResult.success(
                        "p", DerivedAssetFormat.THUMBNAIL_WEBP, 64));
        assertThrows(IllegalArgumentException.class,
                () -> ImageTranscodeResult.success(
                        "p", DerivedAssetFormat.THUMBNAIL_WEBP, 8192));
    }

    @Test
    void videoTranscodeResultBitrateBoundsEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> VideoTranscodeResult.success(
                        "p",
                        VideoPreset.VIDEO_720P,
                        DerivedAssetFormat.VIDEO_720P,
                        100));
        assertThrows(IllegalArgumentException.class,
                () -> VideoTranscodeResult.success(
                        "p",
                        VideoPreset.VIDEO_720P,
                        DerivedAssetFormat.VIDEO_720P,
                        30000));
    }

    @Test
    void ocrResultDpiBoundsEnforced() {
        assertThrows(IllegalArgumentException.class,
                () -> OcrResult.success(
                        "p",
                        OcrLanguage.VI,
                        OcrOutputMode.TEXT,
                        10,
                        100));
        assertThrows(IllegalArgumentException.class,
                () -> OcrResult.success(
                        "p",
                        OcrLanguage.VI,
                        OcrOutputMode.TEXT,
                        10,
                        700));
    }

    @Test
    void derivedAssetDecisionTerminalRefusesBadInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> DerivedAssetDecision.terminal(
                        "", DerivedAssetStatus.DERIVED_READY, null,
                        Map.of(), "x"));
        assertThrows(IllegalArgumentException.class,
                () -> DerivedAssetDecision.terminal(
                        "p",
                        DerivedAssetStatus.DERIVED_READY,
                        ProcessingFailureReason.PROCESS_ERROR,
                        Map.of(),
                        "x"));
        assertThrows(IllegalArgumentException.class,
                () -> DerivedAssetDecision.terminal(
                        "p",
                        DerivedAssetStatus.FAILED,
                        null,
                        Map.of(),
                        "x"));
    }
}