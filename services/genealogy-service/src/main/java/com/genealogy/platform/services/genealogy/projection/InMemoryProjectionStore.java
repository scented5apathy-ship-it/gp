package com.genealogy.platform.services.genealogy.projection;

import com.genealogy.platform.services.genealogy.domain.Person;
import com.genealogy.platform.services.genealogy.domain.Relationship;
import com.genealogy.platform.services.genealogy.domain.Tree;
import com.genealogy.platform.services.genealogy.domain.Visibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory read-model store for the E5.2 tree projection. Holds
 * a bounded snapshot of {@link Person} + {@link Relationship}
 * + {@link Tree} for a tenant and answers the
 * {@link TreeProjectionBuilder} queries.
 *
 * <p>The class is NOT a JDBC implementation — the real executor
 * lives behind a JDBC adapter (out of scope for E5.2). The
 * in-memory store is used by unit tests and by the smoke
 * profile; production code paths resolve the JDBC variant.
 *
 * <p>Storage layout:
 *
 * <pre>
 *   tenantTrees   : { treeId -> Tree }
 *   tenantPersons : { (treeId, personId) -> Person }
 *   tenantRels    : { treeId -> [Relationship] } (sorted by relationshipId)
 *   treeVersion   : { treeId -> monotonic counter }
 *   tenantCursors : { tenantId -> last cursor }
 * </pre>
 *
 * <p>Every {@link #bumpVersion} call increments the monotonic
 * counter so the cache layer can detect stale reads via the
 * version number (defence in depth against missed
 * invalidations, per
 * {@code contracts/genealogy/tree-projection-cache.yaml::
 * spec.requireVersionOnEntry}).
 */
public final class InMemoryProjectionStore {

    private final Map<String, Tree> tenantTrees = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Person>> tenantPersons = new ConcurrentHashMap<>();
    private final Map<String, List<Relationship>> tenantRels = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> treeVersion = new ConcurrentHashMap<>();
    private final Map<String, Long> tenantCursors = new ConcurrentHashMap<>();

    /** Insert or replace the tree. The monotonic version is reset to 1. */
    public void upsertTree(Tree tree) {
        Objects.requireNonNull(tree, "tree");
        tenantTrees.put(tree.treeId(), tree);
        tenantPersons.computeIfAbsent(tree.treeId(), k -> new ConcurrentHashMap<>());
        tenantRels.computeIfAbsent(tree.treeId(), k -> new ArrayList<>());
        treeVersion.computeIfAbsent(tree.treeId(), k -> new AtomicLong(1L));
    }

    /** Insert or replace the person. Bumps the monotonic version. */
    public void upsertPerson(Person person) {
        Objects.requireNonNull(person, "person");
        Map<String, Person> byId = tenantPersons
                .computeIfAbsent(person.treeId(), k -> new ConcurrentHashMap<>());
        byId.put(person.personId(), person);
        bumpVersion(person.treeId());
    }

    /** Insert or replace the relationship. Bumps the monotonic version. */
    public void upsertRelationship(Relationship relationship) {
        Objects.requireNonNull(relationship, "relationship");
        List<Relationship> bucket = tenantRels
                .computeIfAbsent(relationship.treeId(), k -> new ArrayList<>());
        bucket.removeIf(r -> r.relationshipId().wire().equals(relationship.relationshipId().wire()));
        bucket.add(relationship);
        bumpVersion(relationship.treeId());
    }

    /** Remove the person. Bumps the version so cache invalidates. */
    public void deletePerson(String treeId, String personId) {
        Objects.requireNonNull(treeId, "treeId");
        Objects.requireNonNull(personId, "personId");
        Map<String, Person> byId = tenantPersons.get(treeId);
        if (byId != null) {
            byId.remove(personId);
        }
        List<Relationship> bucket = tenantRels.get(treeId);
        if (bucket != null) {
            bucket.removeIf(r -> r.participants().stream()
                    .anyMatch(p -> personId.equals(p.personId())));
        }
        bumpVersion(treeId);
    }

    public Optional<Tree> findTree(String treeId) {
        return Optional.ofNullable(tenantTrees.get(treeId));
    }

    public Optional<Person> findPerson(String treeId, String personId) {
        Map<String, Person> byId = tenantPersons.get(treeId);
        if (byId == null) return Optional.empty();
        return Optional.ofNullable(byId.get(personId));
    }

    /** All persons belonging to {@code treeId}. Defensive copy. */
    public List<Person> listPersons(String treeId) {
        Map<String, Person> byId = tenantPersons.get(treeId);
        if (byId == null) return List.of();
        return List.copyOf(byId.values());
    }

    /** All relationships belonging to {@code treeId}. Sorted by id. */
    public List<Relationship> listRelationships(String treeId) {
        List<Relationship> bucket = tenantRels.get(treeId);
        if (bucket == null) return List.of();
        Map<String, Relationship> sorted = new TreeMap<>();
        for (Relationship r : bucket) {
            sorted.put(r.relationshipId().wire(), r);
        }
        return List.copyOf(sorted.values());
    }

    /**
     * Monotonic projection version. The executor assigns the
     * version + the ETag + the {@code generatedAt} stamp on every
     * rebuild so the cache layer can compare.
     */
    public long currentVersion(String treeId) {
        AtomicLong counter = treeVersion.get(treeId);
        return counter == null ? 0L : counter.get();
    }

    /** Manually increment the monotonic version. */
    public long bumpVersion(String treeId) {
        AtomicLong counter = treeVersion.computeIfAbsent(treeId, k -> new AtomicLong(1L));
        return counter.incrementAndGet();
    }

    /** Cursor generator — opaque cursor for the pagination contract. */
    public long nextCursor(String tenantId) {
        return tenantCursors.merge(tenantId, 1L, Long::sum);
    }

    public Map<String, Long> debugCounters() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, AtomicLong> e : treeVersion.entrySet()) {
            snapshot.put(e.getKey(), e.getValue().get());
        }
        return Collections.unmodifiableMap(snapshot);
    }
}