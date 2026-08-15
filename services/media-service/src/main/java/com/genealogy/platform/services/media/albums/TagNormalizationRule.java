package com.genealogy.platform.services.media.albums;

/**
 * Closed-set tag-normalisation rule.
 *
 * <p>Mirrors {@code contracts/media/albums-linking-policy.yaml
 * ::spec.tagNormalizationRules} (E7.5). The rule is applied
 * BEFORE persistence so the same tag (regardless of the
 * caller's case / whitespace / punctuation) hashes to the
 * same {@code tagNormalised} value; the public-facing
 * display string keeps the original capitalisation.
 */
public enum TagNormalizationRule {
    LOWERCASE_TRIM_DASH;

    public String wire() {
        return name();
    }

    public static TagNormalizationRule fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("wire is null");
        }
        String norm = wire.trim().toUpperCase();
        for (TagNormalizationRule v : values()) {
            if (v.name().equals(norm)) {
                return v;
            }
        }
        throw new IllegalArgumentException("unknown tagNormalizationRule: " + wire);
    }
}