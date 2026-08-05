# Contract-first artifacts for the Genealogy Platform

All cross-service and cross-process communication in this monorepo is
defined here **before** any code that consumes the contract. The
artifacts in this tree are:

| Sub-tree    | Purpose                                                                                | Tooling                                |
| ----------- | -------------------------------------------------------------------------------------- | -------------------------------------- |
| `openapi/`  | External REST API contracts for `apps/public-api` and `apps/web-bff` (BFF surface).     | Stoplight Spectral + openapi-generator |
| `protobuf/` | Internal gRPC contracts shared by Spring Boot services, Temporal workers and the BFF.   | `buf` (lint + breaking)                |
| `events/`   | Kafka event schemas (Avro) per [ADR-E0.5-08](../../../../specs/genealogy-platform/architecture-decisions.md). | Apicurio Registry compatibility checker |
| `envelopes/`| Reusable envelope types (REST problem details, event envelope) shared across surfaces. | Manual + Spectral                      |

## Cross-cutting contract rules

1. **URI versioned REST.** Every REST path under `/api/v{version}/...`
   (see `openapi/public-api.yaml` for the canonical example).
2. **RFC 9457 Problem Details.** Every error response carries a
   `application/problem+json` payload. The shared `Problem` schema
   lives in `envelopes/problem-details.yaml`.
3. **Cursor pagination.** Lists expose `?cursor=...` + `Page.cursor`
   with `Page.nextCursor` (opaque string). Never expose `offset/limit`.
4. **Optimistic concurrency.** Every mutable REST resource carries an
   `etag` field and accepts `If-Match`. gRPC mutation RPCs accept
   `base_version`.
5. **Idempotency key.** Every non-`GET` REST endpoint documents the
   `Idempotency-Key` header (draft-ietf-httpapi-idempotency-key). gRPC
   mutations accept `idempotency_key`.
6. **Correlation ID.** Every inbound/outbound request carries
   `X-Correlation-Id` (REST) or `x-correlation-id` metadata (gRPC).
   Event envelopes always include `traceId`.
7. **Tenant context is server-derived.** REST/gRPC payloads **must
   not** accept `tenantId` from the client; see
   `protobuf/common/v1/context.proto`.
8. **No raw DNA / file content / token / PII** in REST, gRPC or event
   payloads (see `config/spectral.yaml` and
   `scripts/lint-events.mjs`). DNA is a separate service and uses its
   own envelope encryption boundary per `design.md` §5.4.

## Compatibility policy

| Surface          | Compatibility rule          | Source of truth                          |
| ---------------- | --------------------------- | ---------------------------------------- |
| REST public API  | additive only per release   | Spectral ruleset (`config/spectral.yaml`)|
| REST BFF         | additive only per release   | Spectral ruleset                         |
| gRPC services    | `buf breaking --against`    | `buf.yaml` + `scripts/lint-protobuf.mjs` |
| Kafka events     | `BACKWARD` per ADR-E0.5-08  | Apicurio compatibility checker           |

Breaking changes require a new `major` version segment (e.g. `v2`).
The CI pipeline fails any PR that introduces an incompatible change
against the committed previous-version snapshot.

## CI gates

| Gate                                  | Script                                       |
| ------------------------------------- | -------------------------------------------- |
| `pnpm lint:openapi`                   | `scripts/lint-openapi.mjs`                   |
| `pnpm lint:protobuf`                  | `scripts/lint-protobuf.mjs`                  |
| `pnpm lint:events`                    | `scripts/lint-events.mjs`                    |
| `pnpm test:contract`                  | `scripts/test-contracts.mjs` + Gradle target |
| `pnpm check:contracts`                | aggregate of the three linters              |
