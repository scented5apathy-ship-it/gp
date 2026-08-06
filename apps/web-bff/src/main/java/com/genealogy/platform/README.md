# apps/{public-api,web-bff}

Top-level edge applications described in `design.md` §4.1. Both are
Spring Boot services that compose domain microservices via gRPC and
serve REST/OpenAPI to Kong. They do not own aggregate data.

| App          | Purpose                                                                                                                              |
| ------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| `web-bff`    | Screen-shaped REST API optimised for the Next.js PWA. Performs multi-service composition, caching and pagination; never replaces Kong. |
| `public-api` | Versioned REST/OpenAPI surface for partner integrations. Provides resource model, idempotency, webhook management and developer portal hooks. |

Owner per `ownership-catalog.md` §3:

- `web-bff` — web-bff team. SLO 99.95 %, p95 compose < 800 ms,
  `n_sync ≤ 3`.
- `public-api` — public-api team. SLO 99.95 %, p95 < 600 ms,
  `n_sync ≤ 2`. Must NOT depend on `web-bff`.

Both apps share the platform starters in `libs/platform-{errors,
feature-flags, security, telemetry}` and the contract sources in
`contracts/openapi/`. CI gates: `pnpm test:contract`,
`pnpm check:boundary`, `pnpm check:java`.
