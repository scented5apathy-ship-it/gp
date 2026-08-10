package com.genealogy.platform.services.genealogy.persistence;

import com.genealogy.platform.services.genealogy.domain.Person;
import com.genealogy.platform.services.genealogy.domain.PersonLifecycle;
import com.genealogy.platform.services.genealogy.domain.PersonName;
import com.genealogy.platform.services.genealogy.domain.PersonIdentifier;
import com.genealogy.platform.services.genealogy.domain.LivingStatus;
import com.genealogy.platform.services.genealogy.domain.PrivacyLevel;
import com.genealogy.platform.services.genealogy.domain.Pronoun;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@link Person} aggregate. Every method that
 * returns a tenant-scoped row enforces the tenant predicate at
 * the application boundary; PostgreSQL Row-Level Security
 * provides a second line of defence per {@code design.md} §5.1.
 *
 * <p>Names / pronouns / identifiers are exposed only via
 * {@link #loadFull(String, String)} so that the audit / search
 * projections can pick a slim aggregate projection when needed.
 */
public interface PersonRepository {

    void insert(Person person);

    /**
     * CAS update on {@code version}. Throws on stale version.
     */
    void update(Person person);

    /**
     * Slim projection (no name / identifier rows). Used by the
     * search projection / list APIs.
     */
    Optional<Person> findById(String tenantId, String personId);

    /**
     * Full aggregate load (names, pronouns, identifiers). Used
     * by the command service + renderer.
     */
    Optional<Person> loadFull(String tenantId, String personId);

    /**
     * List slim projections in a tenant / tree (paged). Excludes
     * the {@link PersonLifecycle#DELETED} lifecycle state.
     */
    List<Person> listByTree(String tenantId, String treeId, int limit, int offset);

    /** Hard-delete the row (used only after the DELETED terminal state is committed). */
    void purge(String tenantId, String personId);

    /** Count active persons for a tree. */
    long countByTree(String tenantId, String treeId);
}
