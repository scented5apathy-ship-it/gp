# contracts/protobuf/tenant/v1

Current gRPC services for `services/tenant-service/`. Per
`ownership-catalog.md` §5.2 this package is pinned for **9 months**
after a stage transition.

Service:

- `tenant_service.proto` — `gp.tenant.v1.TenantService`:
  - `CreateTenant`, `UpdateTenant`, `GetTenant`.
  - `InviteMember`, `ActivateMember`, `RevokeMember`.
  - `SetEntitlement`, `GetEntitlement`.

Sync dependency budget: `n_sync ≤ 2` (Keycloak admin + Postgres
primary). OpenFGA tuple writes are async via Temporal.

Owner: Identity & Tenant team. Reviewers: Security, Privacy.