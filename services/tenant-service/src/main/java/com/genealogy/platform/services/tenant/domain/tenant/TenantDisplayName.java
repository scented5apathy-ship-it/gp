package com.genealogy.platform.services.tenant.domain.tenant;

/**
 * Tenant display name. Length 1-120 mirrors the OpenAPI pattern and
 * the V2 migration CHECK. Empty / null / over-length names are
 * rejected at the value-object boundary so the repository layer
 * never inserts an invalid row.
 *
 * <p>The class is named {@code TenantDisplayName} (not
 * {@code DisplayName}) to avoid colliding with the JUnit 5
 * {@code org.junit.jupiter.api.DisplayName} annotation that test
 * classes import for {@code @Nested} labelling.
 */
public record TenantDisplayName(String value) {

    public TenantDisplayName {
        if (value == null) {
            throw new IllegalArgumentException("display name must not be null");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("display name must not be blank");
        }
        if (value.length() > 120) {
            throw new IllegalArgumentException(
                    "display name length must be <= 120 (got " + value.length() + ")");
        }
    }
}