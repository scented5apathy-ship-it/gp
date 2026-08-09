#!/bin/sh
# E2.10 — grafana-bootstrap one-shot.
#
# Verifies the 6 source-of-truth files in `platform/grafana/`
# are byte-identical to the mirror in
# `platform/helm/genealogy-platform/files/grafana/`. Exits 0
# on success, 1 on drift (per the deep linter contract).
set -euo pipefail
SRC="${SOURCE_DIR:-/source}"
DST="${MIRROR_DIR:-/mirror}"
echo "[grafana-bootstrap] verifying byte-identity mirror"
for f in otel-collector.yaml prometheus.yaml loki.yaml tempo.yaml dashboards.yaml grafana.yaml; do
  if [ ! -f "${SRC}/${f}" ]; then
    echo "FAIL: source missing ${SRC}/${f}"
    exit 1
  fi
  if [ ! -f "${DST}/${f}" ]; then
    echo "FAIL: mirror missing ${DST}/${f}"
    exit 1
  fi
  if ! cmp -s "${SRC}/${f}" "${DST}/${f}"; then
    echo "FAIL: mirror drift on ${f}"
    exit 1
  fi
  echo "OK: ${f}"
done
echo "[grafana-bootstrap] all 6 source-of-truth files mirror byte-identical"