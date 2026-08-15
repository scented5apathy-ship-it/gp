package com.genealogy.platform.services.media.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Pins the closed-set enums for the media processing
 * pipeline policy. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.processingTasks + processingEngines +
 * processingOutcomes + processingFailureReasons +
 * derivedAssetFormats + ocrLanguages + ocrOutputModes +
 * imagePresets + videoPresets + validationChecks +
 * validationCheckResults + derivedAssetStatuses` (E7.3).
 */
class MediaProcessingClosedSetEnumsTest {

    @Test
    void processingTaskClosedSetMatchesContract() {
        assertEquals(ProcessingTask.IMAGE_TRANSCODE,
                ProcessingTask.fromWire("IMAGE_TRANSCODE"));
        assertEquals(ProcessingTask.DOCUMENT_RENDER,
                ProcessingTask.fromWire("document_render"));
        assertEquals(ProcessingTask.VIDEO_TRANSCODE,
                ProcessingTask.fromWire("VIDEO_TRANSCODE"));
        assertEquals(ProcessingTask.TEXT_OCR,
                ProcessingTask.fromWire("TEXT_OCR"));
        assertEquals(4, ProcessingTask.values().length);
    }

    @Test
    void processingTaskRejectsUnknownAndNull() {
        assertThrows(IllegalArgumentException.class,
                () -> ProcessingTask.fromWire("AUDIO_TRANSCODE"));
        assertThrows(IllegalArgumentException.class,
                () -> ProcessingTask.fromWire(null));
        assertThrows(IllegalArgumentException.class,
                () -> ProcessingTask.fromWire("  "));
    }

    @Test
    void processingEngineClosedSetMatchesContract() {
        assertEquals(ProcessingEngine.LIBVIPS,
                ProcessingEngine.fromWire("LIBVIPS"));
        assertEquals(ProcessingEngine.IMAGEMAGICK,
                ProcessingEngine.fromWire("imagemagick"));
        assertEquals(ProcessingEngine.FFMPEG,
                ProcessingEngine.fromWire("FFMPEG"));
        assertEquals(ProcessingEngine.TESSERACT,
                ProcessingEngine.fromWire("TESSERACT"));
        assertEquals(ProcessingEngine.GOTENBERG,
                ProcessingEngine.fromWire("GOTENBERG"));
        assertEquals(ProcessingEngine.FALLBACK_NONE,
                ProcessingEngine.fromWire("FALLBACK_NONE"));
        assertEquals(6, ProcessingEngine.values().length);
    }

    @Test
    void processingOutcomeClosedSetMatchesContract() {
        assertEquals(ProcessingOutcome.SUCCESS,
                ProcessingOutcome.fromWire("SUCCESS"));
        assertEquals(ProcessingOutcome.PARTIAL,
                ProcessingOutcome.fromWire("partial"));
        assertEquals(ProcessingOutcome.PROCESS_TIMEOUT,
                ProcessingOutcome.fromWire("PROCESS_TIMEOUT"));
        assertEquals(ProcessingOutcome.PROCESS_ERROR,
                ProcessingOutcome.fromWire("PROCESS_ERROR"));
        assertEquals(ProcessingOutcome.UNSUPPORTED_FORMAT,
                ProcessingOutcome.fromWire("UNSUPPORTED_FORMAT"));
        assertEquals(ProcessingOutcome.SANDBOX_DENIED,
                ProcessingOutcome.fromWire("SANDBOX_DENIED"));
        assertEquals(ProcessingOutcome.OUTPUT_KEY_COLLISION,
                ProcessingOutcome.fromWire("OUTPUT_KEY_COLLISION"));
        assertEquals(ProcessingOutcome.VALIDATION_FAILED,
                ProcessingOutcome.fromWire("VALIDATION_FAILED"));
        assertEquals(8, ProcessingOutcome.values().length);
    }

    @Test
    void processingFailureReasonClosedSetMatchesContract() {
        assertEquals(ProcessingFailureReason.PROCESS_TIMEOUT,
                ProcessingFailureReason.fromWire("PROCESS_TIMEOUT"));
        assertEquals(ProcessingFailureReason.PROCESS_ERROR,
                ProcessingFailureReason.fromWire("PROCESS_ERROR"));
        assertEquals(ProcessingFailureReason.PROCESSOR_UNAVAILABLE,
                ProcessingFailureReason.fromWire("PROCESSOR_UNAVAILABLE"));
        assertEquals(ProcessingFailureReason.SANDBOX_NETWORK_DENIED,
                ProcessingFailureReason.fromWire("SANDBOX_NETWORK_DENIED"));
        assertEquals(ProcessingFailureReason.SANDBOX_RESOURCE_LIMIT,
                ProcessingFailureReason.fromWire("SANDBOX_RESOURCE_LIMIT"));
        assertEquals(ProcessingFailureReason.OBJECT_TOO_LARGE,
                ProcessingFailureReason.fromWire("OBJECT_TOO_LARGE"));
        assertEquals(ProcessingFailureReason.INTEGRITY_CHECKSUM_MISMATCH,
                ProcessingFailureReason.fromWire("INTEGRITY_CHECKSUM_MISMATCH"));
        assertEquals(ProcessingFailureReason.VALIDATION_FAILED,
                ProcessingFailureReason.fromWire("VALIDATION_FAILED"));
        assertEquals(ProcessingFailureReason.EXIF_PII_LEAKED,
                ProcessingFailureReason.fromWire("EXIF_PII_LEAKED"));
        assertEquals(ProcessingFailureReason.CONTAINER_CORRUPT,
                ProcessingFailureReason.fromWire("CONTAINER_CORRUPT"));
        assertEquals(ProcessingFailureReason.UNSUPPORTED_DERIVED_FORMAT,
                ProcessingFailureReason.fromWire("UNSUPPORTED_DERIVED_FORMAT"));
        assertEquals(ProcessingFailureReason.DNA_OBJECT_REJECTED,
                ProcessingFailureReason.fromWire("DNA_OBJECT_REJECTED"));
        assertEquals(ProcessingFailureReason.DERIVED_OBJECT_KEY_COLLISION,
                ProcessingFailureReason.fromWire("DERIVED_OBJECT_KEY_COLLISION"));
        assertEquals(13, ProcessingFailureReason.values().length);
    }

    @Test
    void derivedAssetFormatClosedSetMatchesContract() {
        assertEquals(DerivedAssetFormat.THUMBNAIL_WEBP,
                DerivedAssetFormat.fromWire("THUMBNAIL_WEBP"));
        assertEquals(DerivedAssetFormat.THUMBNAIL_AVIF,
                DerivedAssetFormat.fromWire("THUMBNAIL_AVIF"));
        assertEquals(DerivedAssetFormat.THUMBNAIL_JPEG,
                DerivedAssetFormat.fromWire("THUMBNAIL_JPEG"));
        assertEquals(DerivedAssetFormat.PDF_PREVIEW,
                DerivedAssetFormat.fromWire("PDF_PREVIEW"));
        assertEquals(DerivedAssetFormat.VIDEO_360P,
                DerivedAssetFormat.fromWire("VIDEO_360P"));
        assertEquals(DerivedAssetFormat.VIDEO_720P,
                DerivedAssetFormat.fromWire("VIDEO_720P"));
        assertEquals(DerivedAssetFormat.VIDEO_1080P,
                DerivedAssetFormat.fromWire("VIDEO_1080P"));
        assertEquals(DerivedAssetFormat.OCR_TEXT,
                DerivedAssetFormat.fromWire("OCR_TEXT"));
        assertEquals(8, DerivedAssetFormat.values().length);
    }

    @Test
    void ocrLanguageClosedSetMatchesContract() {
        assertEquals(OcrLanguage.EN,
                OcrLanguage.fromWire("EN"));
        assertEquals(OcrLanguage.VI,
                OcrLanguage.fromWire("vi"));
        assertEquals(OcrLanguage.FR,
                OcrLanguage.fromWire("FR"));
        assertEquals(OcrLanguage.DE,
                OcrLanguage.fromWire("DE"));
        assertEquals(OcrLanguage.ZH,
                OcrLanguage.fromWire("ZH"));
        assertEquals(5, OcrLanguage.values().length);
        assertThrows(IllegalArgumentException.class,
                () -> OcrLanguage.fromWire("JA"));
    }

    @Test
    void ocrOutputModeClosedSetMatchesContract() {
        assertEquals(OcrOutputMode.TEXT,
                OcrOutputMode.fromWire("TEXT"));
        assertEquals(OcrOutputMode.HOCR,
                OcrOutputMode.fromWire("hocr"));
        assertEquals(OcrOutputMode.PDF_SEARCHABLE,
                OcrOutputMode.fromWire("PDF_SEARCHABLE"));
        assertEquals(3, OcrOutputMode.values().length);
    }

    @Test
    void imagePresetClosedSetMatchesContract() {
        assertEquals(ImagePreset.THUMBNAIL_128,
                ImagePreset.fromWire("THUMBNAIL_128"));
        assertEquals(ImagePreset.THUMBNAIL_256,
                ImagePreset.fromWire("THUMBNAIL_256"));
        assertEquals(ImagePreset.PREVIEW_1024,
                ImagePreset.fromWire("PREVIEW_1024"));
        assertEquals(ImagePreset.PREVIEW_2048,
                ImagePreset.fromWire("PREVIEW_2048"));
        assertEquals(ImagePreset.ORIGINAL,
                ImagePreset.fromWire("ORIGINAL"));
        assertEquals(5, ImagePreset.values().length);
    }

    @Test
    void videoPresetClosedSetMatchesContract() {
        assertEquals(VideoPreset.AUDIO_ONLY,
                VideoPreset.fromWire("AUDIO_ONLY"));
        assertEquals(VideoPreset.VIDEO_360P,
                VideoPreset.fromWire("VIDEO_360P"));
        assertEquals(VideoPreset.VIDEO_720P,
                VideoPreset.fromWire("VIDEO_720P"));
        assertEquals(VideoPreset.VIDEO_1080P,
                VideoPreset.fromWire("VIDEO_1080P"));
        assertEquals(VideoPreset.VIDEO_4K,
                VideoPreset.fromWire("VIDEO_4K"));
        assertEquals(5, VideoPreset.values().length);
    }

    @Test
    void validationCheckClosedSetMatchesContract() {
        assertEquals(ValidationCheck.SIGNATURE_UP_TO_DATE,
                ValidationCheck.fromWire("SIGNATURE_UP_TO_DATE"));
        assertEquals(ValidationCheck.INTEGRITY_CHECKSUM,
                ValidationCheck.fromWire("INTEGRITY_CHECKSUM"));
        assertEquals(ValidationCheck.MAGIC_BYTES,
                ValidationCheck.fromWire("MAGIC_BYTES"));
        assertEquals(ValidationCheck.CONTAINER_INTEGRITY,
                ValidationCheck.fromWire("CONTAINER_INTEGRITY"));
        assertEquals(ValidationCheck.EXIF_SCRUBBED,
                ValidationCheck.fromWire("EXIF_SCRUBBED"));
        assertEquals(ValidationCheck.DNA_BUCKET_ISOLATED,
                ValidationCheck.fromWire("DNA_BUCKET_ISOLATED"));
        assertEquals(6, ValidationCheck.values().length);
    }

    @Test
    void validationCheckResultClosedSetMatchesContract() {
        assertEquals(ValidationCheckResult.PASS,
                ValidationCheckResult.fromWire("PASS"));
        assertEquals(ValidationCheckResult.WARN,
                ValidationCheckResult.fromWire("warn"));
        assertEquals(ValidationCheckResult.FAIL,
                ValidationCheckResult.fromWire("FAIL"));
        assertEquals(ValidationCheckResult.SKIPPED,
                ValidationCheckResult.fromWire("SKIPPED"));
        assertEquals(4, ValidationCheckResult.values().length);
    }

    @Test
    void derivedAssetStatusClosedSetMatchesContract() {
        assertEquals(DerivedAssetStatus.PENDING,
                DerivedAssetStatus.fromWire("PENDING"));
        assertEquals(DerivedAssetStatus.PROCESSING,
                DerivedAssetStatus.fromWire("processing"));
        assertEquals(DerivedAssetStatus.VALIDATING,
                DerivedAssetStatus.fromWire("VALIDATING"));
        assertEquals(DerivedAssetStatus.DERIVED_READY,
                DerivedAssetStatus.fromWire("DERIVED_READY"));
        assertEquals(DerivedAssetStatus.FAILED,
                DerivedAssetStatus.fromWire("FAILED"));
        assertEquals(DerivedAssetStatus.QUARANTINED_RETAIN,
                DerivedAssetStatus.fromWire("QUARANTINED_RETAIN"));
        assertEquals(6, DerivedAssetStatus.values().length);
    }

    @Test
    void validationReportAllPassedAndAnyFailed() {
        Map<ValidationCheck, ValidationCheckResult> results =
                new EnumMap<>(ValidationCheck.class);
        for (ValidationCheck c : ValidationCheck.values()) {
            results.put(c, ValidationCheckResult.PASS);
        }
        ValidationReport report = new ValidationReport(
                "proc-1", results, Map.of());
        assertTrue(report.allPassed());
        assertFalse(report.anyFailed());

        results.put(ValidationCheck.EXIF_SCRUBBED,
                ValidationCheckResult.FAIL);
        ValidationReport report2 = new ValidationReport(
                "proc-2", results, Map.of());
        assertFalse(report2.allPassed());
        assertTrue(report2.anyFailed());
    }
}