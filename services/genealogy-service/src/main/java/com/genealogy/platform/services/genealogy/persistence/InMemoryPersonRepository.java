package com.genealogy.platform.services.genealogy.persistence;

import com.genealogy.platform.services.genealogy.domain.LivingStatus;
import com.genealogy.platform.services.genealogy.domain.Person;
import com.genealogy.platform.services.genealogy.domain.PersonIdentifier;
import com.genealogy.platform.services.genealogy.domain.PersonLifecycle;
import com.genealogy.platform.services.genealogy.domain.PersonName;
import com.genealogy.platform.services.genealogy.domain.PrivacyLevel;
import com.genealogy.platform.services.genealogy.domain.Pronoun;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link PersonRepository} used by
 * unit tests and as a fast-path default. Production code paths
 * always resolve the JDBC-backed implementation.
 *
 * <p>Person names / pronouns / identifiers are kept on parallel
 * maps keyed by the aggregate id. The slim projection in
 * {@link #findById} returns a {@link Person} with empty child
 * collections; the full projection via {@link #loadFull} loads
 * them.
 */
public final class InMemoryPersonRepository implements PersonRepository {

    private final Map<String, Person> byId = new ConcurrentHashMap<>();
    private final Map<String, List<PersonName>> namesByPerson = new ConcurrentHashMap<>();
    private final Map<String, List<Pronoun>> pronounsByPerson = new ConcurrentHashMap<>();
    private final Map<String, List<PersonIdentifier>> identifiersByPerson =
            new ConcurrentHashMap<>();

    @Override
    public void insert(Person person) {
        if (byId.putIfAbsent(person.personId(), person) != null) {
            throw new IllegalStateException("duplicate personId: " + person.personId());
        }
        namesByPerson.put(person.personId(), new ArrayList<>(person.names()));
        pronounsByPerson.put(person.personId(), new ArrayList<>(person.pronouns()));
        identifiersByPerson.put(
                person.personId(), new ArrayList<>(person.identifiers()));
    }

    @Override
    public void update(Person person) {
        Person existing = byId.get(person.personId());
        if (existing == null) {
            throw new IllegalStateException("person not found: " + person.personId());
        }
        if (!existing.tenantId().equals(person.tenantId())) {
            throw new IllegalStateException(
                    "tenant mismatch on update: " + person.tenantId());
        }
        if (existing.version() + 1 != person.version()) {
            throw new IllegalStateException(
                    "stale version, expected " + (existing.version() + 1)
                            + " got " + person.version());
        }
        byId.put(person.personId(), person);
        namesByPerson.put(person.personId(), new ArrayList<>(person.names()));
        pronounsByPerson.put(person.personId(), new ArrayList<>(person.pronouns()));
        identifiersByPerson.put(
                person.personId(), new ArrayList<>(person.identifiers()));
    }

    @Override
    public Optional<Person> findById(String tenantId, String personId) {
        Person person = byId.get(personId);
        if (person == null || !person.tenantId().equals(tenantId)) {
            return Optional.empty();
        }
        return Optional.of(slim(person));
    }

    @Override
    public Optional<Person> loadFull(String tenantId, String personId) {
        Person person = byId.get(personId);
        if (person == null || !person.tenantId().equals(tenantId)) {
            return Optional.empty();
        }
        return Optional.of(person);
    }

    @Override
    public List<Person> listByTree(String tenantId, String treeId, int limit, int offset) {
        List<Person> all = new ArrayList<>();
        for (Person person : byId.values()) {
            if (person.tenantId().equals(tenantId)
                    && person.treeId().equals(treeId)
                    && person.lifecycleState() != PersonLifecycle.DELETED) {
                all.add(slim(person));
            }
        }
        all.sort((a, b) -> a.createdAt().compareTo(b.createdAt()));
        if (offset >= all.size()) return List.of();
        int end = Math.min(all.size(), offset + limit);
        return new ArrayList<>(all.subList(offset, end));
    }

    @Override
    public void purge(String tenantId, String personId) {
        Person existing = byId.get(personId);
        if (existing == null || !existing.tenantId().equals(tenantId)) {
            return;
        }
        byId.remove(personId);
        namesByPerson.remove(personId);
        pronounsByPerson.remove(personId);
        identifiersByPerson.remove(personId);
    }

    @Override
    public long countByTree(String tenantId, String treeId) {
        long count = 0;
        for (Person person : byId.values()) {
            if (person.tenantId().equals(tenantId)
                    && person.treeId().equals(treeId)
                    && person.lifecycleState() != PersonLifecycle.DELETED) {
                count += 1;
            }
        }
        return count;
    }

    private static Person slim(Person person) {
        return new Person(
                person.personId(),
                person.tenantId(),
                person.treeId(),
                List.of(),
                List.of(),
                List.of(),
                person.livingStatus(),
                person.privacyLevel(),
                person.genderDescription(),
                null,
                person.verifiedUserId(),
                person.lifecycleState(),
                person.version(),
                person.createdAt(),
                person.updatedAt(),
                person.createdBy(),
                new HashMap<>(person.auditAttributes()));
    }
}
