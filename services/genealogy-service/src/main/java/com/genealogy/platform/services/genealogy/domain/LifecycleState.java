package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set lifecycle state for a {@link Tree} aggregate.
 * Mirrors {@code contracts/genealogy/tree-policy.yaml::spec.lifecycleStates}.
 *
 * <ul>
 *   <li>{@link #ACTIVE} — discoverable, mutations allowed.
 *   <li>{@link #ARCHIVED} — soft-deleted / hidden. Projections
 *       drop the tree until {@code TreeRestored}.
 *   <li>{@link #DELETED} — terminal. Emits {@code TreeDeleted}.
 * </ul>
 */
public enum LifecycleState {
    ACTIVE,
    ARCHIVED,
    DELETED;

    public static LifecycleState fromWire(String wire) {
        if (wire == null) {
            return ACTIVE;
        }
        return LifecycleState.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
