package com.genealogy.platform.services.collaboration.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Patch validation result. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.patchValidationMax*` (E6.3) and `requirements.md`
 * R10.1 + `design.md` §8.3 (the collaboration service never
 * applies an arbitrary JSON patch on a forbidden field).
 * The merge command factory refuses to materialise a
 * domain command that touches a forbidden field/key.
 */
public record PatchValidation(
        boolean acceptable,
        Set<String> forbiddenFieldsTouched,
        Map<String, String> sanitizedFieldChanges,
        String reasonCode) {

    public PatchValidation {
        Objects.requireNonNull(forbiddenFieldsTouched, "forbiddenFieldsTouched");
        Objects.requireNonNull(sanitizedFieldChanges, "sanitizedFieldChanges");
        Objects.requireNonNull(reasonCode, "reasonCode");
        if (reasonCode.isBlank()) {
            throw new IllegalArgumentException("reasonCode must not be blank");
        }
        if (reasonCode.length() > 128) {
            throw new IllegalArgumentException("reasonCode exceeds 128 characters");
        }
        if (acceptable && !forbiddenFieldsTouched.isEmpty()) {
            throw new IllegalArgumentException(
                    "acceptable=true but forbiddenFieldsTouched is non-empty");
        }
        if (!acceptable && forbiddenFieldsTouched.isEmpty()) {
            throw new IllegalArgumentException(
                    "acceptable=false but forbiddenFieldsTouched is empty");
        }
    }

    public static PatchValidation accept(Map<String, String> sanitized) {
        return new PatchValidation(true, Set.of(), Map.copyOf(sanitized), "PATCH_ACCEPTED");
    }

    public static PatchValidation reject(Set<String> forbiddenFields, String reasonCode) {
        return new PatchValidation(false, Set.copyOf(forbiddenFields), Map.of(), reasonCode);
    }
}
