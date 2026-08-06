# packages/api-client

Typed REST client surface used by `apps/web` (and any future native
client). Per `design.md` §7.1 the client is generated from the
contract sources in `contracts/openapi/`; the diff is enforced by
`pnpm test:contract`.

Layout:

- `src/codegen/` — generator pipeline.
- `src/generated/` — checked-in artefacts (one module per BFF /
  public-api surface).
- `src/runtime/` — runtime helpers (`problem.ts`, `index.ts` —
  pagination, error envelope, common headers) that wrap the
  generated fetcher with React-friendly ergonomics.
- `scripts/` — `generate.ts`, `diff.ts`, `check-compat.ts`.
- `test/` — runtime tests (`runtime.test.ts`).

Public exports: `@gp/api-client`, `@gp/api-client/testing`.

Owner: web-app team.