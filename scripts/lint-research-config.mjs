#!/usr/bin/env node
/**
 * scripts/lint-research-config.mjs
 *
 * E6.1 deep validator for the research log + citations +
 * provenance contract under
 * `contracts/research/research-policy.yaml` and the platform
 * mirror under `platform/helm/genealogy-platform/files/`.
 *
 * Mirrors the structure of `lint-relationship-config.mjs`
 * (E4.4) and `lint-event-claim-config.mjs` (E4.5):
 *   - parse + structural assertions on the closed-set
 *     vocabularies (sourceKinds, citationQualities,
 *     citationDispositions, researchTaskStatuses,
 *     hypothesisStatuses, conflictKinds, conflictStatuses,
 *     repositoryKinds, attachmentKinds, certainties);
 *   - state-transition matrix validation (researchTaskStatusMatrix,
 *     hypothesisStatusMatrix, conflictStatusMatrix) — every
 *     state MUST be reachable from OPEN unless terminal,
 *     terminal states MUST have empty transition lists,
 *     and the matrix MUST cover every status in the
 *     corresponding closed-set;
 *   - invariant code closed-set + audit class / action;
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

const CONTRACT = join(ROOT, "contracts/research/research-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/research-policy.yaml",
);

const REQUIRED_SOURCE_KINDS = [
  "PRIMARY",
  "SECONDARY",
  "DERIVED",
  "ARCHIVE",
  "FINDING_AID",
  "OTHER",
];
const REQUIRED_CITATION_QUALITIES = [
  "ORIGINAL",
  "TRANSCRIPT",
  "ABSTRACT",
  "IMAGE",
  "COPY",
  "UNKNOWN",
];
const REQUIRED_CITATION_DISPOSITIONS = [
  "SUPPORTS",
  "REFUTES",
  "MENTIONS",
  "UNCERTAIN",
];
const REQUIRED_RESEARCH_TASK_STATUSES = [
  "OPEN",
  "IN_PROGRESS",
  "BLOCKED",
  "RESOLVED",
  "ABANDONED",
];
const REQUIRED_HYPOTHESIS_STATUSES = [
  "DRAFT",
  "ACTIVE",
  "CORROBORATED",
  "REFUTED",
  "SUPERSEDED",
];
const REQUIRED_CONFLICT_KINDS = [
  "SOURCE_DISAGREES",
  "CITATION_DISAGREES",
  "CLAIM_CONTRADICTS_SOURCE",
  "HYPOTHESIS_COLLIDES",
  "OTHER",
];
const REQUIRED_CONFLICT_STATUSES = [
  "OPEN",
  "INVESTIGATING",
  "RESOLVED",
  "ABANDONED",
];
const REQUIRED_REPOSITORY_KINDS = [
  "ARCHIVE",
  "LIBRARY",
  "CHURCH",
  "CIVIL_REGISTRY",
  "CEMETERY",
  "FAMILY_HOLDING",
  "DIGITAL_PLATFORM",
  "OTHER",
];
const REQUIRED_ATTACHMENT_KINDS = [
  "DIGITAL_IMAGE",
  "PDF",
  "AUDIO",
  "VIDEO",
  "TRANSCRIPT",
  "EXTERNAL_URL",
  "OTHER",
];
const REQUIRED_CERTAINTIES = [
  "HYPOTHESIS",
  "ASSERTED",
  "VERIFIED",
  "DISPUTED",
];

const REQUIRED_INVARIANTS = [
  "SOURCE_POINTER_REQUIRES_ATTACHMENT",
  "TRANSCRIPT_QUALITY_REQUIRES_SEGMENT",
  "TRANSCRIPT_LINE_OUT_OF_ORDER",
  "RESEARCH_TASK_IN_PROGRESS_REQUIRES_ASSIGNMENT",
  "RESEARCH_TASK_BLOCKED_REQUIRES_REASON",
  "RESEARCH_TASK_RESOLVED_REQUIRES_PROOF",
  "HYPOTHESIS_CORROBORATED_REQUIRES_CITATION",
  "HYPOTHESIS_REFUTED_REQUIRES_CITATION",
  "HYPOTHESIS_SUPERSEDED_REQUIRES_BACK_REFERENCE",
  "CITATION_REQUIRES_LOCATOR_OR_QUOTE",
  "CITATION_REQUIRES_CONFIRMATION_FOR_LIVING",
  "REPOSITORY_PRIVATE_HOLDING_HIDES_BY_DEFAULT",
  "CONFLICT_RESOLVED_REQUIRES_PROOF",
  "CONFLICT_REQUIRE_MULTIPLE_PARTICIPANTS",
  "ATTACHMENT_EXTERNAL_URL_REQUIRES_DOMAIN_WHITELIST",
  "AUDIT_KEY_FORBIDDEN",
];

const REQUIRED_AUDIT_KEYS = ["actorPseudoId", "correlationId"];

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
  console.error(`[research-config] ${message}`);
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
    const parent = stack[stack.length - 1].value;
    const trimmed = line.trim();
    if (trimmed.startsWith("- ")) {
      const parentTop = stack[stack.length - 1];
      if (!Array.isArray(parentTop.value)) continue;
      const itemRaw = trimmed.slice(2).trim();
      const sub = itemRaw.match(/^([A-Za-z0-9_.\-]+)\s*:\s*(.*)$/);
      if (sub) {
        const obj = {};
        obj[sub[1]] = stripQuotesValue(sub[2]);
        parentTop.value.push(obj);
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

function requireField(parsed, path, fileName) {
  const parts = path.split(".");
  let cur = parsed;
  for (const p of parts) {
    if (cur === undefined || cur === null) {
      fail(`${fileName}: missing required field ${path}`);
      return undefined;
    }
    cur = cur[p];
  }
  if (cur === undefined || cur === null) {
    fail(`${fileName}: missing required field ${path}`);
    return undefined;
  }
  return cur;
}

function assertString(value, expected, field, fileName) {
  if (value !== expected) {
    fail(
      `${fileName}: ${field} must equal ${JSON.stringify(expected)}, got ${JSON.stringify(value)}`,
    );
  }
}

function assertIncludes(set, required, field, fileName) {
  for (const r of required) {
    if (!set.has(r)) {
      fail(`${fileName}: ${field} missing required value ${r}`);
    }
  }
}

function assertPositiveNumber(value, field, fileName) {
  if (typeof value !== "number" || value <= 0) {
    fail(`${fileName}: ${field} must be a positive number`);
  }
}

function assertNonNegativeNumber(value, field, fileName) {
  if (typeof value !== "number" || value < 0) {
    fail(`${fileName}: ${field} must be a non-negative number`);
  }
}

function scanForbiddenLiterals(raw, fileName) {
  for (const pattern of FORBIDDEN_LITERALS) {
    if (pattern.test(raw)) {
      fail(`${fileName}: forbidden literal matches ${pattern}`);
    }
  }
}

function checkClosedSetField(parsed, field, required, fileName) {
  const value = requireField(parsed, field, fileName);
  if (!Array.isArray(value)) {
    fail(`${fileName}: ${field} must be an array`);
    return new Set();
  }
  assertIncludes(new Set(value), required, field, fileName);
  return new Set(value);
}

function checkStatusMatrix(parsed, matrixField, statusesField, statuses, fileName) {
  const matrix = requireField(parsed, matrixField, fileName);
  if (matrix === undefined || matrix === null || typeof matrix !== "object") {
    fail(`${fileName}: ${matrixField} must be an object`);
    return;
  }
  const matrixKeys = new Set(Object.keys(matrix));
  assertIncludes(matrixKeys, statuses, matrixField, fileName);
  for (const status of statuses) {
    const transitions = matrix[status];
    if (transitions === undefined) {
      continue;
    }
    if (!Array.isArray(transitions)) {
      fail(
        `${fileName}: ${matrixField}.${status} must be an array, got ${typeof transitions}`,
      );
      continue;
    }
    for (const next of transitions) {
      if (!statuses.includes(next)) {
        fail(
          `${fileName}: ${matrixField}.${status} references unknown status ${next}`,
        );
      }
    }
  }
  const TERMINAL = new Set(["RESOLVED", "ABANDONED", "REFUTED", "SUPERSEDED"]);
  for (const status of statuses) {
    if (TERMINAL.has(status)) {
      const transitions = matrix[status];
      if (transitions === undefined) {
        fail(
          `${fileName}: ${matrixField}.${status} is terminal and must have [] transitions`,
        );
        continue;
      }
      if (transitions.length !== 0) {
        fail(
          `${fileName}: ${matrixField}.${status} is terminal and must have empty transitions`,
        );
      }
    }
  }
}

function checkResearchPolicy() {
  const contract = loadContract(CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, CONTRACT);

  assertString(
    requireField(parsed, "spec.policyId", fileName),
    "default-research/v1",
    "spec.policyId",
    fileName,
  );

  const sets = [
    ["spec.sourceKinds", REQUIRED_SOURCE_KINDS],
    ["spec.citationQualities", REQUIRED_CITATION_QUALITIES],
    ["spec.citationDispositions", REQUIRED_CITATION_DISPOSITIONS],
    ["spec.researchTaskStatuses", REQUIRED_RESEARCH_TASK_STATUSES],
    ["spec.hypothesisStatuses", REQUIRED_HYPOTHESIS_STATUSES],
    ["spec.conflictKinds", REQUIRED_CONFLICT_KINDS],
    ["spec.conflictStatuses", REQUIRED_CONFLICT_STATUSES],
    ["spec.repositoryKinds", REQUIRED_REPOSITORY_KINDS],
    ["spec.attachmentKinds", REQUIRED_ATTACHMENT_KINDS],
    ["spec.certainties", REQUIRED_CERTAINTIES],
  ];
  for (const [field, required] of sets) {
    checkClosedSetField(parsed, field, required, fileName);
  }

  checkStatusMatrix(
    parsed,
    "spec.researchTaskStatusMatrix",
    "spec.researchTaskStatuses",
    REQUIRED_RESEARCH_TASK_STATUSES,
    fileName,
  );
  checkStatusMatrix(
    parsed,
    "spec.hypothesisStatusMatrix",
    "spec.hypothesisStatuses",
    REQUIRED_HYPOTHESIS_STATUSES,
    fileName,
  );
  checkStatusMatrix(
    parsed,
    "spec.conflictStatusMatrix",
    "spec.conflictStatuses",
    REQUIRED_CONFLICT_STATUSES,
    fileName,
  );

  // Numeric bounds
  const numericFields = [
    "spec.maxSourceTitleLength",
    "spec.maxSourceAuthorLength",
    "spec.maxSourcePublisherLength",
    "spec.maxSourcePublisherLocationLength",
    "spec.maxSourceDescriptionLength",
    "spec.maxSourceAttachments",
    "spec.maxSourceCitations",
    "spec.maxRepositoryNameLength",
    "spec.maxRepositoryLocationLabelLength",
    "spec.maxRepositoryWebsiteUrlLength",
    "spec.maxRepositoryDescriptionLength",
    "spec.maxRepositoryMetadataEntries",
    "spec.maxCitationClaimReferenceLength",
    "spec.maxCitationClaimKindLength",
    "spec.maxCitationQuotedTextLength",
    "spec.maxCitationTranscriptSegments",
    "spec.maxCitationAttachments",
    "spec.maxCitationExternalUrls",
    "spec.maxResearchTaskTitleLength",
    "spec.maxResearchTaskDescriptionLength",
    "spec.maxResearchTaskSubjectReferenceLength",
    "spec.maxResearchTaskSubjectKindLength",
    "spec.maxResearchTaskBlockedReasonLength",
    "spec.maxResearchTaskResolvedProofLength",
    "spec.maxResearchTaskAssignments",
    "spec.maxResearchTaskLinkedCitations",
    "spec.maxHypothesisStatementLength",
    "spec.maxHypothesisSubjectReferenceLength",
    "spec.maxHypothesisSubjectKindLength",
    "spec.maxHypothesisSupersededByLength",
    "spec.maxHypothesisAssignedToLength",
    "spec.maxHypothesisCorroboratingCitations",
    "spec.maxHypothesisRefutingCitations",
    "spec.maxConflictSummaryLength",
    "spec.maxConflictKindNoteLength",
    "spec.maxConflictResolutionLength",
    "spec.maxConflictResolutionProofLength",
    "spec.maxConflictParticipants",
    "spec.maxConflictLinkedCitations",
    "spec.maxLocatorRawLength",
    "spec.maxAttachmentMediaObjectIdLength",
    "spec.maxAttachmentCanonicalUrlLength",
    "spec.maxAttachmentCaptionLength",
    "spec.maxTranscriptSegmentLineNumber",
    "spec.maxTranscriptSegmentTextLength",
    "spec.maxTranscriptSegmentOriginalScriptLength",
    "spec.maxTranscriptSegmentSpeakerLength",
    "spec.maxAuditExtraKeys",
    "spec.maxAuditExtraKeyLength",
    "spec.maxAuditExtraValueLength",
    "spec.maxAuditCorrelationReasonLength",
    "spec.maxHopsPerProvenanceChain",
  ];
  for (const field of numericFields) {
    assertPositiveNumber(requireField(parsed, field, fileName), field, fileName);
  }
  const minParticipants = requireField(parsed, "spec.minConflictParticipants", fileName);
  if (minParticipants !== 2) {
    fail(`${fileName}: spec.minConflictParticipants must equal 2, got ${minParticipants}`);
  }

  const confidenceMin = requireField(parsed, "spec.confidenceMin", fileName);
  const confidenceMax = requireField(parsed, "spec.confidenceMax", fileName);
  if (confidenceMin !== 0.0) {
    fail(`${fileName}: spec.confidenceMin must equal 0.0, got ${confidenceMin}`);
  }
  if (confidenceMax !== 1.0) {
    fail(`${fileName}: spec.confidenceMax must equal 1.0, got ${confidenceMax}`);
  }

  // Audit hooks
  const requiredKeys = requireField(parsed, "spec.auditRequiredKeys", fileName);
  if (!Array.isArray(requiredKeys)) {
    fail(`${fileName}: spec.auditRequiredKeys must be an array`);
  } else {
    assertIncludes(new Set(requiredKeys), REQUIRED_AUDIT_KEYS, "spec.auditRequiredKeys", fileName);
  }
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
  assertString(
    requireField(parsed, "spec.auditRequiredOnCreate", fileName),
    true,
    "spec.auditRequiredOnCreate",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditRequiredOnUpdate", fileName),
    true,
    "spec.auditRequiredOnUpdate",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditRequiredOnArchive", fileName),
    true,
    "spec.auditRequiredOnArchive",
    fileName,
  );

  const auditPairs = [
    ["spec.auditClassOnCreate", "research"],
    ["spec.auditActionOnCreate", "research.created"],
    ["spec.auditClassOnUpdate", "research"],
    ["spec.auditActionOnUpdate", "research.updated"],
    ["spec.auditClassOnArchive", "research"],
    ["spec.auditActionOnArchive", "research.archived"],
    ["spec.auditClassOnResolve", "research"],
    ["spec.auditActionOnResolve", "research.resolved"],
    ["spec.auditClassOnAbandon", "research"],
    ["spec.auditActionOnAbandon", "research.abandoned"],
  ];
  for (const [field, expected] of auditPairs) {
    assertString(requireField(parsed, field, fileName), expected, field, fileName);
  }

  // Invariant codes
  const invariants = requireField(parsed, "spec.invariants", fileName);
  if (!Array.isArray(invariants)) {
    fail(`${fileName}: spec.invariants must be an array`);
  } else {
    assertIncludes(new Set(invariants), REQUIRED_INVARIANTS, "spec.invariants", fileName);
  }

  // Forbidden payload scan
  const forbiddenPayload = requireField(parsed, "spec.forbiddenPayloadPatterns", fileName);
  if (!Array.isArray(forbiddenPayload) || forbiddenPayload.length === 0) {
    fail(`${fileName}: spec.forbiddenPayloadPatterns must be a non-empty array`);
  }

  scanForbiddenLiterals(raw, fileName);
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
    fail(`chart mirror missing at ${relative(ROOT, CHART_FILE)}: ${err.message}`);
    return;
  }
  if (srcRaw !== destRaw) {
    fail(
      `chart mirror ${relative(ROOT, CHART_FILE)} is NOT byte-identical to ${relative(ROOT, CONTRACT)}`,
    );
  }
}

function main() {
  checkResearchPolicy();
  checkChartMirror();
  if (violations === 0) {
    console.log("[research-config] OK");
    process.exit(0);
  } else {
    console.error(`[research-config] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();
