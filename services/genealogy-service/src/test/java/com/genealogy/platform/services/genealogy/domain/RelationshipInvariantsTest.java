package com.genealogy.platform.services.genealogy.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationshipInvariantsTest {

    private static final Instant T = Instant.parse("2026-08-10T10:00:00Z");

    private static ParticipantRef p(String id, ParticipantRole role, String personId) {
        return new ParticipantRef(id, role, personId, false, T);
    }

    private static ParticipantRef unknown(String id, ParticipantRole role) {
        return new ParticipantRef(id, role, null, true, T);
    }

    private static Relationship parentChild(
            String relId, String parentPerson, String childPerson) {
        return new Relationship(
                RelationshipId.of(relId), "tenant-1", "tree-1",
                RelationshipKind.BIOLOGICAL_PARENT, null, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(p("p-" + parentPerson, ParticipantRole.PARENT, parentPerson),
                        p("c-" + childPerson, ParticipantRole.CHILD, childPerson)),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null);
    }

    private static Relationship partner(String relId, String a, String b,
            PartnerSubKind sub, Instant from, Instant until) {
        return new Relationship(
                RelationshipId.of(relId), "tenant-1", "tree-1",
                RelationshipKind.PARTNER, sub, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(p("pa", ParticipantRole.PARTNER, a),
                        p("pb", ParticipantRole.PARTNER, b)),
                new TemporalValidity(from, until),
                T, T, "user-1", 1L, null);
    }

    @Test
    void intrinsic_partner_requires_two_partners() {
        Relationship r = new Relationship(
                RelationshipId.of("rel-1"), "tenant-1", "tree-1",
                RelationshipKind.PARTNER, PartnerSubKind.MARRIED, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(p("p1", ParticipantRole.PARTNER, "a")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null);
        List<RelationshipInvariants.Finding> findings =
                RelationshipInvariants.checkIntrinsic(r);
        assertTrue(RelationshipInvariants.hasDeny(findings));
        assertTrue(findings.stream()
                .anyMatch(f -> f.code() == RelationshipConflictCode.PARTNER_REQUIRES_TWO_PARTICIPANTS));
    }

    @Test
    void intrinsic_guardian_requires_guardian_and_ward() {
        Relationship r = new Relationship(
                RelationshipId.of("rel-g"), "tenant-1", "tree-1",
                RelationshipKind.GUARDIAN, null, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(p("p1", ParticipantRole.GUARDIAN, "g1")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null);
        List<RelationshipInvariants.Finding> findings =
                RelationshipInvariants.checkIntrinsic(r);
        assertTrue(RelationshipInvariants.hasDeny(findings));
        assertTrue(findings.stream()
                .anyMatch(f -> f.code() == RelationshipConflictCode.PARTICIPANT_ROLE_MISMATCH));
    }

    @Test
    void intrinsic_sibling_requires_two_siblings() {
        Relationship r = new Relationship(
                RelationshipId.of("rel-s"), "tenant-1", "tree-1",
                RelationshipKind.SIBLING, null, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(p("p1", ParticipantRole.SIBLING, "a")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null);
        List<RelationshipInvariants.Finding> findings =
                RelationshipInvariants.checkIntrinsic(r);
        assertTrue(RelationshipInvariants.hasDeny(findings));
    }

    @Test
    void intrinsic_ok_for_valid_biological_parent() {
        Relationship r = parentChild("rel-1", "parent-1", "child-1");
        List<RelationshipInvariants.Finding> findings =
                RelationshipInvariants.checkIntrinsic(r);
        assertFalse(RelationshipInvariants.hasDeny(findings));
    }

    @Test
    void chronological_overlap_parental_warns() {
        Relationship older = parentChild("rel-1", "parent-1", "child-1");
        Relationship newer = parentChild("rel-2", "parent-2", "child-1");
        List<RelationshipInvariants.Finding> findings =
                RelationshipInvariants.checkChronologicalOverlap(newer, List.of(older));
        assertFalse(RelationshipInvariants.hasDeny(findings));
        assertTrue(findings.stream()
                .anyMatch(f -> f.code() == RelationshipConflictCode.OVERLAPPING_PARENTAL_VALIDITY));
    }

    @Test
    void chronological_overlap_partner_is_info_only() {
        Relationship p1 = partner("rel-a", "x", "y", PartnerSubKind.MARRIED, T, null);
        Relationship p2 = partner("rel-b", "x", "y", PartnerSubKind.UNMARRIED, T, null);
        List<RelationshipInvariants.Finding> findings =
                RelationshipInvariants.checkChronologicalOverlap(p2, List.of(p1));
        assertFalse(RelationshipInvariants.hasDeny(findings));
        assertTrue(findings.stream()
                .anyMatch(f -> f.code() == RelationshipConflictCode.PARTNER_OVERLAP_WITH_ACTIVE
                        && f.severity() == RelationshipInvariants.Severity.INFO));
    }

    @Test
    void cycle_rejected_when_parent_is_descendant() {
        Relationship aToB = parentChild("rel-ab", "p1", "p2");
        Relationship bToA = new Relationship(
                RelationshipId.of("rel-ba"), "tenant-1", "tree-1",
                RelationshipKind.BIOLOGICAL_PARENT, null, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(p("p-x", ParticipantRole.PARENT, "p2"),
                        p("p-y", ParticipantRole.CHILD, "p1")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null);
        List<RelationshipInvariants.Finding> findings =
                RelationshipInvariants.checkCycle(bToA, List.of(aToB));
        assertTrue(RelationshipInvariants.hasDeny(findings));
        assertTrue(findings.stream()
                .anyMatch(f -> f.code() == RelationshipConflictCode.CYCLE));
    }

    @Test
    void check_all_short_circuits_on_deny() {
        Relationship r = new Relationship(
                RelationshipId.of("rel-bad"), "tenant-1", "tree-1",
                RelationshipKind.BIOLOGICAL_PARENT, null, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(p("p1", ParticipantRole.PARENT, "a")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null);
        List<RelationshipInvariants.Finding> findings =
                RelationshipInvariants.checkAll(r, List.of());
        assertTrue(RelationshipInvariants.hasDeny(findings));
    }

    @Test
    void check_all_passes_on_valid_parent() {
        Relationship r = parentChild("rel-ok", "p", "c");
        List<RelationshipInvariants.Finding> findings =
                RelationshipInvariants.checkAll(r, List.of());
        assertFalse(RelationshipInvariants.hasDeny(findings));
    }

    @Test
    void unknown_participant_kept_in_overlap_check() {
        Relationship older = new Relationship(
                RelationshipId.of("rel-unk-1"), "tenant-1", "tree-1",
                RelationshipKind.BIOLOGICAL_PARENT, null, null,
                Certainty.HYPOTHESIS, ProvenanceStatus.IMPORTED,
                List.of(unknown("p-1", ParticipantRole.PARENT),
                        p("c-1", ParticipantRole.CHILD, "child-1")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null);
        Relationship newer = new Relationship(
                RelationshipId.of("rel-unk-2"), "tenant-1", "tree-1",
                RelationshipKind.BIOLOGICAL_PARENT, null, null,
                Certainty.HYPOTHESIS, ProvenanceStatus.IMPORTED,
                List.of(unknown("p-2", ParticipantRole.PARENT),
                        p("c-1", ParticipantRole.CHILD, "child-1")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null);
        List<RelationshipInvariants.Finding> findings =
                RelationshipInvariants.checkAll(newer, List.of(older));
        assertNotNull(findings);
        assertFalse(RelationshipInvariants.hasDeny(findings));
    }

    @Test
    void partner_active_sub_kinds_recognised() {
        for (PartnerSubKind k : List.of(PartnerSubKind.MARRIED, PartnerSubKind.CIVIL_UNION,
                PartnerSubKind.COMMON_LAW, PartnerSubKind.UNMARRIED)) {
            assertTrue(k.isActive(), "expected " + k + " active");
        }
    }
}
