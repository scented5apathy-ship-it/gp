package com.genealogy.platform.services.collaboration.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One normalized mutation a {@code ChangeProposal} carries
 * (or the {@code PartialMergeExecutor} materialises from a
 * {@code Review} decision). Mirrors
 * `requirements.md` R10.1 (proposal diff carries base
 * version + normalized command) + `design.md` §8.3
 * (proposal stores base version + normalized patch /
 * command, never arbitrary JSON patch on forbidden fields).
 *
 * <p>Invariants enforced by the compact constructor:
 *
 * <ul>
 *   <li>{@code baseVersion} MUST be positive — the executor
 *       uses it to detect optimistic-concurrency conflicts
 *       when {@link #equals(Object)} compares two commands
 *       that target the same resource id.
 *   <li>{@code resourceId} is opaque, ≤ 128 characters,
 *       matches the safe character set used by
 *       {@link TenantScopedId}.
 *   <li>{@code fieldChanges} keys are restricted to the
 *       safe character set + ≤ 64 chars; values ≤ 4096
 *       chars. The {@code PartialMergeExecutor} rejects
 *       keys on {@code forbiddenDomainCommandFields}.
 *   <li>Total number of {@code fieldChanges} is bounded by
 *       {@link #MAX_FIELD_CHANGES}.
 * </ul>
 */
public record DomainCommand(
        DomainCommandKind kind,
        String resourceId,
        long baseVersion,
        Map<String, String> fieldChanges) {

    public static final int MAX_FIELD_CHANGES = 64;
    public static final int MAX_FIELD_KEY_LENGTH = 64;
    public static final int MAX_FIELD_VALUE_LENGTH = 4096;
    public static final int MAX_RESOURCE_ID_LENGTH = 128;

    public DomainCommand {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(resourceId, "resourceId");
        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId must not be blank");
        }
        if (resourceId.length() > MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "resourceId exceeds " + MAX_RESOURCE_ID_LENGTH + " characters");
        }
        if (!resourceId.matches("[A-Za-z0-9._\\-]+")) {
            throw new IllegalArgumentException(
                    "resourceId contains forbidden characters: " + resourceId);
        }
        if (baseVersion <= 0) {
            throw new IllegalArgumentException(
                    "baseVersion must be positive, got " + baseVersion);
        }
        fieldChanges = fieldChanges == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fieldChanges));
        if (fieldChanges.size() > MAX_FIELD_CHANGES) {
            throw new IllegalArgumentException(
                    "fieldChanges exceeds " + MAX_FIELD_CHANGES
                            + ": " + fieldChanges.size());
        }
        for (Map.Entry<String, String> e : fieldChanges.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("fieldChanges key must not be blank");
            }
            if (key.length() > MAX_FIELD_KEY_LENGTH) {
                throw new IllegalArgumentException(
                        "fieldChanges key exceeds "
                                + MAX_FIELD_KEY_LENGTH + " characters: " + key);
            }
            if (!key.matches("[A-Za-z0-9._\\-]+")) {
                throw new IllegalArgumentException(
                        "fieldChanges key contains forbidden characters: " + key);
            }
            if (value != null && value.length() > MAX_FIELD_VALUE_LENGTH) {
                throw new IllegalArgumentException(
                        "fieldChanges value exceeds "
                                + MAX_FIELD_VALUE_LENGTH + " characters for key " + key);
            }
        }
    }

    public static DomainCommand of(
            DomainCommandKind kind,
            String resourceId,
            long baseVersion,
            Map<String, String> fieldChanges) {
        return new DomainCommand(kind, resourceId, baseVersion, fieldChanges);
    }
}