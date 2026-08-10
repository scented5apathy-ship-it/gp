# contracts/trusted-context

Source of truth for the trusted tenant context propagation
contract (E3.5). Mirrors `design.md` §6.1 + §7.2 and
`privacy-and-legal-gate.md` TM-02 / T-01.

## Files

- `policy.yaml` — closed-set policy: per-surface sources for
  `tenant_id` / `actor_id` / `actor_role` / `correlation_id`,
  client-supplied fields the service MUST refuse, the Istio mTLS
  SPIFFE posture, and the BFF reconciliation rules.

## Wire format

- REST: `X-Tenant-Id` (trusted header) + JWT subject
  (`actor_id`) + membership role (`actor_role`).
- gRPC: metadata keys declared in `policy.yaml` (BFF → service
  direction) + SPIFFE peer identity (Istio mTLS).
- Proto `Context` field is NOT a trusted source on the wire —
  the gRPC interceptor overwrites it from the metadata + JWT +
  SPIFFE peer.

## Validation

```
node scripts/lint-trusted-context.mjs
node --test scripts/__tests__/lint-trusted-context.test.mjs
```

Owner: `@genealogy/platform` (primary), `@genealogy/security`
(secondary).
