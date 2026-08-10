# libs/platform-security — ABAC overlay (E3.4)

Cross-cutting library that owns the **ABAC overlay** evaluated on
every privileged mutation in the Java services. The overlay sits
on top of the OpenFGA relationship check (E3.3) — OpenFGA decides
relationships, ABAC decides contextual deny and obligations
(redact / watermark / audit). Per `design.md` §6.2 the two are
non-overlapping: OpenFGA does not know about living status,
DNA consent or jurisdiction; ABAC does not encode the
relationship graph.

## Contents

- `abac/` — ABAC overlay:
  - `AbacPolicyEngine` interface (the seam every service depends on).
  - `DefaultAbacPolicyEngine` — the reference implementation
    (mirrors `contracts/abac/policy.yaml`).
  - `AbacDecision` / `AbacObligation` / `ReasonCode` — value types.
  - `AbacRequest` — the read-only request view.
  - `AbacDecisionCache` — short-lived cache with explicit
    invalidation (ADR-E0.5-06 forbids TTL-only).
  - `OpenFgaAbacGuard` — the OpenFGA + ABAC combinator that
    closes the Semgrep `no-openfga-allow-without-abac` rule.
- `redaction/` — `PiiRedactor` (mirrors `contracts/abac/redaction.yaml`).
- `domain/` — closed-set types: `PrivacyClass`,
  `LivingStatus`, `ConsentRecord`, `Jurisdiction`.

## Wire-up

Add to your service `build.gradle.kts`:

```kotlin
implementation(project(":libs:platform-security"))
```

Wire the beans:

```kotlin
@Bean
fun abacPolicyEngine(clock: Clock): AbacPolicyEngine =
    DefaultAbacPolicyEngine(clock, ...)

@Bean
fun abacDecisionCache(): AbacDecisionCache = AbacDecisionCache()

@Bean
fun abacEnforcer(engine: AbacPolicyEngine, cache: AbacDecisionCache): TenantAbacEnforcer =
    TenantAbacEnforcer(engine, cache)
```

Per-service `TenantAbacEnforcer` lives in the service module
(it carries the service-specific `Actions` enum).

## Tests

```bash
./gradlew :libs:platform-security:test
```

29 / 29 PASS. The tests cover: deny-first rule order,
consent revocation flow, living / minor redaction, jurisdictional
block on GENETIC_RAW, cache TTL + invalidation,
OpenFGA-deny short-circuit, cache invalidation on tenant revoke.

## Related contracts

- `contracts/abac/policy.yaml` — default policy contract.
- `contracts/abac/cache.yaml` — cache + invalidators.
- `contracts/abac/redaction.yaml` — field redaction list.
- `platform/helm/genealogy-platform/files/abac-*.yaml` — chart mirror.

Owner: platform-secondary. Reviewers: Security, Privacy.
