package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set privacy level for a {@link Person}. Mirrors
 * {@code contracts/genealogy/person-policy.yaml::spec.privacyLevels}
 * and `requirements.md` R4.4.
 *
 * <p>The level controls what data-subject visibility the
 * aggregate carries. {@link #PUBLIC} means the data has been
 * cleared for PUBLIC projection; the renderer / BFF MUST still
 * re-evaluate living / minor status before serving.
 */
public enum PrivacyLevel {
    PRIVATE,
    TREE_DEFAULT,
    UNLISTED,
    PUBLIC;

    public static PrivacyLevel fromWire(String wire) {
        if (wire == null) {
            return PrivacyLevel.PRIVATE;
        }
        return PrivacyLevel.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    /** True when the level forces PUBLIC projection redaction. */
    public boolean requiresProjectionRedaction() {
        return this == PRIVATE || this == UNLISTED;
    }
}
