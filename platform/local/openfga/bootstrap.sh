#!/bin/sh
# platform/local/openfga/bootstrap.sh
#
# Local OpenFGA bootstrap — uploads `model.v1.json` and writes
# the default-role tuples. Mirrors the `openfga-bootstrap`
# Helm-hook Job (`platform/helm/genealogy-platform/templates/
# components/openfga/bootstrap-job.yaml`) but in shell + the
# `openfga/openfga:1.10` CLI container.
#
# Idempotent — re-running is a no-op when the model + tuples
# are unchanged.

set -eu

OPENFGA_API_URL="${OPENFGA_API_URL:-http://localhost:8080}"
MODEL_PATH="${MODEL_PATH:-/etc/openfga/models/model.v1.json}"
TUPLES_PATH="${TUPLES_PATH:-/etc/openfga/bootstrap/bootstrap-tuples.json}"

# Wait for OpenFGA health.
for attempt in $(seq 1 30); do
  if curl -sf "${OPENFGA_API_URL}/healthz" > /dev/null; then
    break
  fi
  sleep 1
done

# Upload the model. The `openfga model write` CLI is idempotent
# (it returns the existing authorization_model_id if the
# uploaded model is byte-identical).
openfga model write --server-url="${OPENFGA_API_URL}" --file="${MODEL_PATH}"

# Write the default-role tuples.
openfga tuple write --server-url="${OPENFGA_API_URL}" --file="${TUPLES_PATH}"

echo "openfga-bootstrap complete"
