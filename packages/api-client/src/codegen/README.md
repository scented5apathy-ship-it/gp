# packages/api-client/src/codegen

Local scratch space for the codegen pipeline. This directory holds
the generator configuration (templates, partials, type mappings and
post-processing rules) used by `packages/api-client/scripts/generate.ts`
to produce the artefacts in `../generated/`.

Per `design.md` §7.1 and §10.1, the client is generated, not hand
written — anything that needs to be exposed from the API surface must
appear in `contracts/openapi/` first so the diff is visible at PR time.

Planned contents (added in later epics):

- `openapi-typescript.config.ts` — generator entrypoint.
- `templates/` — handlebars templates that emit typed fetchers,
  React-Query hooks and zod schemas.
- `transformers/` — service-name → SDK-module mapping.
- `README.md` (this file) — operator notes.

Owner: web-app team. CI gate: `pnpm test:contract` (Node side) plus
the Gradle `ContractInvariantsTest` described in `AGENTS.md` §4.
