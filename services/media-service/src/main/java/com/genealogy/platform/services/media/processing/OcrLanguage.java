package com.genealogy.platform.services.media.processing;

/**
 * Closed-set enumeration of OCR language packs. Mirrors
 * `contracts/media/media-processing-pipeline-policy.yaml
 * ::spec.ocrLanguages` (E7.3) +
 * `requirements.md` R9.3 (Tesseract OCR theo language packs).
 *
 * <p>The worker MUST pre-resolve the language pack at workflow
 * start and refuse unknown languages per
 * {@code ocrLanguagePacksPinned=true}. Adding a new language
 * requires an ADR supersession (Avro + Apicurio BACKWARD
 * evolution per ADR-E0.5-08).
 */
public enum OcrLanguage {
    EN,
    VI,
    FR,
    DE,
    ZH;

    public static OcrLanguage fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire must not be null");
        }
        try {
            return OcrLanguage.valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "unknown OcrLanguage from wire: " + wire, ex);
        }
    }

    public String wire() {
        return name();
    }
}