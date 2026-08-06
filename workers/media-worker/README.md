# workers/media-worker

Temporal worker for media processing. Per `ownership-catalog.md`
§2.5 and `design.md` §11 this worker is owned by `media-service`
and orchestrates the quarantine pipeline:

- ClamAV malware scan.
- libvips/ImageMagick transform.
- FFmpeg/ffprobe transcode.
- Tika metadata extraction.
- Tesseract OCR (language packs pinned per
  `architecture-decisions.md` §16 #11).
- Gotenberg PDF/preview.

Worker constraints per `design.md` §11:

- Non-root, read-only root filesystem.
- No egress outside the declared allow-list (Istio + NetworkPolicy).
- Pinned tool/container versions scanned in CI.
- Deterministic, idempotent object keys.

Owner: Media team. Runbook: `runbook/media-pipeline.md`.