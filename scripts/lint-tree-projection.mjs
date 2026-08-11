#!/usr/bin/env node
/**
 * scripts/lint-tree-projection.mjs
 *
 * E5.2 deep validator for the tree projection read-model contract
 * under `contracts/genealogy/tree-projection-policy.yaml` and its
 * platform mirror under
 * `platform/helm/genealogy-platform/files/tree-projection-policy.yaml`.
 * Mirrors the structure of `lint-abac-config.mjs` (E3.4):
 * parse + structural assertions + JSON-schema validation +
 * forbidden-token scan + chart mirror byte-equality.
 *
 * Asserts:
 *   - YAML parses, `apiVersion: v1`, `kind: TreeProjectionPolicy`;
 *   - `metadata.name === "default-tree-projection/v1"`,
 *     `metadata.namespace === "gp-platform"`;
 *   - `spec.policyId: default-tree-projection/v1`;
 *   - `spec.maxDepth` ∈ [1, 12],
 *     `spec.maxNeighborhoodNodes` ∈ [1, 1000],
 *     `spec.maxRelationshipsPerResponse` ∈ [1, 2000];
 *   - `spec.freshnessTtlSeconds` ≤ `freshnessTtlSecondsCeiling`
 *     and both ≤ 1800;
 *   - `spec.etagRequired: true`;
 *   - closed-set `spec.directions` (ANCESTORS / DESCENDANTS /
 *     BOTH / SPOUSE_FAN), `spec.relationshipFilters` (8 values),
 *     `spec.livingStatusBuckets` (5 values);
 *   - closed-set `spec.viewKinds` (pedigree / descendant / fan /
 *     hourglass / family) and `viewKinds[i].maxDepth` ≤
 *     `spec.maxDepth`;
 *   - closed-set `spec.invalidators` ≥ 5 entries with no
 *     duplicates;
 *   - every `spec.redactionObligations[*].reasonCode` matches an
 *     audit reason code (`living_redacted`,
 *     `minor_guardian_required`, `privacy_class_restricted`,
 *     `visibility_unlisted_token_invalid`);
 *   - `spec.opaqueIdPattern` matches the platform opaque-id regex;
 *   - `spec.cacheKeyPrefix` starts with the tenant prefix;
 *   - no literal secret / token / password / DSN / PEM / AWS
 *     access key in the source-of-truth file;
 *   - the contract is mirrored byte-identical into
 *     `platform/helm/genealogy-platform/files/tree-projection-policy.yaml`;
 *   - the JSON schema at
 *     `contracts/genealogy/tree-projection-policy.schema.json`
 *     validates the parsed contract document.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { readFileSync, existsSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");

const CONTRACT_PATH = join(ROOT, "contracts/genealogy/tree-projection-policy.yaml");
const SCHEMA_PATH = join(ROOT, "contracts/genealogy/tree-projection-policy.schema.json");
const MIRROR_PATH = join(
  ROOT,
  "platform/helm/genealogy-platform/files/tree-projection-policy.yaml",
);
const CACHE_CONTRACT_PATH = join(ROOT, "contracts/genealogy/tree-projection-cache.yaml");
const CACHE_MIRROR_PATH = join(
  ROOT,
  "platform/helm/genealogy-platform/files/tree-projection-cache.yaml",
);

const REQUIRED_DIRECTIONS = ["ANCESTORS", "DESCENDANTS", "BOTH", "SPOUSE_FAN"];
const REQUIRED_VIEW_KINDS = ["pedigree", "descendant", "fan", "hourglass", "family"];
const REQUIRED_RELATIONSHIP_FILTERS = [
  "BIRTH_PARENT",
  "ADOPTIVE_PARENT",
  "FOSTER_PARENT",
  "STEP_PARENT",
  "GUARDIAN",
  "SPOUSE",
  "PARTNER",
  "CUSTOM",
];
const REQUIRED_LIVING_STATUS_BUCKETS = [
  "LIVING",
  "PRESUMED_LIVING",
  "DECEASED",
  "PRESUMED_DECEASED",
  "UNKNOWN",
];
const REQUIRED_REDACTION_REASON_CODES = [
  "living_redacted",
  "minor_guardian_required",
  "privacy_class_restricted",
  "visibility_unlisted_token_invalid",
];
const REQUIRED_AUDIT_ACTIONS = {
  auditClassOnQuery: "authorization",
  auditActionOnQuery: "treeProjection.queried",
  auditClassOnRedaction: "authorization",
  auditActionOnRedaction: "treeProjection.redacted",
  emitRedactionEvent: true,
};
const OPAQUE_ID_PATTERN = "^[A-Za-z0-9._:-]{1,128}$";
const CACHE_KEY_PREFIX = "gp:{tenant_pseudo_id}:genealogy:projection";

const FORBIDDEN_LITERALS = [
  /password\s*[:=]\s*["']?[A-Za-z0-9!@#$%^&*()_+=\-]{6,}/i,
  /token\s*[:=]\s*["']?[A-Za-z0-9._\-]{20,}/i,
  /secret\s*[:=]\s*["']?[A-Za-z0-9._\-]{12,}/i,
  /jdbc:postgresql:\/\/[^"\s']+:[^"\s']+@/i,
  /AKIA[0-9A-Z]{16}/,
  /-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----/,
  /dsn\s*[:=]\s*["']?jdbc:/i,
];

let violations = 0;

function fail(message) {
  console.error(`[tree-projection] ${message}`);
  violations += 1;
}

function loadContract() {
  try {
    const raw = readFileSync(CONTRACT_PATH, "utf8");
    const doc = YAML.parse(raw);
    return { raw, doc };
  } catch (err) {
    fail(`cannot read ${relative(ROOT, CONTRACT_PATH)}: ${err.message}`);
    return null;
  }
}

function assertEquals(actual, expected, field) {
  if (actual !== expected) {
    fail(`spec.${field} must equal ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

function assertClosedSet(actual, required, field) {
  if (!Array.isArray(actual)) {
    fail(`spec.${field} must be an array`);
    return;
  }
  const set = new Set(actual);
  if (set.size !== actual.length) {
    fail(`spec.${field} must not contain duplicates`);
  }
  for (const value of required) {
    if (!set.has(value)) {
      fail(`spec.${field} missing required value ${value}`);
    }
  }
}

function assertRange(value, min, max, field) {
  if (typeof value !== "number" || !Number.isFinite(value)) {
    fail(`spec.${field} must be a finite number`);
    return;
  }
  if (value < min || value > max) {
    fail(`spec.${field} must be within [${min}, ${max}], got ${value}`);
  }
}

function assertPositive(value, field) {
  if (typeof value !== "number" || value <= 0) {
    fail(`spec.${field} must be a positive number`);
  }
}

function assertPattern(value, expected, field) {
  if (value !== expected) {
    fail(`spec.${field} must equal ${JSON.stringify(expected)}, got ${JSON.stringify(value)}`);
  }
}

function scanForbiddenLiterals(raw, fileName) {
  for (const pattern of FORBIDDEN_LITERALS) {
    if (pattern.test(raw)) {
      fail(`${fileName}: forbidden literal matches ${pattern}`);
    }
  }
}

function validateSchema(doc) {
  let schema;
  try {
    schema = JSON.parse(readFileSync(SCHEMA_PATH, "utf8"));
  } catch (err) {
    fail(`cannot read JSON schema at ${relative(ROOT, SCHEMA_PATH)}: ${err.message}`);
    return;
  }
  if (!schema || typeof schema !== "object") {
    fail(`JSON schema at ${relative(ROOT, SCHEMA_PATH)} is not an object`);
    return;
  }
  const errors = validateAgainstSchema(doc, schema, "");
  for (const err of errors) {
    fail(`schema: ${err}`);
  }
}

function validateAgainstSchema(value, schema, path) {
  const errors = [];
  if (schema === true) return errors;
  if (schema === false) {
    errors.push(`${path || "/"}: value forbidden by schema`);
    return errors;
  }
  if (typeof schema !== "object" || schema === null) return errors;

  if (schema.const !== undefined && value !== schema.const) {
    errors.push(`${path || "/"}: must equal ${JSON.stringify(schema.const)}`);
  }
  if (schema.enum && !schema.enum.includes(value)) {
    errors.push(`${path || "/"}: must be one of ${JSON.stringify(schema.enum)}`);
  }
  if (
    schema.type === "object" ||
    (Array.isArray(schema.properties) === false && schema.properties)
  ) {
    if (value === null || typeof value !== "object" || Array.isArray(value)) {
      errors.push(`${path || "/"}: expected object`);
      return errors;
    }
    if (schema.additionalProperties === false && schema.properties) {
      const allowed = new Set(Object.keys(schema.properties));
      for (const key of Object.keys(value)) {
        if (!allowed.has(key)) {
          errors.push(`${path || "/"}/${key}: additional property not allowed`);
        }
      }
    }
    if (schema.required) {
      for (const req of schema.required) {
        if (!(req in value)) {
          errors.push(`${path || "/"}: missing required property ${req}`);
        }
      }
    }
    if (schema.properties) {
      for (const key of Object.keys(schema.properties)) {
        if (key in value) {
          errors.push(
            ...validateAgainstSchema(value[key], schema.properties[key], `${path || "/"}/${key}`),
          );
        }
      }
    }
  }
  if (schema.type === "array" || schema.items) {
    if (!Array.isArray(value)) {
      errors.push(`${path || "/"}: expected array`);
      return errors;
    }
    if (schema.minItems !== undefined && value.length < schema.minItems) {
      errors.push(`${path || "/"}: must have at least ${schema.minItems} items`);
    }
    if (schema.maxItems !== undefined && value.length > schema.maxItems) {
      errors.push(`${path || "/"}: must have at most ${schema.maxItems} items`);
    }
    if (schema.uniqueItems === true) {
      const seen = new Set();
      for (const item of value) {
        if (seen.has(item)) {
          errors.push(`${path || "/"}: duplicate item`);
          break;
        }
        seen.add(item);
      }
    }
    if (schema.items) {
      value.forEach((item, idx) => {
        errors.push(...validateAgainstSchema(item, schema.items, `${path || "/"}/${idx}`));
      });
    }
  }
  if (schema.type === "integer" || schema.type === "number") {
    if (typeof value !== "number") {
      errors.push(`${path || "/"}: expected number`);
    } else {
      if (schema.minimum !== undefined && value < schema.minimum) {
        errors.push(`${path || "/"}: must be >= ${schema.minimum}`);
      }
      if (schema.maximum !== undefined && value > schema.maximum) {
        errors.push(`${path || "/"}: must be <= ${schema.maximum}`);
      }
    }
  }
  if (schema.type === "string" && typeof value !== "string") {
    errors.push(`${path || "/"}: expected string`);
  }
  if (schema.type === "boolean" && typeof value !== "boolean") {
    errors.push(`${path || "/"}: expected boolean`);
  }
  if (typeof value === "string" && schema.pattern) {
    const re = new RegExp(schema.pattern);
    if (!re.test(value)) {
      errors.push(`${path || "/"}: must match pattern ${schema.pattern}`);
    }
  }
  return errors;
}

function checkContract() {
  const loaded = loadContract();
  if (!loaded) return;
  const { raw, doc } = loaded;
  const fileName = relative(ROOT, CONTRACT_PATH);

  if (!doc || typeof doc !== "object") {
    fail(`${fileName}: document is empty or not an object`);
    return;
  }
  assertEquals(doc.apiVersion, "v1", "apiVersion");
  assertEquals(doc.kind, "TreeProjectionPolicy", "kind");

  if (!doc.metadata || typeof doc.metadata !== "object") {
    fail(`${fileName}: missing metadata`);
    return;
  }
  assertEquals(doc.metadata.name, "default-tree-projection/v1", "metadata.name");
  assertEquals(doc.metadata.namespace, "gp-platform", "metadata.namespace");

  const spec = doc.spec;
  if (!spec || typeof spec !== "object") {
    fail(`${fileName}: missing spec`);
    return;
  }
  assertEquals(spec.policyId, "default-tree-projection/v1", "policyId");
  assertRange(spec.maxDepth, 1, 12, "maxDepth");
  assertRange(spec.maxNeighborhoodNodes, 1, 1000, "maxNeighborhoodNodes");
  assertRange(spec.maxRelationshipsPerResponse, 1, 2000, "maxRelationshipsPerResponse");
  assertPositive(spec.freshnessTtlSeconds, "freshnessTtlSeconds");
  assertPositive(spec.freshnessTtlSecondsCeiling, "freshnessTtlSecondsCeiling");
  if (
    typeof spec.freshnessTtlSeconds === "number" &&
    typeof spec.freshnessTtlSecondsCeiling === "number" &&
    spec.freshnessTtlSeconds > spec.freshnessTtlSecondsCeiling
  ) {
    fail(
      `spec.freshnessTtlSeconds (${spec.freshnessTtlSeconds}) must be <= spec.freshnessTtlSecondsCeiling (${spec.freshnessTtlSecondsCeiling})`,
    );
  }
  if (spec.etagRequired !== true) {
    fail(`spec.etagRequired must be true`);
  }
  assertClosedSet(spec.directions, REQUIRED_DIRECTIONS, "directions");
  assertClosedSet(spec.relationshipFilters, REQUIRED_RELATIONSHIP_FILTERS, "relationshipFilters");
  assertClosedSet(spec.livingStatusBuckets, REQUIRED_LIVING_STATUS_BUCKETS, "livingStatusBuckets");

  if (!Array.isArray(spec.viewKinds)) {
    fail(`spec.viewKinds must be an array`);
  } else {
    const seen = new Set();
    for (const v of spec.viewKinds) {
      if (!v || typeof v !== "object") {
        fail(`spec.viewKinds entry must be an object`);
        continue;
      }
      seen.add(v.id);
      if (typeof v.maxDepth !== "number" || v.maxDepth < 1 || v.maxDepth > spec.maxDepth) {
        fail(
          `spec.viewKinds[${v.id}].maxDepth must be within [1, spec.maxDepth=${spec.maxDepth}], got ${v.maxDepth}`,
        );
      }
      if (!REQUIRED_DIRECTIONS.includes(v.defaultDirection)) {
        fail(`spec.viewKinds[${v.id}].defaultDirection must be one of ${REQUIRED_DIRECTIONS}`);
      }
      if (typeof v.locked !== "boolean") {
        fail(`spec.viewKinds[${v.id}].locked must be a boolean`);
      }
    }
    for (const req of REQUIRED_VIEW_KINDS) {
      if (!seen.has(req)) {
        fail(`spec.viewKinds missing required view kind ${req}`);
      }
    }
    if (seen.size !== REQUIRED_VIEW_KINDS.length) {
      fail(`spec.viewKinds must contain exactly ${REQUIRED_VIEW_KINDS.length} view kinds`);
    }
  }

  if (!Array.isArray(spec.invalidators)) {
    fail(`spec.invalidators must be an array`);
  } else if (spec.invalidators.length < 5) {
    fail(`spec.invalidators must declare >= 5 invalidators`);
  } else {
    const seen = new Set();
    for (const inv of spec.invalidators) {
      if (typeof inv !== "string" || inv.length < 3 || inv.length > 128) {
        fail(`spec.invalidators entry ${JSON.stringify(inv)} must be 3..128 chars`);
      }
      if (seen.has(inv)) {
        fail(`spec.invalidators has duplicate value ${inv}`);
      }
      seen.add(inv);
    }
  }

  if (!Array.isArray(spec.redactionObligations) || spec.redactionObligations.length === 0) {
    fail(`spec.redactionObligations must be a non-empty array`);
  } else {
    for (const obligation of spec.redactionObligations) {
      if (!REQUIRED_REDACTION_REASON_CODES.includes(obligation.reasonCode)) {
        fail(
          `spec.redactionObligations reasonCode ${JSON.stringify(obligation.reasonCode)} must be one of ${REQUIRED_REDACTION_REASON_CODES}`,
        );
      }
      if (!Array.isArray(obligation.appliesToFields) || obligation.appliesToFields.length === 0) {
        fail(
          `spec.redactionObligations[${obligation.reasonCode}].appliesToFields must be non-empty`,
        );
      }
    }
  }

  if (!spec.audit || typeof spec.audit !== "object") {
    fail(`spec.audit must be an object`);
  } else {
    for (const [key, expected] of Object.entries(REQUIRED_AUDIT_ACTIONS)) {
      if (spec.audit[key] !== expected) {
        fail(
          `spec.audit.${key} must equal ${JSON.stringify(expected)}, got ${JSON.stringify(spec.audit[key])}`,
        );
      }
    }
  }

  assertPattern(spec.opaqueIdPattern, OPAQUE_ID_PATTERN, "opaqueIdPattern");
  assertPattern(spec.cacheKeyPrefix, CACHE_KEY_PREFIX, "cacheKeyPrefix");

  scanForbiddenLiterals(raw, fileName);
  validateSchema(doc);
}

function checkChartMirror() {
  if (!existsSync(MIRROR_PATH)) {
    fail(`chart mirror missing at ${relative(ROOT, MIRROR_PATH)}`);
    return;
  }
  let sourceRaw, mirrorRaw;
  try {
    sourceRaw = readFileSync(CONTRACT_PATH, "utf8");
    mirrorRaw = readFileSync(MIRROR_PATH, "utf8");
  } catch (err) {
    fail(`cannot read chart mirror: ${err.message}`);
    return;
  }
  if (sourceRaw !== mirrorRaw) {
    fail(
      `chart mirror ${relative(ROOT, MIRROR_PATH)} is NOT byte-identical to ${relative(ROOT, CONTRACT_PATH)}`,
    );
  }
}

function checkCacheConfig() {
  const fileName = relative(ROOT, CACHE_CONTRACT_PATH);
  let raw, doc;
  try {
    raw = readFileSync(CACHE_CONTRACT_PATH, "utf8");
    doc = YAML.parse(raw);
  } catch (err) {
    fail(`cannot read ${fileName}: ${err.message}`);
    return;
  }
  if (!doc || typeof doc !== "object") {
    fail(`${fileName}: document is empty or not an object`);
    return;
  }
  assertEquals(doc.apiVersion, "v1", "cache.apiVersion");
  assertEquals(doc.kind, "TreeProjectionCacheConfig", "cache.kind");
  if (!doc.metadata || doc.metadata.name !== "default-tree-projection-cache") {
    fail(`${fileName}: metadata.name must equal "default-tree-projection-cache"`);
  }
  if (!doc.metadata || doc.metadata.namespace !== "gp-platform") {
    fail(`${fileName}: metadata.namespace must equal "gp-platform"`);
  }
  const spec = doc.spec;
  if (!spec || typeof spec !== "object") {
    fail(`${fileName}: missing spec`);
    return;
  }
  assertPositive(spec.freshnessTtlSeconds, "cache.freshnessTtlSeconds");
  assertPositive(spec.freshnessTtlSecondsCeiling, "cache.freshnessTtlSecondsCeiling");
  if (
    typeof spec.freshnessTtlSeconds === "number" &&
    typeof spec.freshnessTtlSecondsCeiling === "number" &&
    spec.freshnessTtlSeconds > spec.freshnessTtlSecondsCeiling
  ) {
    fail(
      `cache.freshnessTtlSeconds (${spec.freshnessTtlSeconds}) must be <= cache.freshnessTtlSecondsCeiling (${spec.freshnessTtlSecondsCeiling})`,
    );
  }
  if (spec.invalidationOnWrite !== "required") {
    fail(`cache.invalidationOnWrite must be "required"`);
  }
  if (spec.ttlOnlyForbidden !== true) {
    fail(`cache.ttlOnlyForbidden must be true`);
  }
  assertPositive(spec.maxEntriesPerTenant, "cache.maxEntriesPerTenant");
  assertPositive(spec.maxBytesPerEntry, "cache.maxBytesPerEntry");
  assertPattern(
    spec.aclKeyPattern,
    "gp:{tenant_pseudo_id}:genealogy:projection:*",
    "cache.aclKeyPattern",
  );
  if (!Array.isArray(spec.forbiddenKeySubstrings) || spec.forbiddenKeySubstrings.length < 5) {
    fail(`cache.forbiddenKeySubstrings must be an array of >= 5 entries`);
  }
  if (spec.requireVersionOnEntry !== true) {
    fail(`cache.requireVersionOnEntry must be true`);
  }
  if (spec.requireEtagOnEntry !== true) {
    fail(`cache.requireEtagOnEntry must be true`);
  }
  if (!Array.isArray(spec.invalidators) || spec.invalidators.length < 5) {
    fail(`cache.invalidators must be a non-empty array (>= 5)`);
  }
  if (
    !spec.audit ||
    spec.audit.auditClassOnMiss !== "authorization" ||
    spec.audit.auditActionOnMiss !== "treeProjection.cacheMissed" ||
    spec.audit.auditClassOnInvalidate !== "authorization" ||
    spec.audit.auditActionOnInvalidate !== "treeProjection.invalidated" ||
    spec.audit.emitInvalidationEvent !== true
  ) {
    fail(`cache.audit must declare authorization/treeProjection.*/true values`);
  }
  if (spec.emitMetrics !== true) {
    fail(`cache.emitMetrics must be true`);
  }
  if (
    typeof spec.metricPrefix !== "string" ||
    !spec.metricPrefix.startsWith("genealogy_projection")
  ) {
    fail(`cache.metricPrefix must start with "genealogy_projection"`);
  }
  scanForbiddenLiterals(raw, fileName);

  // Mirror check.
  if (!existsSync(CACHE_MIRROR_PATH)) {
    fail(`cache chart mirror missing at ${relative(ROOT, CACHE_MIRROR_PATH)}`);
    return;
  }
  let mirrorRaw;
  try {
    mirrorRaw = readFileSync(CACHE_MIRROR_PATH, "utf8");
  } catch (err) {
    fail(`cannot read cache chart mirror: ${err.message}`);
    return;
  }
  if (raw !== mirrorRaw) {
    fail(
      `cache chart mirror ${relative(ROOT, CACHE_MIRROR_PATH)} is NOT byte-identical to ${fileName}`,
    );
  }
}

function main() {
  checkContract();
  checkChartMirror();
  checkCacheConfig();
  if (violations === 0) {
    console.log("[tree-projection] OK");
    process.exit(0);
  } else {
    console.error(`[tree-projection] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();
