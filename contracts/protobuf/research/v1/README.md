# Contracts — research v1

gRPC service definitions for the research-service aggregates
(E6.1d). Authoritative serialization is **Protobuf** per
`architecture-decisions.md` ADR-E0.5-08; the matching Kafka
events live under `contracts/events/research/v1/` (Avro).

The first field of every request message is the platform
`com.genealogy.platform.common.v1.Context` envelope. The
server-side `GrpcTrustedContextInterceptor` (already wired in
`platform-spring-boot-starter`) reconstructs the trusted tenant
context from BFF-signed metadata + the Istio mTLS peer SPIFFE
identity. The `TrustedContextFieldGuard` rejects any request
that supplies the `Context.tenant_id` / `Context.actor_id` /
`Context.actor_role` proto fields directly (per `design.md` §6.1
+ E3.5).

## Services

- `RepositoryService` — CRUD for the Repository aggregate.
- `CitationService`   — CRUD for the Citation aggregate + the
  Provenance traversal RPC.
- `ResearchTaskService` — CRUD + state transitions for the
  ResearchTask aggregate.
- `HypothesisService`  — CRUD + state transitions for the
  Hypothesis aggregate.
- `ConflictService`    — CRUD + state transitions for the
  Conflict aggregate (R8.1 review/approval flow).

All `Submit*` / `Approve*` / `PartialMerge*` RPCs call the
in-process `ReAuthorizationPort` (OpenFGA + ABAC overlay) before
the command services run.
