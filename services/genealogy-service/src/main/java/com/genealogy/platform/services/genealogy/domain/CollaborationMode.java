package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set collaboration mode. Mirrors
 * {@code contracts/genealogy/collaboration-policy.yaml::spec.modes}.
 *
 * <ul>
 *   <li>{@link #DIRECT_EDIT} — any contributor with edit role may
 *       mutate directly.
 *   <li>{@link #APPROVAL_REQUIRED} — every mutation must travel
 *       through a {@code collaboration-service} proposal.
 *   <li>{@link #HYBRID_BY_ROLE} — role decides: e.g. owner direct,
 *       contributor through proposal.
 * </ul>
 */
public enum CollaborationMode {
    DIRECT_EDIT,
    APPROVAL_REQUIRED,
    HYBRID_BY_ROLE;

    public static CollaborationMode fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("collaborationMode is required");
        }
        return CollaborationMode.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
