package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opaque id of a {@link Relationship}. Mirrors
 * `design.md` §5.1 (opaque primary keys). The contract
 * rejects UUID literals at the boundary because the platform
 * may switch to ULID / KSUID later (E0.5 ADR); for now we
 * keep the shape-check only.
 */
public record RelationshipId(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9._:\\-]{1,128}$");

    public RelationshipId {
        Objects.requireNonNull(value, "relationshipId");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "relationshipId is not in opaque-id form: " + value);
        }
    }

    public String wire() {
        return value;
    }

    public static RelationshipId of(String value) {
        return new RelationshipId(value);
    }

    @Override
    public String toString() {
        return wire();
    }
}
