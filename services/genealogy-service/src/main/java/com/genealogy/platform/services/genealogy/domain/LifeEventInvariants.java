package com.genealogy.platform.services.genealogy.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Pure invariant checks for {@link LifeEvent}. Mirrors
 * `requirements.md` R4.1 (event attaches to many persons
 * with roles) + R4.4 + R10 (recurring memorial + living
 * redaction) and `design.md` §5.3 + §5.5 + §6.2 + §6.3.
 *
 * <p>Policy mapping:
 *
 * <ul>
 *   <li>{@code kind = RECURRING_MEMORIAL}: hard deny if the
 *       date is missing (the renderer can't schedule a
 *       recurring notice without an anniversary).
 *   <li>{@code kind = CUSTOM}: hard deny if the customLabel
 *       is blank.
 *   <li>{@code provenance = IMPORTED + certainty = VERIFIED}:
 *       hard deny. Same invariant as {@link ClaimInvariants}.
 *   <li>At least one participant SHOULD be a SUBJECT (warn
 *       otherwise — an event with no subject is suspicious).
 * </ul>
 */
public final class LifeEventInvariants {

    /** Severity of an invariant finding. */
    public enum Severity {
        DENY,
        WARN,
        INFO
    }

    /** Closed-set reason codes emitted by the invariant service. */
    public enum ConflictCode {
        RECURRING_MEMORIAL_REQUIRES_DATE,
        CUSTOM_REQUIRES_LABEL,
        IMPORTED_CANNOT_BE_VERIFIED,
        NO_SUBJECT,
        LIVING_PARTICIPANT_REDACTION_HINT,
    }

    /** One invariant finding. */
    public record Finding(Severity severity, ConflictCode code, String message) {
        public Finding {
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    private LifeEventInvariants() {}

    public static List<Finding> checkIntrinsic(LifeEvent event) {
        Objects.requireNonNull(event, "event");
        List<Finding> findings = new ArrayList<>();
        if (event.kind() == LifeEventKind.RECURRING_MEMORIAL && event.date() == null) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.RECURRING_MEMORIAL_REQUIRES_DATE,
                    "kind=RECURRING_MEMORIAL requires a non-null date"));
        }
        if (event.kind() == LifeEventKind.CUSTOM
                && (event.customLabel() == null || event.customLabel().isBlank())) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.CUSTOM_REQUIRES_LABEL,
                    "kind=CUSTOM requires a non-blank customLabel"));
        }
        if (event.provenance() == ProvenanceStatus.IMPORTED
                && event.certainty() == Certainty.VERIFIED) {
            findings.add(new Finding(
                    Severity.DENY,
                    ConflictCode.IMPORTED_CANNOT_BE_VERIFIED,
                    "provenance=IMPORTED cannot combine with certainty=VERIFIED"));
        }
        boolean hasSubject = false;
        for (EventParticipant p : event.participants()) {
            if (p.role() == EventParticipantRole.SUBJECT) {
                hasSubject = true;
                break;
            }
        }
        if (!hasSubject) {
            findings.add(new Finding(
                    Severity.WARN,
                    ConflictCode.NO_SUBJECT,
                    "event has no SUBJECT participant — verify the participant list"));
        }
        return findings;
    }

    public static boolean hasDeny(List<Finding> findings) {
        for (Finding f : findings) {
            if (f.severity() == Severity.DENY) {
                return true;
            }
        }
        return false;
    }

    public static List<Finding> checkAll(LifeEvent event) {
        return Collections.unmodifiableList(checkIntrinsic(event));
    }
}
