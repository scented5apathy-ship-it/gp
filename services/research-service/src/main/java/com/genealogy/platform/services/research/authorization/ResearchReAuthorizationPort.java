package com.genealogy.platform.services.research.authorization;

import com.genealogy.platform.libs.security.abac.AbacDecision;
import com.genealogy.platform.libs.security.abac.AbacPolicyEngine;
import com.genealogy.platform.libs.security.abac.AbacRequest;
import com.genealogy.platform.libs.security.abac.Jurisdiction;
import com.genealogy.platform.libs.security.abac.LivingStatus;
import com.genealogy.platform.libs.security.abac.OpenFgaAbacGuard;
import com.genealogy.platform.libs.security.abac.PrivacyClass;
import com.genealogy.platform.libs.security.abac.ReasonCode;
import com.genealogy.platform.spring.context.TrustedTenantContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Platform seam the research service uses for the
 * authorization-sensitive mutations (submit / approve /
 * partial-merge). The port is framework-thin on purpose: the
 * caller only has to know the action + the resource it wants
 * to mutate; the combinator delegates the OpenFGA check to
 * the shared {@link OpenFgaAbacGuard} (closed-set action
 * whitelist) and the ABAC overlay to the platform
 * {@link AbacPolicyEngine}.
 *
 * <p>Per ADR-E0.5-06 the OpenFGA check MUST be followed by the
 * ABAC overlay (the Semgrep
 * {@code no-openfga-allow-without-abac} gate enforces this in
 * CI). The combinator refuses to run with a null supplier on
 * either side.
 */
@Component
public class ResearchReAuthorizationPort {

    private final OpenFgaAbacGuard guard;
    private final AbacPolicyEngine policyEngine;

    public ResearchReAuthorizationPort(
            OpenFgaAbacGuard guard,
            AbacPolicyEngine policyEngine) {
        this.guard = Objects.requireNonNull(guard, "guard");
        this.policyEngine = Objects.requireNonNull(policyEngine, "policyEngine");
    }

    /**
     * Run the OpenFGA + ABAC check for the given action + resource.
     * Throws {@link ResearchReAuthorizationDeniedException} when
     * the decision is {@link AbacDecision.Effect#DENY}.
     */
    public void require(
            String tenantId,
            String subjectId,
            String role,
            String resourceType,
            String resourceId,
            Action action,
            Map<String, String> hints) {
        Objects.requireNonNull(action, "action");
        AbacRequest request = new AbacRequest(
                tenantId,
                subjectId,
                role,
                PrivacyClass.PRIVATE,
                resourceType,
                resourceId,
                LivingStatus.unknown(),
                null,
                new Jurisdiction("GLOBAL"),
                false,
                false,
                false,
                false,
                hints == null ? Map.of() : new LinkedHashMap<>(hints));
        AbacDecision decision = guard.check(request, action.wire());
        if (decision.effect() == AbacDecision.Effect.DENY) {
            throw new ResearchReAuthorizationDeniedException(
                    decision.decisionId(),
                    decision.reasonCode());
        }
    }

    /**
     * Convenience overload that reads the trusted tenant context
     * and uses the supplied action. The caller does not need to
     * remember to pass the tenant id every time.
     */
    public void requireFromContext(
            String resourceType,
            String resourceId,
            Action action,
            Map<String, String> hints) {
        TrustedTenantContext ctx = TrustedTenantContext.current();
        require(
                ctx.getTenantId(),
                ctx.getActorId(),
                ctx.getActorRole(),
                resourceType,
                resourceId,
                action,
                hints);
    }

    /**
     * Closed-set action whitelist. Mirrors the
     * {@code research-policy.yaml::spec.reAuthorizationActions}
     * contract (E6.1a). Adding a new action requires an ADR
     * supersession.
     */
    public enum Action {
        CITATION_SUBMIT,
        CITATION_APPROVE,
        CONFLICT_PARTIAL_MERGE,
        HYPOTHESIS_PROMOTE,
        RESEARCH_TASK_ASSIGN;

        public String wire() {
            return switch (this) {
                case CITATION_SUBMIT -> "citation_submit";
                case CITATION_APPROVE -> "citation_approve";
                case CONFLICT_PARTIAL_MERGE -> "conflict_partial_merge";
                case HYPOTHESIS_PROMOTE -> "hypothesis_promote";
                case RESEARCH_TASK_ASSIGN -> "research_task_assign";
            };
        }
    }

    public static final class ResearchReAuthorizationDeniedException extends RuntimeException {
        private final String decisionId;
        private final ReasonCode reasonCode;

        public ResearchReAuthorizationDeniedException(String decisionId, ReasonCode reasonCode) {
            super("re-authorization denied: " + reasonCode.id() + " (decisionId="
                    + decisionId + ")");
            this.decisionId = decisionId;
            this.reasonCode = reasonCode;
        }

        public String decisionId() {
            return decisionId;
        }

        public ReasonCode reasonCode() {
            return reasonCode;
        }
    }

    /**
     * Test seam: exposes the supplier-style port so unit tests
     * can stub the OpenFGA check without wiring the Spring
     * context.
     */
    public static final class TestFriendly {
        private final Supplier<Optional<String>> openfgaCheck;
        private final AbacPolicyEngine policyEngine;

        public TestFriendly(Supplier<Optional<String>> openfgaCheck, AbacPolicyEngine policyEngine) {
            this.openfgaCheck = Objects.requireNonNull(openfgaCheck, "openfgaCheck");
            this.policyEngine = Objects.requireNonNull(policyEngine, "policyEngine");
        }

        public AbacDecision evaluate(AbacRequest request, Action action) {
            Optional<String> checkId = openfgaCheck.get();
            if (checkId.isEmpty()) {
                return AbacDecision.deny(
                        "abac-" + java.util.UUID.randomUUID(),
                        ReasonCode.OPENFGA_DENY);
            }
            AbacDecision decision = policyEngine.evaluate(request);
            return decision
                    .withOpenfgaCheckId(checkId.get())
                    .withAttribute("engine", policyEngine.engineId())
                    .withAttribute("action", action.wire());
        }
    }
}
