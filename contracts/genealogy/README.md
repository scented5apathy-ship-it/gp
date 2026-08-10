# contracts/genealogy — Tree aggregate + visibility contracts (E4.1)

Source-of-truth YAML contracts for the `tree-service` module.
Per `agent-execution.md` §4.4 the YAML is the contract; the
Java implementation in `services/tree-service/` mirrors it.

| File                        | Contract                                                                                                                                               |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `tree-policy.yaml`          | Tree aggregate defaults: locale / timezone / calendar / branding keys / slug pattern / visibility closed-set / collaboration modes / lifecycle states. |
| `collaboration-policy.yaml` | Per-tree collaboration mode + role overrides (DIRECT_EDIT / APPROVAL_REQUIRED / HYBRID_BY_ROLE).                                                       |
| `unlisted-token.yaml`       | UNLISTED visibility token: format / scope / TTL / fingerprint algorithm / audit class / `robots` directive / sweeper interval.                         |

The chart mirror lives at
`platform/helm/genealogy-platform/files/tree-{policy,collaboration,unlisted-token}.yaml`
(byte-identical, enforced by `scripts/lint-tree-config.mjs`).

## Validation

```bash
pnpm lint:tree
pnpm check:tree
```

The linter asserts:

- `tree-policy.yaml` declares the closed-set `visibilities` /
  `collaborationModes` / `lifecycleStates` / `brandingKeys` and
  the `slugPattern` / `maxTreesPerTenant` numeric invariants;
- `collaboration-policy.yaml` declares the three modes and
  `alwaysDirectEditRoles` / `alwaysProposalRoles`;
- `unlisted-token.yaml` declares `fingerprintAlgorithm: SHA-256`,
  `scopes` (FULL_TREE / BRANCH), audit-class mappings and
  `robotsDirective: noindex`;
- no literal secret / token / password / DSN in any
  source-of-truth file;
- the three contracts are mirrored byte-identical into
  `platform/helm/genealogy-platform/files/tree-*.yaml`.

## Change protocol

1. Edit the source-of-truth YAML in this directory.
2. Re-run `pnpm lint:tree`.
3. Update the chart mirror with the same bytes.
4. Update the matching Java implementation in
   `services/tree-service/src/main/java/com/genealogy/platform/services/tree/`.
5. Update the event schemas under `contracts/events/genealogy/v1/`
   if the change affects the wire-format.
6. Update `.github/CODEOWNERS` if the area changes.
7. Commit the source-of-truth, mirror, code, and tests in one PR.
