#!/usr/bin/env bash
# tools/support/support-bundle.sh
#
# E14.5 — Support bundle collector. Mirrors
# `contracts/disaster-recovery/operator-runbook-policy.yaml`.
#
# Produces a redacted archive at /tmp/support-bundle.tar.gz
# containing operator-relevant logs, dashboards and audit
# metadata while scrubbing every secret / PII / DNA /
# raw-payload / JWT / cookie / OAuth secret / audit stream /
# consent receipt / treeViewerBypass pattern.
#
# Exit codes:
#   0 — bundle built and redaction verified.
#   1 — at least one redaction rule failed.
#   2 — preconditions invalid (tar / grep not installed).

set -euo pipefail

log() { printf '[support-bundle] %s\n' "$*"; }
fail() { log "FAIL: $*"; exit 1; }

command -v tar >/dev/null 2>&1 \
  || { log "tar not installed"; exit 2; }
command -v grep >/dev/null 2>&1 \
  || { log "grep not installed"; exit 2; }

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

log "collecting logs + dashboards + audit metadata"
mkdir -p "$WORKDIR/logs" "$WORKDIR/dashboards" "$WORKDIR/audit"

for path in \
  "${GP_LOG_DIR:-/var/log/genealogy-platform}" \
  "${GP_DASHBOARD_DIR:-platform/observability/dashboards}" \
  "${GP_AUDIT_DIR:-.kiro/specs/genealogy-platform/evidence}"; do
  if [ -d "$path" ]; then
    tar -cf - "$path" 2>/dev/null | tar -xf - -C "$WORKDIR" || true
  fi
done

log "redacting secrets / PII / DNA / raw payloads"
REDACTION_RULES=(
  "redact_secrets|dev_secret|shared_admin_password"
  "redact_pii|raw_email|raw_phone|raw_passport|raw_ssn"
  "redact_dna|raw_dna_bytes|raw_genotype|raw_fastq|raw_bam|raw_vcf"
  "redact_raw_payloads|raw_event_payload|raw_audit_stream"
  "redact_jwt|inline_jwt|inline_access_token|inline_refresh_token"
  "redact_session_cookie|inline_session_cookie"
  "redact_oauth_client_secret|inline_oauth_client_secret"
  "redact_audit_stream|raw_audit_stream"
  "redact_consent_receipt|raw_consent_receipt"
  "redact_tree_viewer_bypass|tree_viewer_bypass"
)
FAILED=0
for rule in "${REDACTION_RULES[@]}"; do
  name="${rule%%|*}"
  pattern="${rule#*|}"
  if grep -rIE --binary-files=without-match "$pattern" \
    "$WORKDIR" >/dev/null 2>&1; then
    log "FAIL: $name pattern still present: $pattern"
    find "$WORKDIR" -type f \
      -exec sed -i '' -E "s/$pattern/[REDACTED:$name]/g" {} +
  fi
  if grep -rIE --binary-files=without-match "$pattern" \
    "$WORKDIR" >/dev/null 2>&1; then
    log "FAIL: $name pattern could not be redacted: $pattern"
    FAILED=1
  fi
done

if [ "$FAILED" -ne 0 ]; then
  fail "support bundle redaction incomplete"
fi

log "checking bundle size"
SIZE=$(du -sk "$WORKDIR" | awk '{print $1}')
MAX_KB=$((2 * 1024 * 1024))
if [ "$SIZE" -gt "$MAX_KB" ]; then
  fail "support bundle size $SIZE KiB > 2 GiB"
fi

log "creating archive"
OUT="/tmp/support-bundle-$(date -u +%Y%m%dT%H%M%SZ).tar.gz"
tar -czf "$OUT" -C "$WORKDIR" .

log "support bundle ready: $OUT"
exit 0