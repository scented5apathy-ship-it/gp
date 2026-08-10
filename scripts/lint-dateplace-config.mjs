#!/usr/bin/env node
/**
 * scripts/lint-dateplace-config.mjs
 *
 * E4.3 deep validator for the date / calendar / place contract
 * under `contracts/genealogy/date-place-policy.yaml` and the
 * platform mirror under `platform/helm/genealogy-platform/files/`.
 * Mirrors the structure of `lint-person-config.mjs` (E4.2):
 *
 *   - parse + structural assertions on `spec.policyId`,
 *     `spec.dateQualifiers`, `spec.calendars`, `spec.certainties`,
 *     `spec.coordinateDatums`, `spec.placeKinds`,
 *     `spec.authorityKinds`, `spec.locales`, `spec.timezones`,
 *     `spec.auditClass*`, `spec.storageTimezone`, and the
 *     numeric caps (`maxOriginalExpressionChars`,
 *     `maxNamesPerPlace`, `maxHierarchyDepth`,
 *     `coordinatePrecision`);
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

const CONTRACT = join(ROOT, "contracts/genealogy/date-place-policy.yaml");
const CHART_FILE = join(ROOT, "platform/helm/genealogy-platform/files/date-place-policy.yaml");

const REQUIRED_QUALIFIERS = ["EXACT", "ABOUT", "BEFORE", "AFTER", "BETWEEN", "UNKNOWN"];
const REQUIRED_CALENDARS = [
  "GREGORIAN",
  "JAPANESE",
  "VIETNAMESE_LUNISOLAR",
  "KOREAN",
  "CHINESE_LUNISOLAR",
  "ISLAMIC_CIVIL",
  "HEBREW",
  "FRENCH_REPUBLICAN",
];
const REQUIRED_CERTAINTIES = ["HYPOTHESIS", "ASSERTED", "VERIFIED", "DISPUTED"];
const REQUIRED_DATUMS = ["WGS84"];
const REQUIRED_PLACE_KINDS = [
  "COUNTRY",
  "REGION",
  "LOCALITY",
  "STREET",
  "BUILDING",
  "CEMETERY",
  "RELIGIOUS_SITE",
  "HOSPITAL",
  "UNKNOWN",
];
const REQUIRED_AUTHORITY_KINDS = ["WIKIDATA", "GEONAMES", "NATIONAL_GAZETTEER", "LOCAL"];
const REQUIRED_LOCALES = ["en-US", "vi-VN", "fr-FR", "ja-JP", "zh-Hans"];
const REQUIRED_TIMEZONES = [
  "UTC",
  "Asia/Ho_Chi_Minh",
  "Asia/Tokyo",
  "Europe/Paris",
  "America/New_York",
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
  console.error(`[dateplace-config] ${message}`);
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

function checkDatePlacePolicy() {
  const contract = loadContract(CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, CONTRACT);

  const policyId = requireField(parsed, "spec.policyId", fileName);
  assertString(policyId, "default-date-place/v1", "spec.policyId", fileName);

  const checks = [
    ["spec.dateQualifiers", REQUIRED_QUALIFIERS],
    ["spec.calendars", REQUIRED_CALENDARS],
    ["spec.certainties", REQUIRED_CERTAINTIES],
    ["spec.coordinateDatums", REQUIRED_DATUMS],
    ["spec.placeKinds", REQUIRED_PLACE_KINDS],
    ["spec.authorityKinds", REQUIRED_AUTHORITY_KINDS],
    ["spec.locales", REQUIRED_LOCALES],
    ["spec.timezones", REQUIRED_TIMEZONES],
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
    requireField(parsed, "spec.storageTimezone", fileName),
    "UTC",
    "spec.storageTimezone",
    fileName,
  );

  assertString(
    requireField(parsed, "spec.auditClassOnDateChange", fileName),
    "consent",
    "spec.auditClassOnDateChange",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditActionOnDateChange", fileName),
    "date.updated",
    "spec.auditActionOnDateChange",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditClassOnPlaceChange", fileName),
    "consent",
    "spec.auditClassOnPlaceChange",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditActionOnPlaceChange", fileName),
    "place.updated",
    "spec.auditActionOnPlaceChange",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditClassOnPlaceAttach", fileName),
    "consent",
    "spec.auditClassOnPlaceAttach",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.auditActionOnPlaceAttach", fileName),
    "place.attached",
    "spec.auditActionOnPlaceAttach",
    fileName,
  );

  assertString(
    requireField(parsed, "spec.livingPersonRedactsByDefault", fileName),
    true,
    "spec.livingPersonRedactsByDefault",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.recurringMemorialEnabled", fileName),
    true,
    "spec.recurringMemorialEnabled",
    fileName,
  );

  const maxExpr = requireField(parsed, "spec.maxOriginalExpressionChars", fileName);
  if (typeof maxExpr !== "number" || maxExpr <= 0) {
    fail(`${fileName}: spec.maxOriginalExpressionChars must be a positive number`);
  }
  const maxNames = requireField(parsed, "spec.maxNamesPerPlace", fileName);
  if (typeof maxNames !== "number" || maxNames <= 0) {
    fail(`${fileName}: spec.maxNamesPerPlace must be a positive number`);
  }
  const maxDepth = requireField(parsed, "spec.maxHierarchyDepth", fileName);
  if (typeof maxDepth !== "number" || maxDepth <= 0) {
    fail(`${fileName}: spec.maxHierarchyDepth must be a positive number`);
  }
  const coordPrec = requireField(parsed, "spec.coordinatePrecision", fileName);
  if (typeof coordPrec !== "number" || coordPrec <= 0 || coordPrec > 12) {
    fail(`${fileName}: spec.coordinatePrecision must be 1..12`);
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
  checkDatePlacePolicy();
  checkChartMirror();
  if (violations === 0) {
    console.log("[dateplace-config] OK");
    process.exit(0);
  } else {
    console.error(`[dateplace-config] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();
