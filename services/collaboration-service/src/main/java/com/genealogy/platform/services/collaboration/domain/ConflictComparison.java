package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;

/**
 * Per-field conflict comparison record. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.conflictFieldKinds` (E6.3) and `requirements.md`
 * R10.3 (the system SHALL provide a comparison model). The
 * comparison is rendered by the UI as a side-by-side
 * diff and consumed by the merge command factory to
 * decide whether auto-merge is safe.
 *
 * <p>Carrying the closed-set {@link ConflictFieldKind} keeps
 * the model purely additive: a new field kind requires an
 * ADR supersession.
 */
public record ConflictComparison(
        String resourceId,
        String field,
        ConflictFieldKind kind,
        String baseValue,
        String incomingValue,
        String localValue) {

    public ConflictComparison {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(baseValue, "baseValue");
        Objects.requireNonNull(incomingValue, "incomingValue");
        Objects.requireNonNull(localValue, "localValue");
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        if (field.length() > 64) {
            throw new IllegalArgumentException("field exceeds 64 characters");
        }
        if (baseValue.length() > 4096
                || incomingValue.length() > 4096
                || localValue.length() > 4096) {
            throw new IllegalArgumentException("field value exceeds 4096 characters");
        }
    }
}
