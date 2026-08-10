package com.genealogy.platform.services.tenant.application;

import com.genealogy.platform.libs.security.abac.AbacDecision;
import com.genealogy.platform.libs.security.abac.AbacDecisionCache;
import com.genealogy.platform.libs.security.abac.AbacObligation;
import com.genealogy.platform.libs.security.abac.AbacPolicyEngine;
import com.genealogy.platform.libs.security.abac.AbacRequest;
import com.genealogy.platform.libs.security.abac.Jurisdiction;
import com.genealogy.platform.libs.security.abac.PrivacyClass;
import com.genealogy.platform.libs.security.abac.ReasonCode;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * ABAC overlay enforcer for the tenant service.
 *
 * <p>E3.4 wires the {@code libs/platform-security} ABAC engine
 * into the membership + entitlement write paths. The enforcer is
 * the single seam every privileged mutation goes through before
 * the aggregate is mutated; this is the implementation side of the
 * Semgrep {@code no-openfga-allow-without-abac} rule (E1.6) and
 * the {@code design.md} §6.2 rule that "ABAC overlay decides
 * contextual deny regardless of OpenFGA allow".
 *
 * <p>The enforcer caches decisions under
 * {@code abac:<tenant>:<resource>:<subject>:<action>}; every
 * mutation flow MUST call {@link #invalidateOnChange(String,
 * String, String)} after a successful write so cached decisions
 * reflect the new state on the next read.
 */
@Component
public class TenantAbacEnforcer {

    private final AbacPolicyEngine policyEngine;
    private final AbacDecisionCache cache;

    public TenantAbacEnforcer(AbacPolicyEngine policyEngine,
            AbacDecisionCache cache) {
        this.policyEngine = Objects.requireNonNull(policyEngine, "policyEngine");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    /**
     * Evaluates the ABAC overlay for the supplied request. The
     * caller is expected to deny on a {@link AbacDecision#isDeny()}
     * result; the helper throws {@link AbacDeniedException} so the
     * REST layer maps to {@code 403 Forbidden} with the
     * reason-code {@code type} URI.
     */
    public AbacDecision requireAllow(AbacRequest request, String action) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(action, "action");
        String key = cacheKey(request, action);
        AbacDecision cached = cache.get(key);
        if (cached != null) {
            if (cached.isDeny()) {
                throw new AbacDeniedException(cached);
            }
            return cached;
        }
        AbacDecision decision = policyEngine.evaluate(request)
                .withAttribute("engine", policyEngine.engineId())
                .withAttribute("action", action);
        cache.put(key, decision);
        if (decision.isDeny()) {
            throw new AbacDeniedException(decision);
        }
        return decision;
    }

    /**
     * Convenience factory for a membership-mutation request — the
     * tenant service evaluates membership / role-change actions
     * against {@link PrivacyClass#PRIVATE} (no public projection)
     * and the tenant's {@link Jurisdiction} (read from the trusted
     * tenant context). The caller supplies the live
     * suspended / soft-deleted flags so the enforcer can apply the
     * contextual-deny rules from {@code design.md} §6.2.
     */
    public AbacRequest membershipRequest(
            String tenantId,
            String subjectId,
            String role,
            String membershipId,
            boolean membershipSuspended,
            boolean tenantSoftDeleted,
            Jurisdiction jurisdiction) {
        return AbacRequest.builder()
                .tenantId(tenantId)
                .subjectId(subjectId)
                .role(role == null ? "viewer" : role)
                .resourcePrivacyClass(PrivacyClass.PRIVATE)
                .resourceType("membership")
                .resourceId(membershipId)
                .jurisdiction(jurisdiction == null ? Jurisdiction.ROW : jurisdiction)
                .suspended(membershipSuspended)
                .softDeleted(tenantSoftDeleted)
                .build();
    }

    /**
     * Convenience factory for a tenant-mutation request. Used by
     * the {@code TenantCommandService} / {@code EntitlementCommandService}
     * on {@code update} / {@code changePlan} / {@code suspend} /
     * {@code restore} / {@code softDelete}. The tenant's
     * jurisdiction comes from the trusted tenant context.
     */
    public AbacRequest tenantRequest(
            String tenantId,
            String subjectId,
            String role,
            String tenantResourceId,
            boolean suspended,
            boolean softDeleted,
            Jurisdiction jurisdiction) {
        return AbacRequest.builder()
                .tenantId(tenantId)
                .subjectId(subjectId)
                .role(role == null ? "viewer" : role)
                .resourcePrivacyClass(PrivacyClass.PRIVATE)
                .resourceType("tenant")
                .resourceId(tenantResourceId)
                .jurisdiction(jurisdiction == null ? Jurisdiction.ROW : jurisdiction)
                .suspended(suspended)
                .softDeleted(softDeleted)
                .build();
    }

    /**
     * Removes every cached decision scoped to the supplied tenant.
     * Called from role / policy / consent write flows per E3.4
     * acceptance criterion "cache quyết định ngắn hạn và
     * invalidation khi role/policy/consent đổi".
     */
    public int invalidateOnChange(String tenantId, String resourceType,
            String resourceId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(resourceType, "resourceType");
        Objects.requireNonNull(resourceId, "resourceId");
        return cache.invalidateByPrefix(
                "abac:" + tenantId + ":" + resourceType + ":" + resourceId + ":");
    }

    /**
     * Removes every cached decision scoped to the supplied tenant
     * regardless of resource. Used when a tenant is suspended or
     * soft-deleted (every cached decision is now stale).
     */
    public int invalidateTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        return cache.invalidateByPrefix("abac:" + tenantId + ":");
    }

    public AbacPolicyEngine engine() {
        return policyEngine;
    }

    public AbacDecisionCache cache() {
        return cache;
    }

    private static String cacheKey(AbacRequest request, String action) {
        return "abac:" + request.tenantId() + ":" + request.resourceType() + ":"
                + request.resourceId() + ":" + request.subjectId() + ":" + action;
    }

    /**
     * Raised when the ABAC engine returns deny. The REST layer maps
     * this to {@code 403 Forbidden} with a {@code RFC 9457}
     * Problem Details body carrying the reason-code {@code type}
     * URI ({@code /problems/abac/<reason>}).
     */
    public static class AbacDeniedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final AbacDecision decision;

        public AbacDeniedException(AbacDecision decision) {
            super("ABAC denied: " + decision.reasonCode().id());
            this.decision = decision;
        }

        public AbacDecision decision() {
            return decision;
        }

        public String problemType() {
            return decision.reasonCode().asProblemType().orElse(
                    "/problems/abac/" + decision.reasonCode().id());
        }

        public String reasonId() {
            return decision.reasonCode().id();
        }

        public Map<String, String> problemExtensions() {
            return Map.of(
                    "decisionId", decision.decisionId(),
                    "reasonCode", decision.reasonCode().id(),
                    "effect", decision.effect().name());
        }

        public AbacObligation obligations() {
            return decision.obligations();
        }
    }

    /** Closed set of actions used by the tenant service enforcer. */
    public static final class Actions {
        public static final String MEMBERSHIP_INVITE = "membership.invite";
        public static final String MEMBERSHIP_ACTIVATE = "membership.activate";
        public static final String MEMBERSHIP_REVOKE = "membership.revoke";
        public static final String TENANT_UPDATE = "tenant.update";
        public static final String TENANT_CHANGE_PLAN = "tenant.change_plan";
        public static final String TENANT_SUSPEND = "tenant.suspend";
        public static final String TENANT_RESTORE = "tenant.restore";
        public static final String TENANT_SOFT_DELETE = "tenant.soft_delete";
        public static final String ENTITLEMENT_CHANGE = "entitlement.change";

        private Actions() {
        }
    }

    /** Internal sentinel used by tests; real engine reasons are richer. */
    static final ReasonCode DEBUG_REASON = ReasonCode.OBLIGATION_AUDIT;
}
