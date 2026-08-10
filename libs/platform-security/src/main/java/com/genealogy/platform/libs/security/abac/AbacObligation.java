package com.genealogy.platform.libs.security.abac;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Obligations attached to an ABAC decision. Per
 * {@code design.md} §6.2 obligations are redact / watermark / audit
 * — the engine MAY attach them to an {@code allow} decision.
 *
 * <p>Obligations are non-empty only on {@code allow}; a {@code deny}
 * carries no obligation and the engine never emits a watermark on
 * a denied response.
 */
public record AbacObligation(
        Set<Kind> kinds,
        RedactionProfile redactionProfile,
        WatermarkProfile watermarkProfile,
        AuditProfile auditProfile) {

    public enum Kind {
        REDACT_FIELDS,
        WATERMARK,
        AUDIT
    }

    public AbacObligation {
        Objects.requireNonNull(kinds, "kinds");
        kinds = kinds.isEmpty() ? EnumSet.noneOf(Kind.class) : EnumSet.copyOf(kinds);
        Objects.requireNonNull(redactionProfile, "redactionProfile");
        Objects.requireNonNull(watermarkProfile, "watermarkProfile");
        Objects.requireNonNull(auditProfile, "auditProfile");
    }

    public boolean hasKind(Kind kind) {
        return kinds.contains(kind);
    }

    public static AbacObligation none() {
        return new AbacObligation(
                EnumSet.noneOf(Kind.class),
                RedactionProfile.NONE,
                WatermarkProfile.NONE,
                AuditProfile.NONE);
    }

    public static AbacObligation redact(RedactionProfile profile) {
        return new AbacObligation(
                EnumSet.of(Kind.REDACT_FIELDS),
                Objects.requireNonNull(profile, "profile"),
                WatermarkProfile.NONE,
                AuditProfile.NONE);
    }

    public static AbacObligation redactAndAudit(
            RedactionProfile profile, AuditProfile audit) {
        return new AbacObligation(
                EnumSet.of(Kind.REDACT_FIELDS, Kind.AUDIT),
                Objects.requireNonNull(profile, "profile"),
                WatermarkProfile.NONE,
                Objects.requireNonNull(audit, "audit"));
    }

    public static AbacObligation watermarkAndAudit(
            WatermarkProfile watermark, AuditProfile audit) {
        return new AbacObligation(
                EnumSet.of(Kind.WATERMARK, Kind.AUDIT),
                RedactionProfile.NONE,
                Objects.requireNonNull(watermark, "watermark"),
                Objects.requireNonNull(audit, "audit"));
    }

    public record RedactionProfile(Set<String> fieldNames) {
        public static final RedactionProfile NONE = new RedactionProfile(Set.of());
        public RedactionProfile {
            Objects.requireNonNull(fieldNames, "fieldNames");
            fieldNames = Set.copyOf(fieldNames);
        }
    }

    public record WatermarkProfile(String label, boolean visible) {
        public static final WatermarkProfile NONE = new WatermarkProfile(null, false);
        public WatermarkProfile {
            if (label == null && visible) {
                throw new IllegalArgumentException(
                        "watermark label is required when visible=true");
            }
        }
    }

    public record AuditProfile(String action, boolean beforeAction) {
        public static final AuditProfile NONE = new AuditProfile(null, false);
        public AuditProfile {
            // NONE carries a null action on purpose; any other
            // AuditProfile MUST declare a non-blank action so the
            // audit hook can bucket events consistently.
            if (action != null && action.isBlank()) {
                throw new IllegalArgumentException(
                        "audit action must not be blank when supplied");
            }
        }

        public boolean isPresent() {
            return action != null;
        }
    }
}
