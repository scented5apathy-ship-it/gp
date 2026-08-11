package com.genealogy.platform.services.genealogy.projection;

import com.genealogy.platform.services.genealogy.domain.LivingStatus;
import com.genealogy.platform.services.genealogy.domain.PrivacyLevel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * One projected person node in the projection response.
 * Mirrors the BFF OpenAPI schema {@code ProjectionNode}. Built
 * by {@link TreeProjectionBuilder}; never mutated afterwards
 * (the builder applies redaction BEFORE the record is
 * constructed, see {@code TreeProjectionBuilder}).
 *
 * <p>All dropped fields are recorded in
 * {@link #droppedFields()} so the audit ledger can correlate.
 */
public record ProjectionNode(
        String personId,
        String displayName,
        LivingStatus livingStatus,
        Integer birthYear,
        Integer deathYear,
        PrivacyLevel privacyLevel,
        int generation,
        boolean redacted,
        Set<ProjectionRedactionReasonCode> reasonCodes,
        List<String> droppedFields) {

    public ProjectionNode {
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(livingStatus, "livingStatus");
        Objects.requireNonNull(privacyLevel, "privacyLevel");
        Objects.requireNonNull(reasonCodes, "reasonCodes");
        Objects.requireNonNull(droppedFields, "droppedFields");
        reasonCodes = Collections.unmodifiableSet(new LinkedHashSet<>(reasonCodes));
        droppedFields = Collections.unmodifiableList(new ArrayList<>(droppedFields));
        if (generation < -12 || generation > 12) {
            throw new IllegalArgumentException("generation outside [-12, 12]: " + generation);
        }
        if (birthYear != null && (birthYear < 1 || birthYear > 9999)) {
            throw new IllegalArgumentException("birthYear outside [1, 9999]: " + birthYear);
        }
        if (deathYear != null && (deathYear < 1 || deathYear > 9999)) {
            throw new IllegalArgumentException("deathYear outside [1, 9999]: " + deathYear);
        }
    }

    /**
     * Stable identity for cache lookups + renderer keying. Mirrors
     * the {@code ETag}/{@code X-Tree-Projection-Version} cache
     * contract (see {@code contracts/genealogy/tree-projection-cache.yaml}).
     */
    public String identity() {
        return personId + "@gen" + generation;
    }

    /** Synthetic stub for the root of the projection. */
    public static ProjectionNode rootStub(String personId,
                                          LivingStatus livingStatus,
                                          PrivacyLevel privacyLevel,
                                          Instant ignored) {
        return new ProjectionNode(
                personId,
                null,
                livingStatus,
                null,
                null,
                privacyLevel,
                0,
                false,
                java.util.Set.of(),
                List.of());
    }
}