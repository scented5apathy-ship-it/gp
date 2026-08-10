package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Closed-set name kind attached to a {@link Person}. Mirrors
 * {@code contracts/genealogy/person-policy.yaml::spec.nameKinds}
 * and `requirements.md` R4.1.
 *
 * <p>A person may carry up to {@code spec.maxNamesPerPerson}
 * names (the default is 16) across the closed-set of kinds.
 * Exactly one {@link #BIRTH} and at most one {@link #PREFERRED}
 * may be attached at any given time (enforced at the aggregate
 * level).
 */
public enum NameKind {
    BIRTH,
    PREFERRED,
    MARRIED,
    RELIGIOUS,
    PROFESSIONAL,
    ALIAS,
    NICKNAME;

    public static NameKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("nameKind is required");
        }
        return NameKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    public static NameKind require(NameKind value) {
        Objects.requireNonNull(value, "nameKind");
        return value;
    }
}
