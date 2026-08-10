package com.genealogy.platform.services.genealogy.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Opaque life-event identifier. Mirrors the
 * {@code relationship-graph-policy.yaml} pattern (E4.4):
 * platform-neutral, no embedded PII. A {@code LifeEventId}
 * is the canonical wire + storage form.
 */
public record LifeEventId(String wire) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9._:\\-]{1,128}$");

    public LifeEventId {
        Objects.requireNonNull(wire, "wire");
        if (!PATTERN.matcher(wire).matches()) {
            throw new IllegalArgumentException(
                    "LifeEventId wire fails pattern " + PATTERN.pattern() + ": " + wire);
        }
    }

    public static LifeEventId of(String wire) {
        return new LifeEventId(wire);
    }

    @Override
    public String toString() {
        return wire;
    }
}
