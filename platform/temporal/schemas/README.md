# Workflow contract schemas — placeholder.

The `schemas/` subdirectory is reserved for the protobuf / Avro /
JSON-Schema files that describe the inputs and outputs of every
Temporal workflow and activity. Each workflow MUST publish its
contract here; the umbrella chart's `templates/components/contract-stubs.yaml`
renders a `temporal-contract-stub` ConfigMap that ships these schemas
to the worker pods at startup.

Contract requirements (per `tasks.md` E2.4 + ADR-E0.5-07):

1. Every workflow declares its input / output schema as Protobuf
   (`workflows/<name>/input.proto`, `workflows/<name>/output.proto`).
2. Every activity declares a single Protobuf message for its
   arguments and a single one for its result. Activities MUST NOT
   accept `Map<String, Object>` / untyped JSON in production — the
   Protobuf boundary is the deterministic-input contract.
3. Schemas are versioned via the `package` directive (`v1`, `v2`,
   ...). A breaking change bumps the version; the previous
   generation stays registered for one release train.
4. Search attributes are referenced by NAME only in the contract.
   The whitelist lives in `../search-attrs.yaml`; the linter rejects
   any workflow that tries to insert a non-whitelisted attribute.

Until the first workflow lands (E7.2 / E9.1) this directory is
intentionally empty. The contract stub template below expects at
least one schema file before `helm template` succeeds:

```text
schemas/
├── workflows/
│   ├── genealogy/
│   │   ├── TreeMergeWorkflow.proto
│   │   └── PersonUpsertWorkflow.proto
│   ├── media/
│   │   ├── AssetScanWorkflow.proto
│   │   └── DerivativeProduceWorkflow.proto
│   ├── search/
│   │   └── ProjectionRebuildWorkflow.proto
│   ├── interop/
│   │   ├── GedcomImportWorkflow.proto
│   │   └── ExportBundleWorkflow.proto
│   ├── notify/
│   │   └── NotificationDispatchWorkflow.proto
│   ├── report/
│   │   └── ReportGenWorkflow.proto
│   └── dna/
│       ├── DnaMatchWorkflow.proto
│       └── DnaRevokeWorkflow.proto
└── README.md
```

These files will be created when the corresponding epics land
(E7.2 = media scan / derive, E9.1 = import / export, E9.4 =
export bundle, E11.1 = notify, E11.3 = report, E10.4 = DNA match,
E10.5 = DNA revoke). The contract stub ConfigMap is wired into
the umbrella chart but renders an empty file until then.
