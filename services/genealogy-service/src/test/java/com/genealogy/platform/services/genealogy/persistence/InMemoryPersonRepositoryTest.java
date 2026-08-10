package com.genealogy.platform.services.genealogy.persistence;

import com.genealogy.platform.services.genealogy.domain.Person;
import com.genealogy.platform.services.genealogy.domain.PersonLifecycle;
import com.genealogy.platform.services.genealogy.domain.PersonName;
import com.genealogy.platform.services.genealogy.domain.LivingStatus;
import com.genealogy.platform.services.genealogy.domain.PrivacyLevel;
import com.genealogy.platform.services.genealogy.domain.NameKind;
import com.genealogy.platform.services.genealogy.domain.Pronoun;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryPersonRepositoryTest {

    private InMemoryPersonRepository repo;

    @BeforeEach
    void setup() {
        repo = new InMemoryPersonRepository();
    }

    private Person sample(List<PersonName> names) {
        return new Person(
                "p-1", "tenant-1", "tree-1",
                names, List.of(Pronoun.HE_HIM), List.of(),
                LivingStatus.LIVING, PrivacyLevel.PRIVATE,
                "MALE", null, null,
                PersonLifecycle.ACTIVE, 1L,
                Instant.parse("2026-08-10T10:00:00Z"),
                Instant.parse("2026-08-10T10:00:00Z"),
                "user-1", Map.of());
    }

    private PersonName birth(String display) {
        return new PersonName(
                "name-" + display, NameKind.BIRTH, "Latn", "en-US",
                display, null, false, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void insertThenFindReturnsSlimProjection() {
        Person person = sample(List.of(birth("An")));
        repo.insert(person);
        Optional<Person> slim = repo.findById("tenant-1", "p-1");
        assertTrue(slim.isPresent());
        assertTrue(slim.get().names().isEmpty());
        assertEquals(LivingStatus.LIVING, slim.get().livingStatus());
    }

    @Test
    void loadFullReturnsAllCollections() {
        Person person = sample(List.of(birth("An")));
        repo.insert(person);
        Optional<Person> full = repo.loadFull("tenant-1", "p-1");
        assertTrue(full.isPresent());
        assertEquals(1, full.get().names().size());
        assertEquals(1, full.get().pronouns().size());
    }

    @Test
    void findAcrossTenantReturnsEmpty() {
        Person person = sample(List.of(birth("An")));
        repo.insert(person);
        assertFalse(repo.findById("tenant-2", "p-1").isPresent());
        assertFalse(repo.loadFull("tenant-2", "p-1").isPresent());
    }

    @Test
    void updateEnforcesCasOnVersion() {
        Person person = sample(List.of(birth("An")));
        repo.insert(person);
        Person updated = person.withPrivacyLevel(
                PrivacyLevel.PUBLIC, Instant.parse("2026-08-10T11:00:00Z"));
        repo.update(updated);
        Person stale = new Person(
                updated.personId(), updated.tenantId(), updated.treeId(),
                updated.names(), updated.pronouns(), updated.identifiers(),
                updated.livingStatus(), updated.privacyLevel(),
                updated.genderDescription(), updated.biography(),
                updated.verifiedUserId(), updated.lifecycleState(),
                updated.version(), updated.createdAt(),
                Instant.parse("2026-08-10T12:00:00Z"),
                updated.createdBy(), Map.of());
        assertThrows(IllegalStateException.class, () -> repo.update(stale));
    }

    @Test
    void updateRejectsTenantMismatch() {
        Person person = sample(List.of(birth("An")));
        repo.insert(person);
        Person mismatch = new Person(
                "p-1", "tenant-2", "tree-1",
                List.of(), List.of(), List.of(),
                LivingStatus.LIVING, PrivacyLevel.PRIVATE,
                "MALE", null, null,
                PersonLifecycle.ACTIVE, 2L,
                Instant.parse("2026-08-10T11:00:00Z"),
                Instant.parse("2026-08-10T11:00:00Z"),
                "user-1", Map.of());
        assertThrows(IllegalStateException.class, () -> repo.update(mismatch));
    }

    @Test
    void updateAppliesNewCollections() {
        Person person = sample(List.of(birth("An")));
        repo.insert(person);
        PersonName alias = new PersonName(
                "name-alias", NameKind.ALIAS, "Latn", "en-US",
                "Annie", null, false, Instant.parse("2026-01-01T00:00:00Z"));
        Person updated = person.withProfile(
                List.of(birth("An"), alias),
                person.pronouns(), person.identifiers(),
                person.genderDescription(), person.biography(),
                Instant.parse("2026-08-10T11:00:00Z"));
        repo.update(updated);
        Person reloaded = repo.loadFull("tenant-1", "p-1").orElseThrow();
        assertEquals(2, reloaded.names().size());
        assertEquals(2L, reloaded.version());
    }

    @Test
    void listByTreeExcludesDeletedAndIsScoped() {
        Person person = sample(List.of(birth("An")));
        repo.insert(person);
        Person other = new Person(
                "p-2", "tenant-1", "tree-1",
                List.of(birth("Binh")), List.of(), List.of(),
                LivingStatus.LIVING, PrivacyLevel.PRIVATE,
                "MALE", null, null,
                PersonLifecycle.ACTIVE, 1L,
                Instant.parse("2026-08-10T10:00:00Z"),
                Instant.parse("2026-08-10T10:00:00Z"),
                "user-1", Map.of());
        repo.insert(other);
        assertEquals(2, repo.listByTree("tenant-1", "tree-1", 10, 0).size());
        assertEquals(0, repo.listByTree("tenant-2", "tree-1", 10, 0).size());
    }

    @Test
    void softDeletedPersonIsDroppedFromListButStaysForAudit() {
        Person person = sample(List.of(birth("An")));
        repo.insert(person);
        Person deleted = person.softDeleted(Instant.parse("2026-08-10T11:00:00Z"));
        repo.update(deleted);
        assertTrue(repo.listByTree("tenant-1", "tree-1", 10, 0).isEmpty());
        assertNotNull(repo.loadFull("tenant-1", "p-1").orElseThrow());
    }
}
