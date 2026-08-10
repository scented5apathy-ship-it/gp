package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Claim aggregate root. Mirrors `requirements.md` R4.4
 * (provenance + certainty + confidence) + R8 (research log
 * + claim lifecycle) and `design.md` §5.3 + §5.5.
 *
 * <p>A claim is an assertion about a fact (a date, a place,
 * a name spelling, a relationship) tied to a {@code subject}
 * (the entity the claim is about) and a {@code subjectKind}
 * (which closed-set the subject belongs to — a Person id, a
 * Date id, a Place id, etc.). Claims are first-class objects
 * so the research log can show "what the editor believed at
 * the time" independently of the data it eventually replaced.
 *
 * <p>Invariants enforced by {@link ClaimInvariants}:
 *
 * <ul>
 *   <li>A claim MUST carry at least one
 *       {@link ClaimSourceReference} (R4.4 / R8).
 *   <li>{@code confidence}, when set, MUST be in [0,1].
 *   <li>{@code provenance = CORRECTION} MUST carry a
 *       non-null {@code correctsClaimId} back-reference so
 *       the merge service (E4.6) can wire the chain.
 *   <li>{@code provenance = IMPORTED} MUST NEVER combine
 *       with {@code certainty = VERIFIED} — the platform
 *       surfaces the gap rather than silently promoting an
 *       imported source to verified.
 * </ul>
 */
public record Claim(
        ClaimId claimId,
        String tenantId,
        String treeId,
        String subjectKind,
        String subjectId,
        Certainty certainty,
        ProvenanceStatus provenance,
        Double confidence,
        String statement,
        List<ClaimSourceReference> sources,
        String correctsClaimId,
        LifeEventId attachedEventId,
        String createdBy,
        Instant createdAt,
        Instant updatedAt,
        long version,
        Map<String, String> auditAttributes) {

    public Claim {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(treeId, "treeId");
        Objects.requireNonNull(subjectKind, "subjectKind");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(certainty, "certainty");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        sources = sources == null
                ? List.of()
                : Collections.unmodifiableList(sources);
        auditAttributes = auditAttributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(auditAttributes));
        if (confidence != null && (confidence < 0.0 || confidence > 1.0)) {
            throw new IllegalArgumentException(
                    "confidence out of [0,1]: " + confidence);
        }
        if (sources.size() > 32) {
            throw new IllegalArgumentException(
                    "sources exceed 32: " + sources.size());
        }
        if (statement != null && statement.length() > 2048) {
            throw new IllegalArgumentException(
                    "statement exceeds 2048 chars: " + statement.length());
        }
        if (provenance == ProvenanceStatus.CORRECTION && correctsClaimId == null) {
            throw new IllegalArgumentException(
                    "provenance=CORRECTION requires a non-null correctsClaimId");
        }
        if (provenance != ProvenanceStatus.CORRECTION && correctsClaimId != null) {
            throw new IllegalArgumentException(
                    "correctsClaimId is only allowed when provenance=CORRECTION");
        }
        if (provenance == ProvenanceStatus.IMPORTED
                && certainty == Certainty.VERIFIED) {
            throw new IllegalArgumentException(
                    "provenance=IMPORTED cannot combine with certainty=VERIFIED");
        }
        if (provenance == ProvenanceStatus.VERIFIED_BY_SOURCE
                && certainty != Certainty.ASSERTED
                && certainty != Certainty.VERIFIED) {
            throw new IllegalArgumentException(
                    "provenance=VERIFIED_BY_SOURCE requires certainty=ASSERTED|VERIFIED");
        }
        if (provenance == ProvenanceStatus.CORRECTION
                && certainty != Certainty.ASSERTED
                && certainty != Certainty.VERIFIED) {
            throw new IllegalArgumentException(
                    "provenance=CORRECTION requires certainty=ASSERTED|VERIFIED");
        }
    }

    public Claim withUpdated(
            Certainty nextCertainty,
            ProvenanceStatus nextProvenance,
            Double nextConfidence,
            String nextStatement,
            List<ClaimSourceReference> nextSources,
            Instant at) {
        return new Claim(
                claimId, tenantId, treeId,
                subjectKind, subjectId,
                nextCertainty == null ? certainty : nextCertainty,
                nextProvenance == null ? provenance : nextProvenance,
                nextConfidence == null ? confidence : nextConfidence,
                nextStatement == null ? statement : nextStatement,
                nextSources == null ? sources : nextSources,
                correctsClaimId,
                attachedEventId,
                createdBy,
                createdAt,
                at,
                version + 1,
                auditAttributes);
    }
}
