# contracts/openapi/public-api/v1

Current OpenAPI spec for `apps/public-api/`. Per
`ownership-catalog.md` §5.1 this version is pinned for **12 months**
of overlap after a deprecation announcement.

Schemas:

- `tenant.yaml` — tenant resource, membership listing,
  invitation/activation flows.
- `tree.yaml` — tree resource, visibility metadata.
- `person.yaml` — person resource (read model).
- `events.yaml` — webhook event delivery API.

Owner: public-api team. Reviewers: every domain service owner
named in `ownership-catalog.md` §2.