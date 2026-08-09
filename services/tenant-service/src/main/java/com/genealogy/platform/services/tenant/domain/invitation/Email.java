package com.genealogy.platform.services.tenant.domain.invitation;

import java.util.regex.Pattern;

/**
 * Invitation email. Format is the simplified RFC 5322 form enforced
 * by V2 migration CHECK. The full RFC 5322 parser is not appropriate
 * here because the email field is purely a routing key for the
 * invite — Keycloak returns a canonicalised email on user lookup
 * that we trust at acceptance time.
 *
 * <p>PII: this value contains a personal email address. It MUST NOT
 * appear in logs, traces or third-party telemetry; the audit hook
 * redacts before forwarding.
 */
public record Email(String value) {

    private static final Pattern PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Email {
        if (value == null) {
            throw new IllegalArgumentException("email must not be null");
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "email must match " + PATTERN.pattern() + " (got '" + value + "')");
        }
    }
}