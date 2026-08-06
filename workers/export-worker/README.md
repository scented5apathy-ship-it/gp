# workers/export-worker

Temporal worker for GEDCOM/CSV/PDF export activities. Per
`ownership-catalog.md` §2.7 this worker is owned by
`import-export-service` and lives outside the synchronous request
path (`n_sync ≤ 4` per `ownership-catalog.md` §4.2). It runs the
chunked transfer saga, mapping evaluation and the export bundle
assembly defined in `design.md` §8.4.

Owner: Interop team. Runbook: `runbook/import-export-service.md`
(workflow-specific section).

The empty `com.genealogy.platform.workers` package marker is the
cross-worker boundary marker added in the previous step.