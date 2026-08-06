# contracts/openapi/public-api

OpenAPI specs owned by `apps/public-api/`. Per `design.md` §4.1 and
`ownership-catalog.md` §3 the public-api is the partner-facing
resource model (idempotency, webhook management, dev portal hooks).
It MUST NOT depend on `web-bff`.

Sub-directories:

- `v1/` — current public-api version. Backed by the same domain
  gRPC services as the BFF, exposed as REST resources
  (`tenant.yaml`, `tree.yaml`, `person.yaml`, `events.yaml`).

Compatibility: 12 months per `ownership-catalog.md` §5.1. Breaking
changes require a new `/v{n+1}/` running in parallel for 12 months.

Owner: public-api team. Reviewers: contract-first platform team,
every domain service owner.