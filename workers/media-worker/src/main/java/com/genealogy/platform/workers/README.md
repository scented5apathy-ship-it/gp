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

The empty `com.genealogy.platform.workers` package inside this
worker is the cross-worker boundary marker. The `media-worker`
host runs ClamAV, libvips, ImageMagick, FFmpeg, Tika, Tesseract and
Gotenberg in a sandboxed namespace per ADR-E0.5-11 and the media
ownership row in `ownership-catalog.md` §2.5.
