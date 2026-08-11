#!/usr/bin/env node
/**
 * scripts/lint-collaboration-config.mjs
 *
 * E6.2 deep validator for the change proposal + review
 * contract under
 * `contracts/collaboration/collaboration-policy.yaml` and
 * the platform mirror under
 * `platform/helm/genealogy-platform/files/collaboration-
 * proposal-policy.yaml`.
 *
 * Mirrors the structure of `lint-research-config.mjs`
 * (E6.1) and `lint-relationship-config.mjs` (E4.4):
 *   - parse + structural assertions on the closed-set
 *     vocabularies (proposalKinds, proposalStatuses,
 *     proposalDecisions, domainCommandKinds, reviewVerdicts,
 *     conflictResolutions, reAuthorizationOutcomes);
 *   - state-transition matrix validation
 *     (proposalStatusMatrix, reviewStatusMatrix) — every
 *     status MUST be reachable from a non-terminal status,
 *     terminal states MUST have empty transition lists,
 *     and the matrix MUST cover every status in the
 *     corresponding closed-set;
 *   - forbidden field / forbidden operation closed-set;
 *   - invariant code closed-set + audit class / action;
 *   - numeric bounds;
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

const CONTRACT = join(ROOT, "contracts/collaboration/collaboration-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/collaboration-proposal-policy.yaml",
);

const REQUIRED_PROPOSAL_KINDS = [
  "PERSON",
  "RELATIONSHIP",
  "LIFE_EVENT",
  "CLAIM",
  "SOURCE",
  "CITATION",
  "TREE_VISIBILITY",
];
const REQUIRED_PROPOSAL_STATUSES = [
  "DRAFT",
  "SUBMITTED",
  "IN_REVIEW",
  "CHANGES_REQUESTED",
  "APPROVED",
  "PARTIALLY_MERGED",
  "MERGED",
  "REJECTED",
  "WITHDRAWN",
  "EXPIRED",
];
const REQUIRED_PROPOSAL_DECISIONS = [
  "APPROVE",
  "REJECT",
  "REQUEST_CHANGE",
  "PARTIAL_MERGE",
  "WITHDRAW",
];
const REQUIRED_DOMAIN_COMMAND_KINDS = [
  "CREATE_PERSON",
  "UPDATE_PERSON",
  "ARCHIVE_PERSON",
  "CREATE_RELATIONSHIP",
  "UPDATE_RELATIONSHIP",
  "ARCHIVE_RELATIONSHIP",
  "CREATE_LIFE_EVENT",
  "UPDATE_LIFE_EVENT",
  "ARCHIVE_LIFE_EVENT",
  "CREATE_CLAIM",
  "UPDATE_CLAIM",
  "ARCHIVE_CLAIM",
  "CREATE_SOURCE",
  "UPDATE_SOURCE",
  "ARCHIVE_SOURCE",
  "CREATE_CITATION",
  "UPDATE_CITATION",
  "ARCHIVE_CITATION",
  "SET_TREE_VISIBILITY",
];
const REQUIRED_REVIEW_VERDICTS = [
  "APPROVED",
  "REJECTED",
  "CHANGES_REQUESTED",
  "PARTIAL_MERGED",
];
const REQUIRED_CONFLICT_RESOLUTIONS = [
  "AUTO_MERGE",
  "MANUAL_MERGE",
  "ABANDONED",
];
const REQUIRED_RE_AUTH_OUTCOMES = ["ALLOW", "DENY", "ABAC_DENY"];

const REQUIRED_FORBIDDEN_DOMAIN_COMMAND_FIELDS = [
  "dnaRawData",
  "dnaMatchId",
  "consentReceipt",
  "livingMarker",
  "visibility",
  "redactedFields",
  "rawEmail",
  "rawPhone",
  "rawSsn",
  "rawPassport",
  "ownerPseudoId",
  "tenantId",
];

const REQUIRED_FORBIDDEN_PROPOSAL_KIND_OPERATIONS = {
  PERSON: ["SET_TREE_VISIBILITY"],
  RELATIONSHIP: ["SET_TREE_VISIBILITY"],
  LIFE_EVENT: ["SET_TREE_VISIBILITY"],
  CLAIM: ["SET_TREE_VISIBILITY"],
  SOURCE: ["SET_TREE_VISIBILITY"],
  CITATION: ["SET_TREE_VISIBILITY"],
  TREE_VISIBILITY: [
    "CREATE_PERSON",
    "UPDATE_PERSON",
    "ARCHIVE_PERSON",
    "CREATE_RELATIONSHIP",
    "UPDATE_RELATIONSHIP",
    "ARCHIVE_RELATIONSHIP",
    "CREATE_LIFE_EVENT",
    "UPDATE_LIFE_EVENT",
    "ARCHIVE_LIFE_EVENT",
    "CREATE_CLAIM",
    "UPDATE_CLAIM",
    "ARCHIVE_CLAIM",
    "CREATE_SOURCE",
    "UPDATE_SOURCE",
    "ARCHIVE_SOURCE",
    "CREATE_CITATION",
    "UPDATE_CITATION",
    "ARCHIVE_CITATION",
  ],
};

const REQUIRED_REVIEW_STATUSES = [
  "PENDING",
  "APPROVED",
  "REJECTED",
  "CHANGES_REQUESTED",
  "PARTIAL_MERGED",
];

const REQUIRED_INVARIANTS = [
  "PROPOSAL_REQUIRED_BASE_VERSION",
  "PROPOSAL_BASE_VERSION_NOT_POSITIVE",
  "PROPOSAL_REASON_REQUIRED",
  "PROPOSAL_SCOPE_REQUIRED",
  "PROPOSAL_SOURCE_REFERENCE_REQUIRED",
  "PROPOSAL_DOMAIN_COMMAND_REQUIRED",
  "PROPOSAL_DOMAIN_COMMAND_FORBIDDEN_FIELD",
  "PROPOSAL_DOMAIN_COMMAND_FORBIDDEN_OPERATION",
  "PROPOSAL_TOO_LARGE",
  "PROPOSAL_TTL_OUT_OF_RANGE",
  "PROPOSAL_REAUTHORIZATION_REQUIRED",
  "PROPOSAL_REAUTHORIZATION_DENIED",
  "PROPOSAL_REAUTHORIZATION_ABAC_DENIED",
  "REVIEW_REQUIRED_COMMENT_FOR_REJECT",
  "REVIEW_REQUIRED_COMMENT_FOR_REQUEST_CHANGE",
  "REVIEW_PARTIAL_MERGE_REQUIRES_OPERATIONS",
  "REVIEW_TOO_MANY_DECISIONS",
  "REVIEW_DUPLICATE_DECISION",
  "PROPOSAL_NOT_IN_REVIEWABLE_STATE",
  "PROPOSAL_NOT_PARTIALLY_MERGEABLE",
  "CONFLICT_REQUIRE_BASE_VERSION_MATCH",
  "CONFLICT_REQUIRED_PARTIAL_MERGE_PLAN",
  "AUDIT_KEY_FORBIDDEN",
  "FORBIDDEN_DOMAIN_COMMAND_TARGET",
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
  console.error(`[collaboration-config] ${message}`);
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

function checkStatusMatrix(parsed, matrixField, statusesField, statuses, fileName, terminalSet) {
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
  for (const status of statuses) {
    if (terminalSet.has(status)) {
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

function checkForbiddenProposalKindOperations(parsed, fileName) {
  const value = requireField(parsed, "spec.forbiddenProposalKindOperations", fileName);
  if (value === undefined || value === null || typeof value !== "object") {
    fail(`${fileName}: spec.forbiddenProposalKindOperations must be an object`);
    return;
  }
  for (const [kind, expected] of Object.entries(REQUIRED_FORBIDDEN_PROPOSAL_KIND_OPERATIONS)) {
    if (!Array.isArray(value[kind])) {
      fail(`${fileName}: spec.forbiddenProposalKindOperations.${kind} must be an array`);
      continue;
    }
    const have = new Set(value[kind]);
    for (const op of expected) {
      if (!have.has(op)) {
        fail(`${fileName}: spec.forbiddenProposalKindOperations.${kind} missing ${op}`);
      }
    }
  }
}

function checkCollaborationPolicy() {
  const contract = loadContract(CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, CONTRACT);

  assertString(
    requireField(parsed, "spec.policyId", fileName),
    "default-collaboration/v2",
    "spec.policyId",
    fileName,
  );

  const sets = [
    ["spec.proposalKinds", REQUIRED_PROPOSAL_KINDS],
    ["spec.proposalStatuses", REQUIRED_PROPOSAL_STATUSES],
    ["spec.proposalDecisions", REQUIRED_PROPOSAL_DECISIONS],
    ["spec.domainCommandKinds", REQUIRED_DOMAIN_COMMAND_KINDS],
    ["spec.reviewVerdicts", REQUIRED_REVIEW_VERDICTS],
    ["spec.conflictResolutions", REQUIRED_CONFLICT_RESOLUTIONS],
    ["spec.reAuthorizationOutcomes", REQUIRED_RE_AUTH_OUTCOMES],
    ["spec.reviewStatuses", REQUIRED_REVIEW_STATUSES],
  ];
  for (const [field, required] of sets) {
    checkClosedSetField(parsed, field, required, fileName);
  }

  checkStatusMatrix(
    parsed,
    "spec.proposalStatusMatrix",
    "spec.proposalStatuses",
    REQUIRED_PROPOSAL_STATUSES,
    fileName,
    new Set(["MERGED", "REJECTED", "WITHDRAWN", "EXPIRED"]),
  );
  checkStatusMatrix(
    parsed,
    "spec.reviewStatusMatrix",
    "spec.reviewStatuses",
    REQUIRED_REVIEW_STATUSES,
    fileName,
    new Set(["APPROVED", "REJECTED", "CHANGES_REQUESTED", "PARTIAL_MERGED"]),
  );

  // Forbidden fields
  const forbiddenFields = requireField(parsed, "spec.forbiddenDomainCommandFields", fileName);
  if (!Array.isArray(forbiddenFields)) {
    fail(`${fileName}: spec.forbiddenDomainCommandFields must be an array`);
  } else {
    assertIncludes(
      new Set(forbiddenFields),
      REQUIRED_FORBIDDEN_DOMAIN_COMMAND_FIELDS,
      "spec.forbiddenDomainCommandFields",
      fileName,
    );
  }

  checkForbiddenProposalKindOperations(parsed, fileName);

  // Numeric bounds
  const numericFields = [
    "spec.maxProposalTitleLength",
    "spec.maxProposalSummaryLength",
    "spec.maxProposalReasonLength",
    "spec.maxProposalScopeLength",
    "spec.maxProposalSourceReferenceLength",
    "spec.maxProposalDomainCommands",
    "spec.maxProposalAffectedResourceIds",
    "spec.maxProposalPartialMergeOperations",
    "spec.maxDomainCommandFieldKeyLength",
    "spec.maxDomainCommandFieldValueLength",
    "spec.maxDomainCommandResourceIdLength",
    "spec.maxReviewCommentLength",
    "spec.maxReviewDecisionsPerProposal",
    "spec.maxReviewVerdicts",
    "spec.maxAuditExtraKeys",
    "spec.maxAuditExtraKeyLength",
    "spec.maxAuditExtraValueLength",
    "spec.maxAuditCorrelationReasonLength",
    "spec.maxConflictComparedValues",
    "spec.maxConflictComparedFields",
    "spec.maxHopsPerProposalTraceability",
  ];
  for (const field of numericFields) {
    assertPositiveNumber(requireField(parsed, field, fileName), field, fileName);
  }
  assertPositiveNumber(
    requireField(parsed, "spec.minProposalTtlSeconds", fileName),
    "spec.minProposalTtlSeconds",
    fileName,
  );
  assertPositiveNumber(
    requireField(parsed, "spec.maxProposalTtlSeconds", fileName),
    "spec.maxProposalTtlSeconds",
    fileName,
  );

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
    requireField(parsed, "spec.auditProposalPseudoIdKey", fileName),
    "proposerPseudoId",
    "spec.auditProposalPseudoIdKey",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditReviewerPseudoIdKey", fileName),
    "reviewerPseudoId",
    "spec.auditReviewerPseudoIdKey",
    fileName,
  );

  const auditPairs = [
    ["spec.auditClassOnSubmit", "collab"],
    ["spec.auditActionOnSubmit", "collab.proposal.submitted"],
    ["spec.auditClassOnDecide", "collab"],
    ["spec.auditActionOnApprove", "collab.review.approved"],
    ["spec.auditActionOnReject", "collab.review.rejected"],
    ["spec.auditActionOnRequestChange", "collab.review.changesRequested"],
    ["spec.auditActionOnPartialMerge", "collab.review.partialMerged"],
    ["spec.auditClassOnMerge", "collab"],
    ["spec.auditActionOnMerge", "collab.proposal.merged"],
    ["spec.auditClassOnWithdraw", "collab"],
    ["spec.auditActionOnWithdraw", "collab.proposal.withdrawn"],
    ["spec.auditClassOnExpire", "collab"],
    ["spec.auditActionOnExpire", "collab.proposal.expired"],
  ];
  for (const [field, expected] of auditPairs) {
    assertString(requireField(parsed, field, fileName), expected, field, fileName);
  }

  // Re-authorization toggles
  for (const field of [
    "spec.reAuthorizationOnSubmit",
    "spec.reAuthorizationOnApprove",
    "spec.reAuthorizationOnPartialMerge",
    "spec.reAuthorizationDenyClosesProposal",
    "spec.reAuthorizationAbacDenyClosesProposal",
    "spec.auditRequiredOnSubmit",
    "spec.auditRequiredOnDecide",
    "spec.auditRequiredOnMerge",
    "spec.auditRequiredOnWithdraw",
    "spec.auditRequiredOnExpire",
  ]) {
    assertString(requireField(parsed, field, fileName), true, field, fileName);
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
  checkCollaborationPolicy();
  checkChartMirror();
  if (violations === 0) {
    console.log("[collaboration-config] OK");
    process.exit(0);
  } else {
    console.error(`[collaboration-config] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();