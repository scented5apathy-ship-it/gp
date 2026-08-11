# collaboration-service

Service owner: see `OWNERS` (per-service CODEOWNERS file mirrored from
`config/teams.yaml`).

E6.2 ships the change proposal + review domain model
(`com.genealogy.platform.services.collaboration.domain`) +
the partial-merge executor + the closed-set vocabulary linter.
The REST surface, gRPC stubs, Flyway migration, jOOQ
persistence, Kafka producer/consumer and OpenFeature wiring
land in the later E6.x / E11.x sub-epics.

The proposal review re-authorizes through OpenFGA + ABAC at
submit + every review decision via an injected port — the
platform never mutates another service's domain record
directly from the executor. The base version + normalized
domain command list pattern (per `design.md` §8.3) replaces
arbitrary JSON patch, so the executor can refuse to mutate
forbidden fields (DNA raw data, consent receipt, living
marker, visibility on a private tree, raw identifiers) at
construction time.