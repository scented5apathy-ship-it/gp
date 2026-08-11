package com.genealogy.platform.services.collaboration.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Merge command factory. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.mergeOutcomeKinds` (E6.3) and `requirements.md`
 * R10.3 + `design.md` §8.3 (merge produces a new domain
 * command rather than an arbitrary JSON patch on a
 * forbidden field).
 *
 * <p>Inputs:
 * <ul>
 *   <li>A {@link ConflictDetectionRequest}.
 *   <li>The forbidden field set (E6.2) + the forbidden
 *       operation set per proposal kind (E6.2).
 *   <li>The patch validation caps (E6.3).
 *   <li>The closed-set `ConflictResolution` requested by the
 *       writer.
 *   <li>Optional manual merge plan: a list of field names
 *       the writer chose to take from the incoming side.
 * </ul>
 */
public final class MergeCommandFactory {

    private MergeCommandFactory() {
    }

    public static MergeOutcome merge(
            ConflictDetectionRequest request,
            ConflictResolution requestedResolution,
            List<String> manualMergePlan,
            Set<String> forbiddenFields,
            Map<ProposalKind, Set<DomainCommandKind>> forbiddenKindOperations,
            long mergedVersion,
            int maxOperations) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(requestedResolution, "requestedResolution");
        Objects.requireNonNull(forbiddenFields, "forbiddenFields");
        Objects.requireNonNull(forbiddenKindOperations, "forbiddenKindOperations");
        if (mergedVersion <= 0) {
            throw new IllegalArgumentException(
                    "mergedVersion must be positive, got " + mergedVersion);
        }
        if (maxOperations <= 0) {
            throw new IllegalArgumentException("maxOperations must be positive");
        }

        if (requestedResolution == ConflictResolution.ABANDONED) {
            return new MergeOutcome(
                    MergeOutcomeKind.ABANDONED,
                    ConflictResolution.ABANDONED,
                    request.baseResourceId(),
                    request.localVersion(),
                    request.incomingVersion(),
                    mergedVersion,
                    List.of(),
                    List.copyOf(request.comparisons()),
                    "CONFLICT_ABANDONED");
        }

        List<ConflictComparison> touched = new ArrayList<>();
        List<ConflictComparison> same = new ArrayList<>();
        boolean hasDifferent = false;
        for (ConflictComparison c : request.comparisons()) {
            switch (c.kind()) {
                case SAME, ONLY_BASE, ONLY_LOCAL:
                    same.add(c);
                    break;
                case DIFFERENT, ONLY_INCOMING:
                    hasDifferent = true;
                    touched.add(c);
                    break;
                default:
                    touched.add(c);
            }
        }

        if (requestedResolution == ConflictResolution.AUTO_MERGE) {
            if (hasDifferent) {
                return new MergeOutcome(
                        MergeOutcomeKind.MANUAL_MERGED,
                        ConflictResolution.MANUAL_MERGE,
                        request.baseResourceId(),
                        request.localVersion(),
                        request.incomingVersion(),
                        mergedVersion,
                        List.of(),
                        List.copyOf(touched),
                        "CONFLICT_AUTO_MERGE_NOT_PERMITTED");
            }
            return new MergeOutcome(
                    MergeOutcomeKind.AUTO_MERGED,
                    ConflictResolution.AUTO_MERGE,
                    request.baseResourceId(),
                    request.localVersion(),
                    request.incomingVersion(),
                    mergedVersion,
                    List.of(new DomainCommand(
                            DomainCommandKind.UPDATE_PERSON,
                            request.baseResourceId(),
                            request.baseVersion(),
                            Map.of())),
                    List.copyOf(same),
                    "CONFLICT_AUTO_MERGED");
        }

        if (manualMergePlan == null || manualMergePlan.isEmpty()) {
            return new MergeOutcome(
                    MergeOutcomeKind.MANUAL_MERGED,
                    ConflictResolution.MANUAL_MERGE,
                    request.baseResourceId(),
                    request.localVersion(),
                    request.incomingVersion(),
                    mergedVersion,
                    List.of(),
                    List.copyOf(touched),
                    "CONFLICT_MANUAL_MERGE_AUDIT_REQUIRED");
        }

        Map<String, String> fieldChanges = new LinkedHashMap<>();
        for (String field : manualMergePlan) {
            if (forbiddenFields.contains(field)) {
                return new MergeOutcome(
                        MergeOutcomeKind.FORBIDDEN_FIELD,
                        ConflictResolution.MANUAL_MERGE,
                        request.baseResourceId(),
                        request.localVersion(),
                        request.incomingVersion(),
                        mergedVersion,
                        List.of(),
                        List.copyOf(request.comparisons()),
                        "CONFLICT_FORBIDDEN_FIELD");
            }
            for (ConflictComparison c : request.comparisons()) {
                if (c.field().equals(field)) {
                    fieldChanges.put(field, c.incomingValue());
                    break;
                }
            }
        }
        if (fieldChanges.size() > maxOperations) {
            return new MergeOutcome(
                    MergeOutcomeKind.FORBIDDEN_OPERATION,
                    ConflictResolution.MANUAL_MERGE,
                    request.baseResourceId(),
                    request.localVersion(),
                    request.incomingVersion(),
                    mergedVersion,
                    List.of(),
                    List.copyOf(request.comparisons()),
                    "CONFLICT_FORBIDDEN_OPERATION");
        }
        Set<DomainCommandKind> forbiddenOps = forbiddenKindOperations.getOrDefault(
                ProposalKind.PERSON, Set.of());
        if (forbiddenOps.contains(DomainCommandKind.UPDATE_PERSON)) {
            return new MergeOutcome(
                    MergeOutcomeKind.FORBIDDEN_OPERATION,
                    ConflictResolution.MANUAL_MERGE,
                    request.baseResourceId(),
                    request.localVersion(),
                    request.incomingVersion(),
                    mergedVersion,
                    List.of(),
                    List.copyOf(request.comparisons()),
                    "CONFLICT_FORBIDDEN_OPERATION");
        }
        List<DomainCommand> commands = new ArrayList<>();
        commands.add(new DomainCommand(
                DomainCommandKind.UPDATE_PERSON,
                request.baseResourceId(),
                request.baseVersion(),
                fieldChanges));
        return new MergeOutcome(
                MergeOutcomeKind.MANUAL_MERGED,
                ConflictResolution.MANUAL_MERGE,
                request.baseResourceId(),
                request.localVersion(),
                request.incomingVersion(),
                mergedVersion,
                List.copyOf(commands),
                List.copyOf(request.comparisons()),
                "CONFLICT_MANUAL_MERGED");
    }
}
