package com.genealogy.platform.services.audit.domain;

import java.util.Objects;

/**
 * Result of {@link HashChainComputer#verify}. {@code tampered}
 * entries MUST trigger an ALERT and abort any retention sweep
 * (per <code>contracts/audit/retention.yaml::spec.sweep.onTamperDetected</code>).
 */
public record IntegrityStatus(boolean valid, String eventId, String detail) {

    public static final String TAMPER_MARKER = "INTEGRITY_BREACH";

    public static IntegrityStatus ok() {
        return new IntegrityStatus(true, null, null);
    }

    public static IntegrityStatus tampered(String eventId, String detail) {
        return new IntegrityStatus(false, Objects.requireNonNull(eventId, "eventId"),
                TAMPER_MARKER + ":" + Objects.requireNonNull(detail, "detail"));
    }
}
