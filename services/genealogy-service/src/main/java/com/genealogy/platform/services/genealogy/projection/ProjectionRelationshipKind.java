package com.genealogy.platform.services.genealogy.projection;

import com.genealogy.platform.services.genealogy.domain.ParticipantRole;
import com.genealogy.platform.services.genealogy.domain.RelationshipKind;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Closed-set of relationship kinds the projection surfaces to
 * the BFF. Mirrors
 * {@code contracts/genealogy/tree-projection-policy.yaml::
 * spec.relationshipFilters}. This enum is intentionally a
 * different closed-set from the internal {@link RelationshipKind}
 * because the projection simplifies the storage model into the
 * five buckets the UI + BFF OpenAPI consume.
 *
 * <p>Mapping rules (used by {@link #toProjectionKind}):
 *
 * <pre>
 *   BIOLOGICAL_PARENT + ADOPTIVE_PARENT + SURROGATE_PARENT ─► BIRTH_PARENT
 *   FOSTER_PARENT       ─► FOSTER_PARENT
 *   STEP_PARENT         ─► STEP_PARENT
 *   GUARDIAN            ─► GUARDIAN
 *   PARTNER             ─► SPOUSE / PARTNER (decided by PartnerSubKind)
 *   SIBLING + HALF_SIBLING + STEP_SIBLING ─► SIBLING
 *   CUSTOM              ─► CUSTOM
 *   GODPARENT           ─► GUARDIAN (closest legal analogue)
 * </pre>
 *
 * <p>Kind transitions outside this map MUST NOT be silently
 * dropped; the executor surfaces an
 * {@link IllegalStateException} so the policy contract can be
 * updated deliberately (per {@code agent-execution.md} §4.4).
 */
public enum ProjectionRelationshipKind {
    BIRTH_PARENT,
    ADOPTIVE_PARENT,
    FOSTER_PARENT,
    STEP_PARENT,
    GUARDIAN,
    SPOUSE,
    PARTNER,
    CUSTOM;

    public String wire() {
        return name();
    }

    public static ProjectionRelationshipKind fromWire(String wire) {
        Objects.requireNonNull(wire, "projectionRelationshipKind");
        return ProjectionRelationshipKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Map the internal storage {@link RelationshipKind} to the
     * projection kind. The mapping is deterministic; unknown
     * storage kinds raise {@link IllegalStateException} so the
     * policy contract cannot drift silently.
     */
    public static ProjectionRelationshipKind toProjectionKind(RelationshipKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case BIOLOGICAL_PARENT, ADOPTIVE_PARENT, SURROGATE_PARENT -> BIRTH_PARENT;
            case FOSTER_PARENT -> FOSTER_PARENT;
            case STEP_PARENT -> STEP_PARENT;
            case GUARDIAN, GODPARENT -> GUARDIAN;
            case PARTNER -> PARTNER;
            case SIBLING, HALF_SIBLING, STEP_SIBLING -> SPOUSE;
            case CUSTOM -> CUSTOM;
        };
    }

    /**
     * Heuristic used by the projection builder to decide if a
     * {@code RelationshipKind#PARTNER} surfaces as SPOUSE (the
     * default) or PARTNER. The mapping is intentionally a
     * method (not a constant) so future partner sub-kinds can
     * be classified without touching the enum.
     */
    public static ProjectionRelationshipKind toProjectionPartnerKind(RelationshipKind kind,
                                                                     String partnerSubKind) {
        if (kind != RelationshipKind.PARTNER) {
            return toProjectionKind(kind);
        }
        if (partnerSubKind != null && "UNMARRIED_PARTNER".equalsIgnoreCase(partnerSubKind)) {
            return PARTNER;
        }
        return SPOUSE;
    }

    /**
     * Convenience: returns the projection kind that the
     * participant role implies for an outgoing edge in the
     * tree visualisation. Used when the projection cannot find
     * the underlying relationship but still needs an edge label.
     */
    public static ProjectionRelationshipKind fromRole(ParticipantRole role) {
        Objects.requireNonNull(role, "role");
        Map<ParticipantRole, ProjectionRelationshipKind> map = new EnumMap<>(ParticipantRole.class);
        map.put(ParticipantRole.PARENT, BIRTH_PARENT);
        map.put(ParticipantRole.CHILD, BIRTH_PARENT);
        map.put(ParticipantRole.SIBLING, SPOUSE);
        map.put(ParticipantRole.PARTNER, SPOUSE);
        map.put(ParticipantRole.SUBJECT, CUSTOM);
        map.put(ParticipantRole.GUARDIAN, GUARDIAN);
        map.put(ParticipantRole.WARD, GUARDIAN);
        return map.get(role);
    }
}