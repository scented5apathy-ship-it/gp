#!/usr/bin/env node
/**
 * scripts/lint-person-merge-config.mjs
 *
 * E4.6 deep validator for the person-merge + history
 * contract under `contracts/genealogy/person-merge-policy.
 * yaml` and the platform mirror under
 * `platform/helm/genealogy-platform/files/`.
 *
 * Mirrors the structure of `lint-event-claim-config.mjs`
 * (E4.5):
 *   - parse + structural assertions on `spec.policyId`,
 *     `spec.mergeKinds`, `spec.mergeStatusLifecycle`,
 *     `spec.scoringComponents`,
 *     `spec.autoScoreThreshold`, `spec.manualScoreFloor`,
 *     `spec.revertWindowDays`,
 *     `spec.maxRekeyedReferencesPerMerge`,
 *     `spec.maxCandidatesPerScoringRun`,
 *     `spec.maxReasonChars`,
 *     `spec.sourcePreservationRequired`,
 *     `spec.redirectPreservationRequired`,
 *     `spec.mergeProvenances`,
 *     `spec.auditClass*` / `spec.auditAction*`,
 *     `spec.reviewReasonRequired`;
 *   - closed-set check on scoring component weights
 *     (must sum to 1.0 ± 0.001);
 *   - forbidden-token scan;
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

const CONTRACT = join(ROOT, "contracts/genealogy/person-merge-policy.yaml");
const CHART_FILE = join(ROOT, "platform/helm/genealogy-platform/files/person-merge-policy.yaml");

const REQUIRED_MERGE_KINDS = ["DUPLICATE_PERSON_MERGE"];
const REQUIRED_STATUS_LIFECYCLE = [
  "CANDIDATES_SCORED",
  "REVIEWED",
  "MERGED",
  "REVERTED",
  "REJECTED",
];
const REQUIRED_SCORING_COMPONENTS = [
  "NAME_EQUALITY",
  "DATE_PROXIMITY",
  "PLACE_PROXIMITY",
  "IDENTIFIER_MATCH",
];
const REQUIRED_MERGE_PROVENANCES = ["USER_REVIEW", "AUTOMATED_SCORER", "IMPORTED", "CORRECTION"];

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
  console.error(`[person-merge-config] ${message}`);
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
      if (!Array.isArray(parentTop.value)) {
        if (Object.keys(parentTop.value).length === 0) {
          const arr = [];
          const parentOfTop = stack.length >= 2 ? stack[stack.length - 2].value : null;
          if (parentOfTop) {
            for (const k of Object.keys(parentOfTop)) {
              if (parentOfTop[k] === parentTop.value) {
                parentOfTop[k] = arr;
                parentTop.value = arr;
                break;
              }
            }
          } else {
            parentTop.value = arr;
          }
        } else {
          continue;
        }
      }
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
      const next = {};
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

function checkPersonMergePolicy() {
  const contract = loadContract(CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, CONTRACT);

  assertString(
    requireField(parsed, "spec.policyId", fileName),
    "default-person-merge/v1",
    "spec.policyId",
    fileName,
  );

  const checks = [
    ["spec.mergeKinds", REQUIRED_MERGE_KINDS],
    ["spec.mergeStatusLifecycle", REQUIRED_STATUS_LIFECYCLE],
    ["spec.mergeProvenances", REQUIRED_MERGE_PROVENANCES],
  ];
  for (const [field, required] of checks) {
    const value = requireField(parsed, field, fileName);
    if (!Array.isArray(value)) {
      fail(`${fileName}: ${field} must be an array`);
      continue;
    }
    assertIncludes(new Set(value), required, field, fileName);
  }

  const components = requireField(parsed, "spec.scoringComponents", fileName);
  if (!Array.isArray(components) || components.length === 0) {
    fail(`${fileName}: spec.scoringComponents must be a non-empty array`);
  } else {
    let total = 0;
    const names = new Set();
    for (const c of components) {
      if (!c || typeof c !== "object") continue;
      if (!REQUIRED_SCORING_COMPONENTS.includes(c.name)) {
        fail(`${fileName}: spec.scoringComponents contains unknown name ${c.name}`);
      }
      if (!names.add(c.name)) {
        fail(`${fileName}: spec.scoringComponents duplicate name ${c.name}`);
      }
      if (typeof c.weight !== "number" || c.weight < 0 || c.weight > 1) {
        fail(`${fileName}: spec.scoringComponents[${c.name}].weight must be a number in [0,1]`);
      } else {
        total += c.weight;
      }
    }
    if (Math.abs(total - 1.0) > 0.001) {
      fail(`${fileName}: spec.scoringComponents weights must sum to 1.0, got ${total}`);
    }
    for (const required of REQUIRED_SCORING_COMPONENTS) {
      if (!names.has(required)) {
        fail(`${fileName}: spec.scoringComponents missing required name ${required}`);
      }
    }
  }

  const auto = requireField(parsed, "spec.autoScoreThreshold", fileName);
  if (typeof auto !== "number" || auto < 0 || auto > 1) {
    fail(`${fileName}: spec.autoScoreThreshold must be a number in [0,1]`);
  } else if (auto !== 0.85) {
    fail(`${fileName}: spec.autoScoreThreshold must equal 0.85 (E0.2 §5 #4)`);
  }

  const floor = requireField(parsed, "spec.manualScoreFloor", fileName);
  if (typeof floor !== "number" || floor < 0 || floor > 1) {
    fail(`${fileName}: spec.manualScoreFloor must be a number in [0,1]`);
  } else if (floor >= auto) {
    fail(`${fileName}: spec.manualScoreFloor must be strictly < autoScoreThreshold`);
  } else if (floor !== 0.5) {
    fail(`${fileName}: spec.manualScoreFloor must equal 0.5`);
  }

  const revert = requireField(parsed, "spec.revertWindowDays", fileName);
  if (typeof revert !== "number" || revert <= 0 || revert > 365) {
    fail(`${fileName}: spec.revertWindowDays must be 1..365`);
  } else if (revert !== 30) {
    fail(`${fileName}: spec.revertWindowDays must equal 30 (E0.2 §2.4 #6)`);
  }

  const maxRekey = requireField(parsed, "spec.maxRekeyedReferencesPerMerge", fileName);
  if (typeof maxRekey !== "number" || maxRekey <= 0 || maxRekey > 1000000) {
    fail(`${fileName}: spec.maxRekeyedReferencesPerMerge must be 1..1000000`);
  }

  const maxCand = requireField(parsed, "spec.maxCandidatesPerScoringRun", fileName);
  if (typeof maxCand !== "number" || maxCand <= 0 || maxCand > 100000) {
    fail(`${fileName}: spec.maxCandidatesPerScoringRun must be 1..100000`);
  }

  const maxReason = requireField(parsed, "spec.maxReasonChars", fileName);
  if (typeof maxReason !== "number" || maxReason <= 0 || maxReason > 65536) {
    fail(`${fileName}: spec.maxReasonChars must be 1..65536`);
  }

  assertString(
    requireField(parsed, "spec.sourcePreservationRequired", fileName),
    true,
    "spec.sourcePreservationRequired",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.redirectPreservationRequired", fileName),
    true,
    "spec.redirectPreservationRequired",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.reviewReasonRequired", fileName),
    true,
    "spec.reviewReasonRequired",
    fileName,
  );

  const auditMap = [
    ["spec.auditClassOnScore", "consent"],
    ["spec.auditActionOnScore", "candidate.scored"],
    ["spec.auditClassOnCompare", "consent"],
    ["spec.auditActionOnCompare", "merge.compared"],
    ["spec.auditClassOnPreview", "consent"],
    ["spec.auditActionOnPreview", "merge.previewed"],
    ["spec.auditClassOnCommit", "consent"],
    ["spec.auditActionOnCommit", "merge.committed"],
    ["spec.auditClassOnRevert", "consent"],
    ["spec.auditActionOnRevert", "merge.reverted"],
    ["spec.auditClassOnReject", "consent"],
    ["spec.auditActionOnReject", "merge.rejected"],
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
  checkPersonMergePolicy();
  checkChartMirror();
  if (violations === 0) {
    console.log("[person-merge-config] OK");
    process.exit(0);
  } else {
    console.error(`[person-merge-config] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();
