#!/usr/bin/env node
/**
 * scripts/lint-media-processing-pipeline.mjs
 *
 * E7.3 deep validator for the media processing pipeline
 * policy contract under
 * `contracts/media/media-processing-pipeline-policy.yaml`
 * and the platform mirror under
 * `platform/helm/genealogy-platform/files/media-processing-pipeline-policy.yaml`.
 *
 * Mirrors the structure of
 * `lint-media-malware-metadata-pipeline.mjs` (E7.2):
 *   - parse + structural assertions on the closed-set
 *     vocabularies (processingTasks, processingEngines,
 *     processingOutcomes, processingFailureReasons,
 *     processingActivityNames, processingInputs,
 *     processingTerminalStatuses, derivedAssetFormats,
 *     ocrLanguages, ocrOutputModes, imagePresets,
 *     videoPresets, validationChecks, validationCheckResults,
 *     derivedAssetStatuses);
 *   - sandbox egress allowlist (`apicurio`, `vault-agent`,
 *     `image-optimizer`, `document-renderer`, `video-encoder`,
 *     `ocr-worker`);
 *   - derived asset status transition matrix validation
 *     (every status reachable from PENDING unless terminal;
 *      terminal states MUST have empty transition lists);
 *   - guard rails (sandboxOnly, sandboxReadOnlyFilesystem,
 *     sandboxNonRoot, egressDeniedToPublicInternet,
 *     requireReadyInputForDerivedReady,
 *     requireSuccessOutcomeForDerivedReady,
 *     requireAllValidationChecksPassForDerivedReady,
 *     validationFailNeverYieldsDerivedReady,
 *     partialOutcomeNeverYieldsDerivedReady,
 *     outputKeyDeterministicAndVersioned,
 *     derivedKeyCollisionFailsPipeline,
 *     processingIdempotentOnProcessingId,
 *     exifScrubbedRequiredForDerivedReady,
 *     libvipsOnlyForImageTranscode, imageMagickBlocked,
 *     ocrLanguagePacksPinned, dnaObjectRejected);
 *   - numeric bounds (timeoutImageTranscodeSeconds,
 *     timeoutDocumentRenderSeconds, timeoutVideoTranscodeSeconds,
 *     timeoutTextOcrSeconds, timeoutValidationSeconds,
 *     timeoutDerivedPersistSeconds, ocrMinDpi, ocrMaxDpi,
 *     ocrMaxPagesPerAsset, ocrMaxRuntimeSeconds,
 *     ocrMaxOutputBytes, imageMinLongestEdgePx,
 *     imageMaxLongestEdgePx, imageMaxOutputBytes,
 *     videoMinBitrateKbps, videoMaxBitrateKbps,
 *     videoMaxOutputBytes, maxConcurrentPipelinesPerTenant,
 *     maxConcurrentPipelinesPerAsset, maxRetriesPerActivity,
 *     maxRetentionDaysForDerivedObject,
 *     maxDerivedObjectsPerAsset, resource quotas,
 *     audit length caps);
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
  "contracts/media/media-processing-pipeline-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/media-processing-pipeline-policy.yaml",
);

const REQUIRED_PROCESSING_TASKS = [
  "IMAGE_TRANSCODE",
  "DOCUMENT_RENDER",
  "VIDEO_TRANSCODE",
  "TEXT_OCR",
];

const REQUIRED_PROCESSING_ENGINES = [
  "LIBVIPS",
  "IMAGEMAGICK",
  "FFMPEG",
  "TESSERACT",
  "GOTENBERG",
  "FALLBACK_NONE",
];

const REQUIRED_PROCESSING_OUTCOMES = [
  "SUCCESS",
  "PARTIAL",
  "PROCESS_TIMEOUT",
  "PROCESS_ERROR",
  "UNSUPPORTED_FORMAT",
  "SANDBOX_DENIED",
  "OUTPUT_KEY_COLLISION",
  "VALIDATION_FAILED",
];

const REQUIRED_PROCESSING_FAILURE_REASONS = [
  "PROCESS_TIMEOUT",
  "PROCESS_ERROR",
  "PROCESSOR_UNAVAILABLE",
  "SANDBOX_NETWORK_DENIED",
  "SANDBOX_RESOURCE_LIMIT",
  "OBJECT_TOO_LARGE",
  "INTEGRITY_CHECKSUM_MISMATCH",
  "VALIDATION_FAILED",
  "EXIF_PII_LEAKED",
  "CONTAINER_CORRUPT",
  "UNSUPPORTED_DERIVED_FORMAT",
  "DNA_OBJECT_REJECTED",
  "DERIVED_OBJECT_KEY_COLLISION",
];

const REQUIRED_PROCESSING_ACTIVITY_NAMES = [
  "IMAGE_TRANSCODE",
  "DOCUMENT_RENDER",
  "VIDEO_TRANSCODE",
  "TEXT_OCR",
  "DERIVED_VALIDATION",
  "DERIVED_PERSIST",
];

const REQUIRED_PROCESSING_INPUTS = ["READY"];

const REQUIRED_PROCESSING_TERMINAL_STATUSES = [
  "DERIVED_READY",
  "FAILED",
  "QUARANTINED_RETAIN",
];

const REQUIRED_DERIVED_ASSET_FORMATS = [
  "THUMBNAIL_WEBP",
  "THUMBNAIL_AVIF",
  "THUMBNAIL_JPEG",
  "PDF_PREVIEW",
  "VIDEO_360P",
  "VIDEO_720P",
  "VIDEO_1080P",
  "OCR_TEXT",
];

const REQUIRED_OCR_LANGUAGES = ["EN", "VI", "FR", "DE", "ZH"];

const REQUIRED_OCR_OUTPUT_MODES = ["TEXT", "HOCR", "PDF_SEARCHABLE"];

const REQUIRED_IMAGE_PRESETS = [
  "THUMBNAIL_128",
  "THUMBNAIL_256",
  "PREVIEW_1024",
  "PREVIEW_2048",
  "ORIGINAL",
];

const REQUIRED_VIDEO_PRESETS = [
  "AUDIO_ONLY",
  "VIDEO_360P",
  "VIDEO_720P",
  "VIDEO_1080P",
  "VIDEO_4K",
];

const REQUIRED_VALIDATION_CHECKS = [
  "SIGNATURE_UP_TO_DATE",
  "INTEGRITY_CHECKSUM",
  "MAGIC_BYTES",
  "CONTAINER_INTEGRITY",
  "EXIF_SCRUBBED",
  "DNA_BUCKET_ISOLATED",
];

const REQUIRED_VALIDATION_CHECK_RESULTS = [
  "PASS",
  "WARN",
  "FAIL",
  "SKIPPED",
];

const REQUIRED_DERIVED_ASSET_STATUSES = [
  "PENDING",
  "PROCESSING",
  "VALIDATING",
  "DERIVED_READY",
  "FAILED",
  "QUARANTINED_RETAIN",
];

const REQUIRED_SANDBOX_EGRESS_ALLOWLIST = [
  "apicurio",
  "vault-agent",
  "image-optimizer",
  "document-renderer",
  "video-encoder",
  "ocr-worker",
];

const REQUIRED_INVARIANTS = [
  "PROCESSING_SANDBOX_ONLY",
  "PROCESSING_SANDBOX_EGRESS_PUBLIC_DENIED",
  "PROCESSING_SANDBOX_NON_ROOT",
  "PROCESSING_INPUT_REQUIRES_READY",
  "PROCESSING_INTEGRITY_CHECKSUM_REQUIRED",
  "PROCESSING_VALIDATION_FAIL_NEVER_DERIVED_READY",
  "PROCESSING_PARTIAL_NEVER_DERIVED_READY",
  "PROCESSING_EXIF_PII_NEVER_DERIVED_READY",
  "PROCESSING_LIBVIPS_ONLY_FOR_IMAGE",
  "PROCESSING_IMAGEMAGICK_BLOCKED",
  "PROCESSING_OCR_LANGUAGE_PACK_PINNED",
  "PROCESSING_TIMEOUT_FAILS_NOT_RETRIES_SILENTLY",
  "PROCESSING_OUTPUT_KEY_DETERMINISTIC",
  "PROCESSING_OUTPUT_KEY_VERSIONED",
  "PROCESSING_DNA_OBJECT_REJECTED",
  "PROCESSING_DERIVED_KEY_COLLISION_FAILS",
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
  console.error(`[media-processing-pipeline] ${msg}`);
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
    "media-processing-pipeline-policy/v1",
    "spec.policyId",
    fileName,
  );

  // Closed-set vocabularies
  const closedSetChecks = [
    ["spec.processingTasks", REQUIRED_PROCESSING_TASKS],
    ["spec.processingEngines", REQUIRED_PROCESSING_ENGINES],
    ["spec.processingOutcomes", REQUIRED_PROCESSING_OUTCOMES],
    [
      "spec.processingFailureReasons",
      REQUIRED_PROCESSING_FAILURE_REASONS,
    ],
    [
      "spec.processingActivityNames",
      REQUIRED_PROCESSING_ACTIVITY_NAMES,
    ],
    ["spec.processingInputs", REQUIRED_PROCESSING_INPUTS],
    [
      "spec.processingTerminalStatuses",
      REQUIRED_PROCESSING_TERMINAL_STATUSES,
    ],
    ["spec.derivedAssetFormats", REQUIRED_DERIVED_ASSET_FORMATS],
    ["spec.ocrLanguages", REQUIRED_OCR_LANGUAGES],
    ["spec.ocrOutputModes", REQUIRED_OCR_OUTPUT_MODES],
    ["spec.imagePresets", REQUIRED_IMAGE_PRESETS],
    ["spec.videoPresets", REQUIRED_VIDEO_PRESETS],
    ["spec.validationChecks", REQUIRED_VALIDATION_CHECKS],
    [
      "spec.validationCheckResults",
      REQUIRED_VALIDATION_CHECK_RESULTS,
    ],
    ["spec.derivedAssetStatuses", REQUIRED_DERIVED_ASSET_STATUSES],
  ];

  for (const [path, required] of closedSetChecks) {
    const value = requireField(parsed, path, fileName);
    if (!Array.isArray(value)) {
      fail(`${fileName}: ${path} must be an array`);
      continue;
    }
    assertIncludes(new Set(value), required, path, fileName);
  }

  // processingInputs MUST be exactly [READY]
  const inputs = requireField(parsed, "spec.processingInputs", fileName);
  if (Array.isArray(inputs)) {
    if (inputs.length !== 1 || inputs[0] !== "READY") {
      fail(
        `${fileName}: spec.processingInputs MUST equal ['READY'] (E7.2 linkability invariant carries through)`,
      );
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

  // Derived asset status transition matrix
  const matrix = requireField(parsed, "spec.derivedAssetStatusMatrix", fileName);
  const derivedAssetStatuses = requireField(parsed, "spec.derivedAssetStatuses", fileName);
  if (
    typeof matrix !== "object" ||
    matrix === null ||
    !Array.isArray(derivedAssetStatuses)
  ) {
    // requireField already logged
  } else {
    const statusSet = new Set(derivedAssetStatuses);
    for (const status of statusSet) {
      if (!(status in matrix)) {
        fail(
          `${fileName}: spec.derivedAssetStatusMatrix missing entry for status '${status}'`,
        );
        continue;
      }
      const allowed = matrix[status];
      if (!Array.isArray(allowed)) {
        fail(
          `${fileName}: spec.derivedAssetStatusMatrix['${status}'] must be an array`,
        );
      }
    }
    const terminals = ["DERIVED_READY", "FAILED", "QUARANTINED_RETAIN"];
    for (const t of terminals) {
      if (Array.isArray(matrix[t]) && matrix[t].length !== 0) {
        fail(
          `${fileName}: spec.derivedAssetStatusMatrix['${t}'] MUST be empty (terminal state)`,
        );
      }
    }
    if (Array.isArray(matrix.PENDING)) {
      const reachable = new Set(matrix.PENDING);
      let changed = true;
      while (changed) {
        changed = false;
        for (const status of statusSet) {
          if (reachable.has(status)) continue;
          const nexts = matrix[status];
          if (Array.isArray(nexts)) {
            for (const n of nexts) {
              if (reachable.has(n)) {
                reachable.add(status);
                changed = true;
                break;
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
    "spec.requireReadyInputForDerivedReady",
    "spec.requireSuccessOutcomeForDerivedReady",
    "spec.requireAllValidationChecksPassForDerivedReady",
    "spec.validationFailNeverYieldsDerivedReady",
    "spec.partialOutcomeNeverYieldsDerivedReady",
    "spec.outputKeyDeterministicAndVersioned",
    "spec.derivedKeyCollisionFailsPipeline",
    "spec.processingIdempotentOnProcessingId",
    "spec.exifScrubbedRequiredForDerivedReady",
    "spec.libvipsOnlyForImageTranscode",
    "spec.imageMagickBlocked",
    "spec.ocrLanguagePacksPinned",
    "spec.dnaObjectRejected",
  ];
  for (const g of guardRails) {
    const v = requireField(parsed, g, fileName);
    if (v !== true) {
      fail(`${fileName}: ${g} must be true`);
    }
  }

  // imageMagickFallbackPolicy MUST equal NEVER
  const fallbackPolicy = requireField(
    parsed,
    "spec.imageMagickFallbackPolicy",
    fileName,
  );
  if (fallbackPolicy !== "NEVER") {
    fail(
      `${fileName}: spec.imageMagickFallbackPolicy MUST equal 'NEVER', got '${fallbackPolicy}'`,
    );
  }

  // Numeric bounds
  const numericBounds = [
    ["spec.timeoutImageTranscodeSeconds", 480],
    ["spec.timeoutDocumentRenderSeconds", 960],
    ["spec.timeoutVideoTranscodeSeconds", 1800],
    ["spec.timeoutTextOcrSeconds", 1800],
    ["spec.timeoutValidationSeconds", 480],
    ["spec.timeoutDerivedPersistSeconds", 480],
    ["spec.ocrMinDpi", 150],
    ["spec.ocrMaxDpi", 600],
    ["spec.ocrMaxPagesPerAsset", 200],
    ["spec.ocrMaxRuntimeSeconds", 1800],
    ["spec.ocrMaxOutputBytes", 67108864],
    ["spec.imageMinLongestEdgePx", 128],
    ["spec.imageMaxLongestEdgePx", 4096],
    ["spec.imageMaxOutputBytes", 33554432],
    ["spec.videoMinBitrateKbps", 200],
    ["spec.videoMaxBitrateKbps", 20000],
    ["spec.videoMaxOutputBytes", 1073741824],
    ["spec.maxConcurrentPipelinesPerTenant", 16],
    ["spec.maxConcurrentPipelinesPerAsset", 1],
    ["spec.maxRetriesPerActivity", 5],
    ["spec.maxRetentionDaysForDerivedObject", 30],
    ["spec.maxDerivedObjectsPerAsset", 16],
    ["spec.activityHeartbeatSeconds", 30],
    ["spec.activityHeartbeatMultiplier", 6],
    ["spec.workerCpuRequestMillis", 500],
    ["spec.workerCpuLimitMillis", 4000],
    ["spec.workerMemoryRequestBytes", 1073741824],
    ["spec.workerMemoryLimitBytes", 8589934592],
    ["spec.workerEphemeralStorageLimitBytes", 2147483648],
    ["spec.maxProcessingIdLength", 128],
    ["spec.maxActivityIdLength", 128],
    ["spec.maxValidationCheckCount", 16],
    ["spec.maxValidationMessageLength", 1024],
    ["spec.maxDerivedObjectKeyLength", 1024],
    ["spec.maxAuditExtraKeys", 16],
    ["spec.maxAuditExtraKeyLength", 64],
    ["spec.maxAuditExtraValueLength", 1024],
    ["spec.maxAuditCorrelationReasonLength", 256],
  ];
  for (const [path, expected] of numericBounds) {
    const v = requireField(parsed, path, fileName);
    if (typeof v !== "number") {
      fail(`${fileName}: ${path} must be a number`);
      continue;
    }
    if (v !== expected) {
      fail(
        `${fileName}: ${path} must equal ${expected}, got ${v}`,
      );
    }
  }

  // Activity heartbeat invariant: timeout > 6× heartbeat for
  // every named activity.
  const heartbeat = requireField(parsed, "spec.activityHeartbeatSeconds", fileName);
  const multiplier = requireField(parsed, "spec.activityHeartbeatMultiplier", fileName);
  const perActivityTimeout = {
    IMAGE_TRANSCODE: requireField(parsed, "spec.timeoutImageTranscodeSeconds", fileName),
    DOCUMENT_RENDER: requireField(parsed, "spec.timeoutDocumentRenderSeconds", fileName),
    VIDEO_TRANSCODE: requireField(parsed, "spec.timeoutVideoTranscodeSeconds", fileName),
    TEXT_OCR: requireField(parsed, "spec.timeoutTextOcrSeconds", fileName),
    DERIVED_VALIDATION: requireField(parsed, "spec.timeoutValidationSeconds", fileName),
    DERIVED_PERSIST: requireField(parsed, "spec.timeoutDerivedPersistSeconds", fileName),
  };
  for (const [name, t] of Object.entries(perActivityTimeout)) {
    if (typeof heartbeat !== "number" || typeof multiplier !== "number") continue;
    if (typeof t !== "number") continue;
    if (t <= multiplier * heartbeat) {
      fail(
        `${fileName}: activity '${name}' timeout (${t}s) must exceed multiplier × heartbeat (${multiplier * heartbeat}s)`,
      );
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
    assertIncludes(new Set(auditKeys), REQUIRED_AUDIT_KEYS, "spec.auditRequiredKeys", fileName);
  }

  const auditToggles = [
    "spec.auditRequiredOnProcessingStart",
    "spec.auditRequiredOnProcessingFinish",
    "spec.auditRequiredOnDerivedReady",
    "spec.auditRequiredOnValidationFailure",
    "spec.auditRequiredOnDerivedPersist",
  ];
  for (const t of auditToggles) {
    assertString(requireField(parsed, t, fileName), true, t, fileName);
  }

  const auditPairs = [
    ["spec.auditClassOnProcessingStart", "media"],
    ["spec.auditActionOnProcessingStart", "media.processing.started"],
    ["spec.auditClassOnProcessingFinish", "media"],
    ["spec.auditActionOnProcessingFinish", "media.processing.finished"],
    ["spec.auditClassOnDerivedReady", "media"],
    ["spec.auditActionOnDerivedReady", "media.derived.ready"],
    ["spec.auditClassOnValidationFailure", "media"],
    ["spec.auditActionOnValidationFailure", "media.validation.failed"],
    ["spec.auditClassOnDerivedPersist", "media"],
    ["spec.auditActionOnDerivedPersist", "media.derived.persisted"],
  ];
  for (const [field, expected] of auditPairs) {
    assertString(requireField(parsed, field, fileName), expected, field, fileName);
  }

  // Invariants
  const invariants = requireField(parsed, "spec.invariants", fileName);
  if (!Array.isArray(invariants)) {
    fail(`${fileName}: spec.invariants must be an array`);
  } else {
    assertIncludes(new Set(invariants), REQUIRED_INVARIANTS, "spec.invariants", fileName);
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
    console.log("[media-processing-pipeline] OK");
    process.exit(0);
  } else {
    console.error(
      `[media-processing-pipeline] ${violations} violation(s)`,
    );
    process.exit(1);
  }
}

main();