/**
 * `@genealogy/api-client` — public typed REST surface.
 *
 * E1.5 ships the foundation: a fetch-based runtime that knows how
 * to call the BFF while respecting the cross-cutting contract
 * rules documented in `contracts/README.md` (RFC 9457, correlation
 * id, idempotency key, optimistic concurrency).
 *
 * Usage:
 *
 *   import { createBffClient } from "@genealogy/api-client";
 *   const client = createBffClient({ baseUrl: process.env.NEXT_PUBLIC_BFF_URL! });
 *   const session = await client.getSession();
 *
 * The generated type module (`components`, `paths`) is produced
 * by `apps/web/scripts/codegen-openapi-client.mjs` and lives in
 * `apps/web/src/lib/api/generated/`. The shell consumes the
 * generated types directly so the api-client package does not
 * need a second copy. Java services (E3+) and the BFF do not
 * consume the generated types; they use OpenAPI via Spectral.
 */
export * from "./runtime/index";
