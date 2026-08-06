# contracts/openapi/common

Reusable OpenAPI components referenced from every other OpenAPI
spec in `contracts/openapi/`. Per `design.md` §7.1 these ensure
headers, pagination, error envelope and shared value objects stay
identical across the BFF and the public-api.

Schemas:

- `headers.yaml` — correlation ID, idempotency key, traceparent.
- `pagination.yaml` — cursor pagination (keyset) per
  `ownership-catalog.md` §2.6 ("Keyset pagination thay offset sâu").
- `problem-details.yaml` — RFC 9457 Problem Details.

Owner: contract-first platform team. Reviewers: every edge app
team.