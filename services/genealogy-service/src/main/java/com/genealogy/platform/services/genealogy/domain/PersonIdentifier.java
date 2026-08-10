package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

/**
 * External identifier attached to a {@link Person}. Mirrors
 * {@code contracts/genealogy/person-policy.yaml::spec.identifierKinds}
 * and `requirements.md` R4.1.
 *
 * <p>The {@code value} field is the raw external reference; it
 * is stored on the person_identifier row but is NEVER emitted on
 * the event bus (per `design.md` §7.3 the events carry only
 * opaque internal references). Public projection MUST treat the
 * raw value as living-PII and apply the same redaction rule as
 * the biography.
 */
public record PersonIdentifier(
        String identifierId,
        IdentifierKind kind,
        String value,
        String sourceSystem,
        boolean verified,
        Instant verifiedAt,
        Instant attachedAt) {

    public PersonIdentifier {
        Objects.requireNonNull(identifierId, "identifierId");
        Objects.requireNonNull(kind, "identifierKind");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(attachedAt, "attachedAt");
        if (value.isBlank()) {
            throw new IllegalArgumentException("identifier value is required");
        }
        if (value.length() > 256) {
            throw new IllegalArgumentException("identifier value exceeds 256 chars");
        }
        if (verified && verifiedAt == null) {
            throw new IllegalArgumentException("verifiedAt required when verified=true");
        }
    }

    public enum IdentifierKind {
        WIKIDATA_QID,
        FAMILYSEARCH_ID,
        ANCESTRY_ID,
        FINDAGRAVE_ID,
        GENI_ID,
        LOCAL_SLUG,
        GEDCOM_XREF;

        public static IdentifierKind fromWire(String wire) {
            if (wire == null) {
                throw new IllegalArgumentException("identifierKind is required");
            }
            return IdentifierKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
        }

        public String wire() {
            return name();
        }
    }
}
