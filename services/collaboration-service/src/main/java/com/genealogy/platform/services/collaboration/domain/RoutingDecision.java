package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set routing decision for the mixed-collaboration
 * policy. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.routingDecisions` (E6.3) and `requirements.md`
 * R10.4: a mutation is either applied directly (the
 * caller's role grants a direct edit on the role × branch ×
 * resource type pair), routed through the proposal review
 * pipeline (the default), or denied outright.
 */
public enum RoutingDecision {
    DIRECT_EDIT,
    APPROVAL_REQUIRED,
    DENY;

    public static RoutingDecision fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("routingDecision must not be null");
        }
        return RoutingDecision.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
