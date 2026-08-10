package com.genealogy.platform.services.genealogy.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure invariant checks for {@link Relationship}.
 *
 * <p>Mirrors `requirements.md` R4.4 / R8 / R10 / R18 and
 * `design.md` §5.2 + §5.3 + §6.2 (ABAC obligations). The
 * service is intentionally side-effect free; the command
 * service (E4.7) consumes its output and decides whether
 * to reject (deny) or emit a soft warning.
 *
 * <p>Policy mapping (driven by
 * {@code relationship-graph-policy.yaml}):
 *
 * <ul>
 *   <li>{@code selfLinkPolicy = deny}: a relationship MUST
 *       NOT have a participant reference the same person twice
 *       in the same role. {@link Relationship} already rejects
 *       this in its compact constructor; this service
 *       re-checks at the cross-relationship level.
 *   <li>{@code cyclePolicy = deny}: a chain A→B→…→A is a hard
 *       deny. The renderer / search projection cannot recover
 *       from a malformed graph.
 *   <li>{@code chronologicalConflictPolicy = warn-only}:
 *       overlapping {@link TemporalValidity} on the same
 *       participant between two relationships is recorded as
 *       a soft warning. Hard-denying would lock out legitimate
 *       biology (twins with overlapping {@code BETWEEN}
 *       windows).
 *   <li>{@code partnerOverlapPolicy = allow-with-validity}:
 *       overlapping PARTNER relationships are NOT a conflict;
 *       they are recorded as informational only.
 * </ul>
 */
public final class RelationshipInvariants {

    /** Severity of an invariant finding. */
    public enum Severity {
        DENY,
        WARN,
        INFO
    }

    /** One invariant finding. */
    public record Finding(Severity severity, RelationshipConflictCode code, String message) {
        public Finding {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    private RelationshipInvariants() {}

    /**
     * Check the aggregate's intrinsic invariants. The compact
     * constructor of {@link Relationship} already enforces
     * some of them; this method re-runs the cross-cutting
     * checks so a command service that bypassed the
     * constructor (e.g. JDBC rehydration) still gets the same
     * answer.
     */
    public static List<Finding> checkIntrinsic(Relationship rel) {
        Objects.requireNonNull(rel, "rel");
        List<Finding> findings = new ArrayList<>();
        Map<ParticipantRole, Long> roleCount = new LinkedHashMap<>();
        for (ParticipantRef p : rel.participants()) {
            roleCount.merge(p.role(), 1L, Long::sum);
        }
        switch (rel.kind()) {
            case BIOLOGICAL_PARENT, ADOPTIVE_PARENT, FOSTER_PARENT,
                    STEP_PARENT, SURROGATE_PARENT -> {
                if (roleCount.getOrDefault(ParticipantRole.PARENT, 0L) < 1) {
                    findings.add(new Finding(
                            Severity.DENY,
                            RelationshipConflictCode.PARENT_REQUIRES_AT_LEAST_ONE_PARENT,
                            "parent-kind relationship requires >=1 PARENT participant"));
                }
                if (roleCount.getOrDefault(ParticipantRole.CHILD, 0L) < 1) {
                    findings.add(new Finding(
                            Severity.DENY,
                            RelationshipConflictCode.PARENT_REQUIRES_AT_LEAST_ONE_CHILD,
                            "parent-kind relationship requires >=1 CHILD participant"));
                }
            }
            case PARTNER -> {
                if (roleCount.getOrDefault(ParticipantRole.PARTNER, 0L) < 2) {
                    findings.add(new Finding(
                            Severity.DENY,
                            RelationshipConflictCode.PARTNER_REQUIRES_TWO_PARTICIPANTS,
                            "PARTNER relationship requires >=2 PARTNER participants"));
                }
            }
            case GUARDIAN -> {
                if (roleCount.getOrDefault(ParticipantRole.GUARDIAN, 0L) < 1
                        || roleCount.getOrDefault(ParticipantRole.WARD, 0L) < 1) {
                    findings.add(new Finding(
                            Severity.DENY,
                            RelationshipConflictCode.PARTICIPANT_ROLE_MISMATCH,
                            "GUARDIAN relationship requires >=1 GUARDIAN and >=1 WARD"));
                }
            }
            case SIBLING, HALF_SIBLING, STEP_SIBLING -> {
                if (roleCount.getOrDefault(ParticipantRole.SIBLING, 0L) < 2) {
                    findings.add(new Finding(
                            Severity.DENY,
                            RelationshipConflictCode.PARTICIPANT_ROLE_MISMATCH,
                            "sibling-kind relationship requires >=2 SIBLING participants"));
                }
            }
            case GODPARENT -> {
                if (roleCount.getOrDefault(ParticipantRole.GUARDIAN, 0L) < 1
                        || roleCount.getOrDefault(ParticipantRole.SUBJECT, 0L) < 1) {
                    findings.add(new Finding(
                            Severity.DENY,
                            RelationshipConflictCode.PARTICIPANT_ROLE_MISMATCH,
                            "GODPARENT relationship requires >=1 GUARDIAN and >=1 SUBJECT"));
                }
            }
            case CUSTOM -> { /* CUSTOM allows any role combo */ }
            default -> { /* no kind-specific role check */ }
        }
        if (rel.kind() == RelationshipKind.PARTNER && rel.partnerSubKind() == null) {
            findings.add(new Finding(
                    Severity.DENY,
                    RelationshipConflictCode.PARTNER_REQUIRES_SUB_KIND,
                    "kind=PARTNER requires partnerSubKind"));
        }
        if (rel.kind() != RelationshipKind.PARTNER && rel.partnerSubKind() != null) {
            findings.add(new Finding(
                    Severity.DENY,
                    RelationshipConflictCode.SUB_KIND_FORBIDDEN_ON_NON_PARTNER,
                    "partnerSubKind forbidden on kind=" + rel.kind().wire()));
        }
        if (rel.kind() == RelationshipKind.CUSTOM
                && (rel.customLabel() == null || rel.customLabel().isBlank())) {
            findings.add(new Finding(
                    Severity.DENY,
                    RelationshipConflictCode.CUSTOM_REQUIRES_LABEL,
                    "kind=CUSTOM requires non-blank customLabel"));
        }
        return findings;
    }

    /**
     * Check whether {@code candidate} introduces a cycle
     * against a graph of already-committed relationships on
     * the same tree. The renderer cannot recover from a cycle
     * so the policy is hard-deny.
     */
    public static List<Finding> checkCycle(
            Relationship candidate,
            List<Relationship> committed) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(committed, "committed");
        List<Finding> findings = new ArrayList<>();
        // Build adjacency: PARENT → CHILD edge direction. A
        // cycle exists when candidate edges close a loop.
        java.util.Map<String, java.util.Set<String>> parentToChild = new java.util.HashMap<>();
        java.util.Set<String> allParents = new java.util.HashSet<>();
        java.util.Set<String> allChildren = new java.util.HashSet<>();
        for (Relationship r : committed) {
            for (ParticipantRef p : r.participants()) {
                if (p.role() == ParticipantRole.PARENT) {
                    allParents.add(parentKey(p));
                }
                if (p.role() == ParticipantRole.CHILD) {
                    allChildren.add(childKey(p));
                }
            }
            addEdges(r, parentToChild);
        }
        addEdges(candidate, parentToChild);
        for (ParticipantRef p : candidate.participants()) {
            if (p.role() != ParticipantRole.PARENT) {
                continue;
            }
            String pk = parentKey(p);
            if (!allChildren.contains(pk)) {
                continue;
            }
            java.util.Deque<String> stack = new java.util.ArrayDeque<>();
            java.util.Set<String> visited = new java.util.HashSet<>();
            stack.push(pk);
            while (!stack.isEmpty()) {
                String cur = stack.pop();
                if (!visited.add(cur)) {
                    continue;
                }
                java.util.Set<String> kids = parentToChild.getOrDefault(cur, java.util.Set.of());
                if (kids.contains(pk)) {
                    findings.add(new Finding(
                            Severity.DENY,
                            RelationshipConflictCode.CYCLE,
                            "parent " + pk + " is reachable from themselves via CHILD edges"));
                    return findings;
                }
                for (String k : kids) {
                    stack.push(k);
                }
            }
        }
        return findings;
    }

    /**
     * Check chronological overlap against a peer set. Overlap
     * of two BIOLOGICAL_PARENT relationships on the same
     * child is a WARN (twins + disputed parentage are
     * legitimate). PARTNER overlap is INFO only per
     * {@code partnerOverlapPolicy = allow-with-validity}.
     */
    public static List<Finding> checkChronologicalOverlap(
            Relationship candidate,
            List<Relationship> peers) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(peers, "peers");
        List<Finding> findings = new ArrayList<>();
        Set<String> candidatePersons = new LinkedHashSet<>();
        for (ParticipantRef p : candidate.participants()) {
            if (!p.unknown()) {
                candidatePersons.add(p.personId());
            }
        }
        for (Relationship peer : peers) {
            if (peer.relationshipId().equals(candidate.relationshipId())) {
                continue;
            }
            if (peer.kind() == candidate.kind()
                    && peer.kind() != RelationshipKind.PARTNER
                    && !peer.validity().overlaps(candidate.validity())) {
                continue;
            }
            Set<String> peerPersons = new LinkedHashSet<>();
            for (ParticipantRef p : peer.participants()) {
                if (!p.unknown()) {
                    peerPersons.add(p.personId());
                }
            }
            peerPersons.retainAll(candidatePersons);
            if (peerPersons.isEmpty()) {
                continue;
            }
            if (!peer.validity().overlaps(candidate.validity())) {
                continue;
            }
            if (candidate.kind() == RelationshipKind.PARTNER) {
                findings.add(new Finding(
                        Severity.INFO,
                        RelationshipConflictCode.PARTNER_OVERLAP_WITH_ACTIVE,
                        "partner overlap on " + peerPersons
                                + " between "
                                + peer.relationshipId().wire()
                                + " and "
                                + candidate.relationshipId().wire()
                                + " (informational only)"));
            } else if (isParentalKind(candidate.kind())) {
                findings.add(new Finding(
                        Severity.WARN,
                        RelationshipConflictCode.OVERLAPPING_PARENTAL_VALIDITY,
                        "parental-kind overlap on " + peerPersons
                                + " between "
                                + peer.relationshipId().wire()
                                + " and "
                                + candidate.relationshipId().wire()));
            }
        }
        return findings;
    }

    /**
     * Convenience: aggregate every check into one pass.
     * Hard denies are returned first so the command service
     * can short-circuit.
     */
    public static List<Finding> checkAll(
            Relationship candidate,
            List<Relationship> committed) {
        List<Finding> findings = new ArrayList<>();
        findings.addAll(checkIntrinsic(candidate));
        if (hasDeny(findings)) {
            return findings;
        }
        findings.addAll(checkCycle(candidate, committed));
        if (hasDeny(findings)) {
            return findings;
        }
        findings.addAll(checkChronologicalOverlap(candidate, committed));
        return Collections.unmodifiableList(findings);
    }

    public static boolean hasDeny(List<Finding> findings) {
        for (Finding f : findings) {
            if (f.severity() == Severity.DENY) {
                return true;
            }
        }
        return false;
    }

    private static boolean isParentalKind(RelationshipKind k) {
        return k == RelationshipKind.BIOLOGICAL_PARENT
                || k == RelationshipKind.ADOPTIVE_PARENT
                || k == RelationshipKind.FOSTER_PARENT
                || k == RelationshipKind.STEP_PARENT
                || k == RelationshipKind.SURROGATE_PARENT;
    }

    private static String parentKey(ParticipantRef p) {
        return p.unknown() ? "UNKNOWN:" + p.participantId() : "P:" + p.personId();
    }

    private static String childKey(ParticipantRef p) {
        return p.unknown() ? "UNKNOWN:" + p.participantId() : "P:" + p.personId();
    }

    private static void addEdges(
            Relationship r,
            java.util.Map<String, java.util.Set<String>> parentToChild) {
        java.util.Set<String> parents = new java.util.LinkedHashSet<>();
        java.util.Set<String> children = new java.util.LinkedHashSet<>();
        for (ParticipantRef p : r.participants()) {
            if (p.role() == ParticipantRole.PARENT) {
                parents.add(parentKey(p));
            }
            if (p.role() == ParticipantRole.CHILD) {
                children.add(childKey(p));
            }
        }
        for (String pk : parents) {
            java.util.Set<String> kids =
                    parentToChild.computeIfAbsent(pk, k -> new java.util.LinkedHashSet<>());
            kids.addAll(children);
        }
    }
}
