package com.genealogy.platform.libs.security.abac;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Read-only view of the request used by the ABAC engine. The
 * engine MUST never receive a {@code tenant_id} that came from
 * the client — services must populate it from
 * {@code TrustedTenantContext}.
 *
 * <p>{@code attributes} carries untyped hints the engine can use
 * (e.g. {@code resource_version}, {@code support_session_id},
 * {@code impersonated}). Sensitive fields are filtered upstream
 * by the audit pipeline.
 */
public record AbacRequest(
        String tenantId,
        String subjectId,
        String role,
        PrivacyClass resourcePrivacyClass,
        String resourceType,
        String resourceId,
        LivingStatus livingStatus,
        ConsentRecord consent,
        Jurisdiction jurisdiction,
        boolean supportSession,
        boolean impersonated,
        boolean suspended,
        boolean softDeleted,
        Map<String, String> attributes) {

    public AbacRequest {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(resourcePrivacyClass, "resourcePrivacyClass");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(jurisdiction, "jurisdiction");
        Objects.requireNonNull(attributes, "attributes");
    }

    public Optional<LivingStatus> optionalLivingStatus() {
        return Optional.ofNullable(livingStatus);
    }

    public Optional<ConsentRecord> optionalConsent() {
        return Optional.ofNullable(consent);
    }

    public Optional<String> attribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    public Set<String> attributeKeys() {
        return attributes.keySet();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String tenantId;
        private String subjectId;
        private String role;
        private PrivacyClass resourcePrivacyClass = PrivacyClass.PRIVATE;
        private String resourceType;
        private String resourceId;
        private LivingStatus livingStatus;
        private ConsentRecord consent;
        private Jurisdiction jurisdiction = Jurisdiction.ROW;
        private boolean supportSession;
        private boolean impersonated;
        private boolean suspended;
        private boolean softDeleted;
        private final Map<String, String> attributes = new java.util.LinkedHashMap<>();

        public Builder tenantId(String value) {
            this.tenantId = value;
            return this;
        }

        public Builder subjectId(String value) {
            this.subjectId = value;
            return this;
        }

        public Builder role(String value) {
            this.role = value;
            return this;
        }

        public Builder resourcePrivacyClass(PrivacyClass cls) {
            this.resourcePrivacyClass = cls;
            return this;
        }

        public Builder resourceType(String type) {
            this.resourceType = type;
            return this;
        }

        public Builder resourceId(String id) {
            this.resourceId = id;
            return this;
        }

        public Builder livingStatus(LivingStatus status) {
            this.livingStatus = status;
            return this;
        }

        public Builder consent(ConsentRecord record) {
            this.consent = record;
            return this;
        }

        public Builder jurisdiction(Jurisdiction j) {
            this.jurisdiction = j;
            return this;
        }

        public Builder supportSession(boolean value) {
            this.supportSession = value;
            return this;
        }

        public Builder impersonated(boolean value) {
            this.impersonated = value;
            return this;
        }

        public Builder suspended(boolean value) {
            this.suspended = value;
            return this;
        }

        public Builder softDeleted(boolean value) {
            this.softDeleted = value;
            return this;
        }

        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }

        public AbacRequest build() {
            return new AbacRequest(tenantId, subjectId, role,
                    resourcePrivacyClass, resourceType, resourceId,
                    livingStatus, consent, jurisdiction,
                    supportSession, impersonated, suspended, softDeleted,
                    attributes);
        }
    }
}
