package com.genealogy.platform.services.genealogy.domain;

/**
 * One merge candidate row. The merge scorer emits one
 * {@code MergeCandidate} per (winner, loser) pair it
 * considers; the record carries the per-component
 * contributions plus the resulting overall score (in
 * [0,1]).
 *
 * <p>Mirrors `requirements.md` R4.5 (merge with preview)
 * and `glossary-and-policy-matrix.md` §2.4
 * (auto-threshold = 0.85, manual-floor = 0.5).
 */
public record MergeCandidate(
        String candidateId,
        String winnerPersonId,
        String loserPersonId,
        double nameEquality,
        double dateProximity,
        double placeProximity,
        double identifierMatch,
        double overallScore,
        MergeProvenance provenance) {

    public MergeCandidate {
        if (winnerPersonId == null || winnerPersonId.isBlank()) {
            throw new IllegalArgumentException("winnerPersonId required");
        }
        if (loserPersonId == null || loserPersonId.isBlank()) {
            throw new IllegalArgumentException("loserPersonId required");
        }
        if (winnerPersonId.equals(loserPersonId)) {
            throw new IllegalArgumentException(
                    "self-merge forbidden: winner == loser ("
                            + winnerPersonId + ")");
        }
        if (nameEquality < 0.0 || nameEquality > 1.0) {
            throw new IllegalArgumentException(
                    "nameEquality out of [0,1]: " + nameEquality);
        }
        if (dateProximity < 0.0 || dateProximity > 1.0) {
            throw new IllegalArgumentException(
                    "dateProximity out of [0,1]: " + dateProximity);
        }
        if (placeProximity < 0.0 || placeProximity > 1.0) {
            throw new IllegalArgumentException(
                    "placeProximity out of [0,1]: " + placeProximity);
        }
        if (identifierMatch < 0.0 || identifierMatch > 1.0) {
            throw new IllegalArgumentException(
                    "identifierMatch out of [0,1]: " + identifierMatch);
        }
        if (overallScore < 0.0 || overallScore > 1.0) {
            throw new IllegalArgumentException(
                    "overallScore out of [0,1]: " + overallScore);
        }
        if (provenance == null) {
            throw new IllegalArgumentException("provenance required");
        }
    }
}
