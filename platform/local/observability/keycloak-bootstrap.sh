#!/bin/sh
# Local Keycloak bootstrap — equivalent of the umbrella chart's
# `keycloak-bootstrap` Helm hook Job. Verifies the five
# source-of-truth files are byte-identical between
# `platform/keycloak/` and `platform/helm/genealogy-platform/files/keycloak/`,
# and that the realm name matches the canonical SaaS shared realm.
#
# Per `tasks.md` E3.1 the local stack never runs the Admin REST
# API bootstrap (it ships the realm as JSON import instead);
# this script is the structural-only check that mirrors the
# production contract.

set -eu

SOURCE_DIR=${SOURCE_DIR:-/source}
MIRROR_DIR=${MIRROR_DIR:-/mirror}
REALM_NAME=${REALM_NAME:-genealogy-shared}

REQUIRED_FILES="
realm-strategy.yaml
realm-export.yaml
client-configs.yaml
federation.yaml
key-rotation.yaml
"

failures=0

for f in $REQUIRED_FILES; do
  if [ ! -f "$SOURCE_DIR/$f" ]; then
    echo "[keycloak-bootstrap] FAIL — source-of-truth missing: $f"
    failures=$((failures + 1))
    continue
  fi
  if [ ! -f "$MIRROR_DIR/$f" ]; then
    echo "[keycloak-bootstrap] FAIL — mirror missing: $f"
    failures=$((failures + 1))
    continue
  fi
  if ! cmp -s "$SOURCE_DIR/$f" "$MIRROR_DIR/$f"; then
    echo "[keycloak-bootstrap] FAIL — mirror drift: $f"
    failures=$((failures + 1))
    continue
  fi
  echo "[keycloak-bootstrap] PASS — $f byte-identical"
done

# Realm export must declare the canonical SaaS shared realm
# name. The Keycloak `--import-realm` flag uses this field.
if grep -q "^realm: ${REALM_NAME}" "$SOURCE_DIR/realm-export.yaml"; then
  echo "[keycloak-bootstrap] PASS — realm name = $REALM_NAME"
else
  echo "[keycloak-bootstrap] FAIL — realm-export.yaml must declare realm: $REALM_NAME"
  failures=$((failures + 1))
fi

# Realm strategy must declare realm-per-tenant-group default.
if grep -q "^realmTopology: realm-per-tenant-group" "$SOURCE_DIR/realm-strategy.yaml"; then
  echo "[keycloak-bootstrap] PASS — realmTopology = realm-per-tenant-group"
else
  echo "[keycloak-bootstrap] FAIL — realm-strategy.yaml must declare realmTopology: realm-per-tenant-group"
  failures=$((failures + 1))
fi

# Realm strategy must NOT allow custom SPI providers.
if grep -q "^customSpiAllowed: false" "$SOURCE_DIR/realm-strategy.yaml"; then
  echo "[keycloak-bootstrap] PASS — customSpiAllowed = false"
else
  echo "[keycloak-bootstrap] FAIL — realm-strategy.yaml must declare customSpiAllowed: false"
  failures=$((failures + 1))
fi

# Federation must mark SAML as deprecated.
if grep -q "^deprecatedPath: true" "$SOURCE_DIR/federation.yaml"; then
  echo "[keycloak-bootstrap] PASS — SAML providers marked deprecated"
else
  echo "[keycloak-bootstrap] FAIL — federation.yaml must mark SAML providers deprecated"
  failures=$((failures + 1))
fi

# Key rotation must declare RS256 + 4096-bit realm signing key.
if grep -q "^      algorithm: RS256" "$SOURCE_DIR/key-rotation.yaml" \
   && grep -q "^      keySize: 4096" "$SOURCE_DIR/key-rotation.yaml"; then
  echo "[keycloak-bootstrap] PASS — realm signing key = RS256 4096-bit"
else
  echo "[keycloak-bootstrap] FAIL — key-rotation.yaml must declare RS256 + 4096-bit"
  failures=$((failures + 1))
fi

# JWKS algorithm allowlist must NOT include `none`.
if grep -q "^        - none" "$SOURCE_DIR/key-rotation.yaml"; then
  echo "[keycloak-bootstrap] FAIL — JWKS algorithm 'none' MUST NOT be allowed"
  failures=$((failures + 1))
else
  echo "[keycloak-bootstrap] PASS — JWKS algorithm 'none' forbidden"
fi

echo ""
echo "[keycloak-bootstrap] $((5 - failures)) passed, $failures failed"

if [ "$failures" -gt 0 ]; then
  exit 1
fi

exit 0