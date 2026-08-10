package com.genealogy.platform.services.genealogy.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure invariant checks for {@link Claim}. Mirrors
 * `requirements.md` R4.4 (provenance + certainty +
 * confidence) + R8 (research log + claim lifecycle) +
 * `design.md` §5.3 + §5.5 + §6.2 (ABAC obligations).
 *
 * <p>Policy mapping (driven by
 * {@code event-claim-policy.yaml::spec.provenancePolicy}):
 *
 * <ul>
 *   <li>{@code IMPORTED + VERIFIED}: hard deny. An imported
 *       claim cannot start life as verified; the editor must
 *       explicitly transition it to
 *       {@code provenance = VERIFIED_BY_SOURCE} (or
 *       {@code USER_ENTERED}) AND {@code certainty = VERIFIED}.
 *   <li>{@code sources.size() == 0}: hard deny. Every claim
 *       must trace back to at least one citation; an unsourced
 *       claim is treated as a hypothesis at best.
 *   <li>{@code confidence}: WARN if null, hard deny if out of
 *       range. Null is allowed (the editor chose to skip a
 *       number); out-of-range is never allowed.
 *   <li>{@code provenance = CORRECTION}: hard deny if
 *       {@code correctsClaimId} is missing — the merge
 *       service (E4.6) needs the chain.
 * </ul>
 */
public final class ClaimInvariants {

    /** Severity of an invariant finding. */
    public enum Severity {
        DENY,
        WARN,
        INFO
    }

    /** Closed-set reason codes emitted by the invariant service. */
    public enum ConflictCode {
        SOURCE_REQUIRED,
        CONFIDENCE_OUT_OF_RANGE,
        CORRECTION_MISSING_BACK_REFERENCE,
        CORRECTION_BACK_REFERENCE_FORBIDDEN,
        IMPORTED_CANNOT_BE_VERIFIED,
        PROVENANCE_CERTAINTY_NOT_ALLOWED,
    }

    /** One invariant finding. */
    public record Finding(Severity severity, ConflictCode code, String message) {
        public Finding {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    private ClaimInvariants() {}

    /**
     * Check the aggregate's intrinsic invariants. The
     * compact constructor of {@link Claim} already enforces
     * the structural ones (confidence range, source cap,
     * correction back-reference pairing); this method
     * re-runs them so a command service that bypassed the
     * constructor (e.g. JDBC rehydration) still gets the
     * same answer.
     */
    public static List<Finding> checkIntrinsic(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        List<Finding> findings = new ArrayList<>();
        if (claim.sources().isEmpty()) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.SOURCE_REQUIRED,
                    "claim requires at least one source reference"));
        }
        if (claim.confidence() != null
                && (claim.confidence() < 0.0 || claim.confidence() > 1.0)) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.CONFIDENCE_OUT_OF_RANGE,
                    "confidence out of [0,1]: " + claim.confidence()));
        } else if (claim.confidence() == null) {
            findings.add(new Finding(
                    Severity.WARN,
                    ConflictCode.CONFIDENCE_OUT_OF_RANGE,
                    "confidence is null — editor chose to skip a number"));
        }
        if (claim.provenance() == ProvenanceStatus.CORRECTION
                && claim.correctsClaimId() == null) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.CORRECTION_MISSING_BACK_REFERENCE,
                    "provenance=CORRECTION requires a non-null correctsClaimId"));
        }
        if (claim.provenance() != ProvenanceStatus.CORRECTION
                && claim.correctsClaimId() != null) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.CORRECTION_BACK_REFERENCE_FORBIDDEN,
                    "correctsClaimId is only allowed when provenance=CORRECTION"));
        }
        switch (claim.provenance()) {
            case IMPORTED -> {
                if (claim.certainty() == Certainty.VERIFIED) {
                    findings.add(new Finding(
                            Severity.DENY,
                            ConflictCode.IMPORTED_CANNOT_BE_VERIFIED,
                            "provenance=IMPORTED cannot combine with certainty=VERIFIED"));
                }
            }
            case VERIFIED_BY_SOURCE -> {
                if (claim.certainty() != Certainty.ASSERTED
                        && claim.certainty() != Certainty.VERIFIED) {
                    findings.add(new Finding(
                            Severity.DENY,
                            ConflictCode.PROVENANCE_CERTAINTY_NOT_ALLOWED,
                            "provenance=VERIFIED_BY_SOURCE requires certainty=ASSERTED|VERIFIED"));
                }
            }
            case CORRECTION -> {
                if (claim.certainty() != Certainty.ASSERTED
                        && claim.certainty() != Certainty.VERIFIED) {
                    findings.add(new Finding(
                            Severity.DENY,
                            ConflictCode.PROVENANCE_CERTAINTY_NOT_ALLOWED,
                            "provenance=CORRECTION requires certainty=ASSERTED|VERIFIED"));
                }
            }
            case USER_ENTERED -> { /* all certainties allowed */ }
            default -> { /* no rule */ }
        }
        return findings;
    }

    public static boolean hasDeny(List<Finding> findings) {
        for (Finding f : findings) {
            if (f.severity() == Severity.DENY) {
                return true;
            }
        }
        return false;
    }

    /** Convenience: run every check and return an unmodifiable list. */
    public static List<Finding> checkAll(Claim claim) {
        return Collections.unmodifiableList(checkIntrinsic(claim));
    }
}
