#!/usr/bin/env node
/**
 * scripts/lint-public-api.mjs
 *
 * E9.5 deep validator for the public-API policy contract under
 * `contracts/importexport/public-api-policy.yaml` and the platform
 * mirror under
 * `platform/helm/genealogy-platform/files/public-api-policy.yaml`.
 *
 * Closed-set vocabularies: publicApiResources[8], publicApiMethods[3],
 * publicApiScopes[7], publicApiIdempotencyKeys[4],
 * publicApiQuotaBuckets[5], publicApiRateLimitOutcomes[6],
 * publicApiVersionStates[5], publicApiFailureReasons[20],
 * publicApiAuditEvents[12], publicApiQuotaMetrics[6]; sandbox
 * egress allowlist; 1 state matrix (publicApiTokenStateMatrix
 * initial=ISSUED); 21 boolean guards; 20 numeric bounds;
 * invariants (per-IP per-hour ≥ 16×per-minute, per-client per-hour
 * ≥ 41×per-minute, token heartbeat multiplier, quota heartbeat
 * multiplier); outbox envelope; audit hooks; forbidden payload
 * patterns; capability boundaries; chart mirror byte-equality.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { loadYaml, asArray, assertClosedSet, assertStateMatrix } from "./lint-yaml.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(ROOT, "contracts/importexport/public-api-policy.yaml");
const CHART_FILE = join(ROOT, "platform/helm/genealogy-platform/files/public-api-policy.yaml");

const REQUIRED_RESOURCES = ["PUBLIC_PERSON", "PUBLIC_EVENT", "PUBLIC_PLACE", "PUBLIC_SOURCE", "PUBLIC_CITATION", "PUBLIC_MEDIA", "PUBLIC_TREE", "PUBLIC_ALBUM"];
const REQUIRED_METHODS = ["GET", "HEAD", "OPTIONS"];
const REQUIRED_SCOPES = ["public.read.basic", "public.read.living", "public.read.media", "public.read.tree", "public.read.album", "public.write.token", "admin.read.abuse"];
const REQUIRED_IDEMPOTENCY = ["REQUIRED_FOR_WRITE", "OPTIONAL_FOR_READ", "REQUIRED_FOR_TOKEN_REDEEM", "OPTIONAL_FOR_HEALTH"];
const REQUIRED_QUOTA_BUCKETS = ["PER_IP_PER_MINUTE", "PER_IP_PER_HOUR", "PER_OAUTH_CLIENT_PER_MINUTE", "PER_OAUTH_CLIENT_PER_HOUR", "PER_TOKEN_PER_HOUR"];
const REQUIRED_RATE_OUTCOMES = ["ALLOW", "SOFT_LIMIT", "HARD_LIMIT", "QUOTA_EXHAUSTED", "TOKEN_REDEEMED", "TOKEN_REVOKED"];
const REQUIRED_VERSION_STATES = ["CURRENT", "PREVIOUS", "DEPRECATED", "SUNSET", "RETIRED"];
const REQUIRED_FAILURE_REASONS = ["PUBLIC_API_RESOURCE_UNKNOWN", "PUBLIC_API_METHOD_FORBIDDEN", "PUBLIC_API_VERSION_UNKNOWN", "PUBLIC_API_VERSION_DEPRECATED", "PUBLIC_API_VERSION_SUNSET", "PUBLIC_API_VERSION_RETIRED", "PUBLIC_API_SCOPE_MISSING", "PUBLIC_API_SCOPE_INSUFFICIENT", "PUBLIC_API_IDEMPOTENCY_KEY_MISSING", "PUBLIC_API_IDEMPOTENCY_KEY_REUSED_CONFLICT", "PUBLIC_API_IDEMPOTENCY_KEY_EXPIRED", "PUBLIC_API_RATE_LIMIT_EXCEEDED", "PUBLIC_API_QUOTA_EXCEEDED", "PUBLIC_API_TOKEN_INVALID", "PUBLIC_API_TOKEN_REVOKED", "PUBLIC_API_TOKEN_REDEEMED_TWICE", "PUBLIC_API_DNA_BUCKET_FORBIDDEN", "PUBLIC_API_PII_LEAK_DETECTED", "PUBLIC_API_TENANT_MISMATCH", "PUBLIC_API_ABUSE_SIGNAL_DETECTED"];
const REQUIRED_AUDIT_EVENTS = ["PUBLIC_API_REQUEST_RECEIVED", "PUBLIC_API_REQUEST_ALLOWED", "PUBLIC_API_REQUEST_DENIED", "PUBLIC_API_RATE_LIMIT_BREACHED", "PUBLIC_API_QUOTA_BREACHED", "PUBLIC_API_TOKEN_REDEEMED", "PUBLIC_API_TOKEN_REVOKED", "PUBLIC_API_VERSION_NEGOTIATED", "PUBLIC_API_IDEMPOTENCY_KEY_REUSED", "PUBLIC_API_DNA_BUCKET_REFUSED", "PUBLIC_API_PII_LEAK_REFUSED", "PUBLIC_API_ABUSE_SIGNAL_RECORDED"];
const REQUIRED_QUOTA_METRICS = ["REQUESTS_PER_MINUTE", "REQUESTS_PER_HOUR", "BYTES_PER_MINUTE", "ERRORS_PER_MINUTE", "TOKEN_REDEMPTIONS_PER_HOUR", "ABUSE_SIGNALS_PER_HOUR"];
const REQUIRED_SANDBOX_EGRESS = ["postgres", "apicurio", "vault-agent", "openfga", "audit-service", "kafka-broker", "temporal-frontend"];
const REQUIRED_DNA_BUCKET_PREFIXES = ["dna/raw", "dna/match", "dna/consent"];
const REQUIRED_OUTBOX_FIELDS = ["eventId", "eventType", "occurredAt", "tenantId", "aggregateId", "aggregateVersion", "traceId", "payload"];
const REQUIRED_OUTBOX_TYPES = ["gp.importexport.v1.PublicApiRequestAllowed", "gp.importexport.v1.PublicApiQuotaBreached"];
const REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS = ["rawDnaSequence", "rawFastq", "rawBam", "rawVcf", "exifGps", "cameraSerial", "passportNumber", "socialSecurityNumber", "nameOnBirth", "rawEmail", "rawPhone", "rawAddress", "biometricTemplate", "rawFacialEmbedding", "rawLivingStatus", "rawMinorStatus", "rawConsentDocument", "rawSocialSecurityNumber", "rawPassport", "rawDriverLicense", "rawTaxId", "rawMedicalRecord", "rawPaymentInstrument", "productionPii"];
const REQUIRED_CAPABILITY_FORBIDDEN = ["Custom OAuth/OIDC server (Keycloak per E3.1)", "Custom rate limiter (Kong per E2.2)", "Custom quota database (Kong + OTel metrics are enough)", "Custom reverse proxy / API gateway (Kong is enough)", "Cross-service aggregation (use Kafka events + publisher resolution)", "Custom OpenAPI generator (re-use the platform generator)"];

const violations = [];
const ok = (m) => console.log(`OK  ${m}`);
const fail = (m) => { violations.push(m); console.error(`FAIL ${m}`); };

function main() {
  let contract;
  try { contract = loadYaml(readFileSync(CONTRACT, "utf8")); } catch (err) { fail(`could not read contract: ${err.message}`); process.exit(2); }
  if (!contract || typeof contract !== "object") { fail("contract empty"); process.exit(2); }

  assertClosedSet("publicApiResources", REQUIRED_RESOURCES, asArray(contract.publicApiResources?.values), undefined, ok, fail);
  assertClosedSet("publicApiMethods", REQUIRED_METHODS, asArray(contract.publicApiMethods?.values), undefined, ok, fail);
  assertClosedSet("publicApiScopes", REQUIRED_SCOPES, asArray(contract.publicApiScopes?.values), undefined, ok, fail);
  assertClosedSet("publicApiIdempotencyKeys", REQUIRED_IDEMPOTENCY, asArray(contract.publicApiIdempotencyKeys?.values), undefined, ok, fail);
  assertClosedSet("publicApiQuotaBuckets", REQUIRED_QUOTA_BUCKETS, asArray(contract.publicApiQuotaBuckets?.values), undefined, ok, fail);
  assertClosedSet("publicApiRateLimitOutcomes", REQUIRED_RATE_OUTCOMES, asArray(contract.publicApiRateLimitOutcomes?.values), undefined, ok, fail);
  assertClosedSet("publicApiVersionStates", REQUIRED_VERSION_STATES, asArray(contract.publicApiVersionStates?.values), undefined, ok, fail);
  assertClosedSet("publicApiFailureReasons", REQUIRED_FAILURE_REASONS, asArray(contract.publicApiFailureReasons?.values), undefined, ok, fail);
  assertClosedSet("publicApiAuditEvents", REQUIRED_AUDIT_EVENTS, asArray(contract.publicApiAuditEvents?.values), undefined, ok, fail);
  assertClosedSet("publicApiQuotaMetrics", REQUIRED_QUOTA_METRICS, asArray(contract.publicApiQuotaMetrics?.values), undefined, ok, fail);
  assertClosedSet("sandboxEgressAllowlist", REQUIRED_SANDBOX_EGRESS, asArray(contract.sandboxEgressAllowlist?.values), "sandbox egress allowlist", ok, fail);

  assertStateMatrix("publicApiTokenStateMatrix", contract.publicApiTokenStateMatrix, ["ISSUED", "ACTIVE", "REDEEMED", "EXPIRED", "REVOKED"], "ISSUED", ok, fail);

  const gr = contract.guardRails || {};
  for (const [k, v] of [["kongEdgeRoutingRequired", true], ["kongEdgeAuthRequired", true], ["kongEdgeRateLimitRequired", true], ["kongEdgeCorrelationIdRequired", true], ["openapiContractRequired", true], ["idempotencyKeyRequiredForWrites", true], ["idempotencyKeyReplaySafe", true], ["oauthScopeStrictMapping", true], ["scopeMustMapToOpenFga", true], ["domainAuthorizationInService", true], ["tenantBoundaryOnEveryRepository", true], ["outboxRelaySeparated", true], ["crossServiceReferencesAreOpaque", true], ["crossServiceReferencesRequirePublisherResolution", true], ["publicProjectionOnly", true], ["abuseSignalDetectionRequired", true], ["contractCompatibilityChecked", true], ["quotaMetricsExported", true], ["adrRequiredBeforeExternalIdp", true]]) {
    if (gr[k] !== v) fail(`guardRails.${k} MUST be ${v} (got ${gr[k]})`); else ok(`guardRails.${k} = ${v}`);
  }
  if (gr.dnaBucketAccess !== "FORBIDDEN") fail(`guardRails.dnaBucketAccess MUST equal FORBIDDEN (got ${gr.dnaBucketAccess})`); else ok("guardRails.dnaBucketAccess = FORBIDDEN");
  assertClosedSet("guardRails.dnaBucketPrefixes", REQUIRED_DNA_BUCKET_PREFIXES, asArray(gr.dnaBucketPrefixes), "DNA bucket prefixes", ok, fail);

  const nb = contract.numericBounds || {};
  const expected = { publicApiMaxPayloadBytes: 1048576, publicApiMaxHeaderBytes: 8192, publicApiMaxQueryBytes: 4096, publicApiIdempotencyKeyLength: 128, publicApiIdempotencyKeyTtlSeconds: 86400, publicApiVersionSunsetSeconds: 31536000, publicApiVersionRetiredSeconds: 63072000, publicApiPerIpRequestsPerMinute: 60, publicApiPerIpRequestsPerHour: 1000, publicApiPerClientRequestsPerMinute: 120, publicApiPerClientRequestsPerHour: 5000, publicApiPerTokenRequestsPerHour: 500, publicApiTokensPerClientPerHour: 100, publicApiHealthHeartbeatSeconds: 30, publicApiQuotaHeartbeatSeconds: 60, publicApiTokenTtlSeconds: 86400, publicApiTokenRevocationPropagationSeconds: 30, publicApiCorrelationIdLength: 128, publicApiActorPseudoIdLength: 64, publicApiTenantPseudoIdLength: 64, publicApiClientIdLength: 64, publicApiResourceMaxDepth: 8 };
  for (const [k, v] of Object.entries(expected)) if (nb[k] !== v) fail(`numericBounds.${k} MUST equal ${v} (got ${nb[k]})`); else ok(`numericBounds.${k} = ${v}`);

  const ri = contract.reconciliationInvariants || {};
  const inv = { publicApiPerIpRequestsPerMinute: 60, publicApiPerIpRequestsPerHour: 1000, publicApiPerIpPerHourToMinuteRatio: 16, publicApiPerClientRequestsPerMinute: 120, publicApiPerClientRequestsPerHour: 5000, publicApiPerClientPerHourToMinuteRatio: 41, publicApiPerTokenRequestsPerHour: 500, publicApiTokensPerClientPerHour: 100, publicApiTokenHeartbeatMultiplier: 30, publicApiQuotaHeartbeatMultiplier: 60 };
  for (const [k, v] of Object.entries(inv)) if (ri[k] !== v) fail(`reconciliationInvariants.${k} MUST equal ${v} (got ${ri[k]})`); else ok(`reconciliationInvariants.${k} = ${v}`);
  if (nb.publicApiPerIpRequestsPerHour < ri.publicApiPerIpPerHourToMinuteRatio * nb.publicApiPerIpRequestsPerMinute) fail(`per-ip invariant violated: per-hour=${nb.publicApiPerIpRequestsPerHour} MUST be >= ${ri.publicApiPerIpPerHourToMinuteRatio} × per-minute=${nb.publicApiPerIpRequestsPerMinute}`);
  else ok(`per-ip invariant: per-hour=${nb.publicApiPerIpRequestsPerHour} >= ${ri.publicApiPerIpPerHourToMinuteRatio} × ${nb.publicApiPerIpRequestsPerMinute}`);
  if (nb.publicApiPerClientRequestsPerHour < ri.publicApiPerClientPerHourToMinuteRatio * nb.publicApiPerClientRequestsPerMinute) fail(`per-client invariant violated: per-hour=${nb.publicApiPerClientRequestsPerHour} MUST be >= ${ri.publicApiPerClientPerHourToMinuteRatio} × per-minute=${nb.publicApiPerClientRequestsPerMinute}`);
  else ok(`per-client invariant: per-hour=${nb.publicApiPerClientRequestsPerHour} >= ${ri.publicApiPerClientPerHourToMinuteRatio} × ${nb.publicApiPerClientRequestsPerMinute}`);

  const outbox = asArray(contract.outboxEvents?.items);
  if (outbox.length === 0) fail("outboxEvents.items MUST declare at least one event");
  else {
    const declared = new Set();
    for (const evt of outbox) {
      if (!evt?.type) { fail(`invalid outbox entry ${JSON.stringify(evt)}`); continue; }
      declared.add(evt.type);
      for (const req of REQUIRED_OUTBOX_FIELDS) if (!asArray(evt.envelopeFields).includes(req)) fail(`outboxEvents.items[${evt.type}] MUST declare envelope field '${req}'`);
      ok(`outboxEvents.items[${evt.type}] envelope fields ok`);
    }
    for (const req of REQUIRED_OUTBOX_TYPES) if (!declared.has(req)) fail(`outboxEvents.items missing required event type '${req}'`); else ok(`outboxEvents.items has ${req}`);
  }

  assertClosedSet("auditHooks.auditRequired", REQUIRED_AUDIT_EVENTS, asArray(contract.auditHooks?.auditRequired), "auditHooks.auditRequired", ok, fail);
  assertClosedSet("forbiddenPayloadPatterns", REQUIRED_FORBIDDEN_PAYLOAD_PATTERNS, asArray(contract.forbiddenPayloadPatterns), "forbidden payload patterns", ok, fail);
  assertClosedSet("capabilityBoundaries.forbiddenSelfBuilt", REQUIRED_CAPABILITY_FORBIDDEN, asArray(contract.capabilityBoundaries?.forbiddenSelfBuilt), "capability boundaries", ok, fail);

  try {
    const a = readFileSync(CONTRACT, "utf8");
    const b = readFileSync(CHART_FILE, "utf8");
    if (a !== b) fail(`chart mirror drift`); else ok(`chart mirror byte-equal (${a.length} bytes)`);
  } catch (err) { fail(`chart mirror check failed: ${err.message}`); }

  if (violations.length > 0) { console.error(`\n${violations.length} violation(s).`); process.exit(1); }
  console.log("\nE9.5 public API policy contract OK.");
}

main();