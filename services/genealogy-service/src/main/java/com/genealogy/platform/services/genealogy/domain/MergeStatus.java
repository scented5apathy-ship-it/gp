package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set of merge record statuses. Mirrors
 * {@code contracts/genealogy/person-merge-policy.yaml::
 * spec.mergeStatusLifecycle}.
 *
 * <p>Lifecycle:
 *
 * <ul>
 *   <li>{@link #CANDIDATES_SCORED} — the scorer produced
 *       candidate rows; the record is informational only.
 *   <li>{@link #REVIEWED} — an editor compared the
 *       candidates; the record is ready to commit (or
 *       reject).
 *   <li>{@link #MERGED} — terminal happy-path state. The
 *       losing Person has been re-keyed to the winner; the
 *       tombstone is set. Revertible within
 *       {@code revertWindowDays}.
 *   <li>{@link #REVERTED} — terminal. The merge was
 *       undone inside the revert window.
 *   <li>{@link #REJECTED} — terminal. The reviewer
 *       dismissed the candidate.
 * </ul>
 */
public enum MergeStatus {
    CANDIDATES_SCORED,
    REVIEWED,
    MERGED,
    REVERTED,
    REJECTED;

    public static MergeStatus fromWire(String wire) {
        if (wire == null) {
            return MergeStatus.CANDIDATES_SCORED;
        }
        return MergeStatus.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
