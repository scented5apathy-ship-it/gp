package com.genealogy.platform.services.tenant.domain.tenant;

import java.util.regex.Pattern;

/**
 * Human-readable tenant slug. Format mirrors the OpenAPI pattern and
 * the V2 migration CHECK constraint:
 *
 * <pre>
 *   ^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$
 * </pre>
 *
 * <p>Slugs are globally unique per V2 migration
 * ({@code tenants_slug_global_uidx}); cross-tenant reuse would
 * collide in shared URLs (e.g. public sharing links per R6). The
 * application layer MUST catch {@link DuplicateSlugException} from
 * the repository on insert and surface it as a 409.
 */
public record Slug(String value) {

    private static final Pattern PATTERN =
            Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$");

    public Slug {
        if (value == null) {
            throw new IllegalArgumentException("slug must not be null");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "slug must match " + PATTERN.pattern() + " (got '" + value + "')");
        }
    }
}