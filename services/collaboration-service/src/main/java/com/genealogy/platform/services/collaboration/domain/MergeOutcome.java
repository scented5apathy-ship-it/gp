package com.genealogy.platform.services.collaboration.domain;

import java.util.List;
import java.util.Objects;

/**
 * Output of the conflict detection + merge command factory.
 * Mirrors `contracts/collaboration/mixed-collaboration-policy
 * .yaml` ::spec.mergeOutcomeKinds + `auditClassOnConflict`
 * (E6.3) and `requirements.md` R10.3 + `design.md` §8.3.
 *
 * <p>When {@code kind == AUTO_MERGED} the
 * {@code materialisedCommands} carry the merge commands the
 * downstream consumer must apply. When {@code kind ==
 * MANUAL_MERGED} the comparison list is included so the UI
 * can render the comparison model and the writer can
 * decide field-by-field. When {@code kind == ABANDONED} the
 * writer explicitly requested a discard.
 */
public record MergeOutcome(
        MergeOutcomeKind kind,
        ConflictResolution resolution,
        String resourceId,
        long localVersion,
        long incomingVersion,
        long mergedVersion,
        List<DomainCommand> materialisedCommands,
        List<ConflictComparison> comparisons,
        String reasonCode) {

    public MergeOutcome {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(materialisedCommands, "materialisedCommands");
        Objects.requireNonNull(comparisons, "comparisons");
        Objects.requireNonNull(reasonCode, "reasonCode");
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        if (reasonCode.length() > 128) {
            throw new IllegalArgumentException("reasonCode exceeds 128 characters");
        }
        if (materialisedCommands.size() > 256) {
            throw new IllegalArgumentException(
                    "materialisedCommands exceeds 256 entries, got "
                            + materialisedCommands.size());
        }
        if (comparisons.size() > 64) {
            throw new IllegalArgumentException(
                    "comparisons exceeds 64 entries, got " + comparisons.size());
        }
        if (kind == MergeOutcomeKind.AUTO_MERGED && materialisedCommands.isEmpty()) {
            throw new IllegalArgumentException(
                    "AUTO_MERGED outcome requires at least one materialisedCommand");
        }
        if (kind == MergeOutcomeKind.MANUAL_MERGED && comparisons.isEmpty()) {
            throw new IllegalArgumentException(
                    "MANUAL_MERGED outcome requires at least one comparison entry");
        }
    }
}
