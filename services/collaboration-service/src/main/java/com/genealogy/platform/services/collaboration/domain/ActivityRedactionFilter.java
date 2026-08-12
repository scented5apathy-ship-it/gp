package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Comment redaction filter. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.mentionSensitiveFieldGuardRail + mentionSensitiveFields +
 * activityFeedRedactedFieldMarker` (E6.4), `requirements.md`
 * R10.5 + R15.4 + `design.md` §8.3.
 *
 * <p>The filter is a pure function exposed to the
 * application layer; the executor never lets a comment body
 * or a notification payload carry a sensitive field. The
 * closed-set {@link #MENTION_SENSITIVE_FIELDS} mirrors the
 * E6.2 {@link CollaborationInvariants#FORBIDDEN_DOMAIN_COMMAND_FIELDS}
 * set + the {@link Comment#MENTION_SENSITIVE_FIELDS} set.
 */
public final class ActivityRedactionFilter {

    public static final Set<String> MENTION_SENSITIVE_FIELDS =
            Comment.MENTION_SENSITIVE_FIELDS;

    public static final String REDACTED_FIELD_MARKER = "[REDACTED]";

    private ActivityRedactionFilter() {
    }

    public static boolean mentionsSensitiveField(String body) {
        return Comment.mentionsSensitiveField(body);
    }

    public static String sanitizeBody(String body) {
        return Comment.sanitizeBody(body);
    }

    public static boolean isRedactedKey(String field) {
        Objects.requireNonNull(field, "field");
        return MENTION_SENSITIVE_FIELDS.contains(field);
    }

    public static int redactedKeyCount(String body) {
        if (body == null) return 0;
        int count = 0;
        String lower = body.toLowerCase();
        for (String field : MENTION_SENSITIVE_FIELDS) {
            if (lower.contains(field.toLowerCase())) {
                count += 1;
            }
        }
        return count;
    }
}
