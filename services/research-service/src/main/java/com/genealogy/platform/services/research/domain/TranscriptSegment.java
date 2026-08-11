package com.genealogy.platform.services.research.domain;

import java.util.Objects;

/**
 * Verbatim transcript segment attached to a {@code Citation}
 * of {@link CitationQuality#TRANSCRIPT} quality. Mirrors
 * `contracts/research/research-policy.yaml::
 * spec.transcriptSegments` (E6.1) + `requirements.md` R8.1
 * (transcript).
 *
 * <p>A transcript segment is a short, attributed piece of text
 * — typically a single line from a register or a single
 * sentence from a witness interview. The invariant service
 * enforces a hard line cap and a length cap so the audit log
 * cannot be weaponised into dumping the entire source.
 */
public record TranscriptSegment(
        int lineNumber,
        String text,
        String originalScript,
        String translationLocale,
        String speaker) {

    public static final int MAX_LINE_NUMBER = 100_000;
    public static final int MAX_TEXT_LENGTH = 4_096;

    public TranscriptSegment {
        Objects.requireNonNull(text, "text");
        if (lineNumber <= 0 || lineNumber > MAX_LINE_NUMBER) {
            throw new IllegalArgumentException(
                    "lineNumber out of [1," + MAX_LINE_NUMBER + "]: " + lineNumber);
        }
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "text exceeds " + MAX_TEXT_LENGTH + " characters");
        }
        for (int i = 0; i < text.length(); i += 1) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                throw new IllegalArgumentException(
                        "text must not contain newline characters (line = "
                                + lineNumber + ")");
            }
        }
        if (originalScript != null && originalScript.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "originalScript exceeds " + MAX_TEXT_LENGTH + " characters");
        }
        if (originalScript != null && originalScript.isBlank()) {
            originalScript = null;
        }
        if (translationLocale != null && !translationLocale.isBlank()) {
            if (!translationLocale.matches("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?")) {
                throw new IllegalArgumentException(
                        "translationLocale must match BCP-47 tag: " + translationLocale);
            }
        } else {
            translationLocale = null;
        }
        if (speaker != null && speaker.length() > 256) {
            throw new IllegalArgumentException("speaker exceeds 256 characters");
        }
        if (speaker != null && speaker.isBlank()) {
            speaker = null;
        }
    }

    public boolean hasOriginalScript() {
        return originalScript != null && !originalScript.isBlank();
    }

    public boolean hasTranslation() {
        return translationLocale != null && !translationLocale.isBlank();
    }
}
