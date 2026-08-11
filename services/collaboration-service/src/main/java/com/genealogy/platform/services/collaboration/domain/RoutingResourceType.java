package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set resource type re-used by the mixed-collaboration
 * routing policy. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.resourceTypes` (E6.3) and `requirements.md` R10.4
 * (per-role / per-branch / per-resource-type direct edit vs
 * approval). The baseline `ProposalKind` enum (E6.2) covers
 * the same vocabulary but in a proposal-centric context; this
 * enum is the routing-centric counterpart.
 */
public enum RoutingResourceType {
    PERSON,
    RELATIONSHIP,
    LIFE_EVENT,
    CLAIM,
    SOURCE,
    CITATION,
    TREE_VISIBILITY;

    public static RoutingResourceType fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("resourceType must not be null");
        }
        return RoutingResourceType.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
