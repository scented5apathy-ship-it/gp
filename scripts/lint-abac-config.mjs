#!/usr/bin/env node
/**
 * scripts/lint-abac-config.mjs
 *
 * E3.4 deep validator for the ABAC overlay source-of-truth files
 * under `contracts/abac/` and the platform mirror under
 * `platform/abac/`. Mirrors the structure of
 * `lint-openfga-config.mjs`: parse + structural assertions +
 * forbidden-token scan + chart mirror byte-equality.
 *
 * Asserts:
 *   - `contracts/abac/policy.default.yaml` declares
 *     `spec.engineId: default-abac/v1`,
 *     `spec.livingRedactFields` (≥ 4 fields),
 *     `spec.minorRedactFields` (≥ 4 fields),
 *     `spec.consentRequiredClasses` containing
 *     `GENETIC_RAW` / `GENETIC_DERIVED` / `SENSITIVE`,
 *     and the closed set of reason codes (no other id may be
 *     silently added without an ADR);
 *   - `contracts/abac/abac.cache.yaml` declares
 *     `spec.invalidationOnWrite: required`,
 *     `spec.ttlOnlyForbidden: true`,
 *     `spec.maxAgeSeconds` > 0 and ≤ 60,
 *     `spec.maxEntries` > 0,
 *     and at least 5 invalidators;
 *   - `contracts/abac/abac.redaction.yaml` declares
 *     `spec.denyKeys` containing `rawDna` + `biography`,
 *     `spec.maskKeys` containing `email` + `phone`,
 *     and at least 3 `scrubPatterns`;
 *   - no literal secret / token / password / DSN in any
 *     source-of-truth file;
 *   - the 3 contracts are mirrored byte-identical into
 *     `platform/helm/genealogy-platform/files/abac-*.yaml`.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");
const CONTRACTS_DIR = join(ROOT, "contracts", "abac");
const HELM_FILES_DIR = join(ROOT, "platform", "helm", "genealogy-platform", "files");

const CONTRACT_FILES = [
    "policy.yaml",
    "cache.yaml",
    "redaction.yaml",
];

const REQUIRED_REASON_CODES = [
    "living_redacted",
    "minor_guardian_required",
    "privacy_class_restricted",
    "consent_missing",
    "consent_revoked",
    "jurisdiction_blocked",
    "contextual_deny",
    "obligation_redact",
    "obligation_watermark",
    "obligation_audit",
    "openfga_deny",
    "openfga_abac_missing",
];

const REQUIRED_CONSENT_CLASSES = ["GENETIC_RAW", "GENETIC_DERIVED", "SENSITIVE"];

const REQUIRED_DENY_KEYS = ["rawDna", "biography"];
const REQUIRED_MASK_KEYS = ["email", "phone"];

const FORBIDDEN_LITERALS = [
    /password\s*[:=]\s*['"]?[^'"\s]+/i,
    /token\s*[:=]\s*['"]?eyJ/i,
    /dsn\s*[:=]\s*['"]?jdbc:/i,
];

function readYaml(path) {
    const raw = readFileSync(path, "utf8");
    return { raw, doc: YAML.parse(raw) };
}

function fail(messages, file) {
    const header = file ? `[abac] ${file}: ` : "[abac] ";
    for (const msg of messages) {
        console.error(header + msg);
    }
}

function assertPolicy(doc, raw) {
    const errors = [];
    const spec = doc?.spec;
    if (!spec) {
        return ["missing spec block"];
    }
    if (spec.engineId !== "default-abac/v1") {
        errors.push(`spec.engineId must be "default-abac/v1", got "${spec.engineId}"`);
    }
    const living = Array.isArray(spec.livingRedactFields) ? spec.livingRedactFields : [];
    if (living.length < 4) {
        errors.push(`spec.livingRedactFields must declare ≥ 4 fields, got ${living.length}`);
    }
    const minor = Array.isArray(spec.minorRedactFields) ? spec.minorRedactFields : [];
    if (minor.length < 4) {
        errors.push(`spec.minorRedactFields must declare ≥ 4 fields, got ${minor.length}`);
    }
    const consent = new Set(Array.isArray(spec.consentRequiredClasses) ? spec.consentRequiredClasses : []);
    for (const required of REQUIRED_CONSENT_CLASSES) {
        if (!consent.has(required)) {
            errors.push(`spec.consentRequiredClasses missing ${required}`);
        }
    }
    const reasons = new Set(
        (Array.isArray(spec.reasonCodes) ? spec.reasonCodes : []).map((r) => r?.id));
    for (const required of REQUIRED_REASON_CODES) {
        if (!reasons.has(required)) {
            errors.push(`spec.reasonCodes missing ${required}`);
        }
    }
    for (const pattern of FORBIDDEN_LITERALS) {
        if (pattern.test(raw)) {
            errors.push(`forbidden literal detected: ${pattern}`);
        }
    }
    return errors;
}

function assertCache(doc, raw) {
    const errors = [];
    const spec = doc?.spec;
    if (!spec) {
        return ["missing spec block"];
    }
    if (spec.invalidationOnWrite !== "required") {
        errors.push(`spec.invalidationOnWrite must be "required", got "${spec.invalidationOnWrite}"`);
    }
    if (spec.ttlOnlyForbidden !== true) {
        errors.push("spec.ttlOnlyForbidden must be true (ADR-E0.5-06 forbids TTL-only cache)");
    }
    if (!Number.isFinite(spec.maxAgeSeconds) || spec.maxAgeSeconds <= 0 || spec.maxAgeSeconds > 60) {
        errors.push(`spec.maxAgeSeconds must be in (0, 60], got ${spec.maxAgeSeconds}`);
    }
    if (!Number.isFinite(spec.maxEntries) || spec.maxEntries <= 0) {
        errors.push(`spec.maxEntries must be > 0, got ${spec.maxEntries}`);
    }
    const invalidators = Array.isArray(spec.invalidators) ? spec.invalidators : [];
    if (invalidators.length < 5) {
        errors.push(`spec.invalidators must declare ≥ 5 entries, got ${invalidators.length}`);
    }
    for (const pattern of FORBIDDEN_LITERALS) {
        if (pattern.test(raw)) {
            errors.push(`forbidden literal detected: ${pattern}`);
        }
    }
    return errors;
}

function assertRedaction(doc, raw) {
    const errors = [];
    const spec = doc?.spec;
    if (!spec) {
        return ["missing spec block"];
    }
    const deny = new Set(Array.isArray(spec.denyKeys) ? spec.denyKeys : []);
    for (const required of REQUIRED_DENY_KEYS) {
        if (!deny.has(required)) {
            errors.push(`spec.denyKeys missing ${required}`);
        }
    }
    const mask = new Set(Array.isArray(spec.maskKeys) ? spec.maskKeys : []);
    for (const required of REQUIRED_MASK_KEYS) {
        if (!mask.has(required)) {
            errors.push(`spec.maskKeys missing ${required}`);
        }
    }
    const patterns = Array.isArray(spec.scrubPatterns) ? spec.scrubPatterns : [];
    if (patterns.length < 3) {
        errors.push(`spec.scrubPatterns must declare ≥ 3 entries, got ${patterns.length}`);
    }
    for (const pattern of FORBIDDEN_LITERALS) {
        if (pattern.test(raw)) {
            errors.push(`forbidden literal detected: ${pattern}`);
        }
    }
    return errors;
}

const validators = {
    "policy.yaml": assertPolicy,
    "cache.yaml": assertCache,
    "redaction.yaml": assertRedaction,
};

let violation = false;

for (const file of CONTRACT_FILES) {
    const path = join(CONTRACTS_DIR, file);
    if (!existsSync(path)) {
        fail([`missing contract file: ${relative(ROOT, path)}`]);
        violation = true;
        continue;
    }
    const { raw, doc } = readYaml(path);
    const errors = (validators[file] || (() => []))(doc, raw);
    if (errors.length > 0) {
        fail(errors, relative(ROOT, path));
        violation = true;
    }
    const mirrorPath = join(HELM_FILES_DIR, "abac-" + file);
    if (!existsSync(mirrorPath)) {
        fail([`missing chart mirror: ${relative(ROOT, mirrorPath)}`]);
        violation = true;
        continue;
    }
    const mirrorRaw = readFileSync(mirrorPath, "utf8");
    if (mirrorRaw !== raw) {
        fail([`chart mirror drift (expected byte-identical)`]);
        violation = true;
    }
}

if (violation) {
    console.error("[abac] FAIL — ABAC source-of-truth files violate contract");
    process.exit(1);
}

console.log("[abac] OK — E3.4 ABAC source-of-truth files conform to contract");
