package com.genealogy.platform.services.tenant.domain.tenant;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * IANA timezone id wrapper. Format is intentionally permissive
 * (lowercase letters + digits + {@code _/-+}) because the IANA
 * database evolves; the database layer only stores the raw string.
 * We DO NOT validate against a hardcoded list — that would couple
 * this service to a snapshot of the IANA database and break
 * timezones introduced after a Java release. The platform default
 * (UTC) is the safe fallback.
 *
 * <p>Null is permitted at the boundary — see {@link Locale}.
 */
public record Timezone(String id) {

    /** Conservative subset of IANA tz characters. */
    private static final Pattern PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_+\\-/]{0,62}$");

    /**
     * Canonical platform default. The seed tenant inserts use this
     * so the timezone field is never null on persisted rows (audit
     * hooks depend on a stable default for grouping).
     */
    public static final Timezone PLATFORM_DEFAULT = new Timezone("UTC");

    /** Recognised calendar identifiers per ADR-E0.5-14. */
    public static final Set<String> KNOWN = Set.of(
            "GREGORIAN", "HEBREW", "HIJRI", "ETHIOPIAN", "CUSTOM");

    public Timezone {
        // Null is permitted at the boundary — see Locale.
        if (id != null && !PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "timezone id must match IANA form "
                            + PATTERN.pattern() + " (got '" + id + "')");
        }
    }
}