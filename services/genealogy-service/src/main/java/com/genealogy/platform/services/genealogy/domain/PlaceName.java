package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One historical / current / transliterated name on a {@link Place}.
 * Each name carries the locale tag the renderer should use to
 * decide whether the name is renderable in the user's current
 * locale. The {@code languageTag} follows BCP-47 (e.g. {@code vi},
 * {@code vi-VN}, {@code zh-Hans}).
 */
public record PlaceName(String display, String languageTag, Instant validFrom, Instant validUntil) {

    private static final Pattern BCP47_PATTERN =
            Pattern.compile("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*$");
    private static final int MAX_DISPLAY_CHARS = 512;

    public PlaceName {
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(languageTag, "languageTag");
        if (display.isBlank()) {
            throw new IllegalArgumentException("display must not be blank");
        }
        if (display.length() > MAX_DISPLAY_CHARS) {
            throw new IllegalArgumentException(
                    "display exceeds " + MAX_DISPLAY_CHARS + " chars");
        }
        if (!BCP47_PATTERN.matcher(languageTag).matches()) {
            throw new IllegalArgumentException(
                    "languageTag not BCP-47: " + languageTag);
        }
        if (validFrom != null && validUntil != null && validFrom.isAfter(validUntil)) {
            throw new IllegalArgumentException(
                    "validFrom must be <= validUntil");
        }
    }

    public boolean matchesLocale(String userLanguageTag) {
        if (userLanguageTag == null) {
            return false;
        }
        String user = userLanguageTag.trim().toLowerCase(Locale.ROOT);
        String mine = languageTag.trim().toLowerCase(Locale.ROOT);
        return mine.equals(user) || user.startsWith(mine + "-");
    }
}
