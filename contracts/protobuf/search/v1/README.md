# contracts/protobuf/search/v1

Current gRPC services for `services/search-service/`. Per
`ownership-catalog.md` §5.2 this package is pinned for **9 months**
after a stage transition.

Service:

- `search_service.proto` — `gp.search.v1.SearchService`
  (authorized search, saved search, projection freshness query).
  Consumed by `web-bff` and `public-api`.

Privacy gate: anonymous traffic MUST go through the public
projection only; authorized queries MUST re-evaluate ABAC overlays
before returning results (`design.md` §6.2).

Owner: Search team. Reviewers: Privacy, Security.