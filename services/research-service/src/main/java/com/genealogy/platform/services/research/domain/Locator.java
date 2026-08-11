package com.genealogy.platform.services.research.domain;

import java.util.Objects;

/**
 * Locator pointing at a specific anchor within a
 * {@code Source}: page, folio, line, entry, register,
 * certificate number, etc. Mirrors
 * `contracts/research/research-policy.yaml::
 * spec.locatorSchema` (E6.1) + `requirements.md` R8.1
 * (locator).
 *
 * <p>The locator is a free-form, structural-only string — the
 * research domain never interprets its content (translations,
 * row offsets, etc. belong to the presentation layer). The
 * invariant service rejects the locator when it is empty,
 * exceeds 256 characters, or contains control characters
 * (which would break the audit log).
 */
public record Locator(String raw, String page, String entry, String volume) {

    public static final int MAX_LENGTH = 256;

    public Locator {
        Objects.requireNonNull(raw, "raw");
        if (raw.isBlank()) {
            throw new IllegalArgumentException("locator.raw must not be blank");
        }
        if (raw.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "locator.raw exceeds " + MAX_LENGTH + " characters");
        }
        for (int i = 0; i < raw.length(); i += 1) {
            char c = raw.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new IllegalArgumentException(
                        "locator.raw contains control character at index " + i);
            }
        }
        page = page == null ? "" : page;
        entry = entry == null ? "" : entry;
        volume = volume == null ? "" : volume;
    }

    public boolean hasStructuredParts() {
        return !page.isBlank() || !entry.isBlank() || !volume.isBlank();
    }

    public static Locator of(String raw) {
        return new Locator(raw, "", "", "");
    }

    public static Locator page(String raw, String page) {
        return new Locator(raw, page, "", "");
    }

    public static Locator entry(String raw, String entry) {
        return new Locator(raw, "", entry, "");
    }
}
