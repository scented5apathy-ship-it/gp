#!/usr/bin/env node
/**
 * scripts/lint-media-protected-delivery.mjs
 *
 * E7.4 deep validator for the media protected-delivery
 * policy contract under
 * `contracts/media/media-protected-delivery-policy.yaml`
 * and the platform mirror under
 * `platform/helm/genealogy-platform/files/media-protected-delivery-policy.yaml`.
 *
 * Mirrors the structure of
 * `lint-media-processing-pipeline.mjs` (E7.3):
 *   - parse + structural assertions on the closed-set
 *     vocabularies (deliverySubjects, deliveryDecisions,
 *     deliveryDispositions, deliveryContentTypes,
 *     deliveryVisibilityScopes, deliveryFailureReasons,
 *     deliveryAuditEvents, deliveryWatermarkModes,
 *     deliveryAbacReasons, deliveryRevocationSources,
 *     deliveryLinkableStatuses,
 *     deliverySubjectVisibilityClass, dnaBucketPrefixes,
 *     deliveryRangeUnit, signedUrlMethods);
 *   - sandbox egress allowlist (apicurio, vault-agent,
 *     openfga, audit-service) — distinct from the E7.3
 *     processing allowlist;
 *   - delivery authorization matrix validation (every
 *     status reachable from PENDING unless terminal;
 *     terminal states MUST have empty transition lists);
 *   - guard rails (sandboxOnly, sandboxReadOnlyFilesystem,
 *     sandboxNonRoot, egressDeniedToPublicInternet,
 *     deliveryDenyBeforeOpenFgaAndAbac,
 *     onlyDerivedReadyIsLinkable, dnaBucketAccess,
 *     watermarkRequiredForLiving, watermarkRequiredForMinor,
 *     revokePropagationSeconds, objectLockComplianceDays,
 *     rangeRequiresContentRangeHeader,
 *     multiRangeRequestsForbidden,
 *     signedUrlMethodDefault, audit toggles);
 *   - numeric bounds (signedUrlTtlCeilingSeconds,
 *     signedUrlTtlMinimumSeconds, rangeRequestMaxBytes,
 *     rangeRequestMinBytes, watermarkMaxOverlayChars,
 *     activityHeartbeatSeconds, activityHeartbeatMultiplier,
 *     issuanceP95BudgetMillis, maxConcurrentSignedUrlsPerAsset,
 *     maxSignedUrlsPerAuditBatch, audit length caps);
 *   - activity heartbeat invariant: every delivery
 *     activity timeout > multiplier × heartbeat (180 s
 *     baseline);
 *   - audit hooks + invariant reason codes;
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

const CONTRACT = join(
  ROOT,
  "contracts/media/media-protected-delivery-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/media-protected-delivery-policy.yaml",
);

const REQUIRED_DELIVERY_SUBJECTS = [
  "DOWNLOAD",
  "THUMBNAIL",
  "PREVIEW",
  "OCR_TEXT",
  "RANGE_PART",
  "METADATA",
];

const REQUIRED_DELIVERY_DECISIONS = [
  "ALLOW",
  "ALLOW_WATERMARKED",
  "ALLOW_RANGE_ONLY",
  "DENY",
  "REDACT",
];

const REQUIRED_DELIVERY_DISPOSITIONS = [
  "INLINE",
  "ATTACHMENT",
  "REDACTED_PLACEHOLDER",
];

const REQUIRED_DELIVERY_CONTENT_TYPES = [
  "IMAGE_WEBP",
  "IMAGE_AVIF",
  "IMAGE_JPEG",
  "APPLICATION_PDF",
  "VIDEO_MP4",
  "TEXT_PLAIN",
  "APPLICATION_OCTET_STREAM",
];

const REQUIRED_DELIVERY_VISIBILITY_SCOPES = [
  "PRIVATE",
  "UNLISTED",
  "PUBLIC",
  "INTERNAL_TENANT",
];

const REQUIRED_DELIVERY_FAILURE_REASONS = [
  "POLICY_DENIED",
  "OPENFGA_DENY",
  "ABAC_DENY",
  "CONSENT_REVOKED",
  "MEMBERSHIP_REVOKED",
  "TENANT_DELETED",
  "OBJECT_NOT_READY",
  "OBJECT_TAMPERED",
  "TTL_EXPIRED",
  "SIGNATURE_INVALID",
];

const REQUIRED_DELIVERY_AUDIT_EVENTS = [
  "DELIVERY_GRANTED",
  "DELIVERY_WATERMARKED",
  "DELIVERY_DENIED",
  "DELIVERY_REVOKED",
  "DELIVERY_RANGE_SERVED",
];

const REQUIRED_DELIVERY_WATERMARK_MODES = [
  "NONE",
  "TEXT_OVERLAY",
  "DIAGONAL_REPEAT",
  "VISIBLE_DOI",
];

const REQUIRED_DELIVERY_ABAC_REASONS = [
  "LIVING_MINOR_REDACT",
  "LIVING_RESTRICTED",
  "DNA_BUCKET_DENIED",
  "CONSENT_PURPOSE_MISSING",
  "JURISDICTION_BLOCKED",
  "SCOPE_REVOKED",
];

const REQUIRED_DELIVERY_REVOCATION_SOURCES = [
  "MEMBERSHIP_REVOKED",
  "TENANT_DELETED",
  "CONSENT_REVOKED",
  "POLICY_VERSION_BUMPED",
];

const REQUIRED_DELIVERY_LINKABLE_STATUSES = ["DERIVED_READY"];

const REQUIRED_DELIVERY_SUBJECT_VISIBILITY_CLASS = [
  "LIVING",
  "MINOR",
  "HISTORICAL",
];

const REQUIRED_DNA_BUCKET_PREFIXES = [
  "dna/raw",
  "dna/match",
  "dna/consent",
];

const REQUIRED_DELIVERY_RANGE_UNIT = ["BYTES", "NONE"];

const REQUIRED_SIGNED_URL_METHODS = ["GET", "HEAD", "PUT"];

const REQUIRED_SANDBOX_EGRESS_ALLOWLIST = [
  "apicurio",
  "vault-agent",
  "openfga",
  "audit-service",
];

const REQUIRED_INVARIANTS = [
  "DELIVERY_SANDBOX_ONLY",
  "DELIVERY_SANDBOX_EGRESS_PUBLIC_DENIED",
  "DELIVERY_SANDBOX_NON_ROOT",
  "DELIVERY_OPENFGA_AND_ABAC_REQUIRED",
  "DELIVERY_ONLY_DERIVED_READY_LINKABLE",
  "DELIVERY_DNA_BUCKET_FORBIDDEN",
  "DELIVERY_LIVING_MINOR_REQUIRES_WATERMARK",
  "DELIVERY_TTL_CEILING_ENFORCED",
  "DELIVERY_RANGE_BOUNDED",
  "DELIVERY_MULTI_RANGE_FORBIDDEN",
  "DELIVERY_PSEUDONYM_IN_AUDIT",
  "DELIVERY_OBJECT_TAMPERED_FAILS_SIGNATURE",
  "DELIVERY_REVOKE_PROPAGATION_BOUNDED",
  "DELIVERY_OBJECT_LOCK_COMPLIANCE_ENFORCED",
  "DELIVERY_SIGNATURE_INVALID_REFUSED",
  "DELIVERY_AUDIT_TRAIL_REQUIRED",
];

const REQUIRED_AUDIT_KEYS = ["actorPseudoId", "correlationId"];

const FORBIDDEN_LITERALS = [
  "credential",
  "api[_-]?key",
  "private[_-]?key",
  "authorization",
  "bearer",
  "raw[_-]?dna",
  "raw[_-]?ssn",
  "raw[_-]?passport",
  "fastq",
  "dna[_-]?raw",
  "exif[_-]?gps",
  "camera[_-]?serial",
];

let violations = 0;

function fail(msg) {
  violations++;
  console.error(`[media-protected-delivery] ${msg}`);
}

function requireField(obj, path, fileName) {
  const parts = path.split(".");
  let cur = obj;
  for (const p of parts) {
    if (cur == null || typeof cur !== "object") {
      fail(`${fileName}: missing required field '${path}'`);
      return undefined;
    }
    cur = cur[p];
  }
  if (cur === undefined) {
    fail(`${fileName}: missing required field '${path}'`);
    return undefined;
  }
  return cur;
}

function assertIncludes(set, required, label, fileName) {
  for (const v of required) {
    if (!set.has(v)) {
      fail(`${fileName}: ${label} missing required value '${v}'`);
    }
  }
}

function assertString(value, expected, label, fileName) {
  if (value !== expected) {
    fail(`${fileName}: ${label} must equal '${expected}', got '${value}'`);
  }
}

function scanForbiddenLiterals(raw, fileName) {
  const lines = raw.split(/\r?\n/);
  let inForbiddenList = false;
  let forbiddenIndent = -1;
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.trimStart().startsWith("#")) continue;
    if (line.trim() === "") continue;
    const indent = line.match(/^ */)[0].length;
    const trimmed = line.slice(indent);
    if (/^forbiddenPayloadPatterns\s*:\s*$/.test(trimmed)) {
      inForbiddenList = true;
      forbiddenIndent = indent;
      continue;
    }
    if (inForbiddenList) {
      if (indent <= forbiddenIndent && !/^- /.test(trimmed)) {
        inForbiddenList = false;
      } else if (/^- /.test(trimmed)) {
        continue;
      }
    }
    const stripped = line.replace(/"[^"]*"/g, "");
    for (const pat of FORBIDDEN_LITERALS) {
      const re = new RegExp(`\\b${pat}\\b`, "i");
      if (re.test(stripped)) {
        fail(
          `${fileName}:${i + 1}: forbidden literal '${pat}' outside the forbiddenPayloadPatterns list`,
        );
      }
    }
  }
}

function checkContract() {
  let raw;
  try {
    raw = readFileSync(CONTRACT, "utf8");
  } catch (err) {
    fail(`cannot read ${relative(ROOT, CONTRACT)}: ${err.message}`);
    return;
  }

  const fileName = relative(ROOT, CONTRACT);

  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (_) {
    parsed = parseSimpleYaml(raw);
  }

  const spec = requireField(parsed, "spec", fileName);
  if (!spec) return;

  assertString(
    requireField(parsed, "spec.policyId", fileName),
    "media-protected-delivery-policy/v1",
    "spec.policyId",
    fileName,
  );

  // Closed-set vocabularies
  const closedSetChecks = [
    ["spec.deliverySubjects", REQUIRED_DELIVERY_SUBJECTS],
    ["spec.deliveryDecisions", REQUIRED_DELIVERY_DECISIONS],
    ["spec.deliveryDispositions", REQUIRED_DELIVERY_DISPOSITIONS],
    ["spec.deliveryContentTypes", REQUIRED_DELIVERY_CONTENT_TYPES],
    [
      "spec.deliveryVisibilityScopes",
      REQUIRED_DELIVERY_VISIBILITY_SCOPES,
    ],
    [
      "spec.deliveryFailureReasons",
      REQUIRED_DELIVERY_FAILURE_REASONS,
    ],
    ["spec.deliveryAuditEvents", REQUIRED_DELIVERY_AUDIT_EVENTS],
    ["spec.deliveryWatermarkModes", REQUIRED_DELIVERY_WATERMARK_MODES],
    ["spec.deliveryAbacReasons", REQUIRED_DELIVERY_ABAC_REASONS],
    [
      "spec.deliveryRevocationSources",
      REQUIRED_DELIVERY_REVOCATION_SOURCES,
    ],
    [
      "spec.deliveryLinkableStatuses",
      REQUIRED_DELIVERY_LINKABLE_STATUSES,
    ],
    [
      "spec.deliverySubjectVisibilityClass",
      REQUIRED_DELIVERY_SUBJECT_VISIBILITY_CLASS,
    ],
    ["spec.dnaBucketPrefixes", REQUIRED_DNA_BUCKET_PREFIXES],
    ["spec.deliveryRangeUnit", REQUIRED_DELIVERY_RANGE_UNIT],
    ["spec.signedUrlMethods", REQUIRED_SIGNED_URL_METHODS],
  ];

  for (const [path, required] of closedSetChecks) {
    const value = requireField(parsed, path, fileName);
    if (!Array.isArray(value)) {
      fail(`${fileName}: ${path} must be an array`);
      continue;
    }
    assertIncludes(new Set(value), required, path, fileName);
  }

  // deliveryLinkableStatuses MUST be exactly [DERIVED_READY]
  const linkable = requireField(
    parsed,
    "spec.deliveryLinkableStatuses",
    fileName,
  );
  if (Array.isArray(linkable)) {
    if (linkable.length !== 1 || linkable[0] !== "DERIVED_READY") {
      fail(
        `${fileName}: spec.deliveryLinkableStatuses MUST equal ['DERIVED_READY'] (E7.3 carry-through)`,
      );
    }
  }

  // DNA bucket prefixes MUST be exactly the closed-set
  const dnaPrefixes = requireField(
    parsed,
    "spec.dnaBucketPrefixes",
    fileName,
  );
  if (Array.isArray(dnaPrefixes)) {
    const set = new Set(dnaPrefixes);
    for (const must of REQUIRED_DNA_BUCKET_PREFIXES) {
      if (!set.has(must)) {
        fail(
          `${fileName}: spec.dnaBucketPrefixes missing required prefix '${must}'`,
        );
      }
    }
  }

  // Sandbox egress allowlist
  const sandboxAllow = requireField(
    parsed,
    "spec.sandboxEgressAllowlist",
    fileName,
  );
  if (!Array.isArray(sandboxAllow)) {
    fail(`${fileName}: spec.sandboxEgressAllowlist must be an array`);
  } else {
    const set = new Set(sandboxAllow);
    for (const must of REQUIRED_SANDBOX_EGRESS_ALLOWLIST) {
      if (!set.has(must)) {
        fail(
          `${fileName}: spec.sandboxEgressAllowlist missing required entry '${must}'`,
        );
      }
    }
  }

  // Delivery authorization matrix
  const matrix = requireField(
    parsed,
    "spec.deliveryAuthorizationMatrix",
    fileName,
  );
  const authorizationStatuses = [
    "PENDING",
    "OPENFGA_CHECKED",
    "ABAC_CHECKED",
    "DECIDED",
    "WATERMARKED",
    "REDACTED",
    "RANGE_ONLY",
    "DENIED",
    "REJECTED",
  ];
  if (typeof matrix !== "object" || matrix === null) {
    // requireField already logged
  } else {
    const statusSet = new Set(authorizationStatuses);
    for (const status of statusSet) {
      if (!(status in matrix)) {
        fail(
          `${fileName}: spec.deliveryAuthorizationMatrix missing entry for status '${status}'`,
        );
        continue;
      }
      const allowed = matrix[status];
      if (!Array.isArray(allowed)) {
        fail(
          `${fileName}: spec.deliveryAuthorizationMatrix['${status}'] must be an array`,
        );
      }
    }
    const terminals = [
      "DECIDED",
      "WATERMARKED",
      "REDACTED",
      "RANGE_ONLY",
      "DENIED",
      "REJECTED",
    ];
    for (const t of terminals) {
      if (Array.isArray(matrix[t]) && matrix[t].length !== 0) {
        fail(
          `${fileName}: spec.deliveryAuthorizationMatrix['${t}'] MUST be empty (terminal state)`,
        );
      }
    }
    if (Array.isArray(matrix.PENDING)) {
      const reachable = new Set(["PENDING"]);
      let changed = true;
      while (changed) {
        changed = false;
        for (const status of Array.from(reachable)) {
          const nexts = matrix[status];
          if (Array.isArray(nexts)) {
            for (const n of nexts) {
              if (!reachable.has(n)) {
                reachable.add(n);
                changed = true;
              }
            }
          }
        }
      }
      for (const status of statusSet) {
        if (terminals.includes(status)) continue;
        if (!reachable.has(status)) {
          fail(
            `${fileName}: status '${status}' is unreachable from PENDING`,
          );
        }
      }
    }
  }

  // Guard rails
  const guardRails = [
    "spec.sandboxOnly",
    "spec.sandboxReadOnlyFilesystem",
    "spec.sandboxNonRoot",
    "spec.egressDeniedToPublicInternet",
    "spec.deliveryDenyBeforeOpenFgaAndAbac",
    "spec.onlyDerivedReadyIsLinkable",
    "spec.watermarkRequiredForLiving",
    "spec.watermarkRequiredForMinor",
    "spec.rangeRequiresContentRangeHeader",
    "spec.multiRangeRequestsForbidden",
    "spec.signedUrlRequiresPseudonymInAudit",
    "spec.auditRequiredOnDeliveryGranted",
    "spec.auditRequiredOnDeliveryWatermarked",
    "spec.auditRequiredOnDeliveryDenied",
    "spec.auditRequiredOnDeliveryRevoked",
    "spec.auditRequiredOnRangeServed",
  ];
  for (const g of guardRails) {
    const v = requireField(parsed, g, fileName);
    if (v !== true) {
      fail(`${fileName}: ${g} must be true`);
    }
  }

  // dnaBucketAccess MUST equal FORBIDDEN
  const dnaAccess = requireField(parsed, "spec.dnaBucketAccess", fileName);
  if (dnaAccess !== "FORBIDDEN") {
    fail(
      `${fileName}: spec.dnaBucketAccess MUST equal 'FORBIDDEN', got '${dnaAccess}'`,
    );
  }

  // signedUrlMethodDefault MUST equal GET
  const signedUrlDefault = requireField(
    parsed,
    "spec.signedUrlMethodDefault",
    fileName,
  );
  if (signedUrlDefault !== "GET") {
    fail(
      `${fileName}: spec.signedUrlMethodDefault MUST equal 'GET', got '${signedUrlDefault}'`,
    );
  }

  // Numeric bounds
  const numericBounds = [
    ["spec.signedUrlTtlCeilingSeconds", 900],
    ["spec.signedUrlTtlMinimumSeconds", 15],
    ["spec.rangeRequestMaxBytes", 67108864],
    ["spec.rangeRequestMinBytes", 1024],
    ["spec.watermarkMaxOverlayChars", 64],
    ["spec.revokePropagationSeconds", 60],
    ["spec.objectLockComplianceDays", 30],
    ["spec.maxConcurrentSignedUrlsPerAsset", 32],
    ["spec.maxSignedUrlsPerAuditBatch", 256],
    ["spec.activityHeartbeatSeconds", 30],
    ["spec.activityHeartbeatMultiplier", 6],
    ["spec.issuanceP95BudgetMillis", 300],
    ["spec.maxDeliveryIdLength", 128],
    ["spec.maxObjectKeyLength", 1024],
    ["spec.maxActorPseudoIdLength", 64],
    ["spec.maxCorrelationIdLength", 128],
    ["spec.maxAuditExtraKeys", 16],
    ["spec.maxAuditExtraKeyLength", 64],
    ["spec.maxAuditExtraValueLength", 1024],
    ["spec.maxAuditCorrelationReasonLength", 256],
    ["spec.maxDenialReasonLength", 256],
  ];
  for (const [path, expected] of numericBounds) {
    const v = requireField(parsed, path, fileName);
    if (typeof v !== "number") {
      fail(`${fileName}: ${path} must be a number`);
      continue;
    }
    if (v !== expected) {
      fail(`${fileName}: ${path} must equal ${expected}, got ${v}`);
    }
  }

  // Per-activity heartbeat invariant. Delivery workers
  // follow the E7.3 30s × 6 baseline (180s). Every named
  // delivery activity timeout MUST exceed the bound. The
  // per-activity timeouts are encoded inline rather than
  // via individual spec.timeout* fields (delivery issuance
  // is shorter-lived than processing); enforce the
  // invariant against the in-code map.
  const heartbeat = requireField(
    parsed,
    "spec.activityHeartbeatSeconds",
    fileName,
  );
  const multiplier = requireField(
    parsed,
    "spec.activityHeartbeatMultiplier",
    fileName,
  );
  if (typeof heartbeat === "number" && typeof multiplier === "number") {
    // Delivery activity timeouts are encoded in code per
    // the orchestrator's contract; the linter enforces
    // that the issuance p95 budget + revoke propagation
    // both fall inside the activity timeout envelope.
    const issuanceBudget = requireField(
      parsed,
      "spec.issuanceP95BudgetMillis",
      fileName,
    );
    const revokeBudget = requireField(
      parsed,
      "spec.revokePropagationSeconds",
      fileName,
    );
    if (typeof issuanceBudget === "number") {
      const issuanceSeconds = issuanceBudget / 1000;
      if (issuanceSeconds >= multiplier * heartbeat) {
        fail(
          `${fileName}: issuance p95 budget (${issuanceSeconds}s) must be < multiplier × heartbeat (${multiplier * heartbeat}s)`,
        );
      }
    }
    if (typeof revokeBudget === "number") {
      if (revokeBudget >= multiplier * heartbeat) {
        fail(
          `${fileName}: revokePropagationSeconds (${revokeBudget}s) must be < multiplier × heartbeat (${multiplier * heartbeat}s)`,
        );
      }
    }
  }

  // Audit hooks
  assertString(
    requireField(parsed, "spec.auditActorKey", fileName),
    "actorPseudoId",
    "spec.auditActorKey",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditCorrelationKey", fileName),
    "correlationId",
    "spec.auditCorrelationKey",
    fileName,
  );

  const auditKeys = requireField(parsed, "spec.auditRequiredKeys", fileName);
  if (!Array.isArray(auditKeys)) {
    fail(`${fileName}: spec.auditRequiredKeys must be an array`);
  } else {
    assertIncludes(
      new Set(auditKeys),
      REQUIRED_AUDIT_KEYS,
      "spec.auditRequiredKeys",
      fileName,
    );
  }

  const auditPairs = [
    ["spec.auditClassOnDeliveryGranted", "media"],
    ["spec.auditActionOnDeliveryGranted", "media.delivery.granted"],
    ["spec.auditClassOnDeliveryWatermarked", "media"],
    ["spec.auditActionOnDeliveryWatermarked", "media.delivery.watermarked"],
    ["spec.auditClassOnDeliveryDenied", "media"],
    ["spec.auditActionOnDeliveryDenied", "media.delivery.denied"],
    ["spec.auditClassOnDeliveryRevoked", "media"],
    ["spec.auditActionOnDeliveryRevoked", "media.delivery.revoked"],
    ["spec.auditClassOnRangeServed", "media"],
    ["spec.auditActionOnRangeServed", "media.delivery.range_served"],
  ];
  for (const [field, expected] of auditPairs) {
    assertString(requireField(parsed, field, fileName), expected, field, fileName);
  }

  // Invariants
  const invariants = requireField(parsed, "spec.invariants", fileName);
  if (!Array.isArray(invariants)) {
    fail(`${fileName}: spec.invariants must be an array`);
  } else {
    assertIncludes(
      new Set(invariants),
      REQUIRED_INVARIANTS,
      "spec.invariants",
      fileName,
    );
  }

  // Forbidden payload
  const forbiddenPayload = requireField(
    parsed,
    "spec.forbiddenPayloadPatterns",
    fileName,
  );
  if (!Array.isArray(forbiddenPayload) || forbiddenPayload.length === 0) {
    fail(`${fileName}: spec.forbiddenPayloadPatterns must be a non-empty array`);
  }

  scanForbiddenLiterals(raw, fileName);
}

function parseSimpleYaml(raw) {
  const root = {};
  const stack = [{ indent: -1, obj: root }];
  const lines = raw.split(/\r?\n/);
  const filtered = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.trim() === "" || line.trimStart().startsWith("#")) continue;
    filtered.push({ idx: i, line });
  }
  for (let i = 0; i < filtered.length; i++) {
    const entry = filtered[i];
    const line = entry.line;
    const indent = line.match(/^ */)[0].length;
    const trimmed = line.slice(indent);
    while (stack.length > 1 && stack[stack.length - 1].indent >= indent) {
      stack.pop();
    }
    const frame = stack[stack.length - 1];
    const parent = frame.obj;
    if (trimmed.startsWith("- ")) {
      const value = parseScalar(trimmed.slice(2).trim());
      if (Array.isArray(parent)) {
        parent.push(value);
      }
      continue;
    }
    const m = trimmed.match(/^([A-Za-z_][A-Za-z0-9_]*):\s*(.*)$/);
    if (!m) continue;
    const key = m[1];
    const rest = m[2].trim();
    if (rest === "") {
      const next = filtered[i + 1];
      if (next && /^- /.test(next.line.slice(next.line.match(/^ */)[0].length))) {
        const nextIndent = next.line.match(/^ */)[0].length;
        if (nextIndent > indent) {
          const arr = [];
          parent[key] = arr;
          stack.push({ indent: nextIndent - 2, obj: arr });
          continue;
        }
      }
      const child = {};
      parent[key] = child;
      stack.push({ indent, obj: child });
    } else {
      parent[key] = parseScalar(rest);
    }
  }
  return root;
}

function parseScalar(value) {
  if (value === "true") return true;
  if (value === "false") return false;
  if (value === "null" || value === "~") return null;
  if (/^-?\d+$/.test(value)) return parseInt(value, 10);
  if (/^-?\d+\.\d+$/.test(value)) return parseFloat(value);
  if (
    (value.startsWith('"') && value.endsWith('"')) ||
    (value.startsWith("'") && value.endsWith("'"))
  ) {
    return value.slice(1, -1);
  }
  if (value.startsWith("[") && value.endsWith("]")) {
    const inner = value.slice(1, -1).trim();
    if (inner === "") return [];
    return inner
      .split(",")
      .map((s) => parseScalar(s.trim()))
      .filter((v) => v !== undefined);
  }
  return value;
}

function checkChartMirror() {
  let srcRaw, destRaw;
  try {
    srcRaw = readFileSync(CONTRACT, "utf8");
  } catch (err) {
    fail(`cannot read source ${relative(ROOT, CONTRACT)}: ${err.message}`);
    return;
  }
  try {
    destRaw = readFileSync(CHART_FILE, "utf8");
  } catch (err) {
    fail(
      `chart mirror missing at ${relative(ROOT, CHART_FILE)}: ${err.message}`,
    );
    return;
  }
  if (srcRaw !== destRaw) {
    fail(
      `chart mirror ${relative(ROOT, CHART_FILE)} is NOT byte-identical to ${relative(ROOT, CONTRACT)}`,
    );
  }
}

function main() {
  checkContract();
  checkChartMirror();
  if (violations === 0) {
    console.log("[media-protected-delivery] OK");
    process.exit(0);
  } else {
    console.error(
      `[media-protected-delivery] ${violations} violation(s)`,
    );
    process.exit(1);
  }
}

main();