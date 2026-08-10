package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Place aggregate. Mirrors `requirements.md` R4.1 (places have
 * hierarchy + historical names + coordinates + provider-neutral
 * authority reference) and `design.md` §5.3.
 *
 * <p>A {@code Place} is shared across the whole tenant — many
 * persons / events / relationships may attach to the same
 * place. The {@link #hierarchy} is a top-down chain of opaque
 * place ids: index 0 is the root (country / world), index
 * {@code size() - 1} is the place itself's parent. The chain
 * depth is capped per {@code date-place-policy.yaml::
 * spec.maxHierarchyDepth} (default 8).
 */
public record Place(
        String placeId,
        String tenantId,
        PlaceKind kind,
        List<PlaceName> names,
        Coordinates coordinates,
        PlaceAuthority authority,
        List<String> hierarchy,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        Certainty certainty,
        long version) {

    /** Depth cap mirrors `date-place-policy.yaml::spec.maxHierarchyDepth`. */
    public static final int MAX_HIERARCHY_DEPTH = 8;
    /** Cap mirrors `date-place-policy.yaml::spec.maxNamesPerPlace`. */
    public static final int MAX_NAMES = 32;

    public Place {
        Objects.requireNonNull(placeId, "placeId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(certainty, "certainty");
        names = names == null ? List.of() : Collections.unmodifiableList(names);
        hierarchy = hierarchy == null ? List.of() : Collections.unmodifiableList(hierarchy);
        if (names.size() > MAX_NAMES) {
            throw new IllegalArgumentException(
                    "names exceeds " + MAX_NAMES + " rows: " + names.size());
        }
        if (hierarchy.size() > MAX_HIERARCHY_DEPTH) {
            throw new IllegalArgumentException(
                    "hierarchy exceeds " + MAX_HIERARCHY_DEPTH + " depth: "
                            + hierarchy.size());
        }
        for (String ancestor : hierarchy) {
            if (ancestor == null || ancestor.isBlank()) {
                throw new IllegalArgumentException(
                        "hierarchy ancestor must be non-blank opaque id");
            }
            if (ancestor.equals(placeId)) {
                throw new IllegalArgumentException(
                        "place cannot list itself in hierarchy: " + placeId);
            }
        }
        validateDistinctNames(names);
    }

    private static void validateDistinctNames(List<PlaceName> names) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (PlaceName n : names) {
            String key = n.languageTag().toLowerCase(Locale.ROOT)
                    + "|" + n.display().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                throw new IllegalArgumentException(
                        "duplicate (languageTag, display) on place: " + key);
            }
        }
    }

    /**
     * Pick the best renderable name for the user's locale.
     * Falls back to the first name when no match.
     */
    public PlaceName nameFor(String userLanguageTag) {
        for (PlaceName n : names) {
            if (n.matchesLocale(userLanguageTag)) {
                return n;
            }
        }
        return names.isEmpty() ? null : names.get(0);
    }

    public Place withKind(PlaceKind next, Instant at) {
        return new Place(
                placeId, tenantId, next, names, coordinates, authority, hierarchy,
                createdAt, at, createdBy, certainty, version + 1);
    }

    public Place withNames(List<PlaceName> nextNames, Instant at) {
        return new Place(
                placeId, tenantId, kind, nextNames, coordinates, authority, hierarchy,
                createdAt, at, createdBy, certainty, version + 1);
    }

    public Place withCoordinates(Coordinates next, Instant at) {
        return new Place(
                placeId, tenantId, kind, names, next, authority, hierarchy,
                createdAt, at, createdBy, certainty, version + 1);
    }

    public Place withAuthority(PlaceAuthority next, Instant at) {
        return new Place(
                placeId, tenantId, kind, names, coordinates, next, hierarchy,
                createdAt, at, createdBy, certainty, version + 1);
    }

    /** Closed-set dotted-path diff helper for event payloads. */
    public static java.util.LinkedHashSet<String> diff(Place before, Place after) {
        java.util.LinkedHashSet<String> fields = new java.util.LinkedHashSet<>();
        if (before.kind != after.kind) fields.add("kind");
        if (!before.names.equals(after.names)) fields.add("names[]");
        if (!java.util.Objects.equals(before.coordinates, after.coordinates)) {
            fields.add("coordinates");
        }
        if (!java.util.Objects.equals(before.authority, after.authority)) {
            fields.add("authority");
        }
        if (!before.hierarchy.equals(after.hierarchy)) fields.add("hierarchy[]");
        if (before.certainty != after.certainty) fields.add("certainty");
        return fields;
    }
}
