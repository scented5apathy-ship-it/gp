package com.genealogy.platform.services.tenant.domain.tenant;

import java.util.regex.Pattern;

/**
 * BCP-47 locale tag (e.g. {@code en-US}, {@code vi-VN},
 * {@code zh-Hant-TW}). The pattern is the same as the OpenAPI
 * {@code defaultLocale} field. Null is allowed — the tenant can
 * accept the platform default until the user picks a locale.
 */
public record Locale(String tag) {

    private static final Pattern PATTERN =
            Pattern.compile("^[a-z]{2,3}(-[A-Z]{2})?$");

    public Locale {
        // Null is permitted — means "platform default".
        if (tag != null && !PATTERN.matcher(tag).matches()) {
            throw new IllegalArgumentException(
                    "locale must match BCP-47 simplified form "
                            + PATTERN.pattern() + " (got '" + tag + "')");
        }
    }

    public boolean isPlatformDefault() {
        return tag == null;
    }
}