# apps/web-bff/.../webbff/client

Typed REST clients for downstream services (E3.5).

Classes:

- `MembershipView` — DTO mirroring the
  `tenant-service::Membership` resource.
- `TenantServiceClient` — Spring `RestClient` wrapper that
  the BFF reconciliation layer uses to look up the Keycloak
  subject's memberships.

Privacy: the client MUST forward the validated Keycloak subject
as a service-to-service caller header; tenant-service filters
on the subject + tenant id.

Owner: `@genealogy/web-bff` (primary).
