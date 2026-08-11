package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Snapshot returned by the Flagsmith adapter. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.flagsmithSyncOutcomes` (E6.3) and `requirements.md`
 * R10.4 (policy changes flow through Flagsmith only as a
 * rollout switch — the YAML contract remains the source
 * of truth).
 */
public record FlagsmithSnapshot(
        String featureFlagKey,
        String environmentId,
        String snapshotVersion,
        FlagsmithRolloutStrategy strategy,
        boolean enabled,
        Map<String, String> featureValues,
        Set<CollaborationRole> enabledRoles,
        Set<TreeBranch> enabledBranches,
        Set<RoutingResourceType> enabledResourceTypes,
        Instant capturedAt) {

    public FlagsmithSnapshot {
        Objects.requireNonNull(featureFlagKey, "featureFlagKey");
        Objects.requireNonNull(environmentId, "environmentId");
        Objects.requireNonNull(snapshotVersion, "snapshotVersion");
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(featureValues, "featureValues");
        Objects.requireNonNull(enabledRoles, "enabledRoles");
        Objects.requireNonNull(enabledBranches, "enabledBranches");
        Objects.requireNonNull(enabledResourceTypes, "enabledResourceTypes");
        Objects.requireNonNull(capturedAt, "capturedAt");
        if (featureFlagKey.isBlank() || featureFlagKey.length() > 128) {
            throw new IllegalArgumentException("featureFlagKey length invalid");
        }
        if (environmentId.isBlank() || environmentId.length() > 64) {
            throw new IllegalArgumentException("environmentId length invalid");
        }
        if (snapshotVersion.isBlank() || snapshotVersion.length() > 64) {
            throw new IllegalArgumentException("snapshotVersion length invalid");
        }
    }
}
