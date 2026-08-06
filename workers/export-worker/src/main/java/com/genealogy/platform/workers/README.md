# workers/{export-worker,media-worker,search-indexer}

Placeholder marker for the Java worker packages. Each worker is a
Temporal worker process per `design.md` §7.4 / §11 ("Worker là
Temporal worker theo task queue/capability và scale độc lập") and
per `ownership-catalog.md` §2 it lives outside the synchronous
request path so its `n_sync` cap is higher (4 / chain 5 per
`ownership-catalog.md` §4.2).

| Worker            | Owns                                                                                                            | Source service         |
| ----------------- | --------------------------------------------------------------------------------------------------------------- | ---------------------- |
| `export-worker`   | GEDCOM/CSV/PDF export activities, chunked transfer saga, mapping evaluation.                                    | `import-export-service`|
| `media-worker`    | ClamAV scan, libvips/ImageMagick transform, FFmpeg transcode, Tika metadata, Tesseract OCR, Gotenberg PDF jobs. | `media-service`        |
| `search-indexer`  | Reads `gp.<domain>.v1.*` events from Kafka inbox, rebuilds `SearchDocument` projections and public projection.   | `search-service`       |

Each worker:

- Runs non-root, read-only root filesystem, no egress outside the
  declared allow-list (Istio + NetworkPolicy).
- Uses pinned tool/container versions scanned in CI per
  `design.md` §12 ("Version của tool/container được pin và scan
  trong CI").
- Emits deterministic, idempotent object keys and uses idempotency
  keys on every Temporal activity.
- Wires the platform starters from `libs/platform-{errors,
  feature-flags, security, telemetry}`.

The empty `com.genealogy.platform.workers` package inside each
worker is the cross-worker boundary marker (mirrors the same
pattern used inside `services/<svc>/`). Real package roots will be
created when each worker lands its first activity.
