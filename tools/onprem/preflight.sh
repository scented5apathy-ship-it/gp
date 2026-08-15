#!/usr/bin/env bash
# tools/onprem/preflight.sh
#
# E14.3 — On-prem bundle preflight. Mirrors
# `contracts/disaster-recovery/onprem-bundle-policy.yaml`.
#
# Exit codes:
 0 — every check passed.
 1 — at least one check failed; see log on stderr.
 2 — preconditions invalid (kubectl / openssl not installed).

set -euo pipefail

log() { printf '[preflight] %s\n' "$*"; }
fail() { log "FAIL: $*"; exit 1; }

command -v kubectl >/dev/null 2>&1 \
  || { log "kubectl not installed"; exit 2; }
command -v openssl >/dev/null 2>&1 \
  || { log "openssl not installed"; exit 2; }

# 1. Kubernetes version (1.29..1.32)
log "checking kubernetes version"
KUBE_MINOR="$(kubectl version --short 2>/dev/null \
  | awk '/Server Version/ {print $3}' \
  | awk -F. '{print $2}')"
if [ -z "$KUBE_MINOR" ]; then
  fail "could not detect Kubernetes server version"
fi
if [ "$KUBE_MINOR" -lt 29 ] || [ "$KUBE_MINOR" -gt 32 ]; then
  fail "Kubernetes minor version $KUBE_MINOR outside 1.29..1.32"
fi

# 2. Storage class (CSI provisioner)
log "checking storage class"
if ! kubectl get sc -o json \
  | grep -q '"provisioner"[[:space:]]*:[[:space:]]*"[^"]*csi'; then
  fail "no CSI storage class found"
fi

# 3. DNS resolution
log "checking dns resolution"
if ! kubectl run -it --rm --restart=Never \
  --image=registry.k8s.io/e2e-test-images/jessie-dns:1.7 \
  dns-test -- nslookup kubernetes.default >/dev/null 2>&1; then
  fail "DNS resolution failed"
fi

# 4. Certificate validity (>= 30 days)
log "checking certificate validity"
if ! openssl x509 -checkend 2592000 \
  -in /etc/ssl/certs/ca-certificates.crt -noout >/dev/null 2>&1; then
  fail "certificate expires within 30 days"
fi

# 5. CPU capacity >= 16 vCPU
log "checking cpu capacity"
CPU_MILLICORES="$(kubectl get nodes -o json \
  | awk '/allocatable/ {flag=1; next} flag' \
  | grep -oE '"cpu"[[:space:]]*:[[:space:]]*"[0-9]+' \
  | grep -oE '[0-9]+$' \
  | awk '{s+=$1} END {print s}')"
if [ -z "$CPU_MILLICORES" ] || [ "$CPU_MILLICORES" -lt 16000 ]; then
  fail "CPU capacity $CPU_MILLICORES < 16000 millicores"
fi

# 6. Memory capacity >= 64 GiB
log "checking memory capacity"
MEM_KIB="$(kubectl get nodes -o json \
  | awk '/allocatable/ {flag=1; next} flag' \
  | grep -oE '"memory"[[:space:]]*:[[:space:]]*"[0-9]+' \
  | grep -oE '[0-9]+$' \
  | awk '{s+=$1} END {print s}')"
if [ -z "$MEM_KIB" ] || [ "$MEM_KIB" -lt 67108864 ]; then
  fail "memory capacity $MEM_KIB < 67108864 KiB"
fi

# 7. External dependency reachability
log "checking external dependency reachability"
for host in \
  "${GP_TENANT_DATABASE_HOST:-}" \
  "${GP_KAFKA_BOOTSTRAP:-}" \
  "${GP_OBJECT_STORE_ENDPOINT:-}" \
  "${GP_KEYCLOAK_ISSUER:-}" \
  "${GP_OPENFGA_API_URL:-}" \
  "${GP_TEMPORAL_FRONTEND_URL:-}" \
  "${GP_VAULT_ADDRESS:-}" \
  "${GP_FLAGSMITH_API_URL:-}"; do
  [ -z "$host" ] && continue
  if ! getent hosts "$host" >/dev/null 2>&1 \
    && ! curl --silent --fail --max-time 5 "https://$host" \
      >/dev/null 2>&1; then
    fail "external dependency unreachable: $host"
  fi
done

log "all preflight checks passed"
exit 0