# Event (Avro) contracts

Kafka event schemas. Authoritative serialization is **Avro** per
[ADR-E0.5-08](../../../.kiro/specs/genealogy-platform/architecture-decisions.md).
Protobuf stays for internal gRPC; JSON Schema is reserved for
webhooks.

## Layout

```text
events/
├── README.md
├── envelope/v1/
│   └── event-envelope.avsc   — mandatory wire envelope
├── genealogy/
│   ├── v1/
│   │   ├── tree-created.avsc
│   │   ├── tree-visibility-changed.avsc
│   │   ├── person-created.avsc
│   │   └── person-updated.avsc
└── shared/
    └── v1/
        ├── identifiers.avsc — opaque ids + tenant pseudonymous id
        └── visibility.avsc  — PRIVATE / UNLISTED / PUBLIC enum
```

## Topic naming

`<domain>.<aggregate>.<version>.v{n>` per ADR-E0.5-08, e.g.
`genealogy.person.v1.v1`. Partition key = `tenantId + aggregateId`
when ordering across aggregates is required; `aggregateId` only
otherwise.

## Retention / replay

| Class              | Retention | Compaction | Replay            |
| ------------------ | --------- | ---------- | ----------------- |
| Domain event       | 30 days   | No         | Replay via outbox |
| Projection rebuild | 7 days    | No         | Manual            |
| Audit              | 365 days  | No         | Restricted replay |
| DLQ                | 14 days   | No         | Manual triage     |

## Compatibility policy

Compatibility policy = `BACKWARD` for domain events, `FORWARD` for
command intents, `FULL` for shared enums. The CI gate enforces this
with `scripts/lint-events.mjs` (which wraps `apicurio-cli` when
present and otherwise runs a structural Avro check).

## Privacy contract

The event payload **must not** contain:

- raw DNA / genotype data,
- file content / media bytes,
- access tokens / refresh tokens,
- PII not strictly required by the consumer (per `design.md` §7.3).

`scripts/lint-events.mjs` enforces a forbidden-field list (the same
list as the OpenAPI `forbidden-properties` rule in
`config/spectral.yaml`).
