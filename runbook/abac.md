# Runbook — ABAC overlay (E3.4)

## 1. Scope

ABAC = attribute-based access control. The overlay runs **after**
OpenFGA on every privileged mutation in the Java services
(relationship allow alone is never sufficient per
`design.md` §6.2). The overlay decides contextual deny: living /
minor status, privacy class, consent purpose, jurisdiction,
suspension / soft-delete, support / impersonation flags.

Per `privacy-and-legal-gate.md` §DNA the overlay is the gate that
keeps DNA / living / minor / sensitive data off every public
projection, log line, metric and trace.

## 2. Source of truth

| Concern | File |
| --- | --- |
| Default policy | `contracts/abac/policy.yaml` |
| Cache settings + invalidators | `contracts/abac/cache.yaml` |
| Field redaction list | `contracts/abac/redaction.yaml` |
| Chart mirror | `platform/helm/genealogy-platform/files/abac-*.yaml` |
| Java executor | `libs/platform-security/src/main/java/com/genealogy/platform/libs/security/abac/DefaultAbacPolicyEngine.java` |
| Redaction executor | `libs/platform-security/src/main/java/com/genealogy/platform/libs/security/redaction/PiiRedactor.java` |
| Tenant-service seam | `services/tenant-service/src/main/java/com/genealogy/platform/services/tenant/application/TenantAbacEnforcer.java` |
| Semgrep rule | `security/semgrep/semgrep.local.yaml` (`no-openfga-allow-without-abac`) |
| Lint | `pnpm lint:abac` (`scripts/lint-abac-config.mjs`) |

Per `agent-execution.md` §4.4 the YAML is the contract; the
Java engine is the executor. Any change to the YAML MUST land
together with a matching change to the Java engine + tests.

## 3. Rule order (deny-first)

`DefaultAbacPolicyEngine.evaluate()` short-circuits on the most
specific deny. The order is:

1. **Suspended / soft-deleted** → `CONTEXTUAL_DENY` (privacy
   gate §6.4 T-07).
2. **Impersonation + genetic class** → `CONTEXTUAL_DENY`
   (R16.4 — impersonation never grants DNA access).
3. **Genetic class + support session + GENETIC_RAW** →
   `JURISDICTION_BLOCKED` (R16.4 — support never reads raw DNA).
4. **Genetic class** without an active consent record →
   `CONSENT_MISSING` / `CONSENT_REVOKED` (R13 + privacy gate
   §D-05).
5. **SENSITIVE class** without consent → `CONSENT_MISSING`.
6. **Minor on PUBLIC** → `MINOR_GUARDIAN_REQUIRED` (privacy gate
   §7 P-02).
7. **Minor on PRIVATE** → allow with `MINOR` redact fields.
8. **Living on PUBLIC** → allow with `LIVING` redact fields
   (privacy gate §7 P-01).
9. Default → allow with audit obligation.

## 4. Decision cache invariants

Per ADR-E0.5-06 ("cache invalidation mandatory on every Write,
TTL-only forbidden"):

- `spec.invalidationOnWrite: required` (cache.yaml).
- `spec.ttlOnlyForbidden: true` — TTL is upper bound only.
- Every mutation flow calls
  `TenantAbacEnforcer.invalidateOnChange(...)` or
  `invalidateTenant(...)` after the row update.
- Tenant suspend / soft-delete wipes every cached decision for
  the tenant (`invalidateTenant`).
- Membership revoke wipes the membership-scoped decisions; the
  next read re-evaluates against the new `REVOKED` status.

## 5. Invalidation matrix

| Event | `invalidateOnChange` | `invalidateTenant` |
| --- | --- | --- |
| `membership.invite` | ✓ | — |
| `membership.activate` | ✓ | — |
| `membership.revoke` | ✓ | — |
| `tenant.update` | ✓ | — |
| `tenant.change_plan` | ✓ | — |
| `tenant.suspend` | — | ✓ |
| `tenant.restore` | ✓ | — |
| `tenant.soft_delete` | — | ✓ |
| `entitlement.change` | ✓ | — |
| `consent.revoked` (DNA) | ✓ (resourceType=dna) | — |
| `consent.expired` (DNA) | ✓ (resourceType=dna) | — |

## 6. Wire format

The REST layer maps `AbacDeniedException` to `403 Forbidden` with
an RFC 9457 Problem Details body carrying:

- `type` = `/problems/abac/<reasonCode>` (e.g.
  `/problems/abac/consent_revoked`).
- `title` = reason description.
- `status` = `403`.
- Extensions: `decisionId`, `reasonCode`, `effect`.

The decision id is opaque (UUID v4) and is emitted on the audit
entry so the on-call team can trace a denial back to the policy.

## 7. Service onboarding checklist

1. Add `implementation(project(":libs:platform-security"))` to
   the service `build.gradle.kts`.
2. Wire the engine + cache beans in
   `application/config/ApplicationConfig` (see tenant-service for
   the canonical example).
3. Inject `TenantAbacEnforcer` into every command service that
   mutates a tenant-scoped aggregate.
4. Call `enforcer.requireAllow(request, action)` BEFORE the
   aggregate write.
5. Call `enforcer.invalidateOnChange(...)` (or
   `invalidateTenant(...)`) AFTER the row update.
6. Add a unit test that the deny branch throws
   `AbacDeniedException` for the relevant reason code.
7. Update `contracts/abac/cache.yaml` `invalidators[]` if you
   added a new action.

## 8. Operational signals

The audit hook (`libs/platform-spring-boot-starter/.../audit/`)
emits an entry on every allow + every deny. The
`decisionId` / `reasonCode` are stable identifiers consumed by:

- `audit.denial.count` metric, tagged by `reason_code`.
- Loki log filter `decision_id=~".+"` for incident triage.
- OpenFGA observability dashboard (E2.10) — separate panel for
  ABAC denials to spot regressions.

## 9. ADR / ADR-supersession

Any change to the reason code list, consent-required classes or
cache max-age requires an ADR. The closed set of reason codes is
the audit / dashboard contract; expanding it silently would
break the on-call team's denial-volume alerts.
