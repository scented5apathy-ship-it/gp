# packages/api-client

Generated and curated API client surface used by `apps/web` (and any
future native client). Two responsibilities per `design.md` §7.1 and
§10.1 ("REST client sinh t� OpenAPI hoặc wrapper typed"):

- **codegen** — OpenAPI client generation pipeline. Reads the
  versioned OpenAPI specs under `contracts/openapi/`, runs the
  generator (typescript-fetch / typed wrapper) and writes the result
  to `generated/`. The pipeline MUST run as part of CI so that a
  breaking OpenAPI change fails the contract test before merge.
- **generated** — checked-in artefacts produced by `codegen`. They
  are committed (not gitignored) so reviewers can diff API surface
  changes in normal PR review.
- **scripts** — helper scripts invoked by `pnpm`/`turbo` to refresh
  the client locally (`pnpm --filter @gp/api-client codegen`) and to
  diff against the previously generated tree.

Public exports:

- `@gp/api-client` — typed REST client, one module per BFF/public-api
  surface (`tenants`, `genealogy`, `media`, …).
- `@gp/api-client/testing` — mock client + fixtures for unit tests.

Owner: web-app team with per-service reviewers named in
`contracts/openapi/<service>/OWNERS`.
