package com.genealogy.platform.services.genealogy.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationshipTest {

    private static final Instant T = Instant.parse("2026-08-10T10:00:00Z");

    private static ParticipantRef ref(String id, ParticipantRole role, String personId) {
        return new ParticipantRef(id, role, personId, false, T);
    }

    private static ParticipantRef unknown(String id, ParticipantRole role) {
        return new ParticipantRef(id, role, null, true, T);
    }

    private static Relationship biologicalParent(String childId) {
        return new Relationship(
                RelationshipId.of("rel-1"), "tenant-1", "tree-1",
                RelationshipKind.BIOLOGICAL_PARENT, null, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(ref("p-1", ParticipantRole.PARENT, "parent-1"),
                        ref("p-2", ParticipantRole.CHILD, childId)),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null);
    }

    private static Relationship partner(String a, String b, PartnerSubKind sub) {
        return new Relationship(
                RelationshipId.of("rel-p"), "tenant-1", "tree-1",
                RelationshipKind.PARTNER, sub, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(ref("p-1", ParticipantRole.PARTNER, a),
                        ref("p-2", ParticipantRole.PARTNER, b)),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null);
    }

    @Test
    void biological_parent_two_participants_ok() {
        Relationship r = biologicalParent("child-1");
        assertEquals(2, r.participants().size());
        assertEquals(RelationshipKind.BIOLOGICAL_PARENT, r.kind());
    }

    @Test
    void biological_parent_intrinsic_rejects_only_one_role() {
        Relationship r = new Relationship(
                RelationshipId.of("rel-1"), "tenant-1", "tree-1",
                RelationshipKind.BIOLOGICAL_PARENT, null, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(ref("p-1", ParticipantRole.PARENT, "parent-1")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null);
        java.util.List<RelationshipInvariants.Finding> findings =
                RelationshipInvariants.checkIntrinsic(r);
        assertTrue(RelationshipInvariants.hasDeny(findings));
    }

    @Test
    void partner_requires_sub_kind() {
        assertThrows(IllegalArgumentException.class, () -> new Relationship(
                RelationshipId.of("rel-p"), "tenant-1", "tree-1",
                RelationshipKind.PARTNER, null, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(ref("p-1", ParticipantRole.PARTNER, "a"),
                        ref("p-2", ParticipantRole.PARTNER, "b")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null));
    }

    @Test
    void partner_sub_kind_forbidden_on_non_partner() {
        assertThrows(IllegalArgumentException.class, () -> new Relationship(
                RelationshipId.of("rel-2"), "tenant-1", "tree-1",
                RelationshipKind.ADOPTIVE_PARENT, PartnerSubKind.MARRIED, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(ref("p-1", ParticipantRole.PARENT, "parent-1"),
                        ref("p-2", ParticipantRole.CHILD, "child-1")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null));
    }

    @Test
    void custom_requires_label() {
        assertThrows(IllegalArgumentException.class, () -> new Relationship(
                RelationshipId.of("rel-c"), "tenant-1", "tree-1",
                RelationshipKind.CUSTOM, null, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(ref("p-1", ParticipantRole.SUBJECT, "a"),
                        ref("p-2", ParticipantRole.SUBJECT, "b")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null));
    }

    @Test
    void custom_label_forbidden_on_non_custom() {
        assertThrows(IllegalArgumentException.class, () -> new Relationship(
                RelationshipId.of("rel-3"), "tenant-1", "tree-1",
                RelationshipKind.SIBLING, null, "label",
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(ref("p-1", ParticipantRole.SIBLING, "a"),
                        ref("p-2", ParticipantRole.SIBLING, "b")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null));
    }

    @Test
    void duplicate_role_person_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new Relationship(
                RelationshipId.of("rel-d"), "tenant-1", "tree-1",
                RelationshipKind.BIOLOGICAL_PARENT, null, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(ref("p-1", ParticipantRole.PARENT, "p-1"),
                        ref("p-2", ParticipantRole.PARENT, "p-1"),
                        ref("p-3", ParticipantRole.CHILD, "child-1")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null));
    }

    @Test
    void participants_cap_8() {
        List<ParticipantRef> tooMany = new ArrayList<>();
        for (int i = 0; i < 9; i += 1) {
            tooMany.add(ref("p-" + i, ParticipantRole.PARENT, "person-" + i));
        }
        assertThrows(IllegalArgumentException.class, () -> new Relationship(
                RelationshipId.of("rel-cap"), "tenant-1", "tree-1",
                RelationshipKind.CUSTOM, null, "polyandry",
                Certainty.HYPOTHESIS, ProvenanceStatus.USER_ENTERED,
                tooMany,
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null));
    }

    @Test
    void empty_participants_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new Relationship(
                RelationshipId.of("rel-empty"), "tenant-1", "tree-1",
                RelationshipKind.SIBLING, null, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null));
    }

    @Test
    void unknown_participant_supported() {
        Relationship r = new Relationship(
                RelationshipId.of("rel-u"), "tenant-1", "tree-1",
                RelationshipKind.BIOLOGICAL_PARENT, null, null,
                Certainty.HYPOTHESIS, ProvenanceStatus.IMPORTED,
                List.of(unknown("p-1", ParticipantRole.PARENT),
                        ref("p-2", ParticipantRole.CHILD, "child-1")),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, null);
        assertEquals(2, r.participants().size());
        assertTrue(r.participants().get(0).unknown());
    }

    @Test
    void unknown_with_person_id_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new ParticipantRef(
                "p-1", ParticipantRole.PARENT, "person-1", true, T));
    }

    @Test
    void person_required_when_not_unknown() {
        assertThrows(IllegalArgumentException.class, () -> new ParticipantRef(
                "p-1", ParticipantRole.PARENT, null, false, T));
    }

    @Test
    void partner_married_active() {
        Relationship r = partner("a", "b", PartnerSubKind.MARRIED);
        assertTrue(r.partnerSubKind().isActive());
    }

    @Test
    void partner_divorced_not_active() {
        assertFalse(PartnerSubKind.DIVORCED.isActive());
    }

    @Test
    void with_ended_closes_validity() {
        Relationship r = biologicalParent("child-1");
        Instant end = T.plusSeconds(3600);
        Relationship closed = r.withEnded(end);
        assertNotNull(closed.validity().validUntil());
        assertEquals(end, closed.validity().validUntil());
        assertEquals(r.version() + 1, closed.version());
    }

    @Test
    void with_updated_increments_version() {
        Relationship r = biologicalParent("child-1");
        Instant at = T.plusSeconds(60);
        Relationship next = r.withUpdated(
                r.participants(),
                Certainty.VERIFIED,
                ProvenanceStatus.VERIFIED_BY_SOURCE,
                null,
                null,
                null,
                at);
        assertEquals(Certainty.VERIFIED, next.certainty());
        assertEquals(ProvenanceStatus.VERIFIED_BY_SOURCE, next.provenance());
        assertEquals(at, next.updatedAt());
        assertEquals(r.version() + 1, next.version());
    }

    @Test
    void diff_returns_changed_fields() {
        Relationship r1 = biologicalParent("child-1");
        Relationship r2 = r1.withUpdated(
                r1.participants(),
                Certainty.VERIFIED,
                null,
                null,
                null,
                null,
                T.plusSeconds(60));
        java.util.LinkedHashSet<String> diff = Relationship.diff(r1, r2);
        assertTrue(diff.contains("certainty"));
        assertFalse(diff.contains("kind"));
    }

    @Test
    void relationship_id_must_be_opaque_shape() {
        assertThrows(IllegalArgumentException.class, () -> RelationshipId.of(""));
        assertThrows(IllegalArgumentException.class, () -> RelationshipId.of("bad id with spaces"));
    }

    @Test
    void temporal_validity_rejects_inverted_bounds() {
        Instant a = T.plusSeconds(60);
        Instant b = T;
        assertThrows(IllegalArgumentException.class, () -> new TemporalValidity(a, b));
    }

    @Test
    void temporal_validity_overlaps_open_ended() {
        Instant a = T;
        Instant b = T.plusSeconds(60);
        TemporalValidity left = new TemporalValidity(a, b);
        TemporalValidity right = new TemporalValidity(b.plusSeconds(1), null);
        assertFalse(left.overlaps(right));
        assertTrue(new TemporalValidity(b, null).overlaps(right));
    }
}
