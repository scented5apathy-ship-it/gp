#!/usr/bin/env node
/**
 * scripts/lint-relationship-config.mjs
 *
 * E4.4 deep validator for the relationship graph contract
 * under `contracts/genealogy/relationship-graph-policy.yaml`
 * and the platform mirror under
 * `platform/helm/genealogy-platform/files/`.
 *
 * Mirrors the structure of `lint-dateplace-config.mjs` (E4.3):
 *   - parse + structural assertions on `spec.policyId`,
 *     `spec.relationshipKinds`, `spec.partnerSubKinds`,
 *     `spec.participantRoles`, `spec.certainties`,
 *     `spec.provenanceStatuses`,
 *     `spec.livingLinkRedactsByDefault`,
 *     `spec.maxParticipantsPerRelationship`,
 *     `spec.maxRelationshipsPerTree`,
 *     `spec.chronologicalConflictPolicy`,
 *     `spec.selfLinkPolicy`, `spec.cyclePolicy`,
 *     `spec.partnerOverlapPolicy`,
 *     `spec.disputedAlternativeVisibility`,
 *     `spec.unknownParticipantLabel`, `spec.auditClass*`,
 *     `spec.memorialRecurrenceEnabled`;
 *   - forbidden-token scan;
 *   - chart mirror byte-equality.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, relative, resolve } from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const CONTRACT = join(ROOT, "contracts/genealogy/relationship-graph-policy.yaml");
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/relationship-graph-policy.yaml",
);

const REQUIRED_KINDS = [
  "BIOLOGICAL_PARENT",
  "ADOPTIVE_PARENT",
  "FOSTER_PARENT",
  "STEP_PARENT",
  "SURROGATE_PARENT",
  "GUARDIAN",
  "GODPARENT",
  "PARTNER",
  "SIBLING",
  "HALF_SIBLING",
  "STEP_SIBLING",
  "CUSTOM",
];
const REQUIRED_PARTNER_SUB_KINDS = [
  "MARRIED",
  "CIVIL_UNION",
  "COMMON_LAW",
  "UNMARRIED",
  "DIVORCED",
  "WIDOWED",
  "ANNULLED",
  "UNKNOWN",
];
const REQUIRED_ROLES = ["PARENT", "CHILD", "SIBLING", "PARTNER", "SUBJECT", "GUARDIAN", "WARD"];
const REQUIRED_CERTAINTIES = ["HYPOTHESIS", "ASSERTED", "VERIFIED", "DISPUTED"];
const REQUIRED_PROVENANCE = ["USER_ENTERED", "IMPORTED", "VERIFIED_BY_SOURCE", "CORRECTION"];

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
  console.error(`[relationship-config] ${message}`);
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

function scanForbiddenLiterals(raw, fileName) {
  for (const pattern of FORBIDDEN_LITERALS) {
    if (pattern.test(raw)) {
      fail(`${fileName}: forbidden literal matches ${pattern}`);
    }
  }
}

function checkRelationshipPolicy() {
  const contract = loadContract(CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, CONTRACT);

  assertString(
    requireField(parsed, "spec.policyId", fileName),
    "default-relationship/v1",
    "spec.policyId",
    fileName,
  );

  const checks = [
    ["spec.relationshipKinds", REQUIRED_KINDS],
    ["spec.partnerSubKinds", REQUIRED_PARTNER_SUB_KINDS],
    ["spec.participantRoles", REQUIRED_ROLES],
    ["spec.certainties", REQUIRED_CERTAINTIES],
    ["spec.provenanceStatuses", REQUIRED_PROVENANCE],
  ];
  for (const [field, required] of checks) {
    const value = requireField(parsed, field, fileName);
    if (!Array.isArray(value)) {
      fail(`${fileName}: ${field} must be an array`);
      continue;
    }
    assertIncludes(new Set(value), required, field, fileName);
  }

  const maxParticipants = requireField(parsed, "spec.maxParticipantsPerRelationship", fileName);
  if (typeof maxParticipants !== "number" || maxParticipants <= 0 || maxParticipants > 16) {
    fail(`${fileName}: spec.maxParticipantsPerRelationship must be 1..16`);
  }

  const maxRelPerTree = requireField(parsed, "spec.maxRelationshipsPerTree", fileName);
  if (typeof maxRelPerTree !== "number" || maxRelPerTree <= 0) {
    fail(`${fileName}: spec.maxRelationshipsPerTree must be a positive number`);
  }

  assertString(
    requireField(parsed, "spec.livingLinkRedactsByDefault", fileName),
    true,
    "spec.livingLinkRedactsByDefault",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.memorialRecurrenceEnabled", fileName),
    true,
    "spec.memorialRecurrenceEnabled",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.chronologicalConflictPolicy", fileName),
    "warn-only",
    "spec.chronologicalConflictPolicy",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.selfLinkPolicy", fileName),
    "deny",
    "spec.selfLinkPolicy",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.cyclePolicy", fileName),
    "deny",
    "spec.cyclePolicy",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.partnerOverlapPolicy", fileName),
    "allow-with-validity",
    "spec.partnerOverlapPolicy",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.disputedAlternativeVisibility", fileName),
    "soft-notice",
    "spec.disputedAlternativeVisibility",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.unknownParticipantLabel", fileName),
    "UNKNOWN",
    "spec.unknownParticipantLabel",
    fileName,
  );

  const auditMap = [
    ["spec.auditClassOnCreate", "consent"],
    ["spec.auditActionOnCreate", "relationship.created"],
    ["spec.auditClassOnUpdate", "consent"],
    ["spec.auditActionOnUpdate", "relationship.updated"],
    ["spec.auditClassOnEnd", "consent"],
    ["spec.auditActionOnEnd", "relationship.ended"],
    ["spec.auditClassOnDispute", "consent"],
    ["spec.auditActionOnDispute", "relationship.disputed"],
  ];
  for (const [field, expected] of auditMap) {
    assertString(requireField(parsed, field, fileName), expected, field, fileName);
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
  checkRelationshipPolicy();
  checkChartMirror();
  if (violations === 0) {
    console.log("[relationship-config] OK");
    process.exit(0);
  } else {
    console.error(`[relationship-config] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();
