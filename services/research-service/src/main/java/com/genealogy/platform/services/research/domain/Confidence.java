package com.genealogy.platform.services.research.domain;

import java.util.Objects;

/**
 * Confidence in [0,1] attached to a citation, hypothesis or
 * research task. Mirrors
 * `contracts/research/research-policy.yaml::
 * spec.confidenceBounds` (E6.1) + `requirements.md` R4.4
 * (every claim must carry a confidence in [0,1]).
 *
 * <p>A {@code null} confidence is allowed (the editor may
 * skip the number); out-of-range values are always rejected.
 */
public final class Confidence {

    private Confidence() {
    }

    public static final double MIN = 0.0;
    public static final double MAX = 1.0;

    public static Double requireInRange(Double confidence) {
        if (confidence == null) {
            return null;
        }
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)) {
            throw new IllegalArgumentException(
                    "confidence must be a finite number, got " + confidence);
        }
        if (confidence < MIN || confidence > MAX) {
            throw new IllegalArgumentException(
                    "confidence out of [" + MIN + "," + MAX + "]: " + confidence);
        }
        return confidence;
    }

    public static boolean isInRange(Double confidence) {
        if (confidence == null) {
            return true;
        }
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)) {
            return false;
        }
        return confidence >= MIN && confidence <= MAX;
    }

    public static double requireFinite(double confidence) {
        Objects.requireNonNull(Double.valueOf(confidence), "confidence");
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)) {
            throw new IllegalArgumentException(
                    "confidence must be finite, got " + confidence);
        }
        if (confidence < MIN || confidence > MAX) {
            throw new IllegalArgumentException(
                    "confidence out of [" + MIN + "," + MAX + "]: " + confidence);
        }
        return confidence;
    }
}
