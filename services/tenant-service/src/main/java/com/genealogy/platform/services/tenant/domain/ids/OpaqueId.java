package com.genealogy.platform.services.tenant.domain.ids;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Base class for every opaque server-issued identifier. The shape is
 * pinned by {@code contracts/openapi/public-api/v1/tenant.yaml} and
 * {@code contracts/events/shared/v1/identifiers.avsc} so the same
 * regex validates REST, gRPC and Kafka identifiers.
 *
 * <p>Format: {@code ^[A-Za-z0-9_-]{8,64}$}. Eight characters is the
 * minimum we need to encode 256 bits of entropy (UUIDv4 hex), but
 * subclasses may choose longer representations. The 64-char cap
 * matches the database CHECK constraint and the OpenAPI pattern.
 *
 * <p>Subclasses are intentionally final value objects: equality is
 * by {@link #value} only, the type tag distinguishes identifiers at
 * the API boundary (a {@link TenantId} is never confused with a
 * {@link UserId}).
 */
public abstract class OpaqueId {

    /**
     * Same regex as the database CHECK, the OpenAPI pattern and the
     * Avro {@code OpaqueId} schema. Compiled once.
     */
    public static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final String value;

    protected OpaqueId(String value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    getClass().getSimpleName() + " value must not be null");
        }
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    getClass().getSimpleName()
                            + " value must match "
                            + FORMAT.pattern()
                            + " (got length="
                            + value.length()
                            + ")");
        }
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpaqueId other)) return false;
        // Different subclasses are never equal even if the underlying
        // string matches — the type tag is part of identity.
        if (!getClass().equals(other.getClass())) return false;
        return value.equals(other.value);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getClass(), value);
    }

    @Override
    public final String toString() {
        return getClass().getSimpleName() + "[" + value + "]";
    }
}