package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * A name attached to a {@link Person}. Mirrors
 * `requirements.md` R4.1 (multiple names / script / kind / preferred)
 * and `design.md` §5.2 + §10.4 (preserve original script + locale).
 *
 * <p>{@code scriptTag} follows the closed-set declared in
 * {@code contracts/genealogy/person-policy.yaml::spec.nameScripts}.
 * The validator accepts the canonical case-folded form (Latn,
 * Cyrl, Hans, Hant, ...); unknown scripts are rejected.
 */
public record PersonName(
        String nameId,
        NameKind kind,
        String scriptTag,
        String localeTag,
        String display,
        String romanised,
        boolean preferred,
        Instant createdAt) {

    public PersonName {
        Objects.requireNonNull(nameId, "nameId");
        NameKind.require(kind);
        Objects.requireNonNull(scriptTag, "scriptTag");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(createdAt, "createdAt");
        if (display.isBlank()) {
            throw new IllegalArgumentException("display is required");
        }
        if (display.length() > 512) {
            throw new IllegalArgumentException(
                    "display exceeds 512 chars: " + display.length());
        }
        if (!ALLOWED_SCRIPT_TAG.matcher(scriptTag).matches()) {
            throw new IllegalArgumentException("scriptTag is not in closed-set: " + scriptTag);
        }
        if (localeTag != null && !ALLOWED_LOCALE_TAG.matcher(localeTag).matches()) {
            throw new IllegalArgumentException("localeTag is not BCP-47-ish: " + localeTag);
        }
        if (romanised != null && romanised.length() > 512) {
            throw new IllegalArgumentException("romanised exceeds 512 chars");
        }
    }

    /** Canonicalise the script tag to the form declared in the contract. */
    public static String canonicalScript(String scriptTag) {
        if (scriptTag == null) {
            throw new IllegalArgumentException("scriptTag is required");
        }
        return scriptTag.trim();
    }

    private static final java.util.regex.Pattern ALLOWED_SCRIPT_TAG =
            java.util.regex.Pattern.compile(
                    "^(Latn|Cyrl|Hans|Hant|Jpan|Kana|Hang|Hebr|Thai|Arab)$");
    private static final java.util.regex.Pattern ALLOWED_LOCALE_TAG =
            java.util.regex.Pattern.compile("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$");

    public String canonicalLocaleTag() {
        return localeTag == null ? "und" : localeTag.toLowerCase(Locale.ROOT);
    }
}
