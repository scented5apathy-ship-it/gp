package com.genealogy.platform.services.genealogy.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opaque claim identifier. Mirrors {@code LifeEventId}: the
 * wire form is platform-neutral, no embedded PII.
 */
public record ClaimId(String wire) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9._:\\-]{1,128}$");

    public ClaimId {
        Objects.requireNonNull(wire, "wire");
        if (!PATTERN.matcher(wire).matches()) {
            throw new IllegalArgumentException(
                    "ClaimId wire fails pattern " + PATTERN.pattern() + ": " + wire);
        }
    }

    public static ClaimId of(String wire) {
        return new ClaimId(wire);
    }

    @Override
    public String toString() {
        return wire;
    }
}
