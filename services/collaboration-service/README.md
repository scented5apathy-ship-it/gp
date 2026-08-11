# collaboration-service

Service owner: see `OWNERS` (per-service CODEOWNERS file mirrored from
`config/teams.yaml`).

E6.2 ships the change proposal + review domain model
(`com.genealogy.platform.services.collaboration.domain`) +
the partial-merge executor + the closed-set vocabulary linter.
E6.3 adds the mixed-collaboration policy: per-role ×
per-branch × per-resource-type routing decision
(`DirectEditMatrix` + `RoutingExecutor`), the conflict
comparison model + merge command factory
(`MergeCommandFactory` + `PatchValidator`), and the
Flagsmith rollout sync (`FlagsmithRolloutSync` +
`FlagsmithSnapshot`). The REST surface, gRPC stubs, Flyway
migration, jOOQ persistence, Kafka producer/consumer and
OpenFeature wiring land in the later E6.x / E11.x sub-epics.

The proposal review re-authorizes through OpenFGA + ABAC at
submit + every review decision via an injected port — the
platform never mutates another service's domain record
directly from the executor. The base version + normalized
domain command list pattern (per `design.md` §8.3) replaces
arbitrary JSON patch, so the executor can refuse to mutate
forbidden fields (DNA raw data, consent receipt, living
marker, visibility on a private tree, raw identifiers) at
construction time. The mixed-collaboration policy (E6.3)
routes mutations through one of three outcomes
(`DIRECT_EDIT` / `APPROVAL_REQUIRED` / `DENY`) instead of
forcing every write through the proposal pipeline;
policy changes flow through Flagsmith only as a rollout
switch — the YAML contract remains the source of truth.