package com.genealogy.platform.services.genealogy.projection;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Summary of the redaction obligations applied to the
 * projection. The BFF OpenAPI schema is
 * {@code RedactionSummary}; the count of dropped fields lets
 * the audit ledger correlate with the
 * {@code audit.redaction_event} log entries.
 */
public record RedactionSummary(
        Set<ProjectionRedactionReasonCode> reasonCodes,
        int droppedFieldCount,
        String policyVersion) {

    public RedactionSummary {
        Objects.requireNonNull(reasonCodes, "reasonCodes");
        Objects.requireNonNull(policyVersion, "policyVersion");
        reasonCodes = Set.copyOf(new LinkedHashSet<>(reasonCodes));
    }

    public static RedactionSummary empty(String policyVersion) {
        return new RedactionSummary(Set.of(), 0, policyVersion);
    }
}