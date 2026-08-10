# platform/local/openfga — local-dev README
#
# The local docker-compose stack mounts the model-registry
# ConfigMap at `/etc/openfga/models/` on the `gp-openfga`
# container; the bootstrap script (./bootstrap.sh) uploads the
# model via `openfga model write` and writes the default-role
# tuples via `openfga tuple write`. Both calls are idempotent
# — re-running the bootstrap script is a no-op when the model
# and tuples are unchanged.

## Bring-up

```
docker compose -f platform/local/docker-compose.yml up -d openfga
./platform/local/openfga/bootstrap.sh
```

The bootstrap script exits 0 on success. The `gp-openfga`
container exposes admin `:8080` (model/tuple Admin API) and
check `:8081` (gRPC `Check` / `ListObjects`).
