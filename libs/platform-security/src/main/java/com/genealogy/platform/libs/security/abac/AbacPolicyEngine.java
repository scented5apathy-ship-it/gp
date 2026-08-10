package com.genealogy.platform.libs.security.abac;

/**
 * Public contract every ABAC overlay implements.
 *
 * <p>The default in-process implementation is
 * {@link DefaultAbacPolicyEngine}; services that need a richer
 * policy can ship their own implementation behind the same
 * interface (e.g. a Tempo / SpEL driven engine for tenant
 * admin override flows).
 *
 * <p>The engine MUST be deterministic and side-effect free — no
 * I/O, no audit emission, no metric increment. Audit emission
 * is the caller's responsibility, after the decision is in hand.
 */
public interface AbacPolicyEngine {

    /**
     * Returns the ABAC decision for the supplied request.
     *
     * <p>The engine never throws on a deny — deny is returned as
     * {@link AbacDecision#deny(String, ReasonCode)}. Exceptions are
     * reserved for programming errors (null required fields,
     * unknown privacy class, …).
     */
    AbacDecision evaluate(AbacRequest request);

    /**
     * Returns the engine identifier used as the {@code engine}
     * attribute on every decision. Useful for A/B testing policy
     * versions in a tenant without redeploying services.
     */
    String engineId();
}
