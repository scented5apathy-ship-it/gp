# Ownership mirror

> Mirrors `.kiro/specs/genealogy-platform/ownership-catalog.md` into the
> monorepo so that CODEOWNERS / OWNERS files, Renovate policy, alert
> routing and Argo CD notifications can consume the same source of truth.

## Source of truth

The canonical ownership record lives at
[`.kiro/specs/genealogy-platform/ownership-catalog.md`](../kiro/specs/genealogy-platform/ownership-catalog.md).
This directory is a thin mirror of §2 / §3 / §6 from that document plus
machine-readable `OWNERS` files in every service and platform directory.

## Files

| File | Purpose | Owner |
|---|---|---|
| `OWNERS.md` | This index | `@genealogy/platform` |
| `OWNERS` | Repo-wide CODEOWNERS (delegates to per-area files) | `@genealogy/platform` |
| `team-map.yaml` | Generated snapshot of `config/teams.yaml` (machine-readable) | `@genealogy/platform` |
| `services/<svc>/OWNERS` | CODEOWNERS for that service | per-service team |
| `apps/<app>/OWNERS` | CODEOWNERS for that application | per-area team |
| `platform/<area>/OWNERS` | CODEOWNERS for platform config | `@genealogy/platform` |

## Update process

1. Any change to ownership mapping MUST be opened as a PR that touches
   both `docs/ownership/**` AND
   `.kiro/specs/genealogy-platform/ownership-catalog.md` so that the
   two stay in lock-step.
2. PR description must call out affected service(s) and on-call
   rotation(s).
3. Reviewers must include `@genealogy/platform` and the new owner team.
4. After merge, Renovate is re-evaluated and Argo CD notifications
   re-resolved automatically.

## Acceptance

E1.1 ships the seed for the mirror; E0.6 / E13.2 (SRE lead) ingests the
catalog drift alerts and runs the quarterly review per
`ownership-catalog.md` §7 #3.