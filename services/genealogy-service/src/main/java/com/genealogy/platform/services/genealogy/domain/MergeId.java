package com.genealogy.platform.services.genealogy.domain;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Opaque merge-record id. Mirrors {@code Person.id} and
 * {@code Relationship.id} patterns: prefix + UUID, no
 * business semantics, regex-validated to keep URLs /
 * audit-row references safe.
 */
public record MergeId(String value) {

    private static final Pattern PATTERN =
            Pattern.compile("^[A-Za-z0-9._:\\-]{1,128}$");

    public MergeId {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "mergeId must match " + PATTERN.pattern() + ": " + value);
        }
    }

    public static MergeId of(String raw) {
        return new MergeId(raw);
    }

    /** Build a fresh merge id with the {@code merge-} prefix. */
    public static MergeId newId() {
        return new MergeId("merge-" + UUID.randomUUID());
    }

    public String wire() {
        return value;
    }
}
