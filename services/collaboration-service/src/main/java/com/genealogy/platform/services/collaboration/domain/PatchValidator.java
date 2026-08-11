package com.genealogy.platform.services.collaboration.domain;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure patch validator. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.patchValidation*` (E6.3) and `requirements.md`
 * R10.1 + `design.md` §8.3 (the collaboration service
 * never applies an arbitrary JSON patch on a forbidden
 * field).
 *
 * <p>Validation rules:
 * <ul>
 *   <li>Every key MUST be in the {@link
 *       CollaborationInvariants#FORBIDDEN_DOMAIN_COMMAND_FIELDS}
 *       complement set.
 *   <li>Every key length MUST be ≤
 *       {@code patchValidationMaxFieldKeyLength}
 *       (default 64).
 *   <li>Every value length MUST be ≤
 *       {@code patchValidationMaxFieldValueLength}
 *       (default 4096).
 *   <li>Total entries MUST be ≤
 *       {@code patchValidationMaxOperationsPerPatch}
 *       (default 256).
 * </ul>
 *
 * <p>On rejection the result enumerates every touched
 * forbidden field so the executor can emit a precise
 * audit reason code.
 */
public final class PatchValidator {

    private PatchValidator() {
    }

    public static PatchValidation validate(
            Map<String, String> fieldChanges,
            Set<String> forbiddenFields,
            int maxKeyLength,
            int maxValueLength,
            int maxOperations) {
        Objects.requireNonNull(fieldChanges, "fieldChanges");
        Objects.requireNonNull(forbiddenFields, "forbiddenFields");
        if (maxKeyLength <= 0) {
            throw new IllegalArgumentException("maxKeyLength must be positive");
        }
        if (maxValueLength <= 0) {
            throw new IllegalArgumentException("maxValueLength must be positive");
        }
        if (maxOperations <= 0) {
            throw new IllegalArgumentException("maxOperations must be positive");
        }
        if (fieldChanges.size() > maxOperations) {
            Set<String> all = new HashSet<>(fieldChanges.keySet());
            return PatchValidation.reject(all, "PATCH_OPERATION_TOO_LARGE");
        }
        Set<String> touched = new HashSet<>();
        Map<String, String> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : fieldChanges.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (key == null || key.length() > maxKeyLength) {
                touched.add(key == null ? "<null>" : key);
                continue;
            }
            if (value == null || value.length() > maxValueLength) {
                touched.add(key);
                continue;
            }
            if (forbiddenFields.contains(key)) {
                touched.add(key);
                continue;
            }
            sanitized.put(key, value);
        }
        if (touched.isEmpty()) {
            return PatchValidation.accept(sanitized);
        }
        return PatchValidation.reject(touched, "PATCH_OPERATION_FORBIDDEN_FIELD");
    }
}
