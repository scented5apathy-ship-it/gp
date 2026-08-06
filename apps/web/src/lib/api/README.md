# apps/web/src/lib/api

REST client wrapper consumed by the Next.js app. Per
`design.md` §7.1 the typed client is generated from the contract
sources in `contracts/openapi/`; the wrapper adapts it to React
idioms (hooks, suspense, React Query, etc.).

Layout:

- `generated/` — checked-in artefacts produced by
  `packages/api-client/src/codegen/`. Module per BFF surface
  (`bff__v1__session.ts`, `public-api__v1__person.ts`, …).
- `problem.ts`, `index.ts` — shared error-envelope and pagination
  helpers imported by feature code.

Owner: web-app team. Reviewer: API/contract owners per service.