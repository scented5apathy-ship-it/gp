# apps/web/src/lib

Shared client-side libraries used across pages and components.

- `api/` — typed REST client wrapper. `generated/` holds the
  artefacts produced by `packages/api-client/src/codegen/`; the
  surrounding wrapper layer adapts them to the web app's
  React-Query / state library of choice (ADR still open per
  `design.md` §16 #9).

Owner: web-app team. Reviewer: API/contract owners per service.