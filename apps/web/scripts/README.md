# apps/web/scripts

Node scripts that the Next.js app delegates to. Invoked via
`pnpm --filter @gp/web <name>`.

Current script:

- `codegen-openapi-client.mjs` — refreshes the typed client under
  `apps/web/src/lib/api/generated/` from the versioned OpenAPI specs
  in `contracts/openapi/`. Used during local development and as the
  fallback for the codegen pipeline owned by `packages/api-client/`.

Planned scripts (later epics): accessibility smoke tests, Lighthouse
budget runner, OWASP ZAP baseline runner.

Owner: web-app team.