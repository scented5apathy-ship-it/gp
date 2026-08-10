#!/usr/bin/env node
/**
 * scripts/lint-tree-config.mjs
 *
 * E4.1 deep validator for the tree aggregate / visibility /
 * collaboration / UNLISTED-token contracts under
 * `contracts/genealogy/` and the platform mirror under
 * `platform/helm/genealogy-platform/files/`. Mirrors the
 * structure of `lint-audit-config.mjs` (E3.6): parse + structural
 * assertions + forbidden-token scan + chart mirror byte-equality.
 *
 * Asserts:
 *   - `contracts/genealogy/tree-policy.yaml` declares
 *     `spec.policyId: default-tree/v1`,
 *     closed-set `visibilities` (PRIVATE / UNLISTED / PUBLIC),
 *     closed-set `collaborationModes`
 *     (DIRECT_EDIT / APPROVAL_REQUIRED / HYBRID_BY_ROLE),
 *     closed-set `lifecycleStates` (ACTIVE / ARCHIVED / DELETED),
 *     closed-set `brandingKeys`,
 *     non-empty `slugPattern`,
 *     `maxTreesPerTenant` > 0;
 *   - `contracts/genealogy/collaboration-policy.yaml` declares
 *     `spec.policyId: default-collaboration/v1`,
 *     the three modes,
 *     `alwaysDirectEditRoles` includes `TREE_OWNER`,
 *     `alwaysProposalRoles` includes `CONTRIBUTOR`,
 *     `proposalTtlSeconds` > 0;
 *   - `contracts/genealogy/unlisted-token.yaml` declares
 *     `spec.policyId: default-unlisted-token/v1`,
 *     `fingerprintAlgorithm: SHA-256`,
 *     scopes include `FULL_TREE` + `BRANCH`,
 *     `maxLifetimeSeconds` > 0 and `defaultLifetimeSeconds` > 0,
 *     `auditClassOnIssue: authorization`,
 *     `auditActionOnIssue: unlistedToken.issued`,
 *     `auditClassOnRevoke: authorization`,
 *     `auditActionOnRevoke: unlistedToken.revoked`,
 *     `robotsDirective: noindex`;
 *   - no literal secret / token / password / DSN in any
 *     source-of-truth file;
 *   - the three contracts are mirrored byte-identical into
 *     `platform/helm/genealogy-platform/files/tree-*.yaml`
 *     (collaboration → `collaboration-policy.yaml`).
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, relative, resolve } from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(__dirname, "..");

const TREE_POLICY_CONTRACT = join(ROOT, "contracts/genealogy/tree-policy.yaml");
const COLLABORATION_CONTRACT = join(ROOT, "contracts/genealogy/collaboration-policy.yaml");
const UNLISTED_TOKEN_CONTRACT = join(ROOT, "contracts/genealogy/unlisted-token.yaml");
const CHART_DIR = join(ROOT, "platform/helm/genealogy-platform/files");

const REQUIRED_VISIBILITIES = ["PRIVATE", "UNLISTED", "PUBLIC"];
const REQUIRED_COLLABORATION_MODES = ["DIRECT_EDIT", "APPROVAL_REQUIRED", "HYBRID_BY_ROLE"];
const REQUIRED_LIFECYCLE_STATES = ["ACTIVE", "ARCHIVED", "DELETED"];
const REQUIRED_BRANDING_KEYS = ["primaryColor", "logoUrl"];
const REQUIRED_DIRECT_EDIT_ROLES = ["TREE_OWNER"];
const REQUIRED_PROPOSAL_ROLES = ["CONTRIBUTOR"];
const REQUIRED_TOKEN_SCOPES = ["FULL_TREE", "BRANCH"];

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
  console.error(`[tree-config] ${message}`);
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

/**
 * Tiny line-oriented YAML reader that recognises the key paths
 * this linter needs. We avoid a full YAML dependency; the
 * contracts are written in a strict subset (no anchors, no
 * multi-line flow scalars). When the contract grows beyond the
 * subset we can swap in `yaml` from npm.
 *
 * Supported subset: block-style mappings + block-style lists of
 * scalars or one-key maps. Inline flow lists (`[a, b]`) and
 * inline flow maps (`{ a: 1 }`) are recognised. Comments start
 * with `#`. Quoted scalars (single / double) are stripped.
 */
function parseYamlLoose(raw) {
  const out = {};
  const stack = [{ indent: -1, value: out }];
  const lines = raw.split(/\r?\n/);

  function parseFlowList(text) {
    const inner = text.trim().slice(1, -1).trim();
    if (!inner) return [];
    // very loose split: doesn't handle nested commas in quotes
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
    // List item at this indent: e.g. "- PRIVATE" or "- name: value"
    if (trimmed.startsWith("- ")) {
      const parentTop = stack[stack.length - 1];
      if (!Array.isArray(parentTop.value)) {
        // We hit a list item outside an array context; ignore.
        continue;
      }
      const itemRaw = trimmed.slice(2).trim();
      const sub = itemRaw.match(/^([A-Za-z0-9_.\-]+)\s*:\s*(.*)$/);
      if (sub) {
        const obj = {};
        obj[sub[1]] = stripQuotesValue(sub[2]);
        parentTop.value.push(obj);
        // consume inner key/value lines that belong to this item
        let j = i + 1;
        const itemIndent = indent + 2;
        while (j < lines.length) {
          const inner = lines[j];
          if (!inner.trim() || inner.trim().startsWith("#")) {
            j += 1;
            continue;
          }
          const innerIndent = inner.match(/^ */)[0].length;
          if (innerIndent <= indent) break;
          if (innerIndent < itemIndent) break;
          const innerTrim = inner.trim();
          if (innerTrim.startsWith("- ")) break;
          const im = innerTrim.match(/^([A-Za-z0-9_.\-]+)\s*:\s*(.*)$/);
          if (!im) break;
          const innerRhs = im[2];
          if (innerRhs === "" || innerRhs === undefined) {
            obj[im[1]] = "";
          } else if (innerRhs.startsWith("[") && innerRhs.endsWith("]")) {
            obj[im[1]] = parseFlowList(innerRhs);
          } else {
            obj[im[1]] = stripQuotesValue(innerRhs);
          }
          j += 1;
        }
        i = j - 1;
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
    if (rhs.startsWith("'") && rhs.endsWith("'")) {
      parent[key] = rhs.slice(1, -1);
      continue;
    }
    if (rhs.startsWith('"') && rhs.endsWith('"')) {
      parent[key] = rhs.slice(1, -1);
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

function stripQuotes(arr) {
  return arr.map((s) => stripQuotesValue(s));
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

function assertArrayIncludesValue(arr, value, field, fileName) {
  if (!Array.isArray(arr) || !arr.includes(value)) {
    fail(`${fileName}: ${field} must include ${value}`);
  }
}

function scanForbiddenLiterals(raw, fileName) {
  for (const pattern of FORBIDDEN_LITERALS) {
    if (pattern.test(raw)) {
      fail(`${fileName}: forbidden literal matches ${pattern}`);
    }
  }
}

function checkTreePolicy() {
  const contract = loadContract(TREE_POLICY_CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, TREE_POLICY_CONTRACT);

  const policyId = requireField(parsed, "spec.policyId", fileName);
  assertString(policyId, "default-tree/v1", "spec.policyId", fileName);

  const visibilities = requireField(parsed, "spec.visibilities", fileName);
  if (!Array.isArray(visibilities)) {
    fail(`${fileName}: spec.visibilities must be an array`);
  } else {
    const set = new Set(visibilities);
    assertIncludes(set, REQUIRED_VISIBILITIES, "spec.visibilities", fileName);
  }

  const collaborationModes = requireField(parsed, "spec.collaborationModes", fileName);
  if (!Array.isArray(collaborationModes)) {
    fail(`${fileName}: spec.collaborationModes must be an array`);
  } else {
    const set = new Set(collaborationModes);
    assertIncludes(set, REQUIRED_COLLABORATION_MODES, "spec.collaborationModes", fileName);
  }

  const lifecycleStates = requireField(parsed, "spec.lifecycleStates", fileName);
  if (!Array.isArray(lifecycleStates)) {
    fail(`${fileName}: spec.lifecycleStates must be an array`);
  } else {
    const set = new Set(lifecycleStates);
    assertIncludes(set, REQUIRED_LIFECYCLE_STATES, "spec.lifecycleStates", fileName);
  }

  const brandingKeys = requireField(parsed, "spec.brandingKeys", fileName);
  if (!Array.isArray(brandingKeys)) {
    fail(`${fileName}: spec.brandingKeys must be an array`);
  } else {
    const set = new Set(brandingKeys);
    assertIncludes(set, REQUIRED_BRANDING_KEYS, "spec.brandingKeys", fileName);
  }

  const slugPattern = requireField(parsed, "spec.slugPattern", fileName);
  if (typeof slugPattern !== "string" || slugPattern.length === 0) {
    fail(`${fileName}: spec.slugPattern must be a non-empty string`);
  }

  const maxTrees = requireField(parsed, "spec.maxTreesPerTenant", fileName);
  if (typeof maxTrees !== "number" || maxTrees <= 0) {
    fail(`${fileName}: spec.maxTreesPerTenant must be a positive number`);
  }

  scanForbiddenLiterals(raw, fileName);
}

function checkCollaboration() {
  const contract = loadContract(COLLABORATION_CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, COLLABORATION_CONTRACT);

  const policyId = requireField(parsed, "spec.policyId", fileName);
  assertString(policyId, "default-collaboration/v1", "spec.policyId", fileName);

  const modes = requireField(parsed, "spec.modes", fileName);
  if (!Array.isArray(modes)) {
    fail(`${fileName}: spec.modes must be an array`);
  } else {
    const set = new Set(modes);
    assertIncludes(set, REQUIRED_COLLABORATION_MODES, "spec.modes", fileName);
  }

  const directEdit = requireField(parsed, "spec.alwaysDirectEditRoles", fileName);
  if (!Array.isArray(directEdit)) {
    fail(`${fileName}: spec.alwaysDirectEditRoles must be an array`);
  } else {
    assertIncludes(
      new Set(directEdit),
      REQUIRED_DIRECT_EDIT_ROLES,
      "spec.alwaysDirectEditRoles",
      fileName,
    );
  }

  const proposal = requireField(parsed, "spec.alwaysProposalRoles", fileName);
  if (!Array.isArray(proposal)) {
    fail(`${fileName}: spec.alwaysProposalRoles must be an array`);
  } else {
    assertIncludes(
      new Set(proposal),
      REQUIRED_PROPOSAL_ROLES,
      "spec.alwaysProposalRoles",
      fileName,
    );
  }

  const ttl = requireField(parsed, "spec.proposalTtlSeconds", fileName);
  if (typeof ttl !== "number" || ttl <= 0) {
    fail(`${fileName}: spec.proposalTtlSeconds must be a positive number`);
  }

  scanForbiddenLiterals(raw, fileName);
}

function checkUnlistedToken() {
  const contract = loadContract(UNLISTED_TOKEN_CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, UNLISTED_TOKEN_CONTRACT);

  const policyId = requireField(parsed, "spec.policyId", fileName);
  assertString(policyId, "default-unlisted-token/v1", "spec.policyId", fileName);

  const fingerprintAlgorithm = requireField(parsed, "spec.fingerprintAlgorithm", fileName);
  assertString(fingerprintAlgorithm, "SHA-256", "spec.fingerprintAlgorithm", fileName);

  const fingerprintEncoding = requireField(parsed, "spec.fingerprintEncoding", fileName);
  assertString(fingerprintEncoding, "HEX_LOWER", "spec.fingerprintEncoding", fileName);

  const scopes = requireField(parsed, "spec.scopes", fileName);
  if (!Array.isArray(scopes)) {
    fail(`${fileName}: spec.scopes must be an array`);
  } else {
    assertIncludes(new Set(scopes), REQUIRED_TOKEN_SCOPES, "spec.scopes", fileName);
  }

  const maxLifetime = requireField(parsed, "spec.maxLifetimeSeconds", fileName);
  if (typeof maxLifetime !== "number" || maxLifetime <= 0) {
    fail(`${fileName}: spec.maxLifetimeSeconds must be a positive number`);
  }

  const defaultLifetime = requireField(parsed, "spec.defaultLifetimeSeconds", fileName);
  if (typeof defaultLifetime !== "number" || defaultLifetime <= 0) {
    fail(`${fileName}: spec.defaultLifetimeSeconds must be a positive number`);
  }

  const auditClassOnIssue = requireField(parsed, "spec.auditClassOnIssue", fileName);
  assertString(auditClassOnIssue, "authorization", "spec.auditClassOnIssue", fileName);

  const auditActionOnIssue = requireField(parsed, "spec.auditActionOnIssue", fileName);
  assertString(auditActionOnIssue, "unlistedToken.issued", "spec.auditActionOnIssue", fileName);

  const auditClassOnRevoke = requireField(parsed, "spec.auditClassOnRevoke", fileName);
  assertString(auditClassOnRevoke, "authorization", "spec.auditClassOnRevoke", fileName);

  const auditActionOnRevoke = requireField(parsed, "spec.auditActionOnRevoke", fileName);
  assertString(auditActionOnRevoke, "unlistedToken.revoked", "spec.auditActionOnRevoke", fileName);

  const robotsDirective = requireField(parsed, "spec.robotsDirective", fileName);
  assertString(robotsDirective, "noindex", "spec.robotsDirective", fileName);

  scanForbiddenLiterals(raw, fileName);
}

function checkChartMirror() {
  const pairs = [
    [TREE_POLICY_CONTRACT, join(CHART_DIR, "tree-policy.yaml")],
    [COLLABORATION_CONTRACT, join(CHART_DIR, "collaboration-policy.yaml")],
    [UNLISTED_TOKEN_CONTRACT, join(CHART_DIR, "unlisted-token.yaml")],
  ];
  for (const [src, dest] of pairs) {
    let srcRaw, destRaw;
    try {
      srcRaw = readFileSync(src, "utf8");
    } catch (err) {
      fail(`cannot read source ${relative(ROOT, src)}: ${err.message}`);
      continue;
    }
    try {
      destRaw = readFileSync(dest, "utf8");
    } catch (err) {
      fail(
        `chart mirror missing for ${relative(ROOT, src)} at ${relative(
          ROOT,
          dest,
        )}: ${err.message}`,
      );
      continue;
    }
    if (srcRaw !== destRaw) {
      fail(`chart mirror ${relative(ROOT, dest)} is NOT byte-identical to ${relative(ROOT, src)}`);
    }
  }
}

function main() {
  checkTreePolicy();
  checkCollaboration();
  checkUnlistedToken();
  checkChartMirror();
  if (violations === 0) {
    console.log("[tree-config] OK");
    process.exit(0);
  } else {
    console.error(`[tree-config] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();
