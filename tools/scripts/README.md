# tools/scripts

Standalone repository-maintenance scripts (TypeScript / Node). Per
`AGENTS.md` §2 these tools operate on the repo metadata, not on the
runtime.

Current tool:

- `sync-team-map.mts` — keeps `docs/ownership/team-map.yaml` in
  sync with the canonical `config/teams.yaml`.

Owner: platform-primary.