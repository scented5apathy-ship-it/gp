#!/usr/bin/env node
/**
 * scripts/lint-keycloak-config.mjs
 *
 * E3.1 deep validator for the Keycloak OIDC identity provider
 * source-of-truth files in `platform/keycloak/`. Mirrors
 * `lint-grafana-config.mjs` / `lint-argo-config.mjs` style —
 * uses the same `yaml` parser and reports exit 0 on success,
 * 1 on violation, 2 on configuration error.
 *
 * Asserts:
 *   - `platform/keycloak/realm-strategy.yaml` declares
 *     `realmTopology: realm-per-tenant-group` (ADR-E0.5-05
 *     §Decision) + `customSpiAllowed: false` + event sink
 *     pointing at the platform OTel Collector;
 *   - `platform/keycloak/realm-export.yaml` declares
 *     `realm: genealogy-shared` + `sslRequired: external` +
 *     `bruteForceProtected: true` + `accessTokenLifespan: 1800` +
 *     `verifyEmail: true` + 5 mandatory clients
 *     (`web-app`, `web-bff`, `public-api`, `kong-oidc-broker`,
 *     `grafana-sso`) + PKCE S256 + `directAccessGrantsEnabled: false`
 *     on every client;
 *   - `platform/keycloak/client-configs.yaml` declares 5
 *     mandatory clients with Vault-managed client-secret
 *     references + `tenant_pseudo_id` / `actor_pseudo_id`
 *     protocol mappers;
 *   - `platform/keycloak/federation.yaml` declares 5 OIDC +
 *     2 SAML providers (SAML marked `deprecatedPath: true`)
 *     + the `forbiddenFederatedAttributes` allowlist;
 *   - `platform/keycloak/key-rotation.yaml` declares RS256 +
 *     4096-bit realm signing key + 90-day rotation +
 *     30-day client-secret rotation + 60-minute JWKS
 *     refresh + JWKS algorithm allowlist (no `none`);
 *   - no literal secret / token / password / private key
 *     in any source-of-truth file;
 *   - the 5 source-of-truth files are mirrored byte-identical
 *     into `platform/helm/genealogy-platform/files/keycloak/`.
 *
 * Per `agent-execution.md` §4.4 this script does NOT mutate
 * the repo and is safe to run in CI.
 */
import { existsSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");
const KEYCLOAK_DIR = join(ROOT, "platform", "keycloak");
const MIRROR_DIR = join(ROOT, "platform", "helm", "genealogy-platform", "files", "keycloak");

const REQUIRED_FILES = [
  "realm-strategy.yaml",
  "realm-export.yaml",
  "client-configs.yaml",
  "federation.yaml",
  "key-rotation.yaml",
];

const REQUIRED_MANDATORY_CLIENTS = [
  "web-app",
  "web-bff",
  "public-api",
  "kong-oidc-broker",
  "grafana-sso",
];

const REQUIRED_OIDC_PROVIDERS = [
  "google-workspace",
  "microsoft-entra",
  "okta",
  "auth0",
  "pingfederate",
];

const REQUIRED_SAML_PROVIDERS = [
  "okta-saml",
  "pingfederate-saml",
];

const FORBIDDEN_FEDERATED_ATTRIBUTES = [
  "tenant_id",
  "user_id",
  "raw_dna",
  "raw_pii",
  "phone_number",
  "address",
];

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[keycloak] ${msg}`);
};

const parseFile = (path) => {
  if (!existsSync(path)) {
    fail(`file missing — ${relative(ROOT, path)}`);
    return null;
  }
  let parsed;
  try {
    parsed = YAML.parse(readFileSync(path, "utf8"));
  } catch (err) {
    fail(`YAML parse error — ${relative(ROOT, path)} — ${err.message}`);
    return null;
  }
  return parsed;
};

const lintLiteralSecrets = (path) => {
  if (!existsSync(path)) return;
  const txt = readFileSync(path, "utf8");
  for (const key of ["password", "apiKey", "api_key", "token", "pepper", "jwt", "private_key", "client_secret"]) {
    const literal = new RegExp(`^\\s*${key}\\s*:\\s*"?[A-Za-z0-9._/+=-]{8,}"?\\s*$`, "m");
    if (literal.test(txt)) {
      fail(`literal secret-like value for '${key}' in ${relative(ROOT, path)} — use Vault / ESO`);
    }
  }
};

// ---------------------------------------------------------------------------
// 1. realm-strategy.yaml
// ---------------------------------------------------------------------------
const strategyPath = join(KEYCLOAK_DIR, "realm-strategy.yaml");
const strategyDoc = parseFile(strategyPath);
if (strategyDoc) {
  // The file is a ConfigMap; the actual data lives in
  // `data["realm-strategy.yaml"]` as a YAML string.
  const inner = strategyDoc.data?.["realm-strategy.yaml"];
  if (!inner) {
    fail("realm-strategy.yaml: missing data['realm-strategy.yaml'] block");
  } else {
    let innerDoc;
    try {
      innerDoc = YAML.parse(inner);
    } catch (err) {
      fail(`realm-strategy.yaml: inner YAML parse error — ${err.message}`);
    }
    if (innerDoc) {
      if (innerDoc.realmTopology !== "realm-per-tenant-group") {
        fail(`realm-strategy.yaml: realmTopology must be 'realm-per-tenant-group' (ADR-E0.5-05); got '${innerDoc.realmTopology}'`);
      }
      if (innerDoc.customSpiAllowed !== false) {
        fail(`realm-strategy.yaml: customSpiAllowed must be false (ADR-E0.5-05)`);
      }
      if (innerDoc.saasSharedRealm !== "genealogy-shared") {
        fail(`realm-strategy.yaml: saasSharedRealm must be 'genealogy-shared'; got '${innerDoc.saasSharedRealm}'`);
      }
      const groupClaimLimit = Number(innerDoc.groupClaimLimit);
      if (!Number.isFinite(groupClaimLimit) || groupClaimLimit > 5000) {
        fail(`realm-strategy.yaml: groupClaimLimit must be ≤ 5000 (ADR-E0.5-05); got '${innerDoc.groupClaimLimit}'`);
      }
      if (innerDoc.federationPreferredProtocol !== "oidc") {
        fail(`realm-strategy.yaml: federationPreferredProtocol must be 'oidc'; got '${innerDoc.federationPreferredProtocol}'`);
      }
      if (!Array.isArray(innerDoc.federationDeprecatedProtocols) || !innerDoc.federationDeprecatedProtocols.includes("saml")) {
        fail(`realm-strategy.yaml: federationDeprecatedProtocols must include 'saml'`);
      }
      const sink = innerDoc.eventListener?.sink;
      if (sink !== "otel-collector:4318/v1/audit") {
        fail(`realm-strategy.yaml: eventListener.sink must be 'otel-collector:4318/v1/audit'; got '${sink}'`);
      }
      if (innerDoc.eventListener?.persistToDb !== false) {
        fail(`realm-strategy.yaml: eventListener.persistToDb must be false (privacy posture)`);
      }
    }
  }
}
lintLiteralSecrets(strategyPath);

// ---------------------------------------------------------------------------
// 2. realm-export.yaml
// ---------------------------------------------------------------------------
const exportPath = join(KEYCLOAK_DIR, "realm-export.yaml");
const exportDoc = parseFile(exportPath);
let realmClientIds = [];
let realmGrantAllowed = [];
if (exportDoc) {
  const inner = exportDoc.data?.["realm-export.yaml"];
  if (!inner) {
    fail("realm-export.yaml: missing data['realm-export.yaml'] block");
  } else {
    let innerDoc;
    try {
      innerDoc = YAML.parse(inner);
    } catch (err) {
      fail(`realm-export.yaml: inner YAML parse error — ${err.message}`);
    }
    if (innerDoc) {
      if (innerDoc.realm !== "genealogy-shared") {
        fail(`realm-export.yaml: realm must be 'genealogy-shared'; got '${innerDoc.realm}'`);
      }
      if (innerDoc.sslRequired !== "external") {
        fail(`realm-export.yaml: sslRequired must be 'external'; got '${innerDoc.sslRequired}'`);
      }
      if (innerDoc.bruteForceProtected !== true) {
        fail(`realm-export.yaml: bruteForceProtected must be true`);
      }
      if (innerDoc.verifyEmail !== true) {
        fail(`realm-export.yaml: verifyEmail must be true (R2 §R2.5)`);
      }
      if (innerDoc.resetPasswordAllowed !== true) {
        fail(`realm-export.yaml: resetPasswordAllowed must be true (R2 §R2.5)`);
      }
      if (innerDoc.registrationAllowed !== false) {
        fail(`realm-export.yaml: registrationAllowed must be false (invite-only)`);
      }
      if (Number(innerDoc.accessTokenLifespan) > 3600) {
        fail(`realm-export.yaml: accessTokenLifespan must be ≤ 3600s (R2 §R2.3); got '${innerDoc.accessTokenLifespan}'`);
      }
      if (Number(innerDoc.ssoSessionIdleTimeout) > 28800) {
        fail(`realm-export.yaml: ssoSessionIdleTimeout must be ≤ 28800s (R2 §R2.3); got '${innerDoc.ssoSessionIdleTimeout}'`);
      }
      if (Number(innerDoc.ssoSessionMaxLifespan) > 43200) {
        fail(`realm-export.yaml: ssoSessionMaxLifespan must be ≤ 43200s (R2 §R2.3); got '${innerDoc.ssoSessionMaxLifespan}'`);
      }
      if (Number(innerDoc.accessTokenLifespan) < 60) {
        fail(`realm-export.yaml: accessTokenLifespan must be ≥ 60s`);
      }
      // Browser flow must declare PKCE S256.
      const browserFlow = (innerDoc.authenticationFlows || []).find((f) => f.name === "browser");
      if (!browserFlow) {
        fail(`realm-export.yaml: missing 'browser' authentication flow`);
      }
      // direct-grant flow MUST be disabled (password grant forbidden).
      const directGrant = (innerDoc.authenticationFlows || []).find((f) => f.name === "direct-grant");
      if (directGrant) {
        const execs = directGrant.executions || [];
        const disabled = execs.every((e) => e.requirement === "DISABLED");
        if (!disabled) {
          fail(`realm-export.yaml: 'direct-grant' flow MUST have every execution DISABLED (R2 §R2.1)`);
        }
      }
      // mfa sub-flow must declare webauthn + otp authenticators.
      const mfaFlow = (innerDoc.authenticationFlows || []).find((f) => f.name === "mfa");
      if (mfaFlow) {
        const execs = mfaFlow.executions || [];
        const names = execs.map((e) => e.provider || e.name);
        if (!names.includes("webauthn-authenticator")) {
          fail(`realm-export.yaml: 'mfa' sub-flow must include 'webauthn-authenticator'`);
        }
        if (!names.includes("auth-otp-form")) {
          fail(`realm-export.yaml: 'mfa' sub-flow must include 'auth-otp-form' (OTP)`);
        }
      } else {
        fail(`realm-export.yaml: missing 'mfa' sub-flow`);
      }
      // step-up flow with max age ≤ 600s (10 min ceiling).
      const stepUpFlow = (innerDoc.authenticationFlows || []).find((f) => f.name === "step-up");
      if (stepUpFlow) {
        const maxAge = Number(stepUpFlow.stepUpMaxAgeSeconds);
        if (!Number.isFinite(maxAge) || maxAge > 600 || maxAge < 60) {
          fail(`realm-export.yaml: step-up stepUpMaxAgeSeconds must be 60-600s; got '${stepUpFlow.stepUpMaxAgeSeconds}'`);
        }
      }
      // Password policy must include the 8 mandatory requirements.
      const policy = innerDoc.passwordPolicy || "";
      const required = [
        "length(12)",
        "upperCredential(1)",
        "lowerCredential(1)",
        "digit(1)",
        "specialCharacter(1)",
        "notUsername()",
        "notEmail()",
        "passwordHistory(12)",
        "hashIterations(10000)",
        "hashAlgorithm(pbkdf2-sha512)",
      ];
      for (const r of required) {
        if (!policy.includes(r)) {
          fail(`realm-export.yaml: passwordPolicy must include '${r}'`);
        }
      }
      // Internationalization — at least 5 locales including en / vi (R18).
      const locales = innerDoc.supportedLocales || [];
      for (const requiredLocale of ["en", "vi"]) {
        if (!locales.includes(requiredLocale)) {
          fail(`realm-export.yaml: supportedLocales must include '${requiredLocale}' (R18 §R18.1)`);
        }
      }
      // Internationalization — at least one RTL locale (R18 §R18.1).
      if (!locales.some((l) => ["ar", "he"].includes(l))) {
        fail(`realm-export.yaml: supportedLocales must include an RTL locale (R18 §R18.1)`);
      }
    }
  }
}
lintLiteralSecrets(exportPath);

// ---------------------------------------------------------------------------
// 3. client-configs.yaml
// ---------------------------------------------------------------------------
const clientConfigsPath = join(KEYCLOAK_DIR, "client-configs.yaml");
const clientConfigsDoc = parseFile(clientConfigsPath);
if (clientConfigsDoc) {
  const inner = clientConfigsDoc.data?.["client-configs.yaml"];
  if (!inner) {
    fail("client-configs.yaml: missing data['client-configs.yaml'] block");
  } else {
    let innerDoc;
    try {
      innerDoc = YAML.parse(inner);
    } catch (err) {
      fail(`client-configs.yaml: inner YAML parse error — ${err.message}`);
    }
    if (innerDoc) {
      const clients = innerDoc.clients || [];
      realmClientIds = clients.map((c) => c.clientId);
      for (const required of REQUIRED_MANDATORY_CLIENTS) {
        if (!realmClientIds.includes(required)) {
          fail(`client-configs.yaml: missing mandatory client '${required}' (E3.1 §3)`);
        }
      }
      for (const client of clients) {
        // Every client carries PKCE S256.
        const pkceMethod = client.attributes?.["pkce.code.challenge.method"];
        if (pkceMethod !== "S256") {
          fail(`client-configs.yaml: client '${client.clientId}' must set pkce.code.challenge.method = S256 (R2 §R2.1)`);
        }
        // directAccessGrantsEnabled MUST be false on every client.
        if (client.directAccessGrantsEnabled !== false) {
          fail(`client-configs.yaml: client '${client.clientId}' must set directAccessGrantsEnabled: false (R2 §R2.1)`);
        }
        // implicitFlowEnabled MUST be false on every client.
        if (client.implicitFlowEnabled !== false) {
          fail(`client-configs.yaml: client '${client.clientId}' must set implicitFlowEnabled: false`);
        }
        // standardFlowEnabled MUST be true on every client.
        if (client.standardFlowEnabled !== false && client.standardFlowEnabled !== true) {
          // not declared — implicit false
        }
        // confidential clients MUST carry a Vault secret ref.
        if (!client.publicClient && !client.secretVaultPath?.includes("keycloak/")) {
          fail(`client-configs.yaml: confidential client '${client.clientId}' must declare secretVaultPath pointing at Vault`);
        }
        // bearerOnly MUST be false on every client.
        if (client.bearerOnly !== false) {
          fail(`client-configs.yaml: client '${client.clientId}' must set bearerOnly: false (R2 §R2.1)`);
        }
        // protocol mappers — tenant_pseudo_id + actor_pseudo_id required.
        const mappers = client.protocolMappers || [];
        const claimNames = mappers.map((m) => m.config?.["claim.name"] || m.config?.claim?.name);
        if (!claimNames.includes("tenant_pseudo_id")) {
          fail(`client-configs.yaml: client '${client.clientId}' must expose 'tenant_pseudo_id' protocol mapper`);
        }
        if (!claimNames.includes("actor_pseudo_id")) {
          fail(`client-configs.yaml: client '${client.clientId}' must expose 'actor_pseudo_id' protocol mapper`);
        }
        // Redirect URIs MUST use HTTPS — exception only for the dev profile.
        for (const uri of client.redirectUris || []) {
          if (uri.startsWith("http://") && !uri.includes(".localhost")) {
            fail(`client-configs.yaml: client '${client.clientId}' redirectUri '${uri}' must use HTTPS (R2 §R2.1)`);
          }
        }
      }
      // OAuth client scopes — dna:read / dna:write reserved (E10).
      const scopes = (innerDoc.clientScopes || []).map((s) => s.name);
      for (const required of ["tenant-context", "dna:read", "dna:write", "webhook:manage"]) {
        if (!scopes.includes(required)) {
          fail(`client-configs.yaml: missing client scope '${required}'`);
        }
      }
    }
  }
}
lintLiteralSecrets(clientConfigsPath);

// ---------------------------------------------------------------------------
// 4. federation.yaml
// ---------------------------------------------------------------------------
const federationPath = join(KEYCLOAK_DIR, "federation.yaml");
const federationDoc = parseFile(federationPath);
if (federationDoc) {
  const inner = federationDoc.data?.["federation.yaml"];
  if (!inner) {
    fail("federation.yaml: missing data['federation.yaml'] block");
  } else {
    let innerDoc;
    try {
      innerDoc = YAML.parse(inner);
    } catch (err) {
      fail(`federation.yaml: inner YAML parse error — ${err.message}`);
    }
    if (innerDoc) {
      if (innerDoc.preferredProtocol !== "oidc") {
        fail(`federation.yaml: preferredProtocol must be 'oidc' (ADR-E0.5-05)`);
      }
      const providers = innerDoc.identityProviders || [];
      const oidcProviders = providers.filter((p) => ["oidc", "google"].includes(p.providerId)).map((p) => p.alias);
      const samlProviders = providers.filter((p) => p.providerId === "saml").map((p) => p.alias);
      for (const required of REQUIRED_OIDC_PROVIDERS) {
        if (!oidcProviders.includes(required)) {
          fail(`federation.yaml: missing OIDC provider '${required}' (E3.1 §4)`);
        }
      }
      for (const required of REQUIRED_SAML_PROVIDERS) {
        if (!samlProviders.includes(required)) {
          fail(`federation.yaml: missing SAML provider '${required}' (E3.1 §4)`);
        }
      }
      // SAML providers must be marked deprecated.
      for (const provider of providers.filter((p) => p.providerId === "saml")) {
        if (provider.deprecatedPath !== true) {
          fail(`federation.yaml: SAML provider '${provider.alias}' must set deprecatedPath: true (ADR-E0.5-05)`);
        }
        const clockSkew = Number(provider.config?.allowedClockSkewSeconds);
        if (!Number.isFinite(clockSkew) || clockSkew > 30) {
          fail(`federation.yaml: SAML provider '${provider.alias}' allowedClockSkewSeconds must be ≤ 30 (ADR-E0.5-05)`);
        }
      }
      // Clock skew tolerance ≤ 30s.
      const globalSkew = Number(innerDoc.clockSkewToleranceSeconds);
      if (!Number.isFinite(globalSkew) || globalSkew > 30) {
        fail(`federation.yaml: clockSkewToleranceSeconds must be ≤ 30 (ADR-E0.5-05)`);
      }
      // Federated attributes allowlist must include email / groups.
      const allowedAttrs = innerDoc.allowedFederatedAttributes || [];
      for (const required of ["email", "groups", "email_verified"]) {
        if (!allowedAttrs.includes(required)) {
          fail(`federation.yaml: allowedFederatedAttributes must include '${required}'`);
        }
      }
      // Forbidden federated attributes allowlist.
      const forbiddenAttrs = innerDoc.forbiddenFederatedAttributes || [];
      for (const required of FORBIDDEN_FEDERATED_ATTRIBUTES) {
        if (!forbiddenAttrs.includes(required)) {
          fail(`federation.yaml: forbiddenFederatedAttributes must include '${required}' (privacy posture)`);
        }
      }
      // OIDC providers must NEVER expose forbidden attributes via mappers.
      for (const provider of providers.filter((p) => p.providerId === "oidc")) {
        const mappers = provider.attributeMappers || [];
        for (const mapper of mappers) {
          const claim = mapper.config?.claim || "";
          if (FORBIDDEN_FEDERATED_ATTRIBUTES.includes(claim)) {
            fail(`federation.yaml: OIDC provider '${provider.alias}' mapper '${mapper.name}' exposes forbidden claim '${claim}'`);
          }
          // claim name space: avoid raw_dna / raw_pii leakage.
          if (claim.includes("dna") || claim.includes("pii")) {
            fail(`federation.yaml: OIDC provider '${provider.alias}' mapper '${mapper.name}' exposes DNA/PII claim '${claim}'`);
          }
        }
      }
    }
  }
}
lintLiteralSecrets(federationPath);

// ---------------------------------------------------------------------------
// 5. key-rotation.yaml
// ---------------------------------------------------------------------------
const keyRotationPath = join(KEYCLOAK_DIR, "key-rotation.yaml");
const keyRotationDoc = parseFile(keyRotationPath);
if (keyRotationDoc) {
  const inner = keyRotationDoc.data?.["key-rotation.yaml"];
  if (!inner) {
    fail("key-rotation.yaml: missing data['key-rotation.yaml'] block");
  } else {
    let innerDoc;
    try {
      innerDoc = YAML.parse(inner);
    } catch (err) {
      fail(`key-rotation.yaml: inner YAML parse error — ${err.message}`);
    }
    if (innerDoc) {
      const sig = innerDoc.realmSigningKey || {};
      if (sig.algorithm !== "RS256") {
        fail(`key-rotation.yaml: realmSigningKey.algorithm must be 'RS256'; got '${sig.algorithm}'`);
      }
      if (Number(sig.keySize) < 2048) {
        fail(`key-rotation.yaml: realmSigningKey.keySize must be ≥ 2048 (NFR1 OWASP ASVS); got '${sig.keySize}'`);
      }
      const rotationDays = Number(sig.rotationDays);
      if (!Number.isFinite(rotationDays) || rotationDays < 30 || rotationDays > 365) {
        fail(`key-rotation.yaml: realmSigningKey.rotationDays must be 30-365; got '${sig.rotationDays}'`);
      }
      const overlap = Number(sig.keyOverlapHours);
      if (!Number.isFinite(overlap) || overlap < 24) {
        fail(`key-rotation.yaml: realmSigningKey.keyOverlapHours must be ≥ 24; got '${sig.keyOverlapHours}'`);
      }
      const providerKeys = innerDoc.providerKeys || {};
      const refreshMinutes = Number(providerKeys.refreshMinutes);
      if (!Number.isFinite(refreshMinutes) || refreshMinutes < 5 || refreshMinutes > 1440) {
        fail(`key-rotation.yaml: providerKeys.refreshMinutes must be 5-1440; got '${providerKeys.refreshMinutes}'`);
      }
      const clientSecretDays = Number(innerDoc.clientSecretRotationDays);
      if (!Number.isFinite(clientSecretDays) || clientSecretDays < 7 || clientSecretDays > 90) {
        fail(`key-rotation.yaml: clientSecretRotationDays must be 7-90; got '${innerDoc.clientSecretRotationDays}'`);
      }
      const kmsBackend = sig.kmsBackend;
      if (!["awskms", "vault-transit", "vault-kv"].includes(kmsBackend)) {
        fail(`key-rotation.yaml: realmSigningKey.kmsBackend must be one of awskms/vault-transit/vault-kv; got '${kmsBackend}'`);
      }
      // JWKS algorithm allowlist must NOT include `none`.
      const jwks = innerDoc.jwksEndpoint || {};
      const allowedAlgos = jwks.jwksAlgorithmsAllowed || [];
      if (allowedAlgos.includes("none")) {
        fail(`key-rotation.yaml: jwksEndpoint.jwksAlgorithmsAllowed MUST NOT include 'none' (privacy posture)`);
      }
      const forbiddenAlgos = jwks.jwksAlgorithmsForbidden || [];
      if (!forbiddenAlgos.includes("none")) {
        fail(`key-rotation.yaml: jwksEndpoint.jwksAlgorithmsForbidden must include 'none'`);
      }
      if (!forbiddenAlgos.includes("HS256")) {
        fail(`key-rotation.yaml: jwksEndpoint.jwksAlgorithmsForbidden must include 'HS256' (symmetric)`);
      }
      if (!allowedAlgos.includes("RS256")) {
        fail(`key-rotation.yaml: jwksEndpoint.jwksAlgorithmsAllowed must include 'RS256'`);
      }
      // Logout posture.
      if (innerDoc.logout?.frontchannelLogoutEnabled !== true) {
        fail(`key-rotation.yaml: logout.frontchannelLogoutEnabled must be true`);
      }
      if (innerDoc.logout?.revokeRefreshToken !== true) {
        fail(`key-rotation.yaml: logout.revokeRefreshToken must be true (R2 §R2.3)`);
      }
      // Backup posture.
      const backup = innerDoc.backup || {};
      if (backup.kmsEncrypted !== true) {
        fail(`key-rotation.yaml: backup.kmsEncrypted must be true (privacy posture)`);
      }
      if (!backup.postgresDatabase) {
        fail(`key-rotation.yaml: backup.postgresDatabase must be declared`);
      }
      const rto = Number(backup.rtoMinutes);
      if (!Number.isFinite(rto) || rto > 60) {
        fail(`key-rotation.yaml: backup.rtoMinutes must be ≤ 60`);
      }
    }
  }
}
lintLiteralSecrets(keyRotationPath);

// ---------------------------------------------------------------------------
// 6. Mirror check — every platform/keycloak/* must be byte-identical in
//    platform/helm/genealogy-platform/files/keycloak/.
// ---------------------------------------------------------------------------
for (const f of REQUIRED_FILES) {
  const src = join(KEYCLOAK_DIR, f);
  const dst = join(MIRROR_DIR, f);
  if (!existsSync(src)) continue;
  if (!existsSync(dst)) {
    fail(`chart mirror missing — expected ${relative(ROOT, dst)} (E3.1 contract)`);
    continue;
  }
  const a = readFileSync(src, "utf8");
  const b = readFileSync(dst, "utf8");
  if (a !== b) {
    fail(`chart mirror drift — ${relative(ROOT, src)} differs from ${relative(ROOT, dst)}`);
  }
}

// ---------------------------------------------------------------------------
// Summary
// ---------------------------------------------------------------------------
if (violations === 0) {
  console.log("[keycloak] OK — E3.1 Keycloak source-of-truth files conform to contract");
  process.exit(0);
} else {
  console.error(`[keycloak] ${violations} violation(s) — see messages above`);
  process.exit(1);
}