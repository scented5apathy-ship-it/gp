# apps/web-bff/.../webbff/reconcile

BFF-side tenant reconciliation (E3.5).

Per `design.md` §6.1 + `privacy-and-legal-gate.md` TM-02 the
BFF is the only place where the tenant selection (typically a
`X-Tenant-Id` header) is reconciled against the Keycloak
subject's `ACTIVE` memberships before being propagated to
domain services. The reconciliation layer is the boundary
where a foreign tenant is rejected with HTTP 404 (never 403,
to avoid leaking the existence of the foreign tenant per
E3.2d DoD).

Classes:

- `TenantReconciliationStatus` — closed-set outcome enum.
  Additions require an ADR.
- `TenantReconciliationResult` — read-only record returned by
  the reconciler.
- `MembershipReconciler` — calls
  `tenant-service::ListMemberships` for the Keycloak subject +
  tenant selection; returns the matching ACTIVE membership's
  role.
- `TenantSelectionGuard` — servlet filter that runs after the
  upstream `TrustedContextFilter`; calls the reconciler,
  populates the thread-local `TrustedTenantContext` with the
  reconciled role, and either forwards or returns 404.
- `TenantServiceClient` (in `client/`) — typed Spring
  `RestClient` wrapper around the `tenant-service` REST
  surface.

Privacy: the reconciler MUST refuse tenant selections that
don't match an `ACTIVE` membership; `SUSPENDED` / `INVITED` /
`REVOKED` memberships authorise no tenant selection.

Owner: `@genealogy/web-bff` (primary), `@genealogy/platform`
(secondary).
