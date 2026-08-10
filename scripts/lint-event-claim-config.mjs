#!/usr/bin/env node
/**
 * scripts/lint-event-claim-config.mjs
 *
 * E4.5 deep validator for the life-event + claim contract
 * under `contracts/genealogy/event-claim-policy.yaml` and
 * the platform mirror under
 * `platform/helm/genealogy-platform/files/`.
 *
 * Mirrors the structure of `lint-relationship-config.mjs`
 * (E4.4):
 *   - parse + structural assertions on `spec.policyId`,
 *     `spec.lifeEventKinds`, `spec.eventParticipantRoles`,
 *     `spec.privacyClassifications`, `spec.certainties`,
 *     `spec.provenanceStatuses`,
 *     `spec.sourceReferenceKinds`,
 *     `spec.confidenceRange.min/max`,
 *     `spec.maxParticipantsPerEvent`,
 *     `spec.maxSourceReferencesPerClaim`,
 *     `spec.maxDescriptionChars`,
 *     `spec.maxCustomLabelChars`,
 *     `spec.recurringMemorialRequired`,
 *     `spec.livingLinkRedactsByDefault`,
 *     `spec.provenancePolicy.allowedCombinations`,
 *     `spec.claimLifecycle`,
 *     `spec.claimRequiresSourceReference`,
 *     `spec.correctionRequiresBackReference`,
 *     `spec.auditClass*` / `spec.auditAction*`;
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

const CONTRACT = join(ROOT, "contracts/genealogy/event-claim-policy.yaml");
const CHART_FILE = join(ROOT, "platform/helm/genealogy-platform/files/event-claim-policy.yaml");

const REQUIRED_EVENT_KINDS = [
  "BIRTH",
  "BAPTISM",
  "DEATH",
  "BURIAL",
  "CREMATION",
  "MARRIAGE",
  "DIVORCE",
  "ENGAGEMENT",
  "EDUCATION",
  "OCCUPATION",
  "RESIDENCE",
  "IMMIGRATION",
  "EMIGRATION",
  "MILITARY_SERVICE",
  "ILLNESS",
  "RELIGIOUS_CEREMONY",
  "RECURRING_MEMORIAL",
  "CUSTOM",
];
const REQUIRED_EVENT_ROLES = [
  "SUBJECT",
  "PARENT",
  "CHILD",
  "SIBLING",
  "PARTNER",
  "GUARDIAN",
  "WARD",
  "WITNESS",
  "OFFICIANT",
  "INFORMANT",
];
const REQUIRED_PRIVACY = ["PRIVATE", "UNLISTED", "PUBLIC"];
const REQUIRED_CERTAINTIES = ["HYPOTHESIS", "ASSERTED", "VERIFIED", "DISPUTED"];
const REQUIRED_PROVENANCE = ["USER_ENTERED", "IMPORTED", "VERIFIED_BY_SOURCE", "CORRECTION"];
const REQUIRED_SOURCE_KINDS = [
  "REPOSITORY_CITATION",
  "DOCUMENT_CITATION",
  "TRANSCRIPT_CITATION",
  "PAGE_LOCATOR",
  "URL",
  "MEDIA_ATTACHMENT",
  "INTERVIEW_NOTE",
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
  console.error(`[event-claim-config] ${message}`);
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
        // Promote an empty {} placeholder to [] so list
        // children render correctly. The placeholder exists
        // because we cannot know at key-push time whether the
        // upcoming children will be a list or a key/value map.
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
        // Allow children of this list item to attach to the
        // object itself rather than to the parent list.
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

function checkEventClaimPolicy() {
  const contract = loadContract(CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, CONTRACT);

  assertString(
    requireField(parsed, "spec.policyId", fileName),
    "default-event-claim/v1",
    "spec.policyId",
    fileName,
  );

  const checks = [
    ["spec.lifeEventKinds", REQUIRED_EVENT_KINDS],
    ["spec.eventParticipantRoles", REQUIRED_EVENT_ROLES],
    ["spec.privacyClassifications", REQUIRED_PRIVACY],
    ["spec.certainties", REQUIRED_CERTAINTIES],
    ["spec.provenanceStatuses", REQUIRED_PROVENANCE],
    ["spec.sourceReferenceKinds", REQUIRED_SOURCE_KINDS],
  ];
  for (const [field, required] of checks) {
    const value = requireField(parsed, field, fileName);
    if (!Array.isArray(value)) {
      fail(`${fileName}: ${field} must be an array`);
      continue;
    }
    assertIncludes(new Set(value), required, field, fileName);
  }

  const minConf = requireField(parsed, "spec.confidenceRange.min", fileName);
  const maxConf = requireField(parsed, "spec.confidenceRange.max", fileName);
  if (typeof minConf !== "number" || typeof maxConf !== "number") {
    fail(`${fileName}: spec.confidenceRange.{min,max} must be numbers`);
  } else if (minConf < 0 || maxConf > 1 || minConf > maxConf) {
    fail(`${fileName}: spec.confidenceRange must be within [0,1] with min<=max`);
  } else if (minConf !== 0 || maxConf !== 1) {
    fail(`${fileName}: spec.confidenceRange must equal {0.0, 1.0}`);
  }

  const maxParts = requireField(parsed, "spec.maxParticipantsPerEvent", fileName);
  if (typeof maxParts !== "number" || maxParts <= 0 || maxParts > 64) {
    fail(`${fileName}: spec.maxParticipantsPerEvent must be 1..64`);
  }

  const maxSrc = requireField(parsed, "spec.maxSourceReferencesPerClaim", fileName);
  if (typeof maxSrc !== "number" || maxSrc <= 0 || maxSrc > 256) {
    fail(`${fileName}: spec.maxSourceReferencesPerClaim must be 1..256`);
  }

  const maxDesc = requireField(parsed, "spec.maxDescriptionChars", fileName);
  if (typeof maxDesc !== "number" || maxDesc <= 0 || maxDesc > 65536) {
    fail(`${fileName}: spec.maxDescriptionChars must be 1..65536`);
  }

  const maxLabel = requireField(parsed, "spec.maxCustomLabelChars", fileName);
  if (typeof maxLabel !== "number" || maxLabel <= 0 || maxLabel > 1024) {
    fail(`${fileName}: spec.maxCustomLabelChars must be 1..1024`);
  }

  const required = requireField(parsed, "spec.recurringMemorialRequired", fileName);
  if (!Array.isArray(required) || !required.includes("RECURRING_MEMORIAL")) {
    fail(`${fileName}: spec.recurringMemorialRequired must include RECURRING_MEMORIAL`);
  }

  assertString(
    requireField(parsed, "spec.livingLinkRedactsByDefault", fileName),
    true,
    "spec.livingLinkRedactsByDefault",
    fileName,
  );

  assertString(
    requireField(parsed, "spec.claimRequiresSourceReference", fileName),
    true,
    "spec.claimRequiresSourceReference",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.correctionRequiresBackReference", fileName),
    true,
    "spec.correctionRequiresBackReference",
    fileName,
  );

  const lifecycle = requireField(parsed, "spec.claimLifecycle", fileName);
  if (
    !Array.isArray(lifecycle) ||
    JSON.stringify(lifecycle) !== JSON.stringify(REQUIRED_CERTAINTIES)
  ) {
    fail(`${fileName}: spec.claimLifecycle must equal ${JSON.stringify(REQUIRED_CERTAINTIES)}`);
  }

  const combos = requireField(parsed, "spec.provenancePolicy.allowedCombinations", fileName);
  if (!Array.isArray(combos) || combos.length === 0) {
    fail(`${fileName}: spec.provenancePolicy.allowedCombinations must be a non-empty array`);
  } else {
    let importVerified = false;
    let userAll = false;
    for (const c of combos) {
      if (c && c.provenance === "IMPORTED") {
        const certs = Array.isArray(c.certainties) ? c.certainties : [];
        if (certs.includes("VERIFIED")) {
          importVerified = true;
        }
      }
      if (c && c.provenance === "USER_ENTERED") {
        const certs = Array.isArray(c.certainties) ? c.certainties : [];
        if (
          certs.includes("HYPOTHESIS") &&
          certs.includes("ASSERTED") &&
          certs.includes("VERIFIED") &&
          certs.includes("DISPUTED")
        ) {
          userAll = true;
        }
      }
    }
    if (importVerified) {
      fail(
        `${fileName}: spec.provenancePolicy.allowedCombinations must NEVER pair IMPORTED with VERIFIED (R4.4 / R8 invariant)`,
      );
    }
    if (!userAll) {
      fail(
        `${fileName}: spec.provenancePolicy.allowedCombinations must allow USER_ENTERED with all four certainties`,
      );
    }
  }

  const auditMap = [
    ["spec.auditClassOnEventCreate", "consent"],
    ["spec.auditActionOnEventCreate", "event.created"],
    ["spec.auditClassOnEventUpdate", "consent"],
    ["spec.auditActionOnEventUpdate", "event.updated"],
    ["spec.auditClassOnEventDelete", "consent"],
    ["spec.auditActionOnEventDelete", "event.deleted"],
    ["spec.auditClassOnClaimCreate", "consent"],
    ["spec.auditActionOnClaimCreate", "claim.created"],
    ["spec.auditClassOnClaimUpdate", "consent"],
    ["spec.auditActionOnClaimUpdate", "claim.updated"],
    ["spec.auditClassOnClaimVerify", "consent"],
    ["spec.auditActionOnClaimVerify", "claim.verified"],
    ["spec.auditClassOnClaimDispute", "consent"],
    ["spec.auditActionOnClaimDispute", "claim.disputed"],
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
  checkEventClaimPolicy();
  checkChartMirror();
  if (violations === 0) {
    console.log("[event-claim-config] OK");
    process.exit(0);
  } else {
    console.error(`[event-claim-config] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();
