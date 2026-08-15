/*
 * E1.1 + E11 — `operations-service` skeleton. Implementation lands in
 * later epics (E3.x for tenant, E11.4 for entitlement, E11.5 for
 * admin/support). E1.1 wires the Gradle module so the monorepo
 * build, lockfile and CI smoke run end-to-end. E11.4 + E11.5 add the
 * contract-side guardrails (`EntitlementGuard`, `AdminSupportGuard`)
 * plus the shared `E11Limits` + `E11ForbiddenPayloadKeys` catalogues.
 */
@NonNullApi
package com.genealogy.platform.services.operations;

import org.springframework.lang.NonNullApi;