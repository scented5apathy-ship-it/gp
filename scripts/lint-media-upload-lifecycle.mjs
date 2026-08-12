#!/usr/bin/env node
/**
 * scripts/lint-media-upload-lifecycle.mjs
 *
 * E7.1 deep validator for the upload lifecycle policy
 * contract under
 * `contracts/media/upload-lifecycle-policy.yaml` and the
 * platform mirror under
 * `platform/helm/genealogy-platform/files/media-upload-lifecycle-policy.yaml`.
 *
 * Mirrors the structure of
 * `lint-collaboration-comments-activity.mjs` (E6.4):
 *   - parse + structural assertions on the closed-set
 *     vocabularies (uploadSessionStatuses,
 *     uploadSessionIntents, mediaCategories, mimeVerdicts,
 *     checksumAlgorithms, finalizeOutcomes,
 *     quotaDenialReasons, uploadGuardDenyReasons,
 *     abandonedMultipartReasons, quotaUnits);
 *   - MIME policy guard rails (mimeAllowList, mimeDenyList,
 *     mimeSandboxRequired, mimeDeepScanRequired,
 *     dnaBucketAccess, dnaBucketPrefixes,
 *     dnaSensitiveMimeHints);
 *   - quota + finalize + signed-URL guard rails
 *     (finalizeIdempotentOnChecksum, signedUrlPerPart,
 *     signedUrlReAuthorizationOnReIssue,
 *     finalizeReAuthorizationRequired,
 *     multipartPartReAuthorizationRequired,
 *     uploadSessionIntentNeverRoutesToDnaBucket,
 *     signedUrlMethod, signedUrlRequiresContentType,
 *     signedUrlRequiresMaxSize);
 *   - upload / quota / MIME numeric bounds;
 *   - audit hooks + re-authorization toggles + invariant
 *     reason codes;
 *   - forbidden-literal / forbidden-payload scan;
 *   - chart mirror byte-equality.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration
 * error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, relative, resolve } from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(ROOT, "contracts/media/upload-lifecycle-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/media-upload-lifecycle-policy.yaml",
);

const REQUIRED_UPLOAD_SESSION_STATUSES = [
  "REQUESTED",
  "SIGNED",
  "UPLOADING",
  "FINALIZING",
  "QUARANTINED",
  "READY",
  "REJECTED",
  "ABANDONED",
  "FAILED",
];

const REQUIRED_UPLOAD_SESSION_INTENTS = [
  "ATTACHMENT",
  "ALBUM",
  "PROFILE",
  "TREE_MEDIA",
  "DOCUMENT_THUMBNAIL",
  "OCR_INPUT",
  "DELIVERY_THUMBNAIL",
];

const REQUIRED_MEDIA_CATEGORIES = [
  "IMAGE",
  "AUDIO",
  "VIDEO",
  "DOCUMENT",
  "PDF",
  "SVG",
  "ARCHIVE",
  "DNA_FASTQ",
];

const REQUIRED_MIME_VERDICTS = ["ALLOW", "DENY", "SANDBOX_REQUIRED", "DEEP_SCAN_REQUIRED"];

const REQUIRED_CHECKSUM_ALGORITHMS = ["SHA256", "SHA512", "BLAKE3"];

const REQUIRED_FINALIZE_OUTCOMES = ["READY", "REJECTED", "QUARANTINED", "FAILED"];

const REQUIRED_QUOTA_DENIAL_REASONS = [
  "QUOTA_EXCEEDED_BYTES",
  "QUOTA_EXCEEDED_COUNT",
  "QUOTA_EXCEEDED_SESSION_TTL",
  "QUOTA_SCOPE_NOT_PERMITTED",
  "QUOTA_TENANT_HEADROOM_INSUFFICIENT",
];

const REQUIRED_UPLOAD_GUARD_DENY_REASONS = [
  "MIME_NOT_PERMITTED",
  "CHECKSUM_MISMATCH",
  "DECLARED_SIZE_MISMATCH",
  "MULTIPART_PART_NUMBER_INVALID",
  "MULTIPART_PART_COUNT_OVERFLOW",
  "MULTIPART_PART_SEQUENCE_GAP",
  "SESSION_NOT_OWNED_BY_CALLER",
  "SESSION_ABANDONED",
  "SESSION_ALREADY_FINALIZED",
  "RATE_LIMITED",
  "PAYLOAD_DNA_BUCKET_FORBIDDEN",
];

const REQUIRED_ABANDONED_MULTIPART_REASONS = [
  "SESSION_TTL_EXPIRED",
  "CALLER_ABORTED_FINALIZE",
  "NO_PART_RECEIVED_IN_TTL",
  "CHECKSUM_FINALIZE_TIMEOUT",
  "QUOTA_REVOKED_MID_FLIGHT",
];

const REQUIRED_QUOTA_UNITS = ["BYTES", "ITEMS", "SECONDS"];

const REQUIRED_MEDIA_CATEGORY_KEYS = [
  "IMAGE",
  "AUDIO",
  "VIDEO",
  "DOCUMENT",
  "PDF",
  "SVG",
  "ARCHIVE",
  "DNA_FASTQ",
];

const REQUIRED_AUDIT_KEYS = ["actorPseudoId", "correlationId", "targetPseudoId"];

const REQUIRED_INVARIANTS = [
  "UPLOAD_SESSION_BLANK_REQUESTER",
  "UPLOAD_SESSION_BLANK_TENANT",
  "UPLOAD_SESSION_BLANK_INTENT",
  "UPLOAD_SESSION_INTENT_NOT_PERMITTED",
  "UPLOAD_SESSION_BLANK_MEDIA_CATEGORY",
  "UPLOAD_SESSION_BLANK_CHECKSUM",
  "UPLOAD_SESSION_CHECKSUM_ALGORITHM_NOT_PERMITTED",
  "UPLOAD_SESSION_BODY_BYTES_OUT_OF_RANGE",
  "UPLOAD_SESSION_TTL_OUT_OF_RANGE",
  "UPLOAD_SESSION_TOO_MANY_PER_USER",
  "UPLOAD_SESSION_TOO_MANY_PER_TENANT",
  "UPLOAD_SESSION_METADATA_KEY_FORBIDDEN",
  "UPLOAD_SESSION_METADATA_VALUE_FORBIDDEN",
  "UPLOAD_SESSION_MULTIPART_PART_COUNT_OVERFLOW",
  "UPLOAD_SESSION_MULTIPART_PART_SIZE_OUT_OF_RANGE",
  "UPLOAD_SESSION_NOT_OWNED_BY_CALLER",
  "UPLOAD_SESSION_ALREADY_FINALIZED",
  "UPLOAD_SESSION_FORBIDDEN_TRANSITION",
  "UPLOAD_SESSION_BLANK_SCOPE_ID",
  "QUOTA_EXCEEDED_BYTES",
  "QUOTA_EXCEEDED_COUNT",
  "QUOTA_EXCEEDED_SESSION_TTL",
  "QUOTA_SCOPE_NOT_PERMITTED",
  "QUOTA_TENANT_HEADROOM_INSUFFICIENT",
  "MIME_NOT_PERMITTED",
  "MIME_DENY_LISTED",
  "MIME_SNIFF_MISMATCH",
  "MIME_SANDBOX_REQUIRED",
  "MIME_DEEP_SCAN_REQUIRED",
  "MIME_SNIFF_BYTES_OVERFLOW",
  "CHECKSUM_MISMATCH",
  "CHECKSUM_ALGORITHM_NOT_PERMITTED",
  "CHECKSUM_DIGEST_LENGTH_OUT_OF_RANGE",
  "DECLARED_SIZE_MISMATCH",
  "MULTIPART_PART_NUMBER_INVALID",
  "MULTIPART_PART_NUMBER_DUPLICATE",
  "MULTIPART_PART_SEQUENCE_GAP",
  "MULTIPART_PART_SIZE_OUT_OF_RANGE",
  "MULTIPART_PART_NOT_AUTHORIZED",
  "SIGNED_URL_METHOD_FORBIDDEN",
  "SIGNED_URL_CONTENT_TYPE_FORBIDDEN",
  "SIGNED_URL_TTL_OUT_OF_RANGE",
  "SIGNED_URL_MAX_SIZE_MISSING",
  "SIGNED_URL_REAUTHORIZATION_REQUIRED",
  "ANTIMALWARE_NOT_READY",
  "QUARANTINE_GATE_FORBIDDEN_NEXT_STATE",
  "PAYLOAD_DNA_BUCKET_FORBIDDEN",
  "FINALIZE_REAUTHORIZATION_REQUIRED",
  "FINALIZE_REAUTHORIZATION_DENIED",
  "FINALIZE_REAUTHORIZATION_ABAC_DENIED",
  "ABANDONED_MULTIPART_REAP_DENIED",
  "ABANDONED_MULTIPART_BATCH_OVERFLOW",
  "AUDIT_KEY_FORBIDDEN",
];

const FORBIDDEN_LITERALS = [
  /password\s*[:=]\s*["']?[A-Za-z0-9!@#$%^&*()_+=\-]{6,}/i,
  /token\s*[:=]\s*["']?[A-Za-z0-9._\-]{20,}/i,
  /secret\s*[:=]\s*["']?[A-Za-z0-9._\-]{12,}/i,
  /jdbc:postgresql:\/\/[^"\s']+:[^"\s']+@/i,
  /AKIA[0-9A-Z]{16}/,
  /-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----/,
];

let violations = 0;

function fail(message) {
  console.error(`[media-upload-lifecycle] ${message}`);
  violations += 1;
}

function loadContract(path) {
  try {
    const raw = readFileSync(path, "utf8");
    return { raw, parsed: parseYamlLoose(raw) };
  } catch (err) {
    fail(`cannot read ${relative(ROOT, path)}: ${err.message}`);
    return null;
  }
}

function parseYamlLoose(raw) {
  const out = {};
  const stack = [{ indent: -1, value: out }];
  const lines = raw.split(/\r?\n/);
  function parseFlowList(text) {
    const inner = text.trim().slice(1, -1).trim();
    if (!inner) return [];
    return inner.split(",").map((s) => stripQuotesValue(s.trim()));
  }
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (!line.trim() || line.trim().startsWith("#")) continue;
    const indent = line.match(/^ */)[0].length;
    while (stack.length > 1 && stack[stack.length - 1].indent >= indent) {
      stack.pop();
    }
    const parentTop = stack[stack.length - 1];
    const parent = parentTop.value;
    const trimmed = line.trim();
    if (trimmed.startsWith("- ")) {
      if (!Array.isArray(parentTop.value)) continue;
      const itemRaw = trimmed.slice(2).trim();
      const sub = itemRaw.match(/^([A-Za-z0-9_.\-]+)\s*:\s*(.*)$/);
      if (sub) {
        const obj = {};
        obj[sub[1]] = stripQuotesValue(sub[2]);
        parentTop.value.push(obj);
        stack.push({ indent, value: obj });
      } else {
        parentTop.value.push(stripQuotesValue(itemRaw));
      }
      continue;
    }
    const m = trimmed.match(/^([A-Za-z0-9_.\-]+)\s*:\s*(.*)$/);
    if (!m) continue;
    const key = m[1];
    let rhs = m[2];
    if (rhs === "" || rhs === undefined) {
      const next = [];
      parent[key] = next;
      stack.push({ indent, value: next });
      continue;
    }
    if (rhs.startsWith("[") && rhs.endsWith("]")) {
      parent[key] = parseFlowList(rhs);
      continue;
    }
    if (rhs === "true") {
      parent[key] = true;
      continue;
    }
    if (rhs === "false") {
      parent[key] = false;
      continue;
    }
    const numeric = Number(rhs);
    if (!Number.isNaN(numeric) && rhs.trim() !== "") {
      parent[key] = numeric;
      continue;
    }
    if (rhs.startsWith(">") || rhs.startsWith("|")) {
      const blockIndent = indent + 2;
      const collected = [];
      let j = i + 1;
      while (j < lines.length) {
        const nl = lines[j];
        if (!nl.trim()) {
          j += 1;
          continue;
        }
        const nlIndent = nl.match(/^ */)[0].length;
        if (nlIndent < blockIndent) break;
        collected.push(nl.slice(blockIndent));
        j += 1;
      }
      parent[key] = collected.join(" ").trim();
      i = j - 1;
      continue;
    }
    parent[key] = stripQuotesValue(rhs);
  }
  return out;
}

function stripQuotesValue(v) {
  if (v === undefined || v === null) return v;
  v = v.trim();
  if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
    return v.slice(1, -1);
  }
  return v;
}

function requireField(parsed, path) {
  const parts = path.split(".");
  let cur = parsed;
  for (const p of parts) {
    if (cur === undefined || cur === null) {
      fail(`spec.${path} missing (path broken at '${p}')`);
      return undefined;
    }
    cur = cur[p];
  }
  if (cur === undefined || cur === null) {
    fail(`spec.${path} missing`);
    return undefined;
  }
  return cur;
}

function assertString(value, expected, field) {
  if (value !== expected) {
    fail(`spec.${field} must equal ${JSON.stringify(expected)}, got ${JSON.stringify(value)}`);
  }
}

function checkClosedSetField(parsed, field, required) {
  const value = requireField(parsed, field);
  if (!Array.isArray(value)) {
    fail(`spec.${field} must be an array`);
    return new Set();
  }
  const set = new Set(value);
  for (const r of required) {
    if (!set.has(r)) {
      fail(`spec.${field} missing required value '${r}'`);
    }
  }
  return set;
}

function assertInRange(value, min, max, field) {
  if (typeof value !== "number") {
    fail(`spec.${field} must be a number (got ${JSON.stringify(value)})`);
    return;
  }
  if (value < min || value > max) {
    fail(`spec.${field} must be in [${min}, ${max}] (got ${value})`);
  }
}

function assertPositiveNumber(value, field) {
  if (typeof value !== "number" || value <= 0) {
    fail(`spec.${field} must be a positive number (got ${JSON.stringify(value)})`);
  }
}

function assertNonNegativeNumber(value, field) {
  if (typeof value !== "number" || value < 0) {
    fail(`spec.${field} must be a non-negative number (got ${JSON.stringify(value)})`);
  }
}

function scanForbiddenLiterals(raw, scope) {
  for (const re of FORBIDDEN_LITERALS) {
    if (re.test(raw)) {
      fail(`forbidden literal matched ${re} in ${scope}`);
    }
  }
}

function checkContract(loaded) {
  const { raw, parsed } = loaded;
  if (!parsed || typeof parsed !== "object") {
    fail("contract parsed to empty object");
    return;
  }
  if (parsed.kind !== "MediaUploadLifecyclePolicy") {
    fail(`spec.kind must be 'MediaUploadLifecyclePolicy' (got '${parsed.kind}')`);
  }
  const spec = parsed.spec;
  if (!spec || typeof spec !== "object") {
    fail("spec must be an object");
    return;
  }

  checkClosedSetField(spec, "uploadSessionStatuses", REQUIRED_UPLOAD_SESSION_STATUSES);
  checkClosedSetField(spec, "uploadSessionIntents", REQUIRED_UPLOAD_SESSION_INTENTS);
  checkClosedSetField(spec, "mediaCategories", REQUIRED_MEDIA_CATEGORIES);
  checkClosedSetField(spec, "mimeVerdicts", REQUIRED_MIME_VERDICTS);
  checkClosedSetField(spec, "checksumAlgorithms", REQUIRED_CHECKSUM_ALGORITHMS);
  checkClosedSetField(spec, "finalizeOutcomes", REQUIRED_FINALIZE_OUTCOMES);
  checkClosedSetField(spec, "quotaDenialReasons", REQUIRED_QUOTA_DENIAL_REASONS);
  checkClosedSetField(spec, "uploadGuardDenyReasons", REQUIRED_UPLOAD_GUARD_DENY_REASONS);
  checkClosedSetField(spec, "abandonedMultipartReasons", REQUIRED_ABANDONED_MULTIPART_REASONS);
  checkClosedSetField(spec, "quotaUnits", REQUIRED_QUOTA_UNITS);

  const allowList = requireField(spec, "mimeAllowList");
  if (!allowList || typeof allowList !== "object") {
    fail("spec.mimeAllowList must be an object keyed by mediaCategory");
  } else {
    for (const k of REQUIRED_MEDIA_CATEGORY_KEYS) {
      if (!Array.isArray(allowList[k])) {
        fail(`spec.mimeAllowList missing required mediaCategory '${k}'`);
      }
    }
  }

  const denyList = requireField(spec, "mimeDenyList");
  if (!Array.isArray(denyList) || denyList.length === 0) {
    fail("spec.mimeDenyList must be a non-empty array");
  }
  const sandbox = requireField(spec, "mimeSandboxRequired");
  if (!Array.isArray(sandbox) || sandbox.length === 0) {
    fail("spec.mimeSandboxRequired must be a non-empty array");
  }
  const deepScan = requireField(spec, "mimeDeepScanRequired");
  if (!Array.isArray(deepScan) || deepScan.length === 0) {
    fail("spec.mimeDeepScanRequired must be a non-empty array");
  }

  // DNA bucket guard rails
  assertString(requireField(spec, "dnaBucketAccess"), "FORBIDDEN", "dnaBucketAccess");
  const dnaPrefixes = requireField(spec, "dnaBucketPrefixes");
  if (!Array.isArray(dnaPrefixes) || dnaPrefixes.length === 0) {
    fail("spec.dnaBucketPrefixes must be a non-empty array");
  }
  const dnaMimes = requireField(spec, "dnaSensitiveMimeHints");
  if (!Array.isArray(dnaMimes) || dnaMimes.length === 0) {
    fail("spec.dnaSensitiveMimeHints must be a non-empty array");
  }

  // Quota policy
  const quotaScopes = requireField(spec, "quotaScopes");
  if (!Array.isArray(quotaScopes) || quotaScopes.length === 0) {
    fail("spec.quotaScopes must be a non-empty array");
  }
  assertPositiveNumber(requireField(spec, "quotaHeadroomInBytes"), "quotaHeadroomInBytes");

  // Guard rails
  assertString(
    requireField(spec, "finalizeIdempotentOnChecksum"),
    true,
    "finalizeIdempotentOnChecksum",
  );
  assertString(requireField(spec, "signedUrlPerPart"), true, "signedUrlPerPart");
  assertString(
    requireField(spec, "signedUrlReAuthorizationOnReIssue"),
    true,
    "signedUrlReAuthorizationOnReIssue",
  );
  assertString(
    requireField(spec, "finalizeReAuthorizationRequired"),
    true,
    "finalizeReAuthorizationRequired",
  );
  assertString(requireField(spec, "abacDenyClosesSession"), true, "abacDenyClosesSession");
  assertString(
    requireField(spec, "multipartPartReAuthorizationRequired"),
    true,
    "multipartPartReAuthorizationRequired",
  );
  assertString(
    requireField(spec, "uploadSessionIntentNeverRoutesToDnaBucket"),
    true,
    "uploadSessionIntentNeverRoutesToDnaBucket",
  );
  assertString(requireField(spec, "signedUrlMethod"), "PUT", "signedUrlMethod");
  assertString(
    requireField(spec, "signedUrlRequiresContentType"),
    true,
    "signedUrlRequiresContentType",
  );
  assertString(requireField(spec, "signedUrlRequiresMaxSize"), true, "signedUrlRequiresMaxSize");

  // Numeric bounds
  assertInRange(
    requireField(spec, "maxUploadSessionBytes"),
    1,
    5368709120,
    "maxUploadSessionBytes",
  );
  assertInRange(
    requireField(spec, "maxUploadSessionTtlSeconds"),
    60,
    604800,
    "maxUploadSessionTtlSeconds",
  );
  assertInRange(
    requireField(spec, "minUploadSessionTtlSeconds"),
    1,
    3600,
    "minUploadSessionTtlSeconds",
  );
  assertInRange(requireField(spec, "maxMultipartPartCount"), 1, 100000, "maxMultipartPartCount");
  assertInRange(
    requireField(spec, "maxMultipartPartSizeBytes"),
    1,
    5368709120,
    "maxMultipartPartSizeBytes",
  );
  assertInRange(
    requireField(spec, "minMultipartPartSizeBytes"),
    1,
    1073741824,
    "minMultipartPartSizeBytes",
  );
  assertInRange(requireField(spec, "maxUploadSessionPerUser"), 1, 4096, "maxUploadSessionPerUser");
  assertInRange(
    requireField(spec, "maxUploadSessionPerTenant"),
    1,
    65536,
    "maxUploadSessionPerTenant",
  );
  assertInRange(
    requireField(spec, "maxUploadSessionMetadataKeys"),
    1,
    64,
    "maxUploadSessionMetadataKeys",
  );
  assertInRange(
    requireField(spec, "maxUploadSessionMetadataValueLength"),
    1,
    4096,
    "maxUploadSessionMetadataValueLength",
  );
  assertInRange(
    requireField(spec, "maxUploadSessionMetadataKeyLength"),
    1,
    256,
    "maxUploadSessionMetadataKeyLength",
  );
  assertInRange(requireField(spec, "maxUploadSessionIdLength"), 1, 256, "maxUploadSessionIdLength");
  assertInRange(requireField(spec, "maxChecksumDigestLength"), 1, 256, "maxChecksumDigestLength");
  assertInRange(requireField(spec, "maxSignedUrlHeaders"), 1, 64, "maxSignedUrlHeaders");
  assertInRange(
    requireField(spec, "maxAbandonedMultipartBatchSize"),
    1,
    65536,
    "maxAbandonedMultipartBatchSize",
  );
  assertInRange(
    requireField(spec, "maxAbandonedMultipartSweepConcurrency"),
    1,
    256,
    "maxAbandonedMultipartSweepConcurrency",
  );
  assertInRange(requireField(spec, "maxSniffBytes"), 1, 268435456, "maxSniffBytes");
  assertInRange(requireField(spec, "maxFinalizeReasonLength"), 1, 1024, "maxFinalizeReasonLength");
  assertInRange(
    requireField(spec, "maxMimeVerdictReasonLength"),
    1,
    1024,
    "maxMimeVerdictReasonLength",
  );
  assertInRange(requireField(spec, "signedUrlMaxTtlSeconds"), 1, 86400, "signedUrlMaxTtlSeconds");

  // Audit hooks
  const audit = requireField(spec, "auditRequiredKeys");
  if (!Array.isArray(audit)) {
    fail("spec.auditRequiredKeys must be an array");
  } else {
    for (const k of REQUIRED_AUDIT_KEYS) {
      if (!audit.includes(k)) {
        fail(`spec.auditRequiredKeys missing required key '${k}'`);
      }
    }
  }
  assertString(
    requireField(spec, "auditRequiredOnUploadSessionCreated"),
    true,
    "auditRequiredOnUploadSessionCreated",
  );
  assertString(
    requireField(spec, "auditRequiredOnSignedUrlIssued"),
    true,
    "auditRequiredOnSignedUrlIssued",
  );
  assertString(
    requireField(spec, "auditRequiredOnMultipartPartReceived"),
    true,
    "auditRequiredOnMultipartPartReceived",
  );
  assertString(
    requireField(spec, "auditRequiredOnFinalizeAttempted"),
    true,
    "auditRequiredOnFinalizeAttempted",
  );
  assertString(
    requireField(spec, "auditRequiredOnFinalizeCompleted"),
    true,
    "auditRequiredOnFinalizeCompleted",
  );
  assertString(
    requireField(spec, "auditRequiredOnQuarantineGateEvaluated"),
    true,
    "auditRequiredOnQuotaEvaluated",
  );
  assertString(requireField(spec, "auditRequiredOnQuotaDeny"), true, "auditRequiredOnQuotaDeny");
  assertString(
    requireField(spec, "auditRequiredOnAbandonedMultipartReaped"),
    true,
    "auditRequiredOnAbandonedMultipartReaped",
  );
  assertString(
    requireField(spec, "auditRequiredOnMimePolicyViolation"),
    true,
    "auditRequiredOnMimePolicyViolation",
  );

  // Re-authorization
  assertString(
    requireField(spec, "uploadSessionReAuthorizationRequiredOnCreate"),
    true,
    "uploadSessionReAuthorizationRequiredOnCreate",
  );
  assertString(
    requireField(spec, "uploadSessionReAuthorizationRequiredOnSignedUrlIssue"),
    true,
    "uploadSessionReAuthorizationRequiredOnSignedUrlIssue",
  );
  assertString(
    requireField(spec, "uploadSessionReAuthorizationRequiredOnMultipartPartReceipt"),
    true,
    "uploadSessionReAuthorizationRequiredOnMultipartPartReceipt",
  );
  assertString(
    requireField(spec, "uploadSessionReAuthorizationRequiredOnFinalize"),
    true,
    "uploadSessionReAuthorizationRequiredOnFinalize",
  );
  assertString(
    requireField(spec, "quotaReAuthorizationRequiredOnFinalize"),
    true,
    "quotaReAuthorizationRequiredOnFinalize",
  );
  assertString(
    requireField(spec, "quarantineGateReAuthorizationRequiredOnAdmit"),
    true,
    "quarantineGateReAuthorizationRequiredOnAdmit",
  );
  assertString(
    requireField(spec, "abandonedMultipartReAuthorizationRequiredOnReap"),
    true,
    "abandonedMultipartReAuthorizationRequiredOnReap",
  );
  assertString(
    requireField(spec, "reAuthorizationDenyClosesSession"),
    true,
    "reAuthorizationDenyClosesSession",
  );
  assertString(
    requireField(spec, "reAuthorizationAbacDenyClosesSession"),
    true,
    "reAuthorizationAbacDenyClosesSession",
  );

  // Invariants
  const invariants = requireField(spec, "invariants");
  if (!Array.isArray(invariants) || invariants.length === 0) {
    fail("spec.invariants must be a non-empty list");
  } else {
    const set = new Set(invariants);
    for (const code of REQUIRED_INVARIANTS) {
      if (!set.has(code)) {
        fail(`spec.invariants missing required code '${code}'`);
      }
    }
  }

  // Forbidden payload patterns
  const forbidden = requireField(spec, "forbiddenPayloadPatterns");
  if (!Array.isArray(forbidden) || forbidden.length === 0) {
    fail("spec.forbiddenPayloadPatterns must be a non-empty list");
  }

  scanForbiddenLiterals(raw, "media-upload-lifecycle contract");
}

function checkMirror(contractRaw) {
  let chartRaw;
  try {
    chartRaw = readFileSync(CHART_FILE, "utf8");
  } catch (err) {
    fail(`cannot read ${relative(ROOT, CHART_FILE)}: ${err.message}`);
    return;
  }
  if (chartRaw !== contractRaw) {
    fail(`chart mirror ${relative(ROOT, CHART_FILE)} drifted from contract`);
  }
}

function main() {
  const loaded = loadContract(CONTRACT);
  if (!loaded) {
    process.exit(2);
  }
  checkContract(loaded);
  checkMirror(loaded.raw);
  if (violations === 0) {
    console.log("[media-upload-lifecycle] OK");
    process.exit(0);
  }
  console.error(`[media-upload-lifecycle] ${violations} violation(s)`);
  process.exit(1);
}

main();
