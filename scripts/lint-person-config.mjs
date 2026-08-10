#!/usr/bin/env node
/**
 * scripts/lint-person-config.mjs
 *
 * E4.2 deep validator for the person aggregate contract under
 * `contracts/genealogy/person-policy.yaml` and the platform
 * mirror under `platform/helm/genealogy-platform/files/`.
 * Mirrors the structure of `lint-tree-config.mjs` (E4.1):
 *
 *   - parse + structural assertions on `spec.policyId`,
 *     `spec.nameKinds`, `spec.livingStatuses`, `spec.privacyLevels`,
 *     `spec.identifierKinds`, `spec.genderDescriptions`,
 *     `spec.pronouns`, `spec.lifecycleStates`,
 *     `spec.auditClassOnChange` / `auditActionOnChange`,
 *     `spec.userLinkRequiresVerification`;
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

const PERSON_CONTRACT = join(ROOT, "contracts/genealogy/person-policy.yaml");
const CHART_FILE = join(ROOT, "platform/helm/genealogy-platform/files/person-policy.yaml");

const REQUIRED_NAME_KINDS = [
  "BIRTH",
  "PREFERRED",
  "MARRIED",
  "RELIGIOUS",
  "PROFESSIONAL",
  "ALIAS",
  "NICKNAME",
];
const REQUIRED_LIVING_STATUSES = ["LIVING", "DECEASED", "UNKNOWN", "INFERRED_LIVING"];
const REQUIRED_PRIVACY_LEVELS = ["PRIVATE", "TREE_DEFAULT", "UNLISTED", "PUBLIC"];
const REQUIRED_PRONOUNS = [
  "HE_HIM",
  "SHE_HER",
  "THEY_THEM",
  "ZE_ZIR",
  "XE_XEM",
  "SELF_DESCRIBED",
  "NOT_SPECIFIED",
];
const REQUIRED_IDENTIFIER_KINDS = [
  "WIKIDATA_QID",
  "FAMILYSEARCH_ID",
  "ANCESTRY_ID",
  "FINDAGRAVE_ID",
  "GENI_ID",
  "LOCAL_SLUG",
  "GEDCOM_XREF",
];
const REQUIRED_GENDER_DESCRIPTIONS = [
  "FEMALE",
  "MALE",
  "NONBINARY",
  "UNDISCLOSED",
  "SELF_DESCRIBED",
];
const REQUIRED_LIFECYCLE_STATES = ["ACTIVE", "MERGED", "DELETED"];

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
  console.error(`[person-config] ${message}`);
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

function checkPersonPolicy() {
  const contract = loadContract(PERSON_CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, PERSON_CONTRACT);

  const policyId = requireField(parsed, "spec.policyId", fileName);
  assertString(policyId, "default-person/v1", "spec.policyId", fileName);

  const checks = [
    ["spec.nameKinds", REQUIRED_NAME_KINDS],
    ["spec.livingStatuses", REQUIRED_LIVING_STATUSES],
    ["spec.privacyLevels", REQUIRED_PRIVACY_LEVELS],
    ["spec.pronouns", REQUIRED_PRONOUNS],
    ["spec.identifierKinds", REQUIRED_IDENTIFIER_KINDS],
    ["spec.genderDescriptions", REQUIRED_GENDER_DESCRIPTIONS],
    ["spec.lifecycleStates", REQUIRED_LIFECYCLE_STATES],
  ];
  for (const [field, required] of checks) {
    const value = requireField(parsed, field, fileName);
    if (!Array.isArray(value)) {
      fail(`${fileName}: ${field} must be an array`);
      continue;
    }
    assertIncludes(new Set(value), required, field, fileName);
  }

  assertString(
    requireField(parsed, "spec.auditClassOnChange", fileName),
    "consent",
    "spec.auditClassOnChange",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditActionOnChange", fileName),
    "person.updated",
    "spec.auditActionOnChange",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditClassOnPrivacyChange", fileName),
    "consent",
    "spec.auditClassOnPrivacyChange",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditActionOnPrivacyChange", fileName),
    "person.privacyChanged",
    "spec.auditActionOnPrivacyChange",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditClassOnLivingStatusChange", fileName),
    "consent",
    "spec.auditClassOnLivingStatusChange",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditActionOnLivingStatusChange", fileName),
    "person.livingStatusChanged",
    "spec.auditActionOnLivingStatusChange",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditClassOnSoftDelete", fileName),
    "consent",
    "spec.auditClassOnSoftDelete",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditActionOnSoftDelete", fileName),
    "person.deleted",
    "spec.auditActionOnSoftDelete",
    fileName,
  );

  assertString(
    requireField(parsed, "spec.userLinkRequiresVerification", fileName),
    true,
    "spec.userLinkRequiresVerification",
    fileName,
  );

  const maxBio = requireField(parsed, "spec.maxBiographyChars", fileName);
  if (typeof maxBio !== "number" || maxBio <= 0) {
    fail(`${fileName}: spec.maxBiographyChars must be a positive number`);
  }
  const maxNames = requireField(parsed, "spec.maxNamesPerPerson", fileName);
  if (typeof maxNames !== "number" || maxNames <= 0) {
    fail(`${fileName}: spec.maxNamesPerPerson must be a positive number`);
  }
  const maxIds = requireField(parsed, "spec.maxIdentifiersPerPerson", fileName);
  if (typeof maxIds !== "number" || maxIds <= 0) {
    fail(`${fileName}: spec.maxIdentifiersPerPerson must be a positive number`);
  }
  const maxPronouns = requireField(parsed, "spec.maxPronounsPerPerson", fileName);
  if (typeof maxPronouns !== "number" || maxPronouns <= 0) {
    fail(`${fileName}: spec.maxPronounsPerPerson must be a positive number`);
  }

  scanForbiddenLiterals(raw, fileName);
}

function checkChartMirror() {
  let srcRaw, destRaw;
  try {
    srcRaw = readFileSync(PERSON_CONTRACT, "utf8");
  } catch (err) {
    fail(`cannot read source ${relative(ROOT, PERSON_CONTRACT)}: ${err.message}`);
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
      `chart mirror ${relative(ROOT, CHART_FILE)} is NOT byte-identical to ${relative(ROOT, PERSON_CONTRACT)}`,
    );
  }
}

function main() {
  checkPersonPolicy();
  checkChartMirror();
  if (violations === 0) {
    console.log("[person-config] OK");
    process.exit(0);
  } else {
    console.error(`[person-config] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();
