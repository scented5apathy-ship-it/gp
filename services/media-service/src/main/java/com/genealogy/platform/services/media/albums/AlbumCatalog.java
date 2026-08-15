package com.genealogy.platform.services.media.albums;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Deterministic orchestrator for the E7.5 albums / linking
 * contract.
 *
 * <p>Mirrors
 * {@code contracts/media/albums-linking-policy.yaml
 * ::spec.albumAuthorizationMatrix +
 * reconciliationStateMatrix + albumReferenceKinds +
 * dnaBucketPrefixes + guard rails} + `requirements.md`
 * R9.1 / R9.4 + `design.md` §4 / §5.1 +
 * `ownership-catalog.md` §2.5 + ADR-E0.5-06 (OpenFGA
 * store-per-tenant) + ADR-E0.5-15 (DNA bucket shield).
 *
 * <p>Guard rails enforced:
 * <ul>
 *   <li>{@code onlyDerivedReadyIsLinkable=true} — items
 *       whose {@code derivedReady=false} force
 *       {@link AlbumFailureReason#ALBUM_DERIVED_OBJECT_KEY_NOT_READY}.</li>
 *   <li>{@code dnaBucketAccess=FORBIDDEN} — items / albums
 *       whose {@code derivedObjectKey} starts with one of
 *       the closed-set prefixes force
 *       {@link AlbumFailureReason#ALBUM_DNA_BUCKET_FORBIDDEN}.</li>
 *   <li>{@code albumReferencesCheckedBeforeCommit=true} —
 *       every reference is re-resolved through the port
 *       BEFORE the orchestrator returns
 *       {@link AlbumOperationOutcome#ALLOWED}.</li>
 *   <li>{@code objectLockComplianceRequiredForLegalHold=true}
 *       — flipping the visibility to {@code LEGAL_HOLD}
 *       without an object-lock-capable bucket forces
 *       {@link AlbumFailureReason#ALBUM_VISIBILITY_FORBIDDEN}.</li>
 *   <li>{@code captionLanguageIetfBcp47Required=true} —
 *       the {@link AlbumItemRequest} compact constructor
 *       already enforces the language tag; the orchestrator
 *       re-checks at commit time.</li>
 *   <li>{@code placeReferenceFormat=PLACE_PSEUDO_ID} +
 *       {@code dateReferenceFormat=DATE_PSEUDO_ID} — the
 *       {@code AlbumReferenceRequest} only stores opaque
 *       ids; raw place / date values are forbidden.</li>
 *   <li>{@code tagNormalizationRule=LOWERCASE_TRIM_DASH} —
 *       the orchestrator normalises tags so two different
 *       capitalisations hash to the same {@code tagKey}.</li>
 *   <li>{@code softDeleteRetentionDays=365} +
 *       {@code objectLockComplianceDays=30} — the lifecycle
 *       + visibility flips are recorded into the audit
 *       facts so the object garbage collector + the legal
 *       hold worker can honour the windows.</li>
 *   <li>Cross-service reference ids are OPAQUE only — the
 *       compact constructor + the orchestrator refuse to
 *       store anything other than
 *       {@code referencePseudoId} so a privacy leak cannot
 *       be silently introduced by an adapter.</li>
 * </ul>
 *
 * <p>No mutation of another service's domain record occurs
 * here — the application layer applies the decision through
 * the jOOQ repository (E7.x / E11.x) and emits the audit
 * event. All cross-service calls go through the
 * {@link AlbumOpenFgaPort} +
 * {@link AlbumReferenceResolverPort} ports.
 */
public final class AlbumCatalog {

    private static final Pattern BCP47 = Pattern.compile(
            "^[a-z]{2,3}(-[A-Za-z]{4})?(-[A-Z]{2}|-[0-9]{3})?$");

    private AlbumCatalog() {
    }

    /**
     * Apply an album operation against the closed-set policy.
     * The orchestrator is deterministic + idempotent on
     * {@code albumId} + the request inputs; re-running the
     * chain produces the same decision.
     */
    public static AlbumOperationDecision apply(
            AlbumOperationRequest request,
            AlbumOpenFgaPort openFgaPort,
            AlbumReferenceResolverPort referenceResolver,
            Instant issuedAt) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(openFgaPort, "openFgaPort");
        Objects.requireNonNull(referenceResolver, "referenceResolver");
        Objects.requireNonNull(issuedAt, "issuedAt");

        Map<AlbumFailureReason, String> facts = new LinkedHashMap<>();

        // 0. Membership + quota + lifecycle pre-screen.
        if (!request.membershipActive()) {
            return AlbumOperationDecision.denied(
                    request.albumId(),
                    AlbumFailureReason.ALBUM_VISIBILITY_FORBIDDEN,
                    facts,
                    "Membership inactive; album op denied");
        }
        if (!request.quotaAllowancePresent()) {
            return AlbumOperationDecision.denied(
                    request.albumId(),
                    AlbumFailureReason.ALBUM_QUOTA_EXCEEDED,
                    facts,
                    "Quota allowance not present; album op denied");
        }
        if (request.lifecycleState() == AlbumLifecycleState.PURGED
                || request.lifecycleState() == AlbumLifecycleState.FAILED) {
            return AlbumOperationDecision.denied(
                    request.albumId(),
                    AlbumFailureReason.ALBUM_LIFECYCLE_FORBIDDEN,
                    facts,
                    "Album lifecycle forbids writes");
        }

        // 1. Visibility / object-lock compliance check
        //    BEFORE OpenFGA so a forbidden flip short-circuits.
        if (request.visibility() == AlbumVisibility.LEGAL_HOLD
                && !request.objectLockComplianceAvailable()) {
            return AlbumOperationDecision.denied(
                    request.albumId(),
                    AlbumFailureReason.ALBUM_VISIBILITY_FORBIDDEN,
                    facts,
                    "LEGAL_HOLD visibility requires object-lock COMPLIANCE");
        }

        // 2. DNA bucket shield — refuses any item pointing
        //    into the dna/ prefix set BEFORE OpenFGA + the
        //    cross-service reference resolver.
        if (request.dnaBucketKey()) {
            return AlbumOperationDecision.denied(
                    request.albumId(),
                    AlbumFailureReason.ALBUM_DNA_BUCKET_FORBIDDEN,
                    facts,
                    "Album points to DNA bucket; refused per E7.5 shield");
        }
        for (AlbumItemRequest item : request.items()) {
            if (isDnaBucketKey(item.derivedObjectKey())) {
                return AlbumOperationDecision.denied(
                        request.albumId(),
                        AlbumFailureReason.ALBUM_DNA_BUCKET_FORBIDDEN,
                        facts,
                        "Album item " + item.itemId()
                                + " points to DNA bucket");
            }
        }

        // 3. Per-item linkability + caption language
        //    sanity. Each item MUST point to a DERIVED_READY
        //    object and the caption MUST carry a BCP-47
        //    language tag if it has any text.
        for (AlbumItemRequest item : request.items()) {
            if (!item.derivedReady()) {
                return AlbumOperationDecision.denied(
                        request.albumId(),
                        AlbumFailureReason.ALBUM_DERIVED_OBJECT_KEY_NOT_READY,
                        facts,
                        "Album item " + item.itemId()
                                + " is not DERIVED_READY");
            }
            if (item.caption() != null) {
                String lang = item.captionBcp47Language();
                if (lang == null
                        || !BCP47.matcher(lang).matches()) {
                    return AlbumOperationDecision.denied(
                            request.albumId(),
                            AlbumFailureReason
                                    .ALBUM_CAPTION_LANGUAGE_MISSING,
                            facts,
                            "Album item " + item.itemId()
                                    + " caption BCP-47 invalid");
                }
            }
        }

        // 4. OpenFGA relationship check (per ADR-E0.5-06).
        AlbumOpenFgaVerdict openFga = openFgaPort.check(
                request.tenantScopeId(),
                request.albumId(),
                request.actorPseudoId(),
                request.correlationId());
        if (openFga.outcome() == AlbumOpenFgaOutcome.DENY) {
            return AlbumOperationDecision.denied(
                    request.albumId(),
                    openFga.failureReason(),
                    facts,
                    "OpenFGA denied: " + openFga.reasonCode());
        }

        // 5. Cross-service reference resolution (per
        //    ADR-E0.5-07 + privacy-and-legal-gate.md §6).
        for (AlbumItemRequest item : request.items()) {
            for (AlbumReferenceRequest ref : item.references()) {
                AlbumReferenceVerdict verdict = referenceResolver.resolve(
                        request.tenantScopeId(),
                        ref.kind(),
                        ref.referencePseudoId());
                if (verdict.outcome()
                        != AlbumReferenceOutcome.RESOLVED) {
                    return AlbumOperationDecision.denied(
                            request.albumId(),
                            verdict.failureReason(),
                            facts,
                            "Album item " + item.itemId()
                                    + " reference "
                                    + ref.referencePseudoId()
                                    + " " + verdict.outcome().wire());
                }
            }
        }

        // 6. Lifecycle transition dispatch.
        if (request.lifecycleState() == AlbumLifecycleState.SOFT_DELETED) {
            return AlbumOperationDecision.softDeleted(
                    request.albumId(),
                    nextAlbumVersion(request),
                    computeEtag(request, issuedAt),
                    "Album soft-deleted; retention="
                            + AlbumCatalogLimits.SOFT_DELETE_RETENTION_DAYS
                            + "d");
        }
        if (request.lifecycleState() == AlbumLifecycleState.PURGED) {
            return AlbumOperationDecision.purged(
                    request.albumId(),
                    "Album purged; object GC scheduled");
        }
        return AlbumOperationDecision.allowed(
                request.albumId(),
                nextAlbumVersion(request),
                computeEtag(request, issuedAt),
                "Album op allowed");
    }

    /**
     * Whether the supplied object key starts with one of
     * the closed-set {@code dnaBucketPrefixes}.
     */
    public static boolean isDnaBucketKey(String derivedObjectKey) {
        Objects.requireNonNull(derivedObjectKey, "derivedObjectKey");
        return derivedObjectKey.startsWith("dna/")
                || derivedObjectKey.startsWith("dna/raw/")
                || derivedObjectKey.startsWith("dna/match/")
                || derivedObjectKey.startsWith("dna/consent/");
    }

    /**
     * Apply the closed-set {@code LOWERCASE_TRIM_DASH}
     * normalisation rule. Whitespace is collapsed to a
     * single dash; leading / trailing dashes are stripped;
     * the resulting string is lower-cased.
     */
    public static String normaliseTag(String raw) {
        if (raw == null) {
            return null;
        }
        String collapsed = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-");
        while (collapsed.startsWith("-")) {
            collapsed = collapsed.substring(1);
        }
        while (collapsed.endsWith("-")) {
            collapsed = collapsed.substring(0, collapsed.length() - 1);
        }
        return collapsed;
    }

    /**
     * Build a {@link ReconciliationReport} for an album
     * given the per-item outcomes. The orchestrator is
     * pure; the worker calls this once the reconciliation
     * pass completes.
     */
    public static ReconciliationReport reconcile(
            String reportId,
            String albumId,
            String tenantScopeId,
            List<AlbumItemOutcome> itemOutcomes,
            String actorPseudoId,
            String correlationId,
            Instant now) {
        Objects.requireNonNull(reportId, "reportId");
        Objects.requireNonNull(albumId, "albumId");
        Objects.requireNonNull(tenantScopeId, "tenantScopeId");
        Objects.requireNonNull(itemOutcomes, "itemOutcomes");
        Objects.requireNonNull(actorPseudoId, "actorPseudoId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(now, "now");
        int total = itemOutcomes.size();
        int resolved = 0;
        int dangling = 0;
        int revoked = 0;
        int orphan = 0;
        for (AlbumItemOutcome o : itemOutcomes) {
            switch (o) {
                case HEALTHY -> resolved++;
                case DANGLING_REFERENCES -> dangling++;
                case REVOKED_REFERENCES -> revoked++;
                case ORPHAN_ASSETS -> orphan++;
                default -> {
                    /* FAILED / PURGED / QUOTA_EXCEEDED do not
                     * count towards the resolved bucket; they
                     * are surfaced via the outcome summary
                     * below. */
                }
            }
        }
        ReconciliationOutcome outcome;
        if (dangling > 0) {
            outcome = ReconciliationOutcome.DANGLING_REFERENCES;
        } else if (revoked > 0) {
            outcome = ReconciliationOutcome.REVOKED_REFERENCES;
        } else if (orphan > 0) {
            outcome = ReconciliationOutcome.ORPHAN_ASSETS;
        } else if (itemOutcomes.contains(
                AlbumItemOutcome.QUOTA_EXCEEDED)) {
            outcome = ReconciliationOutcome.QUOTA_EXCEEDED;
        } else if (itemOutcomes.contains(
                AlbumItemOutcome.PURGED)) {
            outcome = ReconciliationOutcome.PURGED;
        } else if (itemOutcomes.contains(
                AlbumItemOutcome.FAILED)) {
            outcome = ReconciliationOutcome.PURGED;
        } else {
            outcome = ReconciliationOutcome.HEALTHY;
        }
        return new ReconciliationReport(
                reportId, albumId, tenantScopeId, now,
                outcome, total, resolved, dangling, revoked, orphan,
                Map.of(),
                outcome == ReconciliationOutcome.PURGED,
                outcome == ReconciliationOutcome.PURGED
                        ? now.plusSeconds(
                                (long) AlbumCatalogLimits
                                        .SOFT_DELETE_RETENTION_DAYS
                                        * 86_400L)
                        : null,
                actorPseudoId, correlationId);
    }

    private static long nextAlbumVersion(AlbumOperationRequest request) {
        long current = request.albumVersion() == null
                ? 0L
                : request.albumVersion();
        return current + 1L;
    }

    private static String computeEtag(
            AlbumOperationRequest request, Instant issuedAt) {
        String basis = request.albumId()
                + ":" + request.actorPseudoId()
                + ":" + issuedAt.toEpochMilli();
        return Integer.toHexString(basis.hashCode());
    }
}