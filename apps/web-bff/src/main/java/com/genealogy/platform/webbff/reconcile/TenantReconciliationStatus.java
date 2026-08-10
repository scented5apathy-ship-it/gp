package com.genealogy.platform.webbff.reconcile;

/**
 * Closed-set outcome of the BFF tenant reconciliation (E3.5).
 * Additions require an ADR; the catalogue is the audit contract.
 */
public enum TenantReconciliationStatus {
    /** The tenant selection matched an ACTIVE membership. */
    ALLOWED,
    /** The Keycloak subject has no membership for the selected tenant. */
    TENANT_NOT_FOUND,
    /** The Keycloak subject has a membership but it is not ACTIVE. */
    MEMBERSHIP_NOT_ACTIVE,
    /** The Keycloak subject could not be resolved from the request. */
    SUBJECT_MISSING
}
