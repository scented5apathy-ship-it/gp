package com.genealogy.platform.services.genealogy.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeEventTest {

    private static final Instant T = Instant.parse("2026-08-10T10:00:00Z");

    private static EventParticipant subject(String id, String personId) {
        return new EventParticipant(id, EventParticipantRole.SUBJECT, personId, false, T);
    }

    private static EventParticipant witness(String id, String personId) {
        return new EventParticipant(id, EventParticipantRole.WITNESS, personId, false, T);
    }

    private static LifeEvent baseEvent(
            LifeEventKind kind,
            String customLabel,
            Certainty certainty,
            ProvenanceStatus provenance,
            EventPrivacy privacy,
            List<EventParticipant> participants,
            DateValue date) {
        return new LifeEvent(
                LifeEventId.of("ev-1"),
                "tenant-1",
                "tree-1",
                kind,
                customLabel,
                certainty,
                provenance,
                privacy,
                participants,
                date,
                null,
                null,
                T,
                T,
                "user-1",
                1L,
                null);
    }

    @Test
    void wedding_with_subjects_witness_ok() {
        LifeEvent ev = baseEvent(
                LifeEventKind.MARRIAGE,
                null,
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                EventPrivacy.UNLISTED,
                List.of(
                        subject("p-1", "person-a"),
                        new EventParticipant("p-2", EventParticipantRole.PARTNER, "person-b", false, T),
                        witness("p-3", "person-c")),
                null);
        assertEquals(LifeEventKind.MARRIAGE, ev.kind());
        assertEquals(3, ev.participants().size());
    }

    @Test
    void recurring_memorial_requires_date() {
        assertThrows(IllegalArgumentException.class, () -> baseEvent(
                LifeEventKind.RECURRING_MEMORIAL,
                null,
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                EventPrivacy.PRIVATE,
                List.of(subject("p-1", "person-a")),
                null));
    }

    @Test
    void recurring_memorial_with_date_ok() {
        DateValue date = new DateValue(
                "2025",
                CalendarId.GREGORIAN,
                DateQualifier.EXACT,
                "UTC",
                NormalizedInterval.point(T),
                Certainty.VERIFIED,
                T);
        LifeEvent ev = new LifeEvent(
                LifeEventId.of("ev-1"),
                "tenant-1",
                "tree-1",
                LifeEventKind.RECURRING_MEMORIAL,
                null,
                Certainty.VERIFIED,
                ProvenanceStatus.VERIFIED_BY_SOURCE,
                EventPrivacy.PRIVATE,
                List.of(subject("p-1", "person-a")),
                date,
                null,
                null,
                T,
                T,
                "user-1",
                1L,
                null);
        assertEquals(date, ev.date());
        assertEquals(1, ev.participants().size());
    }

    @Test
    void custom_kind_requires_label() {
        assertThrows(IllegalArgumentException.class, () -> baseEvent(
                LifeEventKind.CUSTOM,
                null,
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                EventPrivacy.PRIVATE,
                List.of(subject("p-1", "person-a")),
                null));
        assertThrows(IllegalArgumentException.class, () -> baseEvent(
                LifeEventKind.CUSTOM,
                "  ",
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                EventPrivacy.PRIVATE,
                List.of(subject("p-1", "person-a")),
                null));
    }

    @Test
    void imported_event_cannot_be_verified() {
        assertThrows(IllegalArgumentException.class, () -> baseEvent(
                LifeEventKind.DEATH,
                null,
                Certainty.VERIFIED,
                ProvenanceStatus.IMPORTED,
                EventPrivacy.PRIVATE,
                List.of(subject("p-1", "person-a")),
                null));
    }

    @Test
    void invariants_warn_when_no_subject() {
        LifeEvent ev = baseEvent(
                LifeEventKind.EDUCATION,
                null,
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                EventPrivacy.PRIVATE,
                List.of(witness("p-1", "person-a")),
                null);
        List<LifeEventInvariants.Finding> findings = LifeEventInvariants.checkIntrinsic(ev);
        assertFalse(LifeEventInvariants.hasDeny(findings));
        assertTrue(findings.stream()
                .anyMatch(f -> f.code() == LifeEventInvariants.ConflictCode.NO_SUBJECT));
    }

    @Test
    void participants_above_cap_rejected() {
        java.util.List<EventParticipant> many = new java.util.ArrayList<>();
        for (int i = 0; i < 17; i += 1) {
            many.add(new EventParticipant("p-" + i, EventParticipantRole.WITNESS, "person-" + i, false, T));
        }
        assertThrows(IllegalArgumentException.class, () -> baseEvent(
                LifeEventKind.MARRIAGE,
                null,
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                EventPrivacy.PRIVATE,
                many,
                null));
    }

    @Test
    void description_above_cap_rejected() {
        String big = "a".repeat(2049);
        assertThrows(IllegalArgumentException.class, () -> new LifeEvent(
                LifeEventId.of("ev-1"),
                "tenant-1",
                "tree-1",
                LifeEventKind.MARRIAGE,
                null,
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                EventPrivacy.PRIVATE,
                List.of(subject("p-1", "person-a")),
                null,
                null,
                big,
                T,
                T,
                "user-1",
                1L,
                null));
    }

    @Test
    void with_updated_bumps_version_and_keeps_id() {
        LifeEvent ev = baseEvent(
                LifeEventKind.MARRIAGE,
                null,
                Certainty.ASSERTED,
                ProvenanceStatus.USER_ENTERED,
                EventPrivacy.PRIVATE,
                List.of(subject("p-1", "person-a")),
                null);
        LifeEvent ev2 = ev.withUpdated(
                null,
                Certainty.VERIFIED,
                null,
                EventPrivacy.UNLISTED,
                null,
                null,
                "private note",
                null,
                T);
        assertEquals(2L, ev2.version());
        assertEquals(ev.eventId(), ev2.eventId());
        assertEquals(EventPrivacy.UNLISTED, ev2.privacyClassification());
        assertEquals("private note", ev2.description());
    }
}
