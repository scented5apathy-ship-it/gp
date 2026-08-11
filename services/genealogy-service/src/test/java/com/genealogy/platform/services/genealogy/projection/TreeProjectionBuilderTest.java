package com.genealogy.platform.services.genealogy.projection;

import com.genealogy.platform.services.genealogy.domain.Certainty;
import com.genealogy.platform.services.genealogy.domain.CollaborationMode;
import com.genealogy.platform.services.genealogy.domain.LifecycleState;
import com.genealogy.platform.services.genealogy.domain.LivingStatus;
import com.genealogy.platform.services.genealogy.domain.NameKind;
import com.genealogy.platform.services.genealogy.domain.ParticipantRole;
import com.genealogy.platform.services.genealogy.domain.PartnerSubKind;
import com.genealogy.platform.services.genealogy.domain.Person;
import com.genealogy.platform.services.genealogy.domain.PersonLifecycle;
import com.genealogy.platform.services.genealogy.domain.PersonName;
import com.genealogy.platform.services.genealogy.domain.PrivacyLevel;
import com.genealogy.platform.services.genealogy.domain.ProvenanceStatus;
import com.genealogy.platform.services.genealogy.domain.Relationship;
import com.genealogy.platform.services.genealogy.domain.RelationshipId;
import com.genealogy.platform.services.genealogy.domain.RelationshipKind;
import com.genealogy.platform.services.genealogy.domain.TemporalValidity;
import com.genealogy.platform.services.genealogy.domain.Tree;
import com.genealogy.platform.services.genealogy.domain.Visibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeProjectionBuilderTest {

    private static final Instant T = Instant.parse("2026-08-10T10:00:00Z");

    private InMemoryProjectionStore store;
    private TreeProjectionBuilder builder;

    @BeforeEach
    void setup() {
        store = new InMemoryProjectionStore();
        builder = new TreeProjectionBuilder(store);
        Tree tree = sampleTree("tree-1", "tenant-1", Visibility.PRIVATE);
        store.upsertTree(tree);
    }

    @Test
    void build_neighbourhood_returns_bounded_nodes_and_edges() {
        store.upsertPerson(samplePerson("p-root", "Root", LivingStatus.DECEASED, PrivacyLevel.PUBLIC));
        store.upsertPerson(samplePerson("p-child", "Child", LivingStatus.DECEASED, PrivacyLevel.PUBLIC));
        store.upsertPerson(samplePerson("p-grand", "Grand", LivingStatus.DECEASED, PrivacyLevel.PUBLIC));
        store.upsertRelationship(parentChild("rel-1", "p-root", "p-child"));
        store.upsertRelationship(parentChild("rel-2", "p-child", "p-grand"));

        TreeProjection projection = builder.build(ProjectionQuery.defaultQuery(
                "tree-1", "p-root", ProjectionViewKind.PEDIGREE,
                ProjectionDirection.DESCENDANTS, 4));

        assertEquals("tree-1", projection.treeId());
        assertEquals(ProjectionViewKind.PEDIGREE, projection.viewKind());
        assertEquals(ProjectionDirection.DESCENDANTS, projection.direction());
        assertEquals(3, projection.nodes().size());
        assertEquals(2, projection.edges().size());
        assertTrue(projection.version() >= 1L, "version must be monotonic >= 1");
        assertTrue(projection.etag().startsWith("\""));
        assertTrue(projection.etag().endsWith("\""));
        assertEquals(0, projection.redaction().reasonCodes().size(),
                "no redaction obligations for non-living PUBLIC persons");
    }

    @Test
    void build_living_person_applies_redaction_obligations() {
        store.upsertPerson(samplePerson("p-living", "Living Person", LivingStatus.LIVING,
                PrivacyLevel.PRIVATE));
        store.upsertPerson(samplePerson("p-dec", "Dec Person", LivingStatus.DECEASED,
                PrivacyLevel.PRIVATE));
        store.upsertRelationship(parentChild("rel-living", "p-living", "p-dec"));

        TreeProjection projection = builder.build(ProjectionQuery.defaultQuery(
                "tree-1", "p-living", ProjectionViewKind.FAMILY,
                ProjectionDirection.BOTH, 2));

        ProjectionNode living = findNode(projection, "p-living");
        assertNotNull(living, "living person must remain in the projection");
        assertTrue(living.redacted(), "living person must be marked redacted");
        assertTrue(living.reasonCodes().contains(ProjectionRedactionReasonCode.LIVING_REDACTED));
        assertTrue(living.reasonCodes().contains(ProjectionRedactionReasonCode.PRIVACY_CLASS_RESTRICTED));
        assertTrue(living.droppedFields().contains("displayName"));
        assertTrue(living.droppedFields().contains("biography"));
        assertNotNull(projection.redaction().policyVersion());
    }

    @Test
    void build_minor_living_applies_minor_guardian_obligation() {
        store.upsertPerson(samplePerson("p-minor", "Minor", LivingStatus.LIVING,
                PrivacyLevel.TREE_DEFAULT));
        store.upsertPerson(samplePerson("p-parent", "Parent", LivingStatus.DECEASED,
                PrivacyLevel.PUBLIC));
        store.upsertRelationship(parentChild("rel-minor", "p-parent", "p-minor"));

        TreeProjection projection = builder.build(ProjectionQuery.defaultQuery(
                "tree-1", "p-minor", ProjectionViewKind.FAMILY,
                ProjectionDirection.BOTH, 2));

        ProjectionNode minor = findNode(projection, "p-minor");
        assertNotNull(minor);
        assertTrue(minor.reasonCodes().contains(ProjectionRedactionReasonCode.MINOR_GUARDIAN_REQUIRED));
        assertTrue(minor.droppedFields().contains("currentResidence"));
    }

    @Test
    void build_unlisted_token_invalid_redacts_living() {
        store.upsertPerson(samplePerson("p-living", "Living", LivingStatus.LIVING,
                PrivacyLevel.UNLISTED));
        store.upsertRelationship(partnerRel("rel-p", "p-living", "p-dec", PartnerSubKind.MARRIED));

        ProjectionQuery query = ProjectionQuery.defaultQuery(
                "tree-1", "p-living", ProjectionViewKind.FAMILY,
                ProjectionDirection.SPOUSE_FAN, 2).withUnlistedTokenInvalid(true);

        TreeProjection projection = builder.build(query);
        ProjectionNode living = findNode(projection, "p-living");
        assertNotNull(living);
        assertTrue(living.reasonCodes().contains(
                ProjectionRedactionReasonCode.VISIBILITY_UNLISTED_TOKEN_INVALID));
    }

    @Test
    void build_depth_cap_is_enforced() {
        for (int i = 0; i < 20; i += 1) {
            String childId = "p-c" + i;
            String parentId = "p-p" + i;
            store.upsertPerson(samplePerson(childId, "c" + i, LivingStatus.DECEASED,
                    PrivacyLevel.PUBLIC));
            store.upsertPerson(samplePerson(parentId, "p" + i, LivingStatus.DECEASED,
                    PrivacyLevel.PUBLIC));
            store.upsertRelationship(parentChild("r-" + i, parentId, childId));
        }
        store.upsertPerson(samplePerson("p-root", "Root", LivingStatus.DECEASED,
                PrivacyLevel.PUBLIC));
        store.upsertRelationship(parentChild("r-root", "p-root", "p-c0"));

        TreeProjection projection = builder.build(ProjectionQuery.defaultQuery(
                "tree-1", "p-root", ProjectionViewKind.DESCENDANT,
                ProjectionDirection.DESCENDANTS, 12));
        assertTrue(projection.nodes().size() <= 21);
        assertTrue(projection.depth() <= 12);
    }

    @Test
    void build_etag_is_stable_across_repeated_queries() {
        store.upsertPerson(samplePerson("p-1", "Alice", LivingStatus.DECEASED, PrivacyLevel.PUBLIC));
        store.upsertPerson(samplePerson("p-2", "Bob", LivingStatus.DECEASED, PrivacyLevel.PUBLIC));
        store.upsertRelationship(parentChild("r-1", "p-1", "p-2"));

        TreeProjection first = builder.build(ProjectionQuery.defaultQuery(
                "tree-1", "p-1", ProjectionViewKind.PEDIGREE,
                ProjectionDirection.DESCENDANTS, 4));
        TreeProjection second = builder.build(ProjectionQuery.defaultQuery(
                "tree-1", "p-1", ProjectionViewKind.PEDIGREE,
                ProjectionDirection.DESCENDANTS, 4));
        assertEquals(first.etag(), second.etag());
    }

    @Test
    void build_version_increments_after_mutation() {
        store.upsertPerson(samplePerson("p-1", "Alice", LivingStatus.DECEASED, PrivacyLevel.PUBLIC));
        TreeProjection before = builder.build(ProjectionQuery.defaultQuery(
                "tree-1", "p-1", ProjectionViewKind.FAMILY,
                ProjectionDirection.BOTH, 2));
        store.upsertPerson(samplePerson("p-1", "Alice 2", LivingStatus.DECEASED, PrivacyLevel.PUBLIC));
        TreeProjection after = builder.build(ProjectionQuery.defaultQuery(
                "tree-1", "p-1", ProjectionViewKind.FAMILY,
                ProjectionDirection.BOTH, 2));
        assertTrue(after.version() > before.version(),
                "version must increase after a mutation; before=" + before.version()
                        + " after=" + after.version());
        assertNotEquals(before.etag(), after.etag());
    }

    @Test
    void build_unknown_root_throws() {
        assertThrows(IllegalArgumentException.class, () -> builder.build(
                ProjectionQuery.defaultQuery("tree-1", "missing",
                        ProjectionViewKind.PEDIGREE, ProjectionDirection.BOTH, 2)));
    }

    @Test
    void build_closed_set_violation_depth_throws() {
        store.upsertPerson(samplePerson("p-1", "Alice", LivingStatus.DECEASED, PrivacyLevel.PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> new ProjectionQuery("tree-1", "p-1",
                ProjectionViewKind.PEDIGREE, ProjectionDirection.BOTH,
                0, 100, 100, ProjectionFilter.none(), false, 0));
    }

    @Test
    void projection_filter_matches_relationship_and_living_status() {
        ProjectionFilter filter = new ProjectionFilter(
                Set.of(ProjectionRelationshipKind.BIRTH_PARENT),
                Set.of(LivingStatus.DECEASED));
        assertTrue(filter.matchesRelationship(ProjectionRelationshipKind.BIRTH_PARENT));
        assertFalse(filter.matchesRelationship(ProjectionRelationshipKind.SPOUSE));
        assertTrue(filter.matchesLivingStatus(LivingStatus.DECEASED));
        assertFalse(filter.matchesLivingStatus(LivingStatus.LIVING));
        ProjectionFilter empty = ProjectionFilter.none();
        assertTrue(empty.matchesRelationship(ProjectionRelationshipKind.SPOUSE));
        assertTrue(empty.matchesLivingStatus(LivingStatus.LIVING));
    }

    @Test
    void projection_cache_invalidates_on_tree_event() {
        store.upsertPerson(samplePerson("p-1", "Alice", LivingStatus.DECEASED, PrivacyLevel.PUBLIC));
        store.upsertPerson(samplePerson("p-2", "Bob", LivingStatus.DECEASED, PrivacyLevel.PUBLIC));
        store.upsertRelationship(parentChild("r-1", "p-1", "p-2"));

        TreeProjectionCache cache = new TreeProjectionCache(builder, 300, 1800);
        ProjectionQuery query = ProjectionQuery.defaultQuery(
                "tree-1", "p-1", ProjectionViewKind.PEDIGREE,
                ProjectionDirection.ANCESTORS, 4);

        TreeProjection first = cache.get("tenant-1", query);
        assertNotNull(cache.peek("tenant-1", query).orElse(null));
        assertEquals(1, cache.debugExpiry().size());

        cache.invalidate("tree-1");
        assertTrue(cache.peek("tenant-1", query).isEmpty(),
                "invalidate(treeId) must wipe every entry that references the tree");
        TreeProjection second = cache.get("tenant-1", query);
        assertEquals(first.etag(), second.etag(),
                "etag is a function of payload + version; version did not bump");
    }

    @Test
    void projection_cache_ceiling_is_enforced() {
        assertThrows(IllegalArgumentException.class,
                () -> new TreeProjectionCache(builder, 300, 299));
        assertThrows(IllegalArgumentException.class,
                () -> new TreeProjectionCache(builder, 0, 1800));
        assertThrows(IllegalArgumentException.class,
                () -> new TreeProjectionCache(builder, 100, 10_000));
    }

    @Test
    void projection_cache_key_is_tenant_aware() {
        assertTrue(TreeProjectionCache.key("tenant-A", ProjectionQuery.defaultQuery(
                "tree-1", "p-1", ProjectionViewKind.PEDIGREE,
                ProjectionDirection.BOTH, 2)).startsWith("gp:tenant-A:genealogy:projection:"));
        assertTrue(TreeProjectionCache.key("tenant-A", ProjectionQuery.defaultQuery(
                "tree-1", "p-1", ProjectionViewKind.PEDIGREE,
                ProjectionDirection.BOTH, 2)).contains(":tree-1:"));
    }

    @Test
    void direction_spouse_fan_includes_only_one_hop() {
        store.upsertPerson(samplePerson("p-root", "Root", LivingStatus.DECEASED, PrivacyLevel.PUBLIC));
        store.upsertPerson(samplePerson("p-spouse", "Spouse", LivingStatus.DECEASED, PrivacyLevel.PUBLIC));
        store.upsertRelationship(partnerRel("r-sp", "p-root", "p-spouse", PartnerSubKind.MARRIED));

        TreeProjection projection = builder.build(ProjectionQuery.defaultQuery(
                "tree-1", "p-root", ProjectionViewKind.FAN,
                ProjectionDirection.SPOUSE_FAN, 3));
        assertEquals(2, projection.nodes().size(),
                "fan chart must include root + direct spouse only");
        assertEquals(1, projection.edges().size());
    }

    @Test
    void projection_kind_mapping_is_closed_set() {
        assertEquals(ProjectionRelationshipKind.BIRTH_PARENT,
                ProjectionRelationshipKind.toProjectionKind(RelationshipKind.BIOLOGICAL_PARENT));
        assertEquals(ProjectionRelationshipKind.BIRTH_PARENT,
                ProjectionRelationshipKind.toProjectionKind(RelationshipKind.ADOPTIVE_PARENT));
        assertEquals(ProjectionRelationshipKind.FOSTER_PARENT,
                ProjectionRelationshipKind.toProjectionKind(RelationshipKind.FOSTER_PARENT));
        assertEquals(ProjectionRelationshipKind.STEP_PARENT,
                ProjectionRelationshipKind.toProjectionKind(RelationshipKind.STEP_PARENT));
        assertEquals(ProjectionRelationshipKind.GUARDIAN,
                ProjectionRelationshipKind.toProjectionKind(RelationshipKind.GUARDIAN));
        assertEquals(ProjectionRelationshipKind.GUARDIAN,
                ProjectionRelationshipKind.toProjectionKind(RelationshipKind.GODPARENT));
        assertEquals(ProjectionRelationshipKind.SPOUSE,
                ProjectionRelationshipKind.toProjectionKind(RelationshipKind.SIBLING));
        assertEquals(ProjectionRelationshipKind.CUSTOM,
                ProjectionRelationshipKind.toProjectionKind(RelationshipKind.CUSTOM));
        assertEquals(ProjectionRelationshipKind.PARTNER,
                ProjectionRelationshipKind.toProjectionPartnerKind(
                        RelationshipKind.PARTNER, "UNMARRIED_PARTNER"));
        assertEquals(ProjectionRelationshipKind.SPOUSE,
                ProjectionRelationshipKind.toProjectionPartnerKind(
                        RelationshipKind.PARTNER, "MARRIED"));
    }

    private static ProjectionNode findNode(TreeProjection projection, String personId) {
        for (ProjectionNode node : projection.nodes()) {
            if (node.personId().equals(personId)) {
                return node;
            }
        }
        return null;
    }

    private static Tree sampleTree(String treeId, String tenantId, Visibility visibility) {
        return new Tree(
                treeId, tenantId, "smith", "Smith Family",
                visibility, CollaborationMode.DIRECT_EDIT,
                LifecycleState.ACTIVE, "en-US", "UTC", "GREGORIAN",
                Map.of(), "user-1", 1L, T, T);
    }

    private static Person samplePerson(String id, String display, LivingStatus status,
                                       PrivacyLevel privacy) {
        PersonName name = new PersonName(
                "name-" + id, NameKind.PREFERRED, "Latn", "en-US",
                display, null, true, T);
        return new Person(
                id, "tenant-1", "tree-1",
                List.of(name), List.of(), List.of(),
                status, privacy,
                null, null, null,
                PersonLifecycle.ACTIVE, 1L, T, T, "user-1", Map.of());
    }

    private static Relationship parentChild(String relId, String parentId, String childId) {
        return new Relationship(
                RelationshipId.of(relId), "tenant-1", "tree-1",
                RelationshipKind.BIOLOGICAL_PARENT, null, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(
                        new com.genealogy.platform.services.genealogy.domain.ParticipantRef(
                                "p-" + parentId, ParticipantRole.PARENT, parentId, false, T),
                        new com.genealogy.platform.services.genealogy.domain.ParticipantRef(
                                "c-" + childId, ParticipantRole.CHILD, childId, false, T)),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, Map.of());
    }

    private static Relationship partnerRel(String relId, String a, String b,
                                           PartnerSubKind sub) {
        return new Relationship(
                RelationshipId.of(relId), "tenant-1", "tree-1",
                RelationshipKind.PARTNER, sub, null,
                Certainty.ASSERTED, ProvenanceStatus.USER_ENTERED,
                List.of(
                        new com.genealogy.platform.services.genealogy.domain.ParticipantRef(
                                "p-" + a, ParticipantRole.PARTNER, a, false, T),
                        new com.genealogy.platform.services.genealogy.domain.ParticipantRef(
                                "p-" + b, ParticipantRole.PARTNER, b, false, T)),
                new TemporalValidity(T, null),
                T, T, "user-1", 1L, Map.of());
    }
}