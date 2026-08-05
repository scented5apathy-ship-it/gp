# OpenAPI contracts

External REST surface. The canonical consumers are:

- `apps/public-api` — public + partner REST API exposed behind Kong.
- `apps/web-bff` — internal BFF used by `apps/web` (Next.js PWA).

## Layout

```text
openapi/
├── README.md                — this file
├── common/                  — shared parameters, headers, schemas
│   ├── headers.yaml         — RFC 9457, cursor, ETag, idempotency, correlation
│   ├── problem-details.yaml — shared `application/problem+json` envelope
│   └── pagination.yaml      — cursor pagination envelope
├── public-api/              — per-domain public REST specs (versioned)
│   └── v1/
│       ├── tenant.yaml
│       ├── tree.yaml
│       ├── person.yaml
│       └── events.yaml
└── bff/                     — BFF internal REST (not exposed publicly)
    └── v1/
        └── session.yaml
```

A single domain must be defined in a single file. Multiple files only
to keep line counts manageable; cross-file `$ref` is resolved by
Spectral.

## Versioning

- `info.version` MUST be `MAJOR.MINOR.PATCH` (semver).
- `info.x-contract-major` is the integer used in the URI prefix
  (`/api/v{info.x-contract-major}/...`).
- The CI gate runs `oasdiff` against the previous tagged snapshot; any
  breaking change increments `x-contract-major` and ships in a new
  `/api/v{new}/...` URI.

## Mandatory shapes

Every operation:

- has `operationId` (kebab-case),
- has `summary` (single sentence),
- documents `Idempotency-Key` header for non-GET mutations,
- documents `If-Match` header for mutations on versioned resources,
- returns `application/problem+json` for `4xx`/`5xx` via the shared
  `Problem` schema in `common/problem-details.yaml`,
- exposes cursor pagination for any list response.

See `scripts/lint-openapi.mjs` + `config/spectral.yaml` for the
machine-checkable ruleset.

## Forbidden properties

`config/spectral.yaml` rejects these properties at the schema level
across every contract: `dnaRaw`, `rawGenotype`, `dna`, `kit`, `rawDna`,
`raw_dna`. DNA is the responsibility of `services/dna-service`; no
other surface may even mention the field.
