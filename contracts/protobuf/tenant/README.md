# contracts/protobuf/tenant

gRPC services owned by `services/tenant-service/`. Per
`ownership-catalog.md` §2.1 the tenant service is the authoritative
source for `Tenant`, `Membership`, `Invitation` and `Entitlement`;
Keycloak is the credential store, but mapping to membership and
authorization relationships happens here.

Sub-directories:

- `v1/` — `gp.tenant.v1.TenantService`
  (`tenant_service.proto`). Includes provisioning, membership
  management and entitlement queries.

Owner: Identity & Tenant team. Reviewers: Security (auth path),
Privacy (consent and residency).