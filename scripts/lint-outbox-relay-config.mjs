#!/usr/bin/env node
/**
 * scripts/lint-outbox-relay-config.mjs
 *
 * E4.7 deep validator for the outbox relay + event
 * publishing contract under
 * `contracts/genealogy/outbox-relay-policy.yaml` and the
 * platform mirror under
 * `platform/helm/genealogy-platform/files/`.
 *
 * Mirrors the structure of `lint-person-merge-config.mjs`
 * (E4.6):
 *   - parse + structural assertions on `spec.policyId`,
 *     `spec.outboxStatusLifecycle`,
 *     `spec.retryClassClosedSet`,
 *     `spec.dlqReasonClosedSet`,
 *     `spec.partitionKeyClasses`,
 *     `spec.replayStrategies`,
 *     `spec.inboxIdempotencyStrategies`,
 *     `spec.maxAttempts`,
 *     `spec.initialBackoffSeconds`,
 *     `spec.maxBackoffSeconds`,
 *     `spec.backoffMultiplier`,
 *     `spec.jitterFactor`,
 *     `spec.pollBatchSize`,
 *     `spec.pollIntervalMillis`,
 *     `spec.claimLeaseSeconds`,
 *     `spec.maxPayloadBytes`,
 *     `spec.envelopeSerialization`,
 *     `spec.compatibilityPolicies`,
 *     `spec.auditClass*` / `spec.auditAction*`,
 *     `spec.payloadForbiddenFields`,
 *     `spec.payloadForbiddenTokenScanEnabled`,
 *     `spec.tenantContextCheckEnabled`,
 *     `spec.defaultIdempotencyStrategy`,
 *     `spec.inboxIdempotencyTtlDays`,
 *     `spec.dlqRetentionDays`,
 *     `spec.outboxRetentionDays`;
 *   - numeric threshold pinning (per ADR-E0.5-08 + §A
 *     ratification list);
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

const CONTRACT = join(ROOT, "contracts/genealogy/outbox-relay-policy.yaml");
const CHART_FILE = join(ROOT, "platform/helm/genealogy-platform/files/outbox-relay-policy.yaml");

const REQUIRED_OUTBOX_STATUS = ["PENDING", "PUBLISHED", "FAILED", "DEAD_LETTERED"];
const REQUIRED_RETRY_CLASSES = [
  "TRANSIENT",
  "PERMANENT",
  "RATE_LIMITED",
  "SCHEMA_MISMATCH",
];
const REQUIRED_DLQ_REASONS = [
  "PUBLISH_TIMEOUT",
  "SERIALIZATION_ERROR",
  "SCHEMA_INCOMPATIBLE",
  "PERMISSION_DENIED",
  "TENANT_MISMATCH",
  "UNKNOWN_TOPIC",
];
const REQUIRED_PARTITION_KEYS = ["AGGREGATE_ONLY", "TENANT_PLUS_AGGREGATE", "TRACE_ID"];
const REQUIRED_REPLAY_STRATEGIES = ["FROM_OUTBOX", "FROM_TOPIC", "DRY_RUN"];
const REQUIRED_IDEMPOTENCY_STRATEGIES = ["EVENT_ID", "AGGREGATE_VERSION", "SCHEMA_HASH"];
const REQUIRED_FORBIDDEN_FIELDS = [
  "dnaRaw",
  "rawGenotype",
  "dna",
  "kit",
  "rawDna",
  "raw_dna",
  "email",
  "phoneNumber",
  "accessToken",
  "refreshToken",
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
  console.error(`[outbox-relay-config] ${message}`);
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

function checkOutboxRelayPolicy() {
  const contract = loadContract(CONTRACT);
  if (!contract) return;
  const { raw, parsed } = contract;
  const fileName = relative(ROOT, CONTRACT);

  assertString(
    requireField(parsed, "spec.policyId", fileName),
    "default-outbox-relay/v1",
    "spec.policyId",
    fileName,
  );

  const setChecks = [
    ["spec.outboxStatusLifecycle", REQUIRED_OUTBOX_STATUS],
    ["spec.retryClassClosedSet", REQUIRED_RETRY_CLASSES],
    ["spec.dlqReasonClosedSet", REQUIRED_DLQ_REASONS],
    ["spec.replayStrategies", REQUIRED_REPLAY_STRATEGIES],
    ["spec.inboxIdempotencyStrategies", REQUIRED_IDEMPOTENCY_STRATEGIES],
    ["spec.payloadForbiddenFields", REQUIRED_FORBIDDEN_FIELDS],
  ];
  for (const [field, required] of setChecks) {
    const value = requireField(parsed, field, fileName);
    if (!Array.isArray(value)) {
      fail(`${fileName}: ${field} must be an array`);
      continue;
    }
    assertIncludes(new Set(value), required, field, fileName);
  }

  const pk = requireField(parsed, "spec.partitionKeyClasses", fileName);
  if (!Array.isArray(pk) || pk.length === 0) {
    fail(`${fileName}: spec.partitionKeyClasses must be a non-empty array`);
  } else {
    const names = new Set();
    for (const c of pk) {
      if (!c || typeof c !== "object") continue;
      if (!REQUIRED_PARTITION_KEYS.includes(c.name)) {
        fail(`${fileName}: spec.partitionKeyClasses contains unknown name ${c.name}`);
      }
      if (!names.add(c.name)) {
        fail(`${fileName}: spec.partitionKeyClasses duplicate name ${c.name}`);
      }
    }
    for (const required of REQUIRED_PARTITION_KEYS) {
      if (!names.has(required)) {
        fail(`${fileName}: spec.partitionKeyClasses missing required name ${required}`);
      }
    }
  }

  assertString(
    requireField(parsed, "spec.envelopeSerialization", fileName),
    "AVRO",
    "spec.envelopeSerialization",
    fileName,
  );

  assertString(
    requireField(parsed, "spec.compatibilityPolicies.domainEvent", fileName),
    "BACKWARD",
    "spec.compatibilityPolicies.domainEvent",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.compatibilityPolicies.commandIntent", fileName),
    "FORWARD",
    "spec.compatibilityPolicies.commandIntent",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.compatibilityPolicies.sharedEnum", fileName),
    "FULL",
    "spec.compatibilityPolicies.sharedEnum",
    fileName,
  );

  assertString(
    requireField(parsed, "spec.defaultIdempotencyStrategy", fileName),
    "EVENT_ID",
    "spec.defaultIdempotencyStrategy",
    fileName,
  );

  const maxAttempts = requireField(parsed, "spec.maxAttempts", fileName);
  if (typeof maxAttempts !== "number" || maxAttempts !== 5) {
    fail(`${fileName}: spec.maxAttempts must equal 5 (ADR-E0.5-08 retry ceiling)`);
  }

  const initialBackoff = requireField(parsed, "spec.initialBackoffSeconds", fileName);
  if (typeof initialBackoff !== "number" || initialBackoff !== 1) {
    fail(`${fileName}: spec.initialBackoffSeconds must equal 1 (ADR-E0.5-08 retry policy)`);
  }

  const maxBackoff = requireField(parsed, "spec.maxBackoffSeconds", fileName);
  if (typeof maxBackoff !== "number" || maxBackoff !== 60) {
    fail(`${fileName}: spec.maxBackoffSeconds must equal 60 (ADR-E0.5-08 retry policy)`);
  }

  const multiplier = requireField(parsed, "spec.backoffMultiplier", fileName);
  if (typeof multiplier !== "number" || multiplier !== 2) {
    fail(`${fileName}: spec.backoffMultiplier must equal 2`);
  }

  const jitter = requireField(parsed, "spec.jitterFactor", fileName);
  if (typeof jitter !== "number" || jitter < 0 || jitter > 1) {
    fail(`${fileName}: spec.jitterFactor must be a number in [0,1]`);
  } else if (jitter !== 0.25) {
    fail(`${fileName}: spec.jitterFactor must equal 0.25`);
  }

  const batch = requireField(parsed, "spec.pollBatchSize", fileName);
  if (typeof batch !== "number" || batch <= 0 || batch > 1000) {
    fail(`${fileName}: spec.pollBatchSize must be 1..1000`);
  }

  const pollMs = requireField(parsed, "spec.pollIntervalMillis", fileName);
  if (typeof pollMs !== "number" || pollMs < 0 || pollMs > 60000) {
    fail(`${fileName}: spec.pollIntervalMillis must be 0..60000`);
  }

  const lease = requireField(parsed, "spec.claimLeaseSeconds", fileName);
  if (typeof lease !== "number" || lease <= 0 || lease > 600) {
    fail(`${fileName}: spec.claimLeaseSeconds must be 1..600`);
  }

  const maxPayload = requireField(parsed, "spec.maxPayloadBytes", fileName);
  if (typeof maxPayload !== "number" || maxPayload <= 0 || maxPayload > 1048576) {
    fail(`${fileName}: spec.maxPayloadBytes must be 1..1048576`);
  } else if (maxPayload !== 921600) {
    fail(`${fileName}: spec.maxPayloadBytes must equal 921600 (900 KiB headroom)`);
  }

  const ttl = requireField(parsed, "spec.inboxIdempotencyTtlDays", fileName);
  if (typeof ttl !== "number" || ttl !== 7) {
    fail(`${fileName}: spec.inboxIdempotencyTtlDays must equal 7 (ADR-E0.5-08 projection retention)`);
  }

  const dlqRet = requireField(parsed, "spec.dlqRetentionDays", fileName);
  if (typeof dlqRet !== "number" || dlqRet !== 14) {
    fail(`${fileName}: spec.dlqRetentionDays must equal 14 (ADR-E0.5-08)`);
  }

  const outboxRet = requireField(parsed, "spec.outboxRetentionDays", fileName);
  if (typeof outboxRet !== "number" || outboxRet !== 30) {
    fail(`${fileName}: spec.outboxRetentionDays must equal 30 (ADR-E0.5-08)`);
  }

  assertString(
    requireField(parsed, "spec.payloadForbiddenTokenScanEnabled", fileName),
    true,
    "spec.payloadForbiddenTokenScanEnabled",
    fileName,
  );
  assertString(
    requireField(parsed, "spec.tenantContextCheckEnabled", fileName),
    true,
    "spec.tenantContextCheckEnabled",
    fileName,
  );

  const auditMap = [
    ["spec.auditClassOnEnqueue", "operational"],
    ["spec.auditActionOnEnqueue", "outbox.enqueued"],
    ["spec.auditClassOnPublish", "operational"],
    ["spec.auditActionOnPublish", "outbox.published"],
    ["spec.auditClassOnRetry", "operational"],
    ["spec.auditActionOnRetry", "outbox.retried"],
    ["spec.auditClassOnDeadLetter", "operational"],
    ["spec.auditActionOnDeadLetter", "outbox.dead_lettered"],
    ["spec.auditClassOnReplay", "operational"],
    ["spec.auditActionOnReplay", "outbox.replayed"],
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
  checkOutboxRelayPolicy();
  checkChartMirror();
  if (violations === 0) {
    console.log("[outbox-relay-config] OK");
    process.exit(0);
  } else {
    console.error(`[outbox-relay-config] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();
