package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set life-event kinds. Mirrors
 * {@code contracts/genealogy/event-claim-policy.yaml::
 * spec.lifeEventKinds} (E4.5) and `requirements.md` R4.1
 * (event attached to many persons with roles) + R10
 * (recurring memorial honoring living status).
 *
 * <p>The set intentionally includes {@link #RECURRING_MEMORIAL}
 * and {@link #CUSTOM}. RECURRING_MEMORIAL drives the
 * anniversary scheduler hook; CUSTOM lets the platform carry
 * culture-specific events (e.g. đám hỏi, lễ ăn hỏi,
 * lễ đầy tháng) verbatim under a free-form label without
 * polluting the closed-set semantics.
 */
public enum LifeEventKind {
    BIRTH,
    BAPTISM,
    DEATH,
    BURIAL,
    CREMATION,
    MARRIAGE,
    DIVORCE,
    ENGAGEMENT,
    EDUCATION,
    OCCUPATION,
    RESIDENCE,
    IMMIGRATION,
    EMIGRATION,
    MILITARY_SERVICE,
    ILLNESS,
    RELIGIOUS_CEREMONY,
    RECURRING_MEMORIAL,
    CUSTOM;

    public static LifeEventKind fromWire(String wire) {
        if (wire == null) {
            return CUSTOM;
        }
        return LifeEventKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    public boolean requiresAnniversaryDate() {
        return this == RECURRING_MEMORIAL;
    }

    public boolean requiresCustomLabel() {
        return this == CUSTOM;
    }
}
