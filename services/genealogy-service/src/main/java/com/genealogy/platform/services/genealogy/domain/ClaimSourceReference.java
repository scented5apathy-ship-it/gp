package com.genealogy.platform.services.genealogy.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One citation supporting a {@link Claim}. Mirrors
 * `requirements.md` R4.4 (claim → many citations with
 * reliability rating) + R5 (repository / source / citation /
 * transcript / page locator / URL / quality / attachment).
 *
 * <p>The {@code attributes} map carries renderer hints:
 *
 * <ul>
 *   <li>{@code locator} — page number, file offset, etc.
 *   <li>{@code quote} — verbatim quote from the source.
 *   <li>{@code reliability} — editor-supplied rating 0..1.
 * </ul>
 *
 * <p>The map MUST NOT contain raw DNA / PII (E4.5 invariant,
 * see {@link ClaimInvariants}).
 */
public record ClaimSourceReference(
        SourceReferenceKind kind,
        String sourceId,
        String locator,
        String quote,
        Double reliability,
        Map<String, String> attributes) {

    public ClaimSourceReference {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sourceId, "sourceId");
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId is blank");
        }
        if (reliability != null && (reliability < 0.0 || reliability > 1.0)) {
            throw new IllegalArgumentException(
                    "reliability out of [0,1]: " + reliability);
        }
        attributes = attributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
