#!/usr/bin/env node
/**
 * scripts/lint-collaboration-mixed-policy.mjs
 *
 * E6.3 deep validator for the mixed collaboration policy
 * contract under
 * `contracts/collaboration/mixed-collaboration-policy.yaml`
 * and the platform mirror under
 * `platform/helm/genealogy-platform/files/collaboration-mixed-policy.yaml`.
 *
 * Mirrors the structure of `lint-collaboration-config.mjs`
 * (E6.2):
 *   - parse + structural assertions on the closed-set
 *     vocabularies (collaborationRoles, treeBranches,
 *     resourceTypes, routingDecisions, conflictResolutions,
 *     conflictFieldKinds, mergeOutcomeKinds,
 *     flagsmithRolloutStrategies, flagsmithSyncOutcomes);
 *   - directEditMatrix structural validation (every role in
 *     the matrix MUST be in the closed-set, every branch
 *     key MUST be in the treeBranches closed-set, every
 *     resource-type key MUST be in the resourceTypes
 *     closed-set, every routing decision MUST be in the
 *     routingDecisions closed-set);
 *   - guard-rail validation (alwaysApprovalRequired,
 *     directEditPermittedResources, directEditForbiddenRoles
 *     closed-set intersection);
 *   - conflict + patch validation numeric bounds;
 *   - Flagsmith rollout sync required keys + audit hooks +
 *     snapshot maximum age;
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

const CONTRACT = join(ROOT, "contracts/collaboration/mixed-collaboration-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/collaboration-mixed-policy.yaml",
);

const REQUIRED_COLLABORATION_ROLES = [
  "TENANT_ADMIN",
  "TREE_ADMIN",
  "EDITOR",
  "REVIEWER",
  "CONTRIBUTOR",
  "VIEWER",
  "GUARDIAN",
  "DNA_STEWARD",
];

const REQUIRED_TREE_BRANCHES = [
  "TRUNK",
  "MATERNAL",
  "PATERNAL",
  "ADOPTIVE",
  "STEP",
  "GUARDIAN",
  "CUSTOM",
];

const REQUIRED_RESOURCE_TYPES = [
  "PERSON",
  "RELATIONSHIP",
  "LIFE_EVENT",
  "CLAIM",
  "SOURCE",
  "CITATION",
  "TREE_VISIBILITY",
];

const REQUIRED_ROUTING_DECISIONS = ["DIRECT_EDIT", "APPROVAL_REQUIRED", "DENY"];

const REQUIRED_CONFLICT_RESOLUTIONS = ["AUTO_MERGE", "MANUAL_MERGE", "ABANDONED"];

const REQUIRED_CONFLICT_FIELD_KINDS = [
  "SAME",
  "DIFFERENT",
  "ONLY_BASE",
  "ONLY_INCOMING",
  "ONLY_LOCAL",
];

const REQUIRED_MERGE_OUTCOME_KINDS = [
  "AUTO_MERGED",
  "MANUAL_MERGED",
  "ABANDONED",
  "FORBIDDEN_FIELD",
  "FORBIDDEN_OPERATION",
  "BASE_VERSION_STALE",
  "RESOURCE_ID_NOT_IN_SCOPE",
];

const REQUIRED_FLAGSMITH_STRATEGIES = ["SAFE_DEFAULT", "PROGRESSIVE", "CANARY", "KILL_SWITCH"];

const REQUIRED_FLAGSMITH_OUTCOMES = ["IN_SYNC", "STALE", "DRIFT", "MISSING"];

const REQUIRED_FLAGSMITH_AUDIT_KEYS = [
  "actorPseudoId",
  "correlationId",
  "flagsmithEnvironmentId",
  "flagsmithSnapshotVersion",
];

const REQUIRED_FORBIDDEN_FIELDS = [
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

const REQUIRED_INVARIANTS = [
  "ROUTING_DEFAULT_REQUIRED",
  "ROUTING_FORBIDDEN_RESOURCE",
  "ROUTING_FORBIDDEN_ROLE",
  "ROUTING_MATRIX_ROLE_NOT_FOUND",
  "ROUTING_MATRIX_BRANCH_NOT_FOUND",
  "ROUTING_MATRIX_RESOURCE_NOT_FOUND",
  "ROUTING_FORBIDDEN_FIELD",
  "ROUTING_FORBIDDEN_OPERATION",
  "CONFLICT_BASE_VERSION_STALE",
  "CONFLICT_BASE_VERSION_REQUIRED",
  "CONFLICT_NO_OVERLAP",
  "CONFLICT_FORBIDDEN_FIELD",
  "CONFLICT_FORBIDDEN_OPERATION",
  "CONFLICT_AUTO_MERGE_NOT_PERMITTED",
  "CONFLICT_MANUAL_MERGE_AUDIT_REQUIRED",
  "PATCH_OPERATION_FORBIDDEN_FIELD",
  "PATCH_OPERATION_TOO_LARGE",
  "PATCH_OPERATION_RESOURCE_NOT_IN_SCOPE",
  "FLAGSMITH_SNAPSHOT_STALE",
  "FLAGSMITH_SNAPSHOT_MISSING",
  "FLAGSMITH_STRATEGY_NOT_PERMITTED",
  "FLAGSMITH_SYNC_OUTCOMES_DIVERGED",
  "FLAGSMITH_KILL_SWITCH_ACTIVE",
  "FLAGSMITH_ROLLOUT_NOT_YET_ENABLED",
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
  console.error(`[collaboration-mixed-policy] ${message}`);
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
    // Pop stack down to the first frame whose indent is strictly less
    // than the current line. For array items we want to keep the parent
    // array on the stack so subsequent `key: value` lines (one indent
    // deeper) attach to the latest item.
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
        // Push the new object so subsequent `key: value` lines at the
        // SAME indent attach to it (array-of-objects idiom).
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

function checkDirectEditMatrix(parsed, fileName, roles, branches, resources, decisions) {
  const matrix = requireField(parsed, "spec.directEditMatrix", fileName);
  if (!Array.isArray(matrix)) {
    fail(`${fileName}: spec.directEditMatrix must be an array`);
    return;
  }
  for (const [i, entry] of matrix.entries()) {
    if (entry === null || typeof entry !== "object") {
      fail(`${fileName}: spec.directEditMatrix[${i}] must be an object`);
      continue;
    }
    const { role, branch, resourceType, decision } = entry;
    if (!roles.has(role)) {
      fail(
        `${fileName}: spec.directEditMatrix[${i}].role ${role} is not in closed-set collaborationRoles`,
      );
    }
    if (!branches.has(branch)) {
      fail(
        `${fileName}: spec.directEditMatrix[${i}].branch ${branch} is not in closed-set treeBranches`,
      );
    }
    if (!resources.has(resourceType)) {
      fail(
        `${fileName}: spec.directEditMatrix[${i}].resourceType ${resourceType} is not in closed-set resourceTypes`,
      );
    }
    if (!decisions.has(decision)) {
      fail(
        `${fileName}: spec.directEditMatrix[${i}].decision ${decision} is not in closed-set routingDecisions`,
      );
    }
  }
}

function checkForbiddenIntersection(label, subSet, parentSet, fileName) {
  for (const value of subSet) {
    if (!parentSet.has(value)) {
      fail(`${fileName}: ${label} value ${value} is not in closed-set`);
    }
  }
}

function checkConflictPatchNumericBounds(parsed, fileName) {
  const fields = [
    "spec.conflictComparisonMaxFields",
    "spec.conflictComparisonMaxValues",
    "spec.maxConflictComparedValues",
    "spec.maxConflictComparedFields",
    "spec.maxMergeCommandsPerConflict",
    "spec.maxPatchOperations",
    "spec.patchValidationMaxFieldKeyLength",
    "spec.patchValidationMaxFieldValueLength",
    "spec.patchValidationMaxResourceIdLength",
    "spec.patchValidationMaxAffectedResourceIds",
    "spec.patchValidationMaxDepth",
    "spec.patchValidationMaxOperationsPerPatch",
    "spec.maxFlagsmithSnapshotVersionLength",
    "spec.maxFlagsmithEnvironmentIdLength",
    "spec.maxFlagsmithFeatureFlagKeyLength",
  ];
  for (const f of fields) {
    assertPositiveNumber(requireField(parsed, f, fileName), f, fileName);
  }
  const countBounds = [
    "spec.maxDirectEditMatrixRoles",
    "spec.maxDirectEditMatrixBranches",
    "spec.maxDirectEditMatrixKeysPerRole",
  ];
  for (const f of countBounds) {
    assertNonNegativeNumber(requireField(parsed, f, fileName), f, fileName);
  }
}

function checkFlagsmithSync(parsed, fileName) {
  assertString(
    requireField(parsed, "spec.flagsmithEnabled", fileName),
    true,
    "spec.flagsmithEnabled",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.flagsmithSyncRequiredOnStartup", fileName),
    true,
    "spec.flagsmithSyncRequiredOnStartup",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.flagsmithRolloutContractSupersedesFlag", fileName),
    true,
    "spec.flagsmithRolloutContractSupersedesFlag",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.flagsmithKillSwitchRefusesAllDirectEdits", fileName),
    true,
    "spec.flagsmithKillSwitchRefusesAllDirectEdits",
    fileName,
  );

  const safeDefault = requireField(parsed, "spec.flagsmithSafeDefaultStrategy", fileName);
  const strategies = requireField(parsed, "spec.flagsmithRolloutStrategies", fileName);
  if (!Array.isArray(strategies) || !strategies.includes(safeDefault)) {
    fail(
      `${fileName}: spec.flagsmithSafeDefaultStrategy ${safeDefault} must be in flagsmithRolloutStrategies`,
    );
  }

  const stageMap = requireField(parsed, "spec.flagsmithRolloutStageDurationsSeconds", fileName);
  if (stageMap === undefined || stageMap === null || typeof stageMap !== "object") {
    fail(`${fileName}: spec.flagsmithRolloutStageDurationsSeconds must be an object`);
  } else {
    for (const s of REQUIRED_FLAGSMITH_STRATEGIES) {
      if (stageMap[s] === undefined) {
        fail(`${fileName}: spec.flagsmithRolloutStageDurationsSeconds.${s} is required`);
      } else if (typeof stageMap[s] !== "number" || stageMap[s] < 0) {
        fail(
          `${fileName}: spec.flagsmithRolloutStageDurationsSeconds.${s} must be a non-negative number`,
        );
      }
    }
  }

  const requiredKeys = requireField(parsed, "spec.flagsmithSyncRequiredAuditKeys", fileName);
  if (!Array.isArray(requiredKeys)) {
    fail(`${fileName}: spec.flagsmithSyncRequiredAuditKeys must be an array`);
  } else {
    assertIncludes(
      new Set(requiredKeys),
      REQUIRED_FLAGSMITH_AUDIT_KEYS,
      "spec.flagsmithSyncRequiredAuditKeys",
      fileName,
    );
  }

  assertPositiveNumber(
    requireField(parsed, "spec.flagsmithSyncIntervalSeconds", fileName),
    "spec.flagsmithSyncIntervalSeconds",
    fileName,
  );
  assertPositiveNumber(
    requireField(parsed, "spec.flagsmithSnapshotMaxAgeSeconds", fileName),
    "spec.flagsmithSnapshotMaxAgeSeconds",
    fileName,
  );
}

function checkMixedCollaborationPolicy() {
  const contract = loadContract(CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, CONTRACT);

  assertString(
    requireField(parsed, "spec.policyId", fileName),
    "default-collaboration-mixed/v2",
    "spec.policyId",
    fileName,
  );

  const roles = checkClosedSetField(
    parsed,
    "spec.collaborationRoles",
    REQUIRED_COLLABORATION_ROLES,
    fileName,
  );
  const branches = checkClosedSetField(
    parsed,
    "spec.treeBranches",
    REQUIRED_TREE_BRANCHES,
    fileName,
  );
  const resources = checkClosedSetField(
    parsed,
    "spec.resourceTypes",
    REQUIRED_RESOURCE_TYPES,
    fileName,
  );
  const decisions = checkClosedSetField(
    parsed,
    "spec.routingDecisions",
    REQUIRED_ROUTING_DECISIONS,
    fileName,
  );
  checkClosedSetField(
    parsed,
    "spec.conflictResolutions",
    REQUIRED_CONFLICT_RESOLUTIONS,
    fileName,
  );
  checkClosedSetField(
    parsed,
    "spec.conflictFieldKinds",
    REQUIRED_CONFLICT_FIELD_KINDS,
    fileName,
  );
  checkClosedSetField(
    parsed,
    "spec.mergeOutcomeKinds",
    REQUIRED_MERGE_OUTCOME_KINDS,
    fileName,
  );
  checkClosedSetField(
    parsed,
    "spec.flagsmithRolloutStrategies",
    REQUIRED_FLAGSMITH_STRATEGIES,
    fileName,
  );
  checkClosedSetField(
    parsed,
    "spec.flagsmithSyncOutcomes",
    REQUIRED_FLAGSMITH_OUTCOMES,
    fileName,
  );

  checkDirectEditMatrix(parsed, fileName, roles, branches, resources, decisions);

  // default routing decision must be in routingDecisions
  const defaultRouting = requireField(parsed, "spec.defaultRoutingDecision", fileName);
  if (!decisions.has(defaultRouting)) {
    fail(
      `${fileName}: spec.defaultRoutingDecision ${defaultRouting} must be in routingDecisions closed-set`,
    );
  }

  // Guard-rail intersections
  const alwaysAppr = requireField(parsed, "spec.alwaysApprovalRequired", fileName);
  if (!Array.isArray(alwaysAppr)) {
    fail(`${fileName}: spec.alwaysApprovalRequired must be an array`);
  } else {
    checkForbiddenIntersection(
      "spec.alwaysApprovalRequired",
      new Set(alwaysAppr),
      resources,
      fileName,
    );
  }
  const directEditPermitted = requireField(
    parsed,
    "spec.directEditPermittedResources",
    fileName,
  );
  if (!Array.isArray(directEditPermitted)) {
    fail(`${fileName}: spec.directEditPermittedResources must be an array`);
  } else {
    checkForbiddenIntersection(
      "spec.directEditPermittedResources",
      new Set(directEditPermitted),
      resources,
      fileName,
    );
  }
  const directEditForbiddenRoles = requireField(
    parsed,
    "spec.directEditForbiddenRoles",
    fileName,
  );
  if (!Array.isArray(directEditForbiddenRoles)) {
    fail(`${fileName}: spec.directEditForbiddenRoles must be an array`);
  } else {
    checkForbiddenIntersection(
      "spec.directEditForbiddenRoles",
      new Set(directEditForbiddenRoles),
      roles,
      fileName,
    );
  }

  // Conflict + patch numeric bounds
  checkConflictPatchNumericBounds(parsed, fileName);

  // Conflict toggles
  for (const field of [
    "spec.conflictRequiredBaseVersionMatch",
    "spec.conflictAutoMergeOnlyWhenBaseVersionMatch",
    "spec.conflictAutoMergeOnlyWhenNoForbiddenField",
    "spec.conflictAutoMergeOnlyWhenNoForbiddenOperation",
    "spec.conflictManualMergeRequiredAuditReason",
  ]) {
    assertString(requireField(parsed, field, fileName), true, field, fileName);
  }

  // Patch validation forbidden fields
  const patchForbidden = requireField(parsed, "spec.patchValidationForbiddenFields", fileName);
  if (!Array.isArray(patchForbidden)) {
    fail(`${fileName}: spec.patchValidationForbiddenFields must be an array`);
  } else {
    assertIncludes(
      new Set(patchForbidden),
      REQUIRED_FORBIDDEN_FIELDS,
      "spec.patchValidationForbiddenFields",
      fileName,
    );
  }

  // Flagsmith rollout sync
  checkFlagsmithSync(parsed, fileName);

  // Audit hooks
  const auditPairs = [
    ["spec.auditClassOnRoute", "collab"],
    ["spec.auditActionOnDirectEdit", "collab.routing.directEdit"],
    ["spec.auditActionOnApprovalRequired", "collab.routing.approvalRequired"],
    ["spec.auditActionOnDeny", "collab.routing.deny"],
    ["spec.auditClassOnConflict", "collab"],
    ["spec.auditActionOnConflictDetected", "collab.conflict.detected"],
    ["spec.auditActionOnConflictAutoMerged", "collab.conflict.autoMerged"],
    ["spec.auditActionOnConflictManualMerged", "collab.conflict.manualMerged"],
    ["spec.auditClassOnFlagsmithSync", "collab"],
    ["spec.auditActionOnFlagsmithSync", "collab.flagsmith.synced"],
    ["spec.auditActionOnFlagsmithDrift", "collab.flagsmith.drift"],
  ];
  for (const [field, expected] of auditPairs) {
    assertString(requireField(parsed, field, fileName), expected, field, fileName);
  }
  for (const field of [
    "spec.auditRequiredOnRoute",
    "spec.auditRequiredOnConflict",
    "spec.auditRequiredOnFlagsmithSync",
  ]) {
    assertString(requireField(parsed, field, fileName), true, field, fileName);
  }
  const requiredKeys = requireField(parsed, "spec.auditRequiredKeys", fileName);
  if (!Array.isArray(requiredKeys)) {
    fail(`${fileName}: spec.auditRequiredKeys must be an array`);
  } else {
    assertIncludes(
      new Set(requiredKeys),
      ["actorPseudoId", "correlationId"],
      "spec.auditRequiredKeys",
      fileName,
    );
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
  checkMixedCollaborationPolicy();
  checkChartMirror();
  if (violations === 0) {
    console.log("[collaboration-mixed-policy] OK");
    process.exit(0);
  } else {
    console.error(`[collaboration-mixed-policy] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();
