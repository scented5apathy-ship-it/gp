package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * Closed-set pronoun declaration attached to a {@link Person}.
 * Mirrors {@code contracts/genealogy/person-policy.yaml::spec.pronouns}
 * and `requirements.md` R4.1 + R18.1 (non-binary, self-described,
 * not-specified). A person may carry up to
 * {@code spec.maxPronounsPerPerson} pronouns (default 4).
 */
public enum Pronoun {
    HE_HIM,
    SHE_HER,
    THEY_THEM,
    ZE_ZIR,
    XE_XEM,
    SELF_DESCRIBED,
    NOT_SPECIFIED;

    public static Pronoun fromWire(String wire) {
        if (wire == null) {
            return Pronoun.NOT_SPECIFIED;
        }
        return Pronoun.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    public static final class WithFreeText {
        private final Pronoun pronoun;
        private final String freeText;
        private final Instant declaredAt;

        public WithFreeText(Pronoun pronoun, String freeText, Instant declaredAt) {
            this.pronoun = Objects.requireNonNull(pronoun, "pronoun");
            this.declaredAt = Objects.requireNonNull(declaredAt, "declaredAt");
            if (pronoun == Pronoun.SELF_DESCRIBED) {
                Objects.requireNonNull(freeText, "freeText required for SELF_DESCRIBED");
                if (freeText.isBlank() || freeText.length() > 256) {
                    throw new IllegalArgumentException(
                            "SELF_DESCRIBED freeText must be 1..256 chars");
                }
            }
            this.freeText = freeText;
        }

        public Pronoun pronoun() {
            return pronoun;
        }

        public String freeText() {
            return freeText;
        }

        public Instant declaredAt() {
            return declaredAt;
        }
    }
}
