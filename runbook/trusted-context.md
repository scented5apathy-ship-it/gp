# Trusted tenant context (E3.5) — operator runbook

This runbook describes the trusted tenant context propagation
contract that ships with E3.5. It is the operator-side companion
to:

- `contracts/trusted-context/policy.yaml` — the source of truth
  (per-surface sources, refuse list, mTLS posture, BFF
  reconciliation rules, gRPC metadata keys).
- `platform/helm/genealogy-platform/files/trusted-context-policy.yaml`
  — the byte-identical chart mirror.
- `scripts/lint-trusted-context.mjs` — the deep validator.

## Source of truth

| Concern                                | Source                                                                                                                                |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| REST trusted header                    | `policy.yaml::sources.rest.tenantId.from = X-Tenant-Id`                                                                               |
| REST actor identity                    | `policy.yaml::sources.rest.actorId.from = jwt.sub`                                                                                    |
| REST actor role                        | `policy.yaml::sources.rest.actorRole.from = membership.role`                                                                          |
| gRPC trusted metadata                  | `policy.yaml::sources.grpc.tenantId.from = bffMetadata.x-tenant-id`                                                                   |
| gRPC correlation                       | `policy.yaml::sources.grpc.correlationId.from = grpcMetadata.x-correlation-id`                                                        |
| gRPC peer identity (mTLS)              | `policy.yaml::mtls.expectedPeerPattern`                                                                                               |
| BFF tenant selection → membership rule | `policy.yaml::reconciliation.membershipStatusRequired = ACTIVE`                                                                       |
| Mismatch response                      | `policy.yaml::reconciliation.mismatchResponse = { restStatus: 404, grpcCode: NOT_FOUND, leakAvoidance: avoid-tenant-existence-leak }` |
| Refused client params (REST)           | `policy.yaml::refuseClientSupplied.rest` (closed set, 10 entries)                                                                     |
| Refused proto fields (gRPC)            | `policy.yaml::refuseClientSupplied.grpc` (closed set, 3 entries)                                                                      |

Additions require an ADR — the catalogue is the audit contract.

## Wire format

### REST

Inbound header on every authenticated request:

```
X-Tenant-Id: <opaque tenant id>
X-Correlation-Id: <opaque>
Authorization: Bearer <JWT>
```

The Keycloak subject claim (`sub`) is the source of `actor_id`;
the membership role (looked up at the BFF) is the source of
`actor_role`. The REST filter (`TrustedContextFilter`) refuses
any `tenantId` / `tenant_id` / `role` / `actor_role` /
`subject` / `actor_id` query parameter — E3.5 mirror of the
Semgrep rule `no-client-supplied-tenant-id`.

### gRPC

Inbound gRPC metadata keys (lowercase per gRPC convention):

```
x-tenant-id: <opaque>
x-actor-id: <opaque>
x-actor-role: <opaque role from membership>
x-correlation-id: <opaque>
x-idempotency-key: <opaque>      (only for non-idempotent RPCs)
x-peer-spiffe-id: spiffe://...   (filled by Istio mTLS)
```

The proto `Context.tenant_id` / `Context.actor_id` /
`Context.actor_role` fields MUST be empty on inbound gRPC
requests — the server reconstructs the trusted context from the
metadata above and the JWT claims.

## Architecture

```
[BFF filter]  ── X-Tenant-Id + JWT ──>  [TrustedContextFilter]
   │
   ▼
[TenantSelectionGuard]  ── ListMemberships ──>  [tenant-service]
   │ (membership.role → actor_role)
   ▼
[TrustedTenantContext populated]
   │
   ▼
[GrpcTrustedContextClientInterceptor]  ── gRPC metadata ──>  domain service
                                                              │
                                                              ▼
                                              [GrpcTrustedContextInterceptor]
                                                              │
                                                              ▼
                                          [TrustedContextReconstructor.validate()]
                                                              │
                                                              ▼
                                          [TrustedTenantContext.set()]
                                                              │
                                                              ▼
                                              [domain code (ABAC + RLS)]
```

## Cache invariants

The trusted context is **not** cached. Every request rebuilds
the context from scratch; the membership role for the BFF
reconciliation is the only lookup, and tenant-service
ListMemberships is `O(memberships for this tenant)` which is
bounded by the plan quota.

## Invalidation matrix

| Trigger                                   | Effect                                                |
| ----------------------------------------- | ----------------------------------------------------- |
| Membership status flip (INVITED → ACTIVE) | Next BFF request reconciles; no special signal needed |
| Membership REVOKED                        | Next BFF request returns 404 (reconciler rejects)     |
| Membership SUSPENDED                      | Next BFF request returns 404 (reconciler rejects)     |
| Tenant suspend / restore / soft-delete    | ABAC enforcer invalidates per-tenant cache (E3.4)     |
| Keycloak token issued / refreshed         | TrustedContextFilter rebuilds from the new JWT        |
| Service config reload                     | Spring rebinds `TrustedContextReconstructor` bean     |

## REST wire format — problem responses

When the BFF guard rejects a tenant selection (cross-tenant /
membership inactive), the response is RFC 9457 with status 404:

```
HTTP/1.1 404 Not Found
Content-Type: application/problem+json
X-Correlation-Id: <opaque>

{
  "type": "https://genealogy/problems/tenant-not-found",
  "title": "selected tenant is not accessible to the caller",
  "status": 404
}
```

Per E3.2d DoD we never return 403 — the response must not leak
the existence of the foreign tenant.

## gRPC wire format — violation trailers

When the server-side gRPC interceptor rejects a request, the
call is closed with `PERMISSION_DENIED` + the
`x-trusted-context-violation` trailer carrying the closed-set
reason code:

```
grpc-status: 7  (PERMISSION_DENIED)
grpc-message: Context.tenant_id must be empty on inbound gRPC; clients cannot set it (contracts/trusted-context/policy.yaml E3.5).
x-trusted-context-violation: CLIENT_SUPPLIED_TENANT_ID
```

The reason codes are:

- `CLIENT_SUPPLIED_TENANT_ID`
- `CLIENT_SUPPLIED_ACTOR_ID`
- `CLIENT_SUPPLIED_ACTOR_ROLE`
- `MISSING_SPIFFE_PEER`
- `UNTRUSTED_SPIFFE_PEER`
- `MISSING_TENANT_ID`
- `MISSING_ACTOR_ID`
- `MISSING_ACTOR_ROLE`
- `MISSING_CORRELATION_ID`

## Operator signals

| Metric                                                                | Where to look                                          |
| --------------------------------------------------------------------- | ------------------------------------------------------ |
| `platform.audit.events{action="tenant.reconcile.denied"}`             | Micrometer / Grafana tenant-service dashboard          |
| `platform.audit.events{action="grpc.trusted_context.violation"}`      | Micrometer / Grafana per-service dashboard             |
| `genealogy.audit.v1.v1` events with `reason_code` `client_supplied_*` | Audit topic via audit-service consumer                 |
| BFF 404 spike with `tenant-not-found` problem                         | Kong → web-bff dashboard; investigate membership flips |
| SPIFFE peer rejection spike                                           | Istio metrics; investigate mTLS posture                |

## ADR supersession protocol

Any change to `contracts/trusted-context/policy.yaml` (or the
chart mirror) is a wire-contract change. Follow the standard
contract supersession protocol:

1. Open an ADR describing the change + backward-compat
   strategy.
2. Bump `spec.policyId` from `trusted-context/v1` to
   `trusted-context/v2`.
3. Update both the contract and the chart mirror atomically.
4. Update the Java executor + the BFF reconciler to match.
5. Add a `compatibility.test.mjs` next to the contract and run
   it via `node --test scripts/__tests__/lint-trusted-context.test.mjs`.
6. Roll out with the standard Argo progressive-delivery
   strategy (Argo Rollouts).

## Service onboarding checklist

1. Add `implementation(project(":libs:platform-spring-boot-starter"))`
   to the service `build.gradle.kts`.
2. If the service exposes gRPC, the autoconfiguration
   `GrpcTrustedContextAutoConfiguration` registers the
   `GrpcTrustedContextInterceptor` automatically. Verify with
   a unit test (`TrustedContextReconstructorTest`).
3. If the service has gRPC methods that carry the
   `com.genealogy.platform.common.v1.Context` field, call
   `TrustedContextFieldGuard.enforce(...)` as the first line of
   the method body (after deserialisation).
4. Add a service `OWNERS` entry mirroring the E0.6 ownership
   catalog.

## BFF onboarding checklist

1. `apps/web-bff/build.gradle.kts` must depend on
   `platform-spring-boot-starter` and the tenant-service REST
   contract.
2. Configure `platform.bff.tenant-service.base-url` to the
   tenant-service URL.
3. Register `TenantSelectionGuard` as a servlet filter at
   order `HIGHEST_PRECEDENCE + 25` (after `TrustedContextFilter`).
4. Add a unit test for `MembershipReconciler` covering all
   membership statuses (ACTIVE, INVITED, SUSPENDED, REVOKED).

Owner: `@genealogy/platform` (primary), `@genealogy/security`
(secondary).
