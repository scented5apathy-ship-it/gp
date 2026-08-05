# Protobuf contracts

Internal gRPC contracts. The consumers are Spring Boot services
(`services/*`), the Temporal workers (`workers/*`) and the BFF
(`apps/web-bff`).

## Layout

```text
protobuf/
├── README.md
├── buf.yaml                 — buf module + lint + breaking config
├── buf.gen.yaml             — code generation config (Java + TS)
├── common/
│   └── v1/
│       ├── context.proto    — trusted tenant / correlation metadata
│       ├── page.proto       — cursor pagination envelope
│       └── error.proto      — gRPC error mapping for RFC 9457
├── tenant/v1/
│   └── tenant_service.proto
├── genealogy/
│   └── v1/
│       ├── tree_service.proto
│       └── person_service.proto
└── search/v1/
    └── search_service.proto
```

## Versioning

- Every package is `<area>.<domain>.v{N}` (e.g. `com.genealogy.platform.genealogy.v1`).
- Field numbers are **never** reused once assigned. Removing a field is
  done by renaming to `reserved` (see `tenant_service.proto` for the
  example block).
- New RPCs / messages go into `v{N+1}`, never `v{N}`.
- The CI gate runs `buf breaking --against .contracts/protobuf/main`
  against the snapshot stored in `.contracts/protobuf/main` (the
  previous-release tag, populated by E1.6 once releases begin).

## Mandatory shapes

Every RPC:

- accepts a `Context` (tenant + actor + correlation) as the first field
  on the request message (see `common/v1/context.proto`),
- returns a `Page` envelope for list operations,
- accepts `base_version` for mutations on versioned aggregates,
- accepts `idempotency_key` for non-idempotent mutations,
- has explicit `google.api.Method` annotations only when needed (auth,
  visibility); defaults are fine for v1.

## Lint + breaking gates

- `scripts/lint-protobuf.mjs` — runs `buf lint` if `buf` is installed,
  falls back to a structural check otherwise (kept in E1.2 for
  environments without `buf`).
- `pnpm test:contract` — runs the full buf + Spectral + Apicurio
  contract suite in CI.
