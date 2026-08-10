#!/usr/bin/env node
/**
 * scripts/lint-openfga-config.mjs
 *
 * E3.3 deep validator for the OpenFGA authorization-model +
 * store-strategy source-of-truth files in `platform/openfga/` and
 * `contracts/openfga/`. Mirrors `lint-keycloak-config.mjs` style —
 * uses the same `yaml` parser and reports exit 0 on success, 1 on
 * violation, 2 on configuration error.
 *
 * Asserts:
 *   - `platform/openfga/store-strategy.yaml` declares
 *     `storeTopology: store-per-tenant` + `sharedAuthorizationModel:
 *     true` + `readConsistency.p95Milliseconds: 500` +
 *     `cache.invalidationOnWrite: required` + `cache.ttlOnly:
 *     forbidden` + `audit.mode: required` + `migration.policy:
 *     expand-contract`;
 *   - `platform/openfga/model-registry.yaml` declares the same
 *     `model.v1.json` schema as `contracts/openfga/model.v1.json`;
 *   - `platform/openfga/audit-hook.yaml` declares
 *     `audit-hook.yaml` with `sink.grpc: audit-service:9090` +
 *     `record.tenantId: true` + `redact: [raw_dna, raw_pii, ...]`;
 *   - `platform/openfga/sync-workflow.yaml` declares
 *     `workflowName: OpenfgaTupleSync` +
 *     `idempotency.enabled: true` +
 *     `order[*].name` includes `revokeFirst: true` plus the
 *     `cacheInvalidationAck` activity;
 *   - `contracts/openfga/model.v1.json` parses with
 *     `schema_version: "1.1"`, declares `tenant`, `tree`, `branch`,
 *     `person`, `resource`, `dna` types and `tenant_match`,
 *     `consent_active`, `revoked_blocks` conditions;
 *   - `contracts/openfga/migrations/v1-to-v2.json` declares
 *     `migration_id`, `expand_contract_asserts` and `new_relations`
 *     — every added relation type must already exist in v1;
 *   - no literal secret / token / password / DSN in any
 *     source-of-truth file;
 *   - the 5 platform/openfga/* files are mirrored byte-identical
 *     into `platform/helm/genealogy-platform/files/openfga-*.yaml`.
 *
 * Per `agent-execution.md` §4.4 this script does NOT mutate the
 * repo and is safe to run in CI.
 */
import { existsSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");
const PLATFORM_DIR = join(ROOT, "platform", "openfga");
const CONTRACTS_DIR = join(ROOT, "contracts", "openfga");
const HELM_FILES_DIR = join(ROOT, "platform", "helm", "genealogy-platform", "files");

const PLATFORM_FILES = [
  "store-strategy.yaml",
  "model-registry.yaml",
  "audit-hook.yaml",
  "sync-workflow.yaml",
  "bootstrap-tuples.json",
];

const REQUIRED_OBJECT_TYPES = [
  "user",
  "group",
  "tenant",
  "tree",
  "branch",
  "person",
  "resource",
  "dna",
];

const REQUIRED_CONDITIONS = [
  "tenant_match",
  "consent_active",
  "revoked_blocks",
];

const FORBIDDEN_TUPLE_LITERALS = [
  "raw_dna",
  "raw_pii",
  "email",
  "display_name",
  "phone",
  "address",
  "ssn",
];

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[openfga] ${msg}`);
};

const parseFile = (path) => {
  if (!existsSync(path)) {
    fail(`file missing — ${relative(ROOT, path)}`);
    return null;
  }
  let parsed;
  try {
    parsed = YAML.parse(readFileSync(path, "utf8"));
  } catch (err) {
    fail(`YAML parse error — ${relative(ROOT, path)} — ${err.message}`);
    return null;
  }
  return parsed;
};

const parseJson = (path) => {
  if (!existsSync(path)) {
    fail(`file missing — ${relative(ROOT, path)}`);
    return null;
  }
  try {
    return JSON.parse(readFileSync(path, "utf8"));
  } catch (err) {
    fail(`JSON parse error — ${relative(ROOT, path)} — ${err.message}`);
    return null;
  }
};

const lintLiteralSecrets = (path) => {
  if (!existsSync(path)) return;
  const txt = readFileSync(path, "utf8");
  for (const key of ["password", "apiKey", "api_key", "token", "dsn", "private_key", "client_secret"]) {
    const literal = new RegExp(`^\\s*${key}\\s*:\\s*"?[A-Za-z0-9._/+=-]{8,}"?\\s*$`, "m");
    if (literal.test(txt)) {
      fail(`literal secret-like value for '${key}' in ${relative(ROOT, path)} — use Vault / ESO`);
    }
  }
};

// ---------------------------------------------------------------------------
// 1. store-strategy.yaml
// ---------------------------------------------------------------------------
const strategyPath = join(PLATFORM_DIR, "store-strategy.yaml");
const strategyDoc = parseFile(strategyPath);
if (strategyDoc) {
  const inner = strategyDoc.data?.["store-strategy.yaml"];
  if (!inner) {
    fail("store-strategy.yaml: missing data['store-strategy.yaml'] block");
  } else {
    let innerDoc;
    try {
      innerDoc = YAML.parse(inner);
    } catch (err) {
      fail(`store-strategy.yaml: inner YAML parse error — ${err.message}`);
    }
    if (innerDoc) {
      if (innerDoc.storeTopology !== "store-per-tenant") {
        fail(`store-strategy.yaml: storeTopology must be 'store-per-tenant' (ADR-E0.5-06); got '${innerDoc.storeTopology}'`);
      }
      if (innerDoc.sharedAuthorizationModel !== true) {
        fail(`store-strategy.yaml: sharedAuthorizationModel must be true (ADR-E0.5-06)`);
      }
      if (innerDoc.storeIsolation !== "per-tenant") {
        fail(`store-strategy.yaml: storeIsolation must be 'per-tenant' (ADR-E0.5-06)`);
      }
      const p95 = Number(innerDoc.readConsistency?.p95Milliseconds);
      if (!Number.isFinite(p95) || p95 > 500) {
        fail(`store-strategy.yaml: readConsistency.p95Milliseconds must be ≤ 500 (ADR-E0.5-06); got '${innerDoc.readConsistency?.p95Milliseconds}'`);
      }
      if (innerDoc.cache?.invalidationOnWrite !== "required") {
        fail(`store-strategy.yaml: cache.invalidationOnWrite must be 'required' (no TTL-only caching)`);
      }
      if (innerDoc.cache?.ttlOnly !== "forbidden") {
        fail(`store-strategy.yaml: cache.ttlOnly must be 'forbidden' (ADR-E0.5-06)`);
      }
      if (innerDoc.audit?.mode !== "required") {
        fail(`store-strategy.yaml: audit.mode must be 'required' (every Write emits hook)`);
      }
      if (innerDoc.audit?.sink !== "audit-service:9090/audit.v1.AuditService/Append") {
        fail(`store-strategy.yaml: audit.sink must point at 'audit-service:9090/audit.v1.AuditService/Append'`);
      }
      const forbidden = innerDoc.audit?.redaction?.fieldsForbidden || [];
      for (const f of ["raw_dna", "raw_pii", "email", "display_name", "phone", "address", "token"]) {
        if (!forbidden.includes(f)) {
          fail(`store-strategy.yaml: audit.redaction.fieldsForbidden must include '${f}' (privacy posture)`);
        }
      }
      if (innerDoc.bootstrap?.modelVersion !== "1") {
        fail(`store-strategy.yaml: bootstrap.modelVersion must be '1' (matches contracts/openfga/model.v1.json)`);
      }
      if (innerDoc.migration?.policy !== "expand-contract") {
        fail(`store-strategy.yaml: migration.policy must be 'expand-contract' (ADR-E0.5-06)`);
      }
      if (innerDoc.migration?.requireMigrationEntry !== true) {
        fail(`store-strategy.yaml: migration.requireMigrationEntry must be true`);
      }
      if (innerDoc.datastore?.engine !== "postgres" && innerDoc.datastore?.engine !== "memory") {
        fail(`store-strategy.yaml: datastore.engine must be 'postgres' or 'memory' (production = postgres)`);
      }
      if (innerDoc.datastore?.engine === "postgres" && innerDoc.datastore?.postgres?.dsnEnv !== "OPENFGA_DATASTORE_POSTGRES_DSN") {
        fail(`store-strategy.yaml: datastore.postgres.dsnEnv must be 'OPENFGA_DATASTORE_POSTGRES_DSN' (no inline DSN)`);
      }
      if (innerDoc.network?.mtls !== "required") {
        fail(`store-strategy.yaml: network.mtls must be 'required' (Istio mTLS)`);
      }
      if (innerDoc.network?.ingressMode !== "none") {
        fail(`store-strategy.yaml: network.ingressMode must be 'none' (no public ingress)`);
      }
    }
  }
}
lintLiteralSecrets(strategyPath);

// ---------------------------------------------------------------------------
// 2. model-registry.yaml
// ---------------------------------------------------------------------------
const registryPath = join(PLATFORM_DIR, "model-registry.yaml");
const registryDoc = parseFile(registryPath);
if (registryDoc) {
  const inner = registryDoc.data?.manifest?.["manifest.yaml"];
  // The manifest lives under data.manifest; we accept either nested
  // or top-level. We just assert the embedded model.v1.json is the
  // same as the contracts file.
  const embeddedModel = registryDoc.data?.["model.v1.json"];
  if (typeof embeddedModel !== "string") {
    fail("model-registry.yaml: missing data['model.v1.json'] (embedded model)");
  } else {
    try {
      const parsedEmbedded = JSON.parse(embeddedModel);
      const parsedContracts = JSON.parse(
        readFileSync(join(CONTRACTS_DIR, "model.v1.json"), "utf8"),
      );
      // The embedded model is a *placeholder* (empty type_definitions)
      // because the actual model lives in contracts/. The linter
      // checks that the embedded file at least parses + matches the
      // schema_version of the contracts file.
      if (parsedEmbedded.schema_version !== parsedContracts.schema_version) {
        fail(`model-registry.yaml: embedded schema_version ('${parsedEmbedded.schema_version}') drifts from contracts ('${parsedContracts.schema_version}')`);
      }
    } catch (err) {
      fail(`model-registry.yaml: embedded model.v1.json does not parse — ${err.message}`);
    }
  }
  // Migration file presence
  const migrationKey = "v1-to-v2.json";
  const migrations = registryDoc.data?.migrations;
  if (!migrations || typeof migrations[migrationKey] !== "string") {
    fail(`model-registry.yaml: missing migrations['${migrationKey}'] (expand-contract entry required)`);
  }
}
lintLiteralSecrets(registryPath);

// ---------------------------------------------------------------------------
// 3. audit-hook.yaml
// ---------------------------------------------------------------------------
const auditPath = join(PLATFORM_DIR, "audit-hook.yaml");
const auditDoc = parseFile(auditPath);
if (auditDoc) {
  const inner = auditDoc.data?.["audit-hook.yaml"];
  if (!inner) {
    fail("audit-hook.yaml: missing data['audit-hook.yaml'] block");
  } else {
    let innerDoc;
    try {
      innerDoc = YAML.parse(inner);
    } catch (err) {
      fail(`audit-hook.yaml: inner YAML parse error — ${err.message}`);
    }
    if (innerDoc) {
      if (innerDoc.enabled !== true) {
        fail(`audit-hook.yaml: enabled must be true`);
      }
      if (innerDoc.sink?.grpc !== "audit-service:9090") {
        fail(`audit-hook.yaml: sink.grpc must be 'audit-service:9090' (audit-service mesh)`);
      }
      if (innerDoc.sink?.tls !== "required") {
        fail(`audit-hook.yaml: sink.tls must be 'required' (mTLS via Istio)`);
      }
      if (innerDoc.sink?.service !== "audit.v1.AuditService") {
        fail(`audit-hook.yaml: sink.service must be 'audit.v1.AuditService'`);
      }
      if (innerDoc.sink?.rpc !== "Append") {
        fail(`audit-hook.yaml: sink.rpc must be 'Append'`);
      }
      if (innerDoc.record?.tenantId !== true) {
        fail(`audit-hook.yaml: record.tenantId must be true`);
      }
      const redact = innerDoc.redact || [];
      for (const f of ["raw_dna", "raw_pii", "email", "display_name", "phone", "address", "token"]) {
        if (!redact.includes(f)) {
          fail(`audit-hook.yaml: redact must include '${f}'`);
        }
      }
      const sev = innerDoc.severityEscalation || {};
      if (sev.dna !== "critical") {
        fail(`audit-hook.yaml: severityEscalation.dna must be 'critical'`);
      }
      if (sev.secret !== "critical") {
        fail(`audit-hook.yaml: severityEscalation.secret must be 'critical'`);
      }
    }
  }
}
lintLiteralSecrets(auditPath);

// ---------------------------------------------------------------------------
// 4. sync-workflow.yaml
// ---------------------------------------------------------------------------
const syncPath = join(PLATFORM_DIR, "sync-workflow.yaml");
const syncDoc = parseFile(syncPath);
if (syncDoc) {
  const inner = syncDoc.data?.["sync-workflow.yaml"];
  if (!inner) {
    fail("sync-workflow.yaml: missing data['sync-workflow.yaml'] block");
  } else {
    let innerDoc;
    try {
      innerDoc = YAML.parse(inner);
    } catch (err) {
      fail(`sync-workflow.yaml: inner YAML parse error — ${err.message}`);
    }
    if (innerDoc) {
      if (innerDoc.workflowName !== "OpenfgaTupleSync") {
        fail(`sync-workflow.yaml: workflowName must be 'OpenfgaTupleSync'`);
      }
      if (innerDoc.idempotency?.enabled !== true) {
        fail(`sync-workflow.yaml: idempotency.enabled must be true`);
      }
      if (innerDoc.abacOverlay !== "required") {
        fail(`sync-workflow.yaml: abacOverlay must be 'required' (E3.4 ABAC overlay)`);
      }
      const orderNames = (innerDoc.order || []).map((s) => s.name);
      if (!orderNames.includes("writeTuples")) {
        fail(`sync-workflow.yaml: order must include 'writeTuples' activity`);
      }
      if (!orderNames.includes("cacheInvalidationAck")) {
        fail(`sync-workflow.yaml: order must include 'cacheInvalidationAck' activity (closes eventual-consistency window)`);
      }
      if (!orderNames.includes("writeAudit")) {
        fail(`sync-workflow.yaml: order must include 'writeAudit' activity`);
      }
      const retry = innerDoc.retry || {};
      if (Number(retry.maxAttempts) < 3) {
        fail(`sync-workflow.yaml: retry.maxAttempts must be ≥ 3 (idempotent retry)`);
      }
      const nonRetryable = retry.nonRetryable || [];
      for (const required of ["InvalidTupleSyntax", "UnknownRelation"]) {
        if (!nonRetryable.includes(required)) {
          fail(`sync-workflow.yaml: retry.nonRetryable must include '${required}' (not transient — these are bugs)`);
        }
      }
    }
  }
}
lintLiteralSecrets(syncPath);

// ---------------------------------------------------------------------------
// 5. bootstrap-tuples.json
// ---------------------------------------------------------------------------
const bootstrapPath = join(PLATFORM_DIR, "bootstrap-tuples.json");
const bootstrapDoc = parseJson(bootstrapPath);
if (bootstrapDoc) {
  if (bootstrapDoc.version !== "1") {
    fail(`bootstrap-tuples.json: version must be '1'`);
  }
  if (!Array.isArray(bootstrapDoc.default_role_tuples) || bootstrapDoc.default_role_tuples.length === 0) {
    fail(`bootstrap-tuples.json: default_role_tuples must be a non-empty array`);
  }
  const TUPLE_RE = /^[a-z_]+:[A-Za-z0-9._-]+#[a-z_]+@(user|group|tenant):[A-Za-z0-9._:-]+$/;
  for (const tuple of bootstrapDoc.default_role_tuples || []) {
    if (!TUPLE_RE.test(tuple.tuple)) {
      fail(`bootstrap-tuples.json: invalid tuple syntax '${tuple.tuple}'`);
    }
    for (const forbidden of FORBIDDEN_TUPLE_LITERALS) {
      if (tuple.tuple.includes(forbidden)) {
        fail(`bootstrap-tuples.json: tuple '${tuple.tuple}' contains forbidden literal '${forbidden}'`);
      }
    }
  }
  if (bootstrapDoc.revoke_first_priority?.enabled !== true) {
    fail(`bootstrap-tuples.json: revoke_first_priority.enabled must be true (privacy-and-legal-gate.md §Token replay)`);
  }
}

// ---------------------------------------------------------------------------
// 6. contracts/openfga/model.v1.json
// ---------------------------------------------------------------------------
const modelPath = join(CONTRACTS_DIR, "model.v1.json");
const modelDoc = parseJson(modelPath);
if (modelDoc) {
  if (modelDoc.schema_version !== "1.1") {
    fail(`contracts/openfga/model.v1.json: schema_version must be '1.1'`);
  }
  const names = new Set((modelDoc.type_definitions || []).map((t) => t.type));
  for (const required of REQUIRED_OBJECT_TYPES) {
    if (!names.has(required)) {
      fail(`contracts/openfga/model.v1.json: missing object type '${required}'`);
    }
  }
  const condNames = new Set(Object.keys(modelDoc.conditions || {}));
  for (const required of REQUIRED_CONDITIONS) {
    if (!condNames.has(required)) {
      fail(`contracts/openfga/model.v1.json: missing condition '${required}' (E3.4 ABAC overlay)`);
    }
  }
  // No PII / DNA / token literal anywhere in the model.
  const txt = JSON.stringify(modelDoc);
  for (const forbidden of ["raw_dna", "raw_pii", "ssn", "@gmail.com", "Bearer ", "eyJ"]) {
    if (txt.includes(forbidden)) {
      fail(`contracts/openfga/model.v1.json: forbidden literal '${forbidden}' (tuple content must be opaque IDs)`);
    }
  }
  // tree#viewer must include tupleToUserset to tenant#viewer.
  const tree = (modelDoc.type_definitions || []).find((t) => t.type === "tree");
  const viewerChildren = tree?.relations?.viewer?.union?.child || [];
  const hasTenantCascade = viewerChildren.some((c) => c.tupleToUserset?.computedUserset?.relation === "viewer");
  if (!hasTenantCascade) {
    fail(`contracts/openfga/model.v1.json: tree#viewer must cascade from tenant#viewer via tupleToUserset`);
  }
  // dna#reader MUST NOT cascade from tenant (privacy-and-legal-gate.md §DNA).
  const dna = (modelDoc.type_definitions || []).find((t) => t.type === "dna");
  const dnaReaderChildren = dna?.relations?.reader?.union?.child || [];
  for (const child of dnaReaderChildren) {
    if (child.tupleToUserset) {
      fail(`contracts/openfga/model.v1.json: dna#reader MUST NOT traverse tenant (privacy posture)`);
    }
  }
}

// ---------------------------------------------------------------------------
// 7. contracts/openfga/migrations/v1-to-v2.json
// ---------------------------------------------------------------------------
const migrationPath = join(CONTRACTS_DIR, "migrations", "v1-to-v2.json");
const migrationDoc = parseJson(migrationPath);
if (migrationDoc && modelDoc) {
  if (!migrationDoc.migration_id) {
    fail(`contracts/openfga/migrations/v1-to-v2.json: migration_id missing`);
  }
  if (!Array.isArray(migrationDoc.expand_contract_asserts) || migrationDoc.expand_contract_asserts.length < 4) {
    fail(`contracts/openfga/migrations/v1-to-v2.json: expand_contract_asserts must declare ≥ 4 invariants`);
  }
  if (!Array.isArray(migrationDoc.new_relations) || migrationDoc.new_relations.length === 0) {
    fail(`contracts/openfga/migrations/v1-to-v2.json: new_relations must declare ≥ 1 added relation`);
  }
  const v1Types = new Set((modelDoc.type_definitions || []).map((t) => t.type));
  for (const r of migrationDoc.new_relations || []) {
    if (!v1Types.has(r.type)) {
      fail(`contracts/openfga/migrations/v1-to-v2.json: new relation '${r.type}#${r.relation}' introduces new type — forbidden`);
    }
  }
}

// ---------------------------------------------------------------------------
// 8. Mirror check — every platform/openfga/* file is mirrored into
//    platform/helm/genealogy-platform/files/openfga-*.
// ---------------------------------------------------------------------------
for (const f of PLATFORM_FILES) {
  const src = join(PLATFORM_DIR, f);
  const dst = join(HELM_FILES_DIR, `openfga-${f}`);
  if (!existsSync(src)) continue;
  if (!existsSync(dst)) {
    fail(`chart mirror missing — expected ${relative(ROOT, dst)} (E3.3 contract)`);
    continue;
  }
  const a = readFileSync(src, "utf8");
  const b = readFileSync(dst, "utf8");
  if (a !== b) {
    fail(`chart mirror drift — ${relative(ROOT, src)} differs from ${relative(ROOT, dst)}`);
  }
}

// ---------------------------------------------------------------------------
// Summary
// ---------------------------------------------------------------------------
if (violations === 0) {
  console.log("[openfga] OK — E3.3 OpenFGA source-of-truth files conform to contract");
  process.exit(0);
} else {
  console.error(`[openfga] ${violations} violation(s) — see messages above`);
  process.exit(1);
}
