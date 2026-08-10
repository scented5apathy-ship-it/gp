package com.genealogy.platform.libs.security.abac;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Reference implementation of {@link AbacPolicyEngine} that mirrors
 * the rules in {@code design.md} §6.2, {@code requirements.md} R4 /
 * R13 / R16 and {@code privacy-and-legal-gate.md} §5 / §7.
 *
 * <p>The engine is purely functional: it never touches I/O and never
 * emits audit events. The caller is responsible for publishing an
 * audit entry whenever the decision carries an audit obligation.
 *
 * <p>Rule ordering follows deny-first: a single failing predicate
 * short-circuits the evaluation and returns the most specific deny
 * reason code. The motivation is traceability — the audit entry
 * MUST surface the most informative reason rather than a generic
 * {@code context_deny}.
 */
public final class DefaultAbacPolicyEngine implements AbacPolicyEngine {

    private static final String ENGINE_ID = "default-abac/v1";

    /** Default fields redacted from a living record on public projection. */
    public static final Set<String> DEFAULT_LIVING_REDACT_FIELDS = Set.of(
            "birthDate",
            "deathDate",
            "currentResidence",
            "phone",
            "email",
            "occupation",
            "biography");

    /** Default fields redacted from a minor record on every projection. */
    public static final Set<String> DEFAULT_MINOR_REDACT_FIELDS = Set.of(
            "birthDate",
            "currentResidence",
            "phone",
            "email",
            "school",
            "guardians",
            "biography");

    /** Privacy classes that REQUIRE a purpose-scoped consent record. */
    public static final Set<PrivacyClass> CONSENT_REQUIRED_CLASSES = EnumSet.of(
            PrivacyClass.GENETIC_RAW,
            PrivacyClass.GENETIC_DERIVED,
            PrivacyClass.SENSITIVE);

    private final Clock clock;
    private final Set<String> livingRedactFields;
    private final Set<String> minorRedactFields;

    public DefaultAbacPolicyEngine() {
        this(Clock.systemUTC(),
                DEFAULT_LIVING_REDACT_FIELDS,
                DEFAULT_MINOR_REDACT_FIELDS);
    }

    public DefaultAbacPolicyEngine(
            Clock clock,
            Set<String> livingRedactFields,
            Set<String> minorRedactFields) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.livingRedactFields = Set.copyOf(livingRedactFields);
        this.minorRedactFields = Set.copyOf(minorRedactFields);
    }

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public AbacDecision evaluate(AbacRequest request) {
        Objects.requireNonNull(request, "request");
        String decisionId = "abac-" + UUID.randomUUID();

        // 1. Suspended / soft-deleted resources deny immediately
        //    regardless of relationship allow — privacy gate §6.4 T-07.
        if (request.suspended() || request.softDeleted()) {
            return AbacDecision.denyWithObligations(
                    decisionId,
                    ReasonCode.CONTEXTUAL_DENY,
                    AbacObligation.redactAndAudit(
                            new AbacObligation.RedactionProfile(
                                    Set.of("displayName", "biography")),
                            new AbacObligation.AuditProfile(
                                    "resource_unavailable", false)),
                    null);
        }

        // 2. Impersonation must never grant DNA / secret export.
        //    requirements.md R16.4 + privacy gate §10 TM-06.
        if (request.impersonated()
                && request.resourcePrivacyClass().isGenetic()) {
            return AbacDecision.denyWithObligations(
                    decisionId,
                    ReasonCode.CONTEXTUAL_DENY,
                    AbacObligation.redactAndAudit(
                            new AbacObligation.RedactionProfile(Set.of("dnaRaw")),
                            new AbacObligation.AuditProfile(
                                    "impersonation_dna_blocked", false)),
                    null);
        }

        // 3. Genetic data requires explicit consent per purpose
        //    (R13 + privacy gate §DNA D-05). Support sessions are
        //    NEVER allowed to read GENETIC_RAW even with a
        //    technical support flag (R16.4).
        if (request.resourcePrivacyClass().isGenetic()) {
            if (request.supportSession()
                    && request.resourcePrivacyClass() == PrivacyClass.GENETIC_RAW) {
                return AbacDecision.denyWithObligations(
                        decisionId,
                        ReasonCode.JURISDICTION_BLOCKED,
                        AbacObligation.redactAndAudit(
                                new AbacObligation.RedactionProfile(Set.of("dnaRaw")),
                                new AbacObligation.AuditProfile(
                                        "support_session_dna_blocked", false)),
                        null);
            }

            if (request.optionalConsent().isEmpty()) {
                return AbacDecision.denyWithObligations(
                        decisionId,
                        ReasonCode.CONSENT_MISSING,
                        AbacObligation.none(),
                        null);
            }

            ConsentRecord consent = request.optionalConsent().get();
            if (consent.isRevoked()) {
                return AbacDecision.denyWithObligations(
                        decisionId,
                        ReasonCode.CONSENT_REVOKED,
                        AbacObligation.redactAndAudit(
                                new AbacObligation.RedactionProfile(Set.of("dnaRaw")),
                                new AbacObligation.AuditProfile(
                                        "consent_revoked_dna_blocked", false)),
                        null);
            }
            if (!consent.isActiveAt(clock.instant())) {
                return AbacDecision.denyWithObligations(
                        decisionId,
                        ReasonCode.CONSENT_MISSING,
                        AbacObligation.none(),
                        null);
            }
        }

        // 4. SENSITIVE class requires purpose-scoped consent regardless
        //    of genetic flag (R4.4 + privacy gate §5 PII.SENSITIVE).
        if (request.resourcePrivacyClass() == PrivacyClass.SENSITIVE
                && request.optionalConsent().isEmpty()) {
            return AbacDecision.denyWithObligations(
                    decisionId,
                    ReasonCode.CONSENT_MISSING,
                    AbacObligation.none(),
                    null);
        }

        // 5. Living / minor rules.
        LivingStatus living = request.optionalLivingStatus().orElse(null);
        boolean isMinor = living != null
                && living.isMinor(LocalDate.now(clock.withZone(ZoneId.of("UTC"))));
        boolean isLiving = living != null
                && living.status() == LivingStatus.Status.LIVING;
        boolean isPublicProjection =
                request.resourcePrivacyClass() == PrivacyClass.PUBLIC;

        if (isMinor) {
            // Minors default to PRIVATE — never expose to public projection
            // and require a guardian consent (privacy gate §7 P-02).
            if (isPublicProjection) {
                return AbacDecision.denyWithObligations(
                        decisionId,
                        ReasonCode.MINOR_GUARDIAN_REQUIRED,
                        AbacObligation.redactAndAudit(
                                new AbacObligation.RedactionProfile(
                                        new LinkedHashSet<>(minorRedactFields)),
                                new AbacObligation.AuditProfile(
                                        "minor_redaction_applied", false)),
                        null);
            }
            // Allow + redact minor fields by default.
            return AbacDecision.allowWithReason(
                    decisionId,
                    ReasonCode.OBLIGATION_REDACT,
                    AbacObligation.redactAndAudit(
                            new AbacObligation.RedactionProfile(
                                    new LinkedHashSet<>(minorRedactFields)),
                            new AbacObligation.AuditProfile(
                                    "minor_redaction_applied", false)));
        }

        if (isLiving && isPublicProjection) {
            // Living + public projection ⇒ redact living fields
            // (privacy gate §7 P-01).
            return AbacDecision.allowWithReason(
                    decisionId,
                    ReasonCode.LIVING_REDACTED,
                    AbacObligation.redactAndAudit(
                            new AbacObligation.RedactionProfile(
                                    new LinkedHashSet<>(livingRedactFields)),
                            new AbacObligation.AuditProfile(
                                    "living_redaction_applied", false)));
        }

        // 6. Default allow + audit (the engine never silently allows
        //    any mutation on a tenant-scoped resource; the audit
        //    obligation drives the audit hook contract).
        return AbacDecision.allowWithReason(
                decisionId,
                ReasonCode.OBLIGATION_AUDIT,
                AbacObligation.redactAndAudit(
                        new AbacObligation.RedactionProfile(Set.of()),
                        new AbacObligation.AuditProfile(
                                "abac_allow", false)));
    }
}
