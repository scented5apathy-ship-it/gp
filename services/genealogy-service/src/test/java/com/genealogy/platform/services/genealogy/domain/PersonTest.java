package com.genealogy.platform.services.genealogy.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonTest {

    private static PersonName birth(String display) {
        return new PersonName(
                "name-" + display, NameKind.BIRTH, "Latn", "en-US",
                display, null, false, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static PersonName preferred(String display) {
        return new PersonName(
                "name-pref-" + display, NameKind.PREFERRED, "Latn", "en-US",
                display, null, true, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static Person newBase(List<PersonName> names) {
        return new Person(
                "p-1", "tenant-1", "tree-1",
                names,
                List.of(Pronoun.HE_HIM),
                List.of(),
                LivingStatus.LIVING, PrivacyLevel.PRIVATE,
                "MALE", "short bio", null,
                PersonLifecycle.ACTIVE, 1L,
                Instant.parse("2026-08-10T10:00:00Z"),
                Instant.parse("2026-08-10T10:00:00Z"),
                "user-1", Map.of());
    }

    @Test
    void primaryNamePicksPreferredFirstThenBirth() {
        Person person = newBase(List.of(birth("Nguyen Van A"), preferred("An")));
        assertEquals("An", person.primaryName().orElseThrow().display());
    }

    @Test
    void primaryNameFallsBackToBirthWhenNoPreferred() {
        Person person = newBase(List.of(birth("Nguyen Van A")));
        assertEquals("Nguyen Van A", person.primaryName().orElseThrow().display());
    }

    @Test
    void rejectsDuplicateBirthName() {
        List<PersonName> names = new ArrayList<>();
        names.add(birth("A"));
        names.add(new PersonName(
                "name-2", NameKind.BIRTH, "Latn", "en-US",
                "B", null, false, Instant.parse("2026-01-01T00:00:00Z")));
        assertThrows(IllegalArgumentException.class, () -> newBase(names));
    }

    @Test
    void rejectsDuplicatePreferred() {
        List<PersonName> names = new ArrayList<>();
        names.add(preferred("A"));
        names.add(new PersonName(
                "name-2", NameKind.PREFERRED, "Latn", "en-US",
                "B", null, true, Instant.parse("2026-01-01T00:00:00Z")));
        assertThrows(IllegalArgumentException.class, () -> newBase(names));
    }

    @Test
    void rejectsBiographyTooLong() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Person.MAX_BIOGRAPHY_CHARS + 1; i += 1) sb.append('a');
        assertThrows(IllegalArgumentException.class, () -> new Person(
                "p-1", "tenant-1", "tree-1", List.of(), List.of(), List.of(),
                LivingStatus.LIVING, PrivacyLevel.PRIVATE, "MALE", sb.toString(),
                null, PersonLifecycle.ACTIVE, 1L,
                Instant.parse("2026-08-10T10:00:00Z"),
                Instant.parse("2026-08-10T10:00:00Z"),
                "user-1", Map.of()));
    }

    @Test
    void rejectsUnknownScriptTag() {
        assertThrows(IllegalArgumentException.class, () -> new PersonName(
                "n-1", NameKind.BIRTH, "Mong", "mn", "B", null, false,
                Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void rejectsTooManyNames() {
        List<PersonName> many = new ArrayList<>();
        for (int i = 0; i < Person.MAX_NAMES + 1; i += 1) {
            many.add(new PersonName(
                    "n-" + i, NameKind.ALIAS, "Latn", "en-US",
                    "alias-" + i, null, false, Instant.parse("2026-01-01T00:00:00Z")));
        }
        assertThrows(IllegalArgumentException.class, () -> newBase(many));
    }

    @Test
    void rejectsTooManyIdentifiers() {
        List<PersonIdentifier> many = new ArrayList<>();
        for (int i = 0; i < Person.MAX_IDENTIFIERS + 1; i += 1) {
            many.add(new PersonIdentifier(
                    "id-" + i, PersonIdentifier.IdentifierKind.GENI_ID,
                    "value-" + i, null, false, null,
                    Instant.parse("2026-01-01T00:00:00Z")));
        }
        assertThrows(IllegalArgumentException.class, () -> new Person(
                "p-1", "tenant-1", "tree-1", List.of(), List.of(), many,
                LivingStatus.LIVING, PrivacyLevel.PRIVATE, "MALE", null,
                null, PersonLifecycle.ACTIVE, 1L,
                Instant.parse("2026-08-10T10:00:00Z"),
                Instant.parse("2026-08-10T10:00:00Z"),
                "user-1", Map.of()));
    }

    @Test
    void rejectsTooManyPronouns() {
        List<Pronoun> many = List.of(
                Pronoun.HE_HIM, Pronoun.SHE_HER, Pronoun.THEY_THEM,
                Pronoun.ZE_ZIR, Pronoun.XE_XEM);
        assertThrows(IllegalArgumentException.class, () -> new Person(
                "p-1", "tenant-1", "tree-1", List.of(), many, List.of(),
                LivingStatus.LIVING, PrivacyLevel.PRIVATE, "MALE", null,
                null, PersonLifecycle.ACTIVE, 1L,
                Instant.parse("2026-08-10T10:00:00Z"),
                Instant.parse("2026-08-10T10:00:00Z"),
                "user-1", Map.of()));
    }

    @Test
    void rejectsGenderDescriptionOutsideClosedSet() {
        assertThrows(IllegalArgumentException.class, () -> new Person(
                "p-1", "tenant-1", "tree-1", List.of(), List.of(), List.of(),
                LivingStatus.LIVING, PrivacyLevel.PRIVATE,
                "Other", null, null, PersonLifecycle.ACTIVE, 1L,
                Instant.parse("2026-08-10T10:00:00Z"),
                Instant.parse("2026-08-10T10:00:00Z"),
                "user-1", Map.of()));
    }

    @Test
    void rejectsBlankVerifiedUserId() {
        assertThrows(IllegalArgumentException.class, () -> new Person(
                "p-1", "tenant-1", "tree-1", List.of(), List.of(), List.of(),
                LivingStatus.LIVING, PrivacyLevel.PRIVATE, "MALE", null,
                "   ", PersonLifecycle.ACTIVE, 1L,
                Instant.parse("2026-08-10T10:00:00Z"),
                Instant.parse("2026-08-10T10:00:00Z"),
                "user-1", Map.of()));
    }

    @Test
    void livingStatusRequiresRedaction() {
        assertTrue(LivingStatus.LIVING.isLiving());
        assertTrue(LivingStatus.INFERRED_LIVING.isLiving());
        assertSame(LivingStatus.UNKNOWN, LivingStatus.fromWire(null));
    }

    @Test
    void privacyLevelRequiresProjectionRedaction() {
        assertTrue(PrivacyLevel.PRIVATE.requiresProjectionRedaction());
        assertTrue(PrivacyLevel.UNLISTED.requiresProjectionRedaction());
        assertSame(PrivacyLevel.PRIVATE, PrivacyLevel.fromWire(null));
    }

    @Test
    void softDeleteFlipsToDeletedLifecycle() {
        Person person = newBase(List.of(birth("A")));
        Person deleted = person.softDeleted(Instant.parse("2026-08-10T11:00:00Z"));
        assertEquals(PersonLifecycle.DELETED, deleted.lifecycleState());
        assertEquals(2L, deleted.version());
    }

    @Test
    void linkVerifiedUserRequiresOpaqueId() {
        Person person = newBase(List.of(birth("A")));
        Person linked = person.withVerifiedUser("user-uuid-1", Instant.parse("2026-08-10T11:00:00Z"));
        assertNotNull(linked.verifiedUserId());
        assertEquals("user-uuid-1", linked.verifiedUserId());
        assertEquals(2L, linked.version());
    }

    @Test
    void diffReportsAllChangedFields() {
        Person before = newBase(List.of(birth("A")));
        Person after = before.withPrivacyLevel(PrivacyLevel.PUBLIC, Instant.parse("2026-08-10T11:00:00Z"));
        assertTrue(Person.diff(before, after).contains("privacyLevel"));
    }
}
