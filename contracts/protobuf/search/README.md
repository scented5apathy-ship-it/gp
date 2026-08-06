# contracts/protobuf/search

gRPC services owned by `services/search-service/`. Per
`ownership-catalog.md` §2.6 the search service exposes an
authorized query path and a saved-search management path; the
public projection is served via the public-api app and Kong, never
directly to anonymous clients.

Sub-directories:

- `v1/` — `gp.search.v1.SearchService`
  (`search_service.proto`). Authorized queries consume the
  precomputed `SearchDocument` projection rebuilt from Kafka
  events by `workers/search-indexer/`.

Owner: Search team. Reviewers: Privacy (redaction of authorized
projection), Security (projection poisoning).