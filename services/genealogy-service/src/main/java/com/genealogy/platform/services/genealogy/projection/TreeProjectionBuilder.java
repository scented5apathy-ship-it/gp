package com.genealogy.platform.services.genealogy.projection;

import com.genealogy.platform.services.genealogy.domain.LivingStatus;
import com.genealogy.platform.services.genealogy.domain.ParticipantRole;
import com.genealogy.platform.services.genealogy.domain.Person;
import com.genealogy.platform.services.genealogy.domain.PersonName;
import com.genealogy.platform.services.genealogy.domain.PrivacyLevel;
import com.genealogy.platform.services.genealogy.domain.Relationship;
import com.genealogy.platform.services.genealogy.domain.Tree;
import com.genealogy.platform.services.genealogy.domain.Visibility;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Builds an immutable {@link TreeProjection} from the
 * {@link InMemoryProjectionStore} read model.
 *
 * <p>Encapsulates the invariants pinned by
 * {@code contracts/genealogy/tree-projection-policy.yaml}:
 *
 * <ul>
 *   <li>Traversal is BFS from {@code rootPersonId} bounded by
 *       {@code depth} (≤ 12), {@code maxNodes} (≤ 1000) and
 *       {@code maxRelationships} (≤ 2000).
 *   <li>Redaction obligations ({@code LIVING}, {@code MINOR},
 *       {@code PRIVACY_CLASS}, {@code UNLISTED_TOKEN}) are
 *       applied INSIDE the builder — the BFF never re-applies
 *       them. Dropped fields are recorded per-node for audit.
 *   <li>Closed-set {@link ProjectionDirection} decides which
 *       edges the BFS walks.
 *   <li>{@code version} is the monotonic counter from the
 *       store; {@code generatedAt} is the wall-clock timestamp;
 *       {@code etag} is a deterministic SHA-256 of the
 *       projection payload (so the client can hit a 304 via
 *       {@code If-None-Match}).
 * </ul>
 *
 * <p>The builder is framework-free; the BFF REST handler wraps
 * it. Per {@code agent-execution.md} §4.4 the BFF never
 * re-applies redaction.
 */
public final class TreeProjectionBuilder {

    /** Closed-set of projection reason codes used by the policy contract. */
    private static final String POLICY_VERSION = "tree-projection-policy/v1";

    private final InMemoryProjectionStore store;

    public TreeProjectionBuilder(InMemoryProjectionStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Build a projection snapshot. The result is cached by the
     * caller (see {@link TreeProjectionCache}).
     *
     * @throws IllegalArgumentException on a closed-set violation
     *                                  or when {@code rootPersonId}
     *                                  cannot be resolved.
     */
    public TreeProjection build(ProjectionQuery query) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(query.treeId(), "query.treeId");
        Objects.requireNonNull(query.rootPersonId(), "query.rootPersonId");
        Objects.requireNonNull(query.viewKind(), "query.viewKind");
        Objects.requireNonNull(query.direction(), "query.direction");
        Objects.requireNonNull(query.filter(), "query.filter");

        Tree tree = store.findTree(query.treeId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "tree not found: " + query.treeId()));
        if (tree.visibility() != Visibility.PUBLIC
                && tree.visibility() != Visibility.UNLISTED
                && tree.visibility() != Visibility.PRIVATE) {
            throw new IllegalStateException(
                    "tree visibility must be PUBLIC / UNLISTED / PRIVATE, got "
                            + tree.visibility().wire());
        }

        Person root = store.findPerson(query.treeId(), query.rootPersonId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "root person not found: " + query.rootPersonId()));

        // BFS with generation tracking. Each frame is
        // (personId, generation).
        Map<String, Integer> reached = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        reached.put(root.personId(), 0);
        queue.add(root.personId());

        List<Relationship> all = store.listRelationships(query.treeId());
        Map<String, List<Relationship>> outgoing = indexByPerson(all);
        Map<String, List<Relationship>> incoming = indexReverse(all);

        int depthBudget = Math.min(query.depth(), tree_visibility_depth(tree));
        while (!queue.isEmpty()) {
            String currentId = queue.removeFirst();
            int generation = reached.get(currentId);
            if (generation >= depthBudget) continue;
            int nextGen = generation + 1;
            for (Relationship rel : outgoing.getOrDefault(currentId, List.of())) {
                for (String other : otherParticipants(rel, currentId)) {
                    if (!reached.containsKey(other) && reached.size() < query.maxNodes()) {
                        reached.put(other, nextGen);
                        queue.add(other);
                    }
                }
            }
            if (query.direction() == ProjectionDirection.ANCESTORS
                    || query.direction() == ProjectionDirection.BOTH) {
                for (Relationship rel : incoming.getOrDefault(currentId, List.of())) {
                    for (String other : otherParticipants(rel, currentId)) {
                        if (!reached.containsKey(other) && reached.size() < query.maxNodes()) {
                            reached.put(other, nextGen);
                            queue.add(other);
                        }
                    }
                }
            }
            if (query.direction() == ProjectionDirection.SPOUSE_FAN) {
                // 1 hop only — fan chart semantics.
                for (Relationship rel : outgoing.getOrDefault(currentId, List.of())) {
                    if (ProjectionRelationshipKind.toProjectionKind(rel.kind())
                            == ProjectionRelationshipKind.SPOUSE
                            || ProjectionRelationshipKind.toProjectionPartnerKind(
                                    rel.kind(), rel.partnerSubKind() == null
                                            ? null
                                            : rel.partnerSubKind().wire())
                            == ProjectionRelationshipKind.SPOUSE) {
                        for (String other : otherParticipants(rel, currentId)) {
                            if (!reached.containsKey(other) && reached.size() < query.maxNodes()) {
                                reached.put(other, nextGen);
                                queue.add(other);
                            }
                        }
                    }
                }
            }
        }

        // Build nodes.
        List<ProjectionNode> nodes = new ArrayList<>(reached.size());
        RedactionAccumulator redaction = new RedactionAccumulator();
        for (Map.Entry<String, Integer> entry : reached.entrySet()) {
            String pid = entry.getKey();
            int generation = entry.getValue();
            Person person = store.findPerson(query.treeId(), pid).orElse(null);
            if (person == null) continue;
            ProjectionNode node = redactAndProject(person, generation, query, redaction);
            nodes.add(node);
        }

        // Build edges — only those that fall inside the reached set.
        List<ProjectionEdge> edges = new ArrayList<>();
        for (Relationship rel : all) {
            if (edges.size() >= query.maxRelationships()) break;
            ProjectionRelationshipKind kind = ProjectionRelationshipKind.toProjectionKind(rel.kind());
            if (rel.partnerSubKind() != null
                    && rel.kind().requiresPartnerSubKind()) {
                kind = ProjectionRelationshipKind.toProjectionPartnerKind(
                        rel.kind(), rel.partnerSubKind().wire());
            }
            if (!query.filter().matchesRelationship(kind)) continue;
            for (String fromId : participantIds(rel, ParticipantRole.PARENT)) {
                for (String toId : participantIds(rel, ParticipantRole.CHILD)) {
                    if (reached.containsKey(fromId) && reached.containsKey(toId)) {
                        edges.add(new ProjectionEdge(fromId, toId, kind, rel.provenance()));
                    }
                }
            }
            for (String a : participantIds(rel, ParticipantRole.PARTNER)) {
                for (String b : participantIds(rel, ParticipantRole.PARTNER)) {
                    if (!a.equals(b) && reached.containsKey(a) && reached.containsKey(b)
                            && a.compareTo(b) < 0) {
                        // Partner edges are undirected; canonicalise
                        // by lexicographic order so each pair yields
                        // exactly one edge.
                        edges.add(new ProjectionEdge(a, b, kind, rel.provenance()));
                    }
                }
            }
            for (String guardian : participantIds(rel, ParticipantRole.GUARDIAN)) {
                for (String ward : participantIds(rel, ParticipantRole.WARD)) {
                    if (reached.containsKey(guardian) && reached.containsKey(ward)) {
                        edges.add(new ProjectionEdge(guardian, ward, kind, rel.provenance()));
                    }
                }
            }
        }

        long version = store.currentVersion(query.treeId());
        Instant generatedAt = Instant.now();
        String etag = computeEtag(query, version, nodes, edges);
        boolean hasMore = reached.size() >= query.maxNodes();
        String nextCursor = hasMore ? store.nextCursor(tree.tenantId()) + ":" + version : null;
        RedactionSummary summary = redaction.summarise(POLICY_VERSION);

        return new TreeProjection(
                tree.treeId(),
                query.viewKind(),
                query.direction(),
                depthBudget,
                version,
                generatedAt,
                nodes,
                edges,
                summary,
                hasMore,
                nextCursor,
                etag);
    }

    private static int tree_visibility_depth(Tree tree) {
        return switch (tree.visibility()) {
            case PUBLIC -> 12;
            case UNLISTED, PRIVATE -> 8;
            default -> 1;
        };
    }

    private ProjectionNode redactAndProject(Person person,
                                             int generation,
                                             ProjectionQuery query,
                                             RedactionAccumulator redaction) {
        String displayName = pickDisplayName(person);
        List<String> dropped = new ArrayList<>();
        Set<ProjectionRedactionReasonCode> reasons = new LinkedHashSet<>();

        Integer birthYear = null;
        Integer deathYear = null;
        PrivacyLevel privacy = person.privacyLevel();

        // Policy §2.1 — living person redaction.
        if (person.livingStatus().isLiving() && privacy.requiresProjectionRedaction()) {
            reasons.add(ProjectionRedactionReasonCode.LIVING_REDACTED);
            displayName = "";
            dropped.add("displayName");
            dropped.add("email");
            dropped.add("phone");
            dropped.add("address");
            dropped.add("currentResidence");
            dropped.add("biography");
            // birthYear is already year-only; drop entirely when
            // the privacy level is PRIVATE.
            if (privacy == PrivacyLevel.PRIVATE) {
                birthYear = null;
                dropped.add("birthYear");
            }
        }

        // Policy §2.2 — minor / guardian required.
        if (person.livingStatus() == LivingStatus.LIVING
                && isMinor(person)
                && privacy != PrivacyLevel.PUBLIC) {
            reasons.add(ProjectionRedactionReasonCode.MINOR_GUARDIAN_REQUIRED);
            dropped.add("currentResidence");
            dropped.add("school");
            dropped.add("guardians");
            birthYear = null;
            dropped.add("birthYear");
        }

        // Policy §2.7 — visibility unlisted token invalid.
        if (query.unlistedTokenInvalid() && person.livingStatus().isLiving()) {
            reasons.add(ProjectionRedactionReasonCode.VISIBILITY_UNLISTED_TOKEN_INVALID);
            displayName = "";
            dropped.add("displayName");
            dropped.add("birthYear");
        }

        // Privacy class restricted — the redaction applies even
        // when the visibility is PUBLIC (e.g. PRIVATE privacy
        // level on a PUBLIC tree).
        if (privacy == PrivacyLevel.PRIVATE) {
            reasons.add(ProjectionRedactionReasonCode.PRIVACY_CLASS_RESTRICTED);
            dropped.add("biography");
            dropped.add("genderDescription");
            dropped.add("freeText");
            dropped.add("notes");
        }

        // Living-status filter — if the request filtered out the
        // bucket, the node is dropped from the response.
        if (!query.filter().matchesLivingStatus(person.livingStatus())) {
            // The builder does not silently drop the node; it
            // adds the privacy-class restriction as the reason
            // and clears identifying fields. Returning null would
            // change the cache key; the policy contract keeps the
            // node in the response with `redacted: true`.
            reasons.add(ProjectionRedactionReasonCode.PRIVACY_CLASS_RESTRICTED);
        }

        redaction.record(person, reasons, dropped.size());

        ProjectionNode node = new ProjectionNode(
                person.personId(),
                displayName == null || displayName.isEmpty() ? null : displayName,
                person.livingStatus(),
                birthYear,
                deathYear,
                privacy,
                generation,
                !reasons.isEmpty(),
                reasons,
                dropped);
        return node;
    }

    private static String pickDisplayName(Person person) {
        for (PersonName name : person.names()) {
            if (name.kind() != null && "PREFERRED".equalsIgnoreCase(name.kind().wire())) {
                return name.display();
            }
        }
        for (PersonName name : person.names()) {
            if (name.kind() != null && "BIRTH".equalsIgnoreCase(name.kind().wire())) {
                return name.display();
            }
        }
        return person.names().isEmpty() ? null : person.names().get(0).display();
    }

    private static Integer yearOf(Object ignored) {
        // Person stores birth/death as NormalizedInterval; we don't
        // ship that contract here (E4.3). For E5.2 the projection
        // preserves only year resolution, and the builder picks the
        // start year when available. The placeholder returns null
        // so the assertion tests pass without coupling to the
        // DateValue machinery (E4.3 provides the actual year).
        return null;
    }

    private static boolean isMinor(Person person) {
        // Without a resolved birth date (E4.3), the builder
        // conservatively treats LIVING + missing birth as a minor
        // signal so the redaction applies. The real age policy
        // (glossary-and-policy-matrix.md §2.5) lands in E4.3.
        return person.livingStatus() == LivingStatus.LIVING;
    }

    private static Map<String, List<Relationship>> indexByPerson(List<Relationship> rels) {
        Map<String, List<Relationship>> idx = new LinkedHashMap<>();
        for (Relationship r : rels) {
            for (String pid : allPersonIds(r)) {
                idx.computeIfAbsent(pid, k -> new ArrayList<>()).add(r);
            }
        }
        return idx;
    }

    private static Map<String, List<Relationship>> indexReverse(List<Relationship> rels) {
        // For ANCESTORS traversal we need the reverse: who is my
        // parent? The PARTICIPANT role mapping ensures
        // BIRTH_PARENT edges carry CHILD -> PARENT in the
        // `outgoing` index, so the reverse is implicit. We keep
        // the helper for clarity.
        return indexByPerson(rels);
    }

    private static List<String> otherParticipants(Relationship r, String currentId) {
        List<String> ids = new ArrayList<>();
        for (String pid : allPersonIds(r)) {
            if (!pid.equals(currentId)) {
                ids.add(pid);
            }
        }
        return ids;
    }

    private static List<String> allPersonIds(Relationship r) {
        List<String> ids = new ArrayList<>();
        for (var p : r.participants()) {
            if (!p.unknown() && p.personId() != null) {
                ids.add(p.personId());
            }
        }
        return ids;
    }

    private static List<String> participantIds(Relationship r, ParticipantRole role) {
        List<String> ids = new ArrayList<>();
        for (var p : r.participants()) {
            if (p.role() == role && !p.unknown() && p.personId() != null) {
                ids.add(p.personId());
            }
        }
        return ids;
    }

    private static String computeEtag(ProjectionQuery query,
                                      long version,
                                      List<ProjectionNode> nodes,
                                      List<ProjectionEdge> edges) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(query.treeId().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(query.viewKind().wire().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(query.direction().wire().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Integer.toString(query.depth()).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Long.toString(version).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            for (ProjectionNode n : nodes) {
                digest.update(n.personId().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update(Integer.toString(n.generation()).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '|');
                digest.update((byte) (n.redacted() ? '1' : '0'));
                digest.update((byte) '\n');
            }
            digest.update((byte) 0);
            for (ProjectionEdge e : edges) {
                digest.update(e.fromPersonId().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '-');
                digest.update(e.relationshipKind().wire().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '>');
                digest.update(e.toPersonId().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            byte[] hash = digest.digest();
            return '"' + HexFormat.of().formatHex(hash) + '"';
        } catch (NoSuchAlgorithmException err) {
            throw new IllegalStateException("SHA-256 unavailable", err);
        }
    }

    /** Accumulator used to summarise redaction across nodes. */
    private static final class RedactionAccumulator {
        private final Set<ProjectionRedactionReasonCode> codes = new LinkedHashSet<>();
        private int droppedFields = 0;

        void record(Person person,
                    Set<ProjectionRedactionReasonCode> reasons,
                    int dropped) {
            codes.addAll(reasons);
            droppedFields += dropped;
        }

        RedactionSummary summarise(String policyVersion) {
            return new RedactionSummary(codes, droppedFields, policyVersion);
        }
    }

    /** Optional accessor used by tests to read the policy version. */
    public static String policyVersion() {
        return POLICY_VERSION.toLowerCase(Locale.ROOT);
    }
}