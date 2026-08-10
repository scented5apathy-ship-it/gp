/**
 * E3.6 audit-service ledger. Owns the append-only WORM ledger that
 * backs every audit event emitted by every other service. See
 * <code>contracts/audit/policy.yaml</code> for the source-of-truth
 * contract and <code>.kiro/specs/genealogy-platform/ownership-catalog.md</code>
 * section 2.11 for the service ownership / SLO profile.
 */
@NonNullApi
package com.genealogy.platform.services.audit;

import org.springframework.lang.NonNullApi;
