# packages/api-client/scripts

Helper scripts that wrap the OpenAPI → typed-client codegen pipeline
referenced by `packages/api-client/`. Per `design.md` §7.1 the typed
client is generated from the contract sources in `contracts/openapi/`
and the diff is enforced by `pnpm test:contract`.

Typical contents (added in later epics):

- `generate.ts` — runs the generator (typescript-fetch or wrapper of
  choice) over `contracts/openapi/**/*.yaml`, normalises naming and
  emits to `packages/api-client/src/generated/`.
- `diff.ts` — compares the freshly generated tree against HEAD and
  exits non-zero if anything changed (used in CI for change detection).
- `check-compat.ts` — wraps `oasdiff` to fail the build on breaking
  OpenAPI changes per `ownership-catalog.md` §5.1 (12-month REST
  envelope window).

Invoked by `pnpm --filter @gp/api-client codegen` /
`pnpm --filter @gp/api-client check`. Owner: web-app team.
