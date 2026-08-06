# workers/search-indexer

Temporal worker that consumes every domain event from Kafka and
rebuilds the authorized + public projections owned by
`services/search-service/`. Per `ownership-catalog.md` §2.6 the
projection rebuild is a Temporal workflow with idempotent inbox,
never a synchronous call.

Responsibilities:

- Subscribe to `gp.genealogy.v1.*`, `gp.research.v1.*`,
  `gp.collab.v1.*`, `gp.media.v1.AssetReady`,
  `gp.media.v1.AssetRevoked`.
- Apply ABAC redaction before the document enters the projection.
- Maintain projection freshness SLO (`scale-and-slo.md` §5.4).

Owner: Search team. Runbook: `runbook/search-service.md`.