#!/usr/bin/env bash
# tools/upgrade/simulate-upgrade.sh
#
# E14.4 — Simulate Flyway expand-contract upgrade + Argo
# Rollouts abort. Mirrors
# `contracts/disaster-recovery/recovery-rollback-policy.yaml`.
#
# Exit codes:
#   0 — every step passed; upgrade may proceed.
#   1 — at least one step failed; see log on stderr.
#   2 — preconditions invalid (mvn / yq not installed).

set -euo pipefail

log() { printf '[simulate-upgrade] %s\n' "$*"; }
fail() { log "FAIL: $*"; exit 1; }

SOURCE_VERSION="${GP_SOURCE_VERSION:-2026.02}"
TARGET_VERSION="${GP_TARGET_VERSION:-2026.06}"

command -v mvn >/dev/null 2>&1 \
  || { log "mvn not installed"; exit 2; }
command -v yq >/dev/null 2>&1 \
  || { log "yq not installed"; exit 2; }

log "validating source + target version"
case "$SOURCE_VERSION" in
  2025.10|2025.12|2026.02|2026.04|2026.06) ;;
  *) fail "unsupported source version: $SOURCE_VERSION" ;;
esac
case "$TARGET_VERSION" in
  2025.10|2025.12|2026.02|2026.04|2026.06) ;;
  *) fail "unsupported target version: $TARGET_VERSION" ;;
esac
if [ "$SOURCE_VERSION" = "$TARGET_VERSION" ]; then
  fail "source and target are identical: $SOURCE_VERSION"
fi

log "checking Flyway migrations for destructive operations"
MIGRATION_DIR="${GP_MIGRATION_DIR:-services/tenant-service/src/main/resources/db/migration}"
if [ -d "$MIGRATION_DIR" ]; then
  if grep -rE "(DROP[[:space:]]+(TABLE|COLUMN|INDEX)|TRUNCATE|ALTER[[:space:]]+TABLE[[:space:]]+[^[:space:]]+[[:space:]]+DROP)" \
    "$MIGRATION_DIR/V*_*.sql" 2>/dev/null | grep -v "deprecated_drop_followup"; then
    fail "destructive migration detected in $MIGRATION_DIR"
  fi
fi

log "checking Argo Rollouts abort rules wiring"
ABORT_RULES="platform/argocd/canary/abort-rules.yaml"
if [ ! -f "$ABORT_RULES" ]; then
  fail "abort rules file missing: $ABORT_RULES"
fi
for rule in five_xx_ratio_exceeded p95_latency_regression \
  error_rate_spike privacy_finding_detected; do
  if ! grep -q "$rule" "$ABORT_RULES"; then
    fail "abort rule missing in $ABORT_RULES: $rule"
  fi
done

log "checking schema + event compatibility"
SCHEMA_REGISTRY="${GP_SCHEMA_REGISTRY:-contracts/events}"
if [ -d "$SCHEMA_REGISTRY" ]; then
  for f in "$SCHEMA_REGISTRY"/*.avsc; do
    [ -f "$f" ] || continue
    compat="$(yq -r '.compatibility' "$f" 2>/dev/null || echo none)"
    case "$compat" in
      BACKWARD|BACKWARD_TRANSITIVE|FULL) ;;
      NONE_BREAKING_SUPERSEDED_BY_ADR) ;;
      *) fail "schema $f has unsupported compat: $compat" ;;
    esac
  done
fi

log "all simulation checks passed"
exit 0