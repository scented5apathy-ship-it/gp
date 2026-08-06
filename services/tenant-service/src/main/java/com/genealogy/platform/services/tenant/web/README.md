# services/tenant-service/src/main/java/com/genealogy/platform/services/tenant/web

Canonical production code for the **Tenant / Membership / Invitation / Entitlement** service.

Per `design.md` §4 and `ownership-catalog.md` §2.1 this
package is owned by `Identity & Tenant team` and implements the
aggregate, command handlers, gRPC service, REST controllers,
outbox publisher and tests for the
`Tenant / Membership / Invitation / Entitlement` domain. Real implementation lands in later
epics (E3.x).

Contents:

- `TenantController.java`
- `TenantInfoController.java`

Boundary rules enforced by CI (`AGENTS.md` §2):

- No file under `services/tenant-service/` may import another service's
  `db/` or `domain/` package.
- Shared cross-cutting concerns live in
  `libs/platform-{errors, feature-flags, security, telemetry,
  spring-boot-starter}`, not here.
- Cross-service interaction happens via gRPC + Kafka events,
  never via shared database tables or domain imports.
- RLS is mandatory on every tenant-scoped query
  (`design.md` §5.1).

Ownership row from `ownership-catalog.md`:

```
### 2.1 tenant-service (E3.2)

| Field                   | Value                                                                                                                                                  |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Domain owner            | Identity & Tenant team (lead placeholder: EM-Identity)                                                                                                 |
| Product owner           | Onboarding journey (Product-IC-01)                                                                                                                     |
| Privacy owner           | DPO delegate (Consents & Tenancy)                                                                                                                      |
| Security owner          | AppSec partner (Identity)                                                                                                                              |
| SRE / on-call lead      | sre-primary                                                                                                                                            |
| Owns aggregate/data     | `Tenant`, `Membership`, `Invitation`, `Entitlement` (per `design.md` §4)                                                                               |
| Public REST             | `services/tenant-service/openapi.yaml` (provisionally `…/v1/tenants`, `…/v1/memberships`)                                                              |
| gRPC                    | `gp.tenant.v1.{TenantService, MembershipService}`                                                                                                      |
| Events published        | `gp.tenant.v1.{TenantCreated, MembershipInvited, MembershipActivated, MembershipRevoked, EntitlementChanged}` (Apicurio)                               |
| Events consumed         | `gp.identity.v1.SubjectProvisioned` (Keycloak mirror)                                                                                                  |
```
Owner: `services/tenant-service/OWNERS`.
