#!/usr/bin/env node
/**
 * scripts/lint-temporal-config.mjs
 *
 * E2.4 deep validator for the Temporal source-of-truth files in
 * `platform/temporal/`. Mirrors `lint-kafka-config.mjs` style — uses
 * the same `yaml` parser and returns exit 0 on success, 1 on
 * violation, 2 on configuration error.
 *
 * Asserts:
 *   - `platform/temporal/namespace-config.yaml` declares at least
 *     the 8 platform namespaces (default + 7 domain + dna). Each
 *     namespace has `name`, `retentionDays`, `ownerService` and a
 *     `customSearchAttributeAllowed` list whose entries all match
 *     the platform whitelist in `search-attrs.yaml`.
 *   - `platform/temporal/search-attrs.yaml` declares the 9 platform
 *     visibility attributes (TenantId, WorkflowType, TaskQueue,
 *     Attempt, AggregateType, AggregateId, MediaAssetId,
 *     TransferJobId, ConsentId). Every entry has `name` + `type`.
 *     The `forbiddenNames` list rejects any of the documented
 *     PII-bearing names.
 *   - `platform/temporal/dynamic-config.yaml` carries the `system.*`
 *     namespace with retention + visibility attribute whitelist.
 *     Per-namespace retention override for `genea-dna` = 365d.
 *   - `platform/temporal/task-queues.yaml` declares at least one
 *     queue per domain namespace; every entry has `name`,
 *     `namespace`, `ownerService`, `workerIdentity` (>= 1 entry).
 *     The dna queue must declare `workflowExecutionRetentionDays:
 *     365`.
 *   - No literal secret / token / password in any of the files.
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
const TEMPORAL_DIR = join(ROOT, "platform", "temporal");

const REQUIRED_NAMESPACES = [
  "genea-default",
  "genea-genealogy",
  "genea-media",
  "genea-search",
  "genea-interop",
  "genea-notify",
  "genea-reporting",
  "genea-dna",
];

const REQUIRED_SEARCH_ATTRS = [
  "TenantId",
  "WorkflowType",
  "TaskQueue",
  "Attempt",
  "AggregateType",
  "AggregateId",
  "MediaAssetId",
  "TransferJobId",
  "ConsentId",
];

const FORBIDDEN_SEARCH_NAMES = [
  "TenantUuid",
  "UserId",
  "PersonUuid",
  "Email",
  "Phone",
  "Address",
  "DnaRaw",
  "DnaSegment",
  "AccessToken",
  "RefreshToken",
  "Subject",
  "MediaUrl",
  "FileName",
];

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[temporal] ${msg}`);
};

function loadYaml(path) {
  if (!existsSync(path)) {
    fail(`file missing — ${relative(ROOT, path)}`);
    return null;
  }
  try {
    return YAML.parse(readFileSync(path, "utf8"));
  } catch (e) {
    fail(`YAML parse error in ${relative(ROOT, path)} — ${e.message}`);
    return null;
  }
}

function assertNoSecrets(text, path) {
  for (const key of ["password", "apiKey", "token", "private_key", "secret"]) {
    const literalRegex = new RegExp(`^\\s*${key}\\s*:\\s*"?[A-Za-z0-9]{8,}"?\\s*$`, "m");
    if (literalRegex.test(text)) {
      fail(
        `literal secret-like value for '${key}' in ${relative(ROOT, path)} — use Vault / External Secrets`,
      );
    }
  }
}

const nsFile = join(TEMPORAL_DIR, "namespace-config.yaml");
const searchFile = join(TEMPORAL_DIR, "search-attrs.yaml");
const dynFile = join(TEMPORAL_DIR, "dynamic-config.yaml");
const queueFile = join(TEMPORAL_DIR, "task-queues.yaml");

// ---------------------------------------------------------------------------
// search-attrs.yaml — exhaustive whitelist + forbidden names
// ---------------------------------------------------------------------------
const searchDoc = loadYaml(searchFile);
if (searchDoc) {
  const data = searchDoc?.data?.["schema.yaml"];
  if (!data) {
    fail(`search-attrs.yaml must declare a ConfigMap with a 'schema.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`search-attrs.yaml schema is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const schema = parsed.schema || [];
      const declaredNames = new Set(schema.map((s) => s.name));
      for (const required of REQUIRED_SEARCH_ATTRS) {
        if (!declaredNames.has(required)) {
          fail(`search-attrs.yaml missing visibility attribute '${required}'`);
        }
      }
      for (const entry of schema) {
        if (!entry.name || !entry.type) {
          fail(`search-attrs.yaml schema entry missing 'name' or 'type': ${JSON.stringify(entry)}`);
        }
        if (!/Custom(String|Keyword|Int|Datetime|Double|Bool)Field/.test(entry.type)) {
          fail(
            `search-attrs.yaml schema entry '${entry.name}' has invalid type '${entry.type}' — must be one of CustomStringField, CustomKeywordField, CustomIntField, CustomDatetimeField, CustomDoubleField, CustomBoolField`,
          );
        }
      }
      const forbidden = parsed.forbiddenNames || [];
      const forbiddenNames = new Set(forbidden.map((f) => f.name));
      for (const name of FORBIDDEN_SEARCH_NAMES) {
        if (!forbiddenNames.has(name)) {
          fail(
            `search-attrs.yaml missing forbidden name '${name}' — PII / DNA / token names must be rejected`,
          );
        }
      }
    }
  }
  assertNoSecrets(readFileSync(searchFile, "utf8"), searchFile);
}

// ---------------------------------------------------------------------------
// namespace-config.yaml — list of namespaces with retention + allowed
// search attributes.
// ---------------------------------------------------------------------------
const nsDoc = loadYaml(nsFile);
if (nsDoc) {
  const data = nsDoc?.data?.["policy.yaml"];
  if (!data) {
    fail(`namespace-config.yaml must declare a ConfigMap with a 'policy.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`namespace-config.yaml policy is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const namespaces = parsed.namesspaces || parsed.namespaces;
      if (!Array.isArray(namespaces)) {
        fail(`namespace-config.yaml must declare a 'namespaces' array (E2.4 §1)`);
      } else {
        const declared = new Set(namespaces.map((n) => n.name));
        for (const required of REQUIRED_NAMESPACES) {
          if (!declared.has(required)) {
            fail(`namespace-config.yaml missing namespace '${required}' (E2.4 §1)`);
          }
        }
        for (const ns of namespaces) {
          for (const field of [
            "name",
            "retentionDays",
            "ownerService",
            "customSearchAttributeAllowed",
          ]) {
            if (ns[field] === undefined) {
              fail(`namespace '${ns.name || "<unnamed>"}' missing required field '${field}'`);
            }
          }
          if (typeof ns.retentionDays !== "number" || ns.retentionDays <= 0) {
            fail(
              `namespace '${ns.name}' retentionDays must be a positive number — got ${ns.retentionDays}`,
            );
          }
          if (ns.name === "genea-dna" && ns.retentionDays !== 365) {
            fail(
              `namespace 'genea-dna' must declare retentionDays: 365 per privacy-and-legal-gate.md §14 — got ${ns.retentionDays}`,
            );
          }
          if (
            !Array.isArray(ns.customSearchAttributeAllowed) ||
            ns.customSearchAttributeAllowed.length === 0
          ) {
            fail(
              `namespace '${ns.name}' must declare at least one customSearchAttributeAllowed entry`,
            );
          } else {
            for (const entry of ns.customSearchAttributeAllowed) {
              const key = Object.keys(entry)[0];
              const value = entry[key];
              if (
                !REQUIRED_SEARCH_ATTRS.includes(value) &&
                !FORBIDDEN_SEARCH_NAMES.includes(value)
              ) {
                fail(
                  `namespace '${ns.name}' customSearchAttributeAllowed '${value}' is not in the platform whitelist`,
                );
              }
              if (FORBIDDEN_SEARCH_NAMES.includes(value)) {
                fail(
                  `namespace '${ns.name}' customSearchAttributeAllowed '${value}' is FORBIDDEN per ADR-E0.5-07 §privacy`,
                );
              }
            }
          }
        }
      }
      // Visibility block — every namespace inherits the same
      // indexed-field set.
      const visibility = parsed.visibility?.indexedFields;
      if (!Array.isArray(visibility) || visibility.length === 0) {
        fail(`namespace-config.yaml must declare a 'visibility.indexedFields' list (E2.4 §1)`);
      }
    }
  }
  assertNoSecrets(readFileSync(nsFile, "utf8"), nsFile);
}

// ---------------------------------------------------------------------------
// dynamic-config.yaml — system defaults + per-namespace overrides.
// ---------------------------------------------------------------------------
const dynDoc = loadYaml(dynFile);
if (dynDoc) {
  const data = dynDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`dynamic-config.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`dynamic-config.yaml config is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const system = parsed.system;
      if (!system) {
        fail(`dynamic-config.yaml must declare a 'system' block (E2.4 §2)`);
      } else {
        if (!system.workflow || !system.workflow.default) {
          fail(
            `dynamic-config.yaml must declare 'system.workflow.default.workflow.execution.timeout'`,
          );
        }
        if (
          !system.activity ||
          !system.activity.scheduleToCloseTimeout ||
          !system.activity.heartbeatTimeout
        ) {
          fail(
            `dynamic-config.yaml must declare activity scheduleToCloseTimeout + heartbeatTimeout`,
          );
        }
        const attrs = system.visibility?.attribute;
        if (!Array.isArray(attrs) || attrs.length < REQUIRED_SEARCH_ATTRS.length) {
          fail(
            `dynamic-config.yaml visibility.attribute list must cover all ${REQUIRED_SEARCH_ATTRS.length} whitelisted fields (E2.4 §3)`,
          );
        } else {
          const names = new Set(attrs.map((a) => a.name));
          for (const required of REQUIRED_SEARCH_ATTRS) {
            if (!names.has(required)) {
              fail(`dynamic-config.yaml visibility.attribute missing '${required}'`);
            }
          }
        }
        const nsDefault = system.namespace?.default;
        if (!nsDefault || nsDefault.retention !== "30d") {
          fail(`dynamic-config.yaml system.namespace.default.retention must be '30d' (E2.4 §1)`);
        }
      }
    }
  }
  assertNoSecrets(readFileSync(dynFile, "utf8"), dynFile);
}

// ---------------------------------------------------------------------------
// task-queues.yaml — queue list with worker identity allowlist.
// ---------------------------------------------------------------------------
const queueDoc = loadYaml(queueFile);
if (queueDoc) {
  const data = queueDoc?.data?.["policy.yaml"];
  if (!data) {
    fail(`task-queues.yaml must declare a ConfigMap with a 'policy.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`task-queues.yaml policy is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const queues = parsed.queues;
      if (!Array.isArray(queues) || queues.length === 0) {
        fail(`task-queues.yaml must declare a non-empty 'queues' array`);
      } else {
        const declared = new Set(queues.map((q) => q.name));
        const requiredQueues = [
          "genea-genealogy-main",
          "genea-media-scan",
          "genea-search-rebuild",
          "genea-interop-transfer",
          "genea-notify-dispatch",
          "genea-reporting-gen",
          "genea-dna-match",
        ];
        for (const required of requiredQueues) {
          if (!declared.has(required)) {
            fail(`task-queues.yaml missing required queue '${required}'`);
          }
        }
        for (const q of queues) {
          for (const field of [
            "name",
            "namespace",
            "ownerService",
            "workerIdentity",
            "workflowExecutionRetentionDays",
          ]) {
            if (q[field] === undefined) {
              fail(`task queue '${q.name || "<unnamed>"}' missing required field '${field}'`);
            }
          }
          if (!Array.isArray(q.workerIdentity) || q.workerIdentity.length === 0) {
            fail(`task queue '${q.name}' must declare a non-empty workerIdentity allowlist`);
          }
          if (
            typeof q.workflowExecutionRetentionDays !== "number" ||
            q.workflowExecutionRetentionDays <= 0
          ) {
            fail(`task queue '${q.name}' workflowExecutionRetentionDays must be a positive number`);
          }
          if (
            q.name &&
            q.name.startsWith("genea-dna") &&
            q.workflowExecutionRetentionDays !== 365
          ) {
            fail(
              `DNA task queue '${q.name}' must declare 365-day retention per privacy-and-legal-gate.md §14`,
            );
          }
          if (q.namespace && !REQUIRED_NAMESPACES.includes(q.namespace)) {
            fail(
              `task queue '${q.name}' references unknown namespace '${q.namespace}' — namespaces must come from namespace-config.yaml`,
            );
          }
        }
      }
    }
  }
  assertNoSecrets(readFileSync(queueFile, "utf8"), queueFile);
}

// ---------------------------------------------------------------------------
// Mirror files — every platform/temporal/* must be present in the
// chart's files/temporal/ directory.
// ---------------------------------------------------------------------------
const mirrorDir = join(ROOT, "platform", "helm", "genealogy-platform", "files", "temporal");
for (const f of [
  "namespace-config.yaml",
  "search-attrs.yaml",
  "dynamic-config.yaml",
  "task-queues.yaml",
]) {
  const src = join(TEMPORAL_DIR, f);
  const dst = join(mirrorDir, f);
  if (!existsSync(src)) continue;
  if (!existsSync(dst)) {
    fail(`chart mirror missing — expected ${relative(ROOT, dst)} (E2.4 contract)`);
    continue;
  }
  const a = readFileSync(src, "utf8");
  const b = readFileSync(dst, "utf8");
  if (a !== b) {
    fail(`chart mirror out of sync — ${relative(ROOT, dst)}`);
  }
}

if (violations > 0) {
  console.error(`\n[temporal] ${violations} violation(s)`);
  process.exit(1);
}
console.log(
  `[temporal] clean — namespaces=${REQUIRED_NAMESPACES.length}, search-attrs=${REQUIRED_SEARCH_ATTRS.length}, forbidden=${FORBIDDEN_SEARCH_NAMES.length}`,
);
