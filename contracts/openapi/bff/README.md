# contracts/openapi/bff

OpenAPI specs owned by `apps/web-bff/`. Per `design.md` §7.1 the
BFF exposes screen-shaped endpoints tailored to the PWA; it does
not own domain data and does not replace Kong.

Sub-directories:

- `v1/` — current BFF version (`session.yaml`, …). Backed by
  `gp.collab.v1.*`, `gp.genealogy.v1.*`, `gp.media.v1.*`, etc.

Compatibility: 12 months per `ownership-catalog.md` §5.1.
Generated client: `packages/api-client/src/generated/`.

Owner: web-bff team. Reviewers: web-app team, contract-first
platform team.