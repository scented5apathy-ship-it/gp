package com.genealogy.platform.services.media.delivery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Authorization request envelope for the protected-delivery
 * orchestrator. Mirrors
 * `contracts/media/media-protected-delivery-policy.yaml
 * ::spec.deliverySubjects + deliveryVisibilityScopes +
 * deliverySubjectVisibilityClass + signedUrlMethods +
 * deliveryRangeUnit` (E7.4) + `requirements.md` R9.5 +
 * `design.md` §12.
 *
 * <p>The orchestrator applies the {@code OpenFGA+ABAC}
 * authorization chain in this order; the order is enforced
 * by the {@code MediaProtectedDelivery.authorize(...)}
 * method.
 */
public record DeliveryAuthorizationRequest(
        String deliveryId,
        String tenantScopeId,
        String assetId,
        String derivedObjectKey,
        DeliverySubject subject,
        DeliveryVisibilityScope visibilityScope,
        DeliverySubjectVisibilityClass subjectVisibilityClass,
        SignedUrlMethod method,
        String actorPseudoId,
        String correlationId,
        String jurisdiction,
        RangeRequest range,
        List<DeliveryAbacReason> abacReasons,
        List<DeliveryRevocationSource> revocationSources,
        boolean dnaBucketKey,
        boolean membershipActive,
        boolean consentActive,
        boolean objectReady,
        boolean objectTampered) {

    public static final int MAX_DELIVERY_ID_LENGTH = 128;
    public static final int MAX_OBJECT_KEY_LENGTH = 1024;
    public static final int MAX_ACTOR_PSEUDO_ID_LENGTH = 64;
    public static final int MAX_CORRELATION_ID_LENGTH = 128;
    public static final int MAX_JURISDICTION_LENGTH = 8;

    public DeliveryAuthorizationRequest {
        Objects.requireNonNull(deliveryId, "deliveryId");
        Objects.requireNonNull(tenantScopeId, "tenantScopeId");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(derivedObjectKey, "derivedObjectKey");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(visibilityScope, "visibilityScope");
        Objects.requireNonNull(subjectVisibilityClass,
                "subjectVisibilityClass");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(actorPseudoId, "actorPseudoId");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(abacReasons, "abacReasons");
        Objects.requireNonNull(revocationSources, "revocationSources");
        if (deliveryId.isBlank()
                || deliveryId.length() > MAX_DELIVERY_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "deliveryId length out of bounds");
        }
        if (tenantScopeId.isBlank()) {
            throw new IllegalArgumentException(
                    "tenantScopeId must not be blank");
        }
        if (assetId.isBlank()) {
            throw new IllegalArgumentException(
                    "assetId must not be blank");
        }
        if (derivedObjectKey.isBlank()
                || derivedObjectKey.length() > MAX_OBJECT_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "derivedObjectKey length out of bounds");
        }
        if (actorPseudoId.isBlank()
                || actorPseudoId.length() > MAX_ACTOR_PSEUDO_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "actorPseudoId length out of bounds");
        }
        if (correlationId.isBlank()
                || correlationId.length() > MAX_CORRELATION_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "correlationId length out of bounds");
        }
        if (jurisdiction != null
                && jurisdiction.length() > MAX_JURISDICTION_LENGTH) {
            throw new IllegalArgumentException(
                    "jurisdiction length out of bounds");
        }
        abacReasons = List.copyOf(abacReasons);
        revocationSources = List.copyOf(revocationSources);
        range = range == null ? null : range;
    }

    public Optional<RangeRequest> rangeOpt() {
        return Optional.ofNullable(range);
    }

    public boolean hasAbacReason(DeliveryAbacReason reason) {
        return abacReasons.contains(reason);
    }

    public boolean hasRevocation(DeliveryRevocationSource source) {
        return revocationSources.contains(source);
    }

    /**
     * Build a {@link DeliveryAuthorizationRequest} from the
     * supplied fields. The builder pins default values for
     * the boolean predicates (all {@code true} / {@code false}
     * as appropriate for a clean HISTORICAL request) so
     * tests only have to override the fields they care
     * about.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Mutable builder for {@link DeliveryAuthorizationRequest}.
     */
    public static final class Builder {
        private String deliveryId;
        private String tenantScopeId;
        private String assetId;
        private String derivedObjectKey;
        private DeliverySubject subject;
        private DeliveryVisibilityScope visibilityScope;
        private DeliverySubjectVisibilityClass subjectVisibilityClass;
        private SignedUrlMethod method;
        private String actorPseudoId;
        private String correlationId;
        private String jurisdiction;
        private RangeRequest range;
        private List<DeliveryAbacReason> abacReasons = List.of();
        private List<DeliveryRevocationSource> revocationSources = List.of();
        private boolean dnaBucketKey;
        private boolean membershipActive = true;
        private boolean consentActive = true;
        private boolean objectReady = true;
        private boolean objectTampered;

        public Builder deliveryId(String v) {
            this.deliveryId = v;
            return this;
        }

        public Builder tenantScopeId(String v) {
            this.tenantScopeId = v;
            return this;
        }

        public Builder assetId(String v) {
            this.assetId = v;
            return this;
        }

        public Builder derivedObjectKey(String v) {
            this.derivedObjectKey = v;
            return this;
        }

        public Builder subject(DeliverySubject v) {
            this.subject = v;
            return this;
        }

        public Builder visibilityScope(DeliveryVisibilityScope v) {
            this.visibilityScope = v;
            return this;
        }

        public Builder subjectVisibilityClass(
                DeliverySubjectVisibilityClass v) {
            this.subjectVisibilityClass = v;
            return this;
        }

        public Builder method(SignedUrlMethod v) {
            this.method = v;
            return this;
        }

        public Builder actorPseudoId(String v) {
            this.actorPseudoId = v;
            return this;
        }

        public Builder correlationId(String v) {
            this.correlationId = v;
            return this;
        }

        public Builder jurisdiction(String v) {
            this.jurisdiction = v;
            return this;
        }

        public Builder range(RangeRequest v) {
            this.range = v;
            return this;
        }

        public Builder abacReasons(List<DeliveryAbacReason> v) {
            this.abacReasons = v;
            return this;
        }

        public Builder revocationSources(
                List<DeliveryRevocationSource> v) {
            this.revocationSources = v;
            return this;
        }

        public Builder dnaBucketKey(boolean v) {
            this.dnaBucketKey = v;
            return this;
        }

        public Builder membershipActive(boolean v) {
            this.membershipActive = v;
            return this;
        }

        public Builder consentActive(boolean v) {
            this.consentActive = v;
            return this;
        }

        public Builder objectReady(boolean v) {
            this.objectReady = v;
            return this;
        }

        public Builder objectTampered(boolean v) {
            this.objectTampered = v;
            return this;
        }

        public DeliveryAuthorizationRequest build() {
            return new DeliveryAuthorizationRequest(
                    deliveryId,
                    tenantScopeId,
                    assetId,
                    derivedObjectKey,
                    subject,
                    visibilityScope,
                    subjectVisibilityClass,
                    method,
                    actorPseudoId,
                    correlationId,
                    jurisdiction,
                    range,
                    abacReasons,
                    revocationSources,
                    dnaBucketKey,
                    membershipActive,
                    consentActive,
                    objectReady,
                    objectTampered);
        }
    }
}