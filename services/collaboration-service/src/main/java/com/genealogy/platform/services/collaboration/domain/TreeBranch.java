package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set tree branch. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.treeBranches` (E6.3) and `requirements.md` R10.4
 * (tenant / tree admin SHALL configure direct edit or
 * approval per role / branch / resource type). The branch
 * is the trust boundary that decides which roles may
 * mutate which resource types.
 */
public enum TreeBranch {
    TRUNK,
    MATERNAL,
    PATERNAL,
    ADOPTIVE,
    STEP,
    GUARDIAN,
    CUSTOM;

    public static TreeBranch fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("treeBranch must not be null");
        }
        return TreeBranch.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
