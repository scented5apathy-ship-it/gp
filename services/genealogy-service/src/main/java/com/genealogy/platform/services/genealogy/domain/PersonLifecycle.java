package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set lifecycle state for a {@link Person}. Mirrors
 * {@code contracts/genealogy/person-policy.yaml::spec.lifecycleStates}
 * and `requirements.md` R4.6 (merge is owned by E4.6).
 *
 * <ul>
 *   <li>{@link #ACTIVE} — discoverable, mutations allowed.
 *   <li>{@link #MERGED} — terminal redirect to another person
 *       (set by E4.6 merge workflow). Search / public projections
 *       follow the redirect.
 *   <li>{@link #DELETED} — terminal. Search / public projections
 *       drop the person; audit + reversal history is kept on the
 *       {@code person_history} table.
 * </ul>
 */
public enum PersonLifecycle {
    ACTIVE,
    MERGED,
    DELETED;

    public static PersonLifecycle fromWire(String wire) {
        if (wire == null) {
            return PersonLifecycle.ACTIVE;
        }
        return PersonLifecycle.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
