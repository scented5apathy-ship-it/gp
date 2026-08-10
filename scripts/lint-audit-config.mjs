#!/usr/bin/env node
/**
 * scripts/lint-audit-config.mjs
 *
 * E3.6 deep validator for the audit foundation contract under
 * `contracts/audit/` and the platform mirror under
 * `platform/helm/genealogy-platform/files/`. Mirrors the structure
 * of `lint-trusted-context.mjs` (E3.5): parse + structural
 * assertions + forbidden-token scan + chart mirror
 * byte-equality.
 *
 * Asserts:
 *   - `contracts/audit/policy.yaml` declares
 *     `spec.policyId: default-audit/v1`,
 *     exactly the 6 audit classes required by `tasks.md` line 310
 *     (auth / authorization / policy / support / download /
 *     consent),
 *     the closed-set `actions` catalogue mapping every action to
 *     a known audit class,
 *     `spec.integrity.hashAlgorithm: SHA-256`,
 *     `spec.integrity.genesisHash` is 64 zeros,
 *     `spec.integrity.verificationCadence: scheduled`,
 *     `spec.integrity.tamperEvidenceMarker: INTEGRITY_BREACH`,
 *     `spec.redactionTriggerClasses` contains
 *     `PII.QUASI_ID` + `PII.SENSITIVE` + `GENETIC.RAW`;
 *   - `contracts/audit/retention.yaml` declares
 *     `spec.policyId: default-audit-retention/v1`,
 *     all 6 per-class tiers,
 *     `spec.legalHold.enforcement: HARD_BLOCK`,
 *     `spec.sweep.requireLegalHoldCheck: true`,
 *     `spec.deletionEvidence.required: true`;
 *   - `contracts/audit/redaction.yaml` declares
 *     `rawDna` + `biography` in `denyKeys`,
 *     `email` + `phone` in `maskKeys`,
 *     `jwt` + `bearer` + `dnaSequence` in `scrubPatterns`,
 *     `maxMetadataSizeBytes` > 0,
 *     `overflowBehavior` set;
 *   - `contracts/audit/export.yaml` declares
 *     `spec.policyId: default-audit-export/v1`,
 *     `spec.responseEnvelope.manifest.required: true`,
 *     `spec.signedUrl.requiresDpoRole: true`,
 *     `spec.signedUrl.ttlSeconds` > 0 and ≤ 3600,
 *     `spec.signedUrl.storageClass: S3_WORM`,
 *     `spec.accessLog.auditClass: support`;
 *   - no literal secret / token / password / DSN in any
 *     source-of-truth file;
 *   - the 4 contracts are mirrored byte-identical into
 *     `platform/helm/genealogy-platform/files/audit-*.yaml`.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");
const CONTRACTS_DIR = join(ROOT, "contracts", "audit");
const HELM_FILES_DIR = join(ROOT, "platform", "helm", "genealogy-platform", "files");

const POLICY_CONTRACT = "policy.yaml";
const RETENTION_CONTRACT = "retention.yaml";
const REDACTION_CONTRACT = "redaction.yaml";
const EXPORT_CONTRACT = "export.yaml";

const POLICY_MIRROR = "audit-policy.yaml";
const RETENTION_MIRROR = "audit-retention.yaml";
const REDACTION_MIRROR = "audit-redaction.yaml";
const EXPORT_MIRROR = "audit-export.yaml";

const REQUIRED_AUDIT_CLASSES = [
  "auth",
  "authorization",
  "policy",
  "support",
  "download",
  "consent",
];

const REQUIRED_REDACTION_TRIGGERS = ["PII.QUASI_ID", "PII.SENSITIVE", "GENETIC.RAW"];

const REQUIRED_REDACTION_DENY_KEYS = ["rawDna", "biography"];
const REQUIRED_REDACTION_MASK_KEYS = ["email", "phone"];
const REQUIRED_REDACTION_SCRUB_PATTERNS = ["jwt", "bearer", "dnaSequence"];

const FORBIDDEN_LITERALS = [
  /eyJ[A-Za-z0-9._\-]{8,}/,
  /AKIA[0-9A-Z]{16}/,
  /-----BEGIN [A-Z ]*PRIVATE KEY-----/,
  /\bpassword\s*[:=]\s*["'][^"']{6,}/i,
  /\bpostgres(?:ql)?:\/\/[^:\s]+:[^@\s]+@/i,
  /\bclient[_-]?secret\s*[:=]\s*["'][^"']{6,}/i,
];

let violations = 0;
const messages = [];

function fail(message) {
  violations += 1;
  messages.push(message);
}

function loadContract(fileName) {
  const path = join(CONTRACTS_DIR, fileName);
  if (!existsSync(path)) {
    fail(`missing contract file: ${relative(ROOT, path)}`);
    return null;
  }
  const raw = readFileSync(path, "utf8");
  let parsed;
  try {
    parsed = YAML.parse(raw);
  } catch (err) {
    fail(`invalid YAML in ${relative(ROOT, path)}: ${err.message}`);
    return null;
  }
  return { raw, parsed, path };
}

function requireField(obj, path, fileName) {
  const segments = path.split(".");
  let cursor = obj;
  for (const segment of segments) {
    if (cursor == null || typeof cursor !== "object") {
      fail(`${fileName}: missing or invalid spec.${path}`);
      return undefined;
    }
    cursor = cursor[segment];
  }
  if (cursor === undefined || cursor === null) {
    fail(`${fileName}: missing spec.${path}`);
    return undefined;
  }
  return cursor;
}

function assertString(value, expected, path, fileName) {
  if (value !== expected) {
    fail(`${fileName}: spec.${path} must be "${expected}", got "${value}"`);
  }
}

function assertIncludes(set, required, path, fileName) {
  for (const item of required) {
    if (!set.has(item)) {
      fail(`${fileName}: spec.${path} missing "${item}"`);
    }
  }
}

function scanForbiddenLiterals(raw, fileName) {
  for (const pattern of FORBIDDEN_LITERALS) {
    if (pattern.test(raw)) {
      fail(`${fileName}: forbidden literal detected: ${pattern}`);
    }
  }
}

function checkPolicy() {
  const contract = loadContract(POLICY_CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = POLICY_CONTRACT;

  const policyId = requireField(parsed, "spec.policyId", fileName);
  assertString(policyId, "default-audit/v1", "spec.policyId", fileName);

  const auditClasses = requireField(parsed, "spec.auditClasses", fileName);
  if (!Array.isArray(auditClasses) || auditClasses.length === 0) {
    fail(`${fileName}: spec.auditClasses must be a non-empty array`);
  } else {
    const classIds = new Set(auditClasses.map((c) => c.id));
    assertIncludes(classIds, REQUIRED_AUDIT_CLASSES, "spec.auditClasses", fileName);
    for (const required of REQUIRED_AUDIT_CLASSES) {
      const cls = auditClasses.find((c) => c.id === required);
      if (cls && (typeof cls.minRetentionDays !== "number" || cls.minRetentionDays < 1)) {
        fail(
          `${fileName}: spec.auditClasses.${required}.minRetentionDays must be a positive integer`,
        );
      }
    }
  }

  const actions = requireField(parsed, "spec.actions", fileName);
  if (Array.isArray(actions) && actions.length > 0) {
    const classIdSet = new Set((auditClasses || []).map((c) => c.id));
    for (const action of actions) {
      if (!action.id || typeof action.id !== "string") {
        fail(`${fileName}: spec.actions[] must each declare a string id`);
        continue;
      }
      if (!classIdSet.has(action.auditClass)) {
        fail(
          `${fileName}: spec.actions.${action.id}.auditClass references unknown class "${action.auditClass}"`,
        );
      }
    }
  } else {
    fail(`${fileName}: spec.actions must be a non-empty array`);
  }

  const hashAlgorithm = requireField(parsed, "spec.integrity.hashAlgorithm", fileName);
  assertString(hashAlgorithm, "SHA-256", "spec.integrity.hashAlgorithm", fileName);

  const genesisHash = requireField(parsed, "spec.integrity.genesisHash", fileName);
  if (genesisHash !== "0".repeat(64)) {
    fail(`${fileName}: spec.integrity.genesisHash must be 64 zeros (SHA-256 genesis)`);
  }

  const verificationCadence = requireField(parsed, "spec.integrity.verificationCadence", fileName);
  assertString(verificationCadence, "scheduled", "spec.integrity.verificationCadence", fileName);

  const tamperMarker = requireField(parsed, "spec.integrity.tamperEvidenceMarker", fileName);
  assertString(tamperMarker, "INTEGRITY_BREACH", "spec.integrity.tamperEvidenceMarker", fileName);

  const triggers = requireField(parsed, "spec.redactionTriggerClasses", fileName);
  if (!Array.isArray(triggers) || triggers.length === 0) {
    fail(`${fileName}: spec.redactionTriggerClasses must be a non-empty array`);
  } else {
    const triggerSet = new Set(triggers);
    assertIncludes(
      triggerSet,
      REQUIRED_REDACTION_TRIGGERS,
      "spec.redactionTriggerClasses",
      fileName,
    );
  }

  scanForbiddenLiterals(raw, fileName);
}

function checkRetention() {
  const contract = loadContract(RETENTION_CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = RETENTION_CONTRACT;

  const policyId = requireField(parsed, "spec.policyId", fileName);
  assertString(policyId, "default-audit-retention/v1", "spec.policyId", fileName);

  const perClass = requireField(parsed, "spec.perClassTiers", fileName);
  if (typeof perClass !== "object" || perClass === null || Array.isArray(perClass)) {
    fail(`${fileName}: spec.perClassTiers must be a map keyed by audit class`);
  } else {
    const classIds = new Set(Object.keys(perClass));
    assertIncludes(classIds, REQUIRED_AUDIT_CLASSES, "spec.perClassTiers", fileName);
  }

  const legalHoldEnforcement = requireField(parsed, "spec.legalHold.enforcement", fileName);
  assertString(legalHoldEnforcement, "HARD_BLOCK", "spec.legalHold.enforcement", fileName);

  const requireLegalHoldCheck = requireField(parsed, "spec.sweep.requireLegalHoldCheck", fileName);
  if (requireLegalHoldCheck !== true) {
    fail(`${fileName}: spec.sweep.requireLegalHoldCheck must be true`);
  }

  const requireIntegrityCheck = requireField(parsed, "spec.sweep.requireIntegrityCheck", fileName);
  if (requireIntegrityCheck !== true) {
    fail(`${fileName}: spec.sweep.requireIntegrityCheck must be true`);
  }

  const deletionEvidenceRequired = requireField(parsed, "spec.deletionEvidence.required", fileName);
  if (deletionEvidenceRequired !== true) {
    fail(`${fileName}: spec.deletionEvidence.required must be true`);
  }

  scanForbiddenLiterals(raw, fileName);
}

function checkRedaction() {
  const contract = loadContract(REDACTION_CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = REDACTION_CONTRACT;

  const denyKeys = requireField(parsed, "spec.denyKeys", fileName);
  if (Array.isArray(denyKeys)) {
    const denySet = new Set(denyKeys);
    assertIncludes(denySet, REQUIRED_REDACTION_DENY_KEYS, "spec.denyKeys", fileName);
  } else {
    fail(`${fileName}: spec.denyKeys must be a non-empty array`);
  }

  const maskKeys = requireField(parsed, "spec.maskKeys", fileName);
  if (Array.isArray(maskKeys)) {
    const maskSet = new Set(maskKeys);
    assertIncludes(maskSet, REQUIRED_REDACTION_MASK_KEYS, "spec.maskKeys", fileName);
  } else {
    fail(`${fileName}: spec.maskKeys must be a non-empty array`);
  }

  const scrubPatterns = requireField(parsed, "spec.scrubPatterns", fileName);
  if (Array.isArray(scrubPatterns) && scrubPatterns.length > 0) {
    const scrubNames = new Set(scrubPatterns.map((p) => p.name));
    assertIncludes(scrubNames, REQUIRED_REDACTION_SCRUB_PATTERNS, "spec.scrubPatterns", fileName);
  } else {
    fail(`${fileName}: spec.scrubPatterns must be a non-empty array`);
  }

  const maxMetadataSize = requireField(parsed, "spec.maxMetadataSizeBytes", fileName);
  if (typeof maxMetadataSize !== "number" || maxMetadataSize <= 0) {
    fail(`${fileName}: spec.maxMetadataSizeBytes must be a positive number`);
  }

  const overflow = requireField(parsed, "spec.overflowBehavior", fileName);
  if (typeof overflow !== "string" || overflow.trim() === "") {
    fail(`${fileName}: spec.overflowBehavior must be a non-empty string`);
  }

  scanForbiddenLiterals(raw, fileName);
}

function checkExport() {
  const contract = loadContract(EXPORT_CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = EXPORT_CONTRACT;

  const policyId = requireField(parsed, "spec.policyId", fileName);
  assertString(policyId, "default-audit-export/v1", "spec.policyId", fileName);

  const manifestRequired = requireField(
    parsed,
    "spec.responseEnvelope.manifest.required",
    fileName,
  );
  if (manifestRequired !== true) {
    fail(`${fileName}: spec.responseEnvelope.manifest.required must be true`);
  }

  const requiresDpoRole = requireField(parsed, "spec.signedUrl.requiresDpoRole", fileName);
  if (requiresDpoRole !== true) {
    fail(`${fileName}: spec.signedUrl.requiresDpoRole must be true`);
  }

  const ttlSeconds = requireField(parsed, "spec.signedUrl.ttlSeconds", fileName);
  if (typeof ttlSeconds !== "number" || ttlSeconds <= 0 || ttlSeconds > 3600) {
    fail(`${fileName}: spec.signedUrl.ttlSeconds must be in (0, 3600]`);
  }

  const storageClass = requireField(parsed, "spec.signedUrl.storageClass", fileName);
  assertString(storageClass, "S3_WORM", "spec.signedUrl.storageClass", fileName);

  const accessLogClass = requireField(parsed, "spec.accessLog.auditClass", fileName);
  assertString(accessLogClass, "support", "spec.accessLog.auditClass", fileName);

  scanForbiddenLiterals(raw, fileName);
}

function checkMirror(contractFile, mirrorFile) {
  const contractPath = join(CONTRACTS_DIR, contractFile);
  const mirrorPath = join(HELM_FILES_DIR, mirrorFile);
  if (!existsSync(mirrorPath)) {
    fail(`chart mirror missing — expected ${relative(ROOT, mirrorPath)} (E3.6 contract)`);
    return;
  }
  if (!existsSync(contractPath)) {
    return;
  }
  const a = readFileSync(contractPath, "utf8");
  const b = readFileSync(mirrorPath, "utf8");
  if (a !== b) {
    fail(
      `chart mirror drift — ${relative(ROOT, contractPath)} differs from ${relative(ROOT, mirrorPath)}`,
    );
  }
}

checkPolicy();
checkRetention();
checkRedaction();
checkExport();
checkMirror(POLICY_CONTRACT, POLICY_MIRROR);
checkMirror(RETENTION_CONTRACT, RETENTION_MIRROR);
checkMirror(REDACTION_CONTRACT, REDACTION_MIRROR);
checkMirror(EXPORT_CONTRACT, EXPORT_MIRROR);

if (violations === 0) {
  console.log("[audit] OK — E3.6 audit source-of-truth files conform to contract");
  process.exit(0);
} else {
  console.error(`[audit] ${violations} violation(s) — see messages above`);
  for (const m of messages) {
    console.error(`  - ${m}`);
  }
  process.exit(1);
}
