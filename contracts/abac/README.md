# contracts/abac — ABAC overlay contracts (E3.4)

Source-of-truth YAML contracts for the ABAC overlay engine.
The matching Java executor is `libs/platform-security/`. Per
`agent-execution.md` §4.4 the YAML is the contract and the Java
engine mirrors it.

| File | Contract |
| --- | --- |
| `policy.yaml` | Default policy: engine id, redact field lists, consent-required classes, reason code catalogue. |
| `cache.yaml` | Cache config: max-age (upper bound), max entries, invalidation discipline (mandatory on every Write; TTL-only is forbidden per ADR-E0.5-06), invalidator list. |
| `redaction.yaml` | Field redaction: deny keys (dropped), mask keys (`[REDACTED:<key>]`), scrub patterns (email / SSN / token). |

The chart mirror lives at
`platform/helm/genealogy-platform/files/abac-*.yaml` (byte-identical,
enforced by `scripts/lint-abac-config.mjs`).

## Validation

```bash
pnpm lint:abac
pnpm check:abac
```

The linter asserts:

- `policy.yaml` declares the engine id and the full reason-code
  catalogue.
- `cache.yaml` declares `invalidationOnWrite: required` and
  `ttlOnlyForbidden: true`.
- `redaction.yaml` declares `rawDna` + `biography` in `denyKeys`
  and `email` + `phone` in `maskKeys`.
- The chart mirror files are byte-identical to the contracts.

## Change protocol

1. Edit `contracts/abac/*.yaml`.
2. Mirror to `platform/helm/genealogy-platform/files/abac-*.yaml`
   (`scripts/lint-abac-config.mjs` fails if they drift).
3. Update `DefaultAbacPolicyEngine` + tests when the policy
   changes.
4. Run `pnpm check:abac` + `./gradlew :libs:platform-security:test`.
5. Update `runbook/abac.md` if the wire format or invalidation
   matrix changes.

Owner: platform-secondary. Reviewers: Security, Privacy.
