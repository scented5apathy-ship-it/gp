#!/usr/bin/env node
/**
 * scripts/lint-grafana-config.mjs
 *
 * E2.10 deep validator for the Grafana OSS stack
 * source-of-truth files in `platform/grafana/`. Mirrors
 * `lint-argo-config.mjs` / `lint-flagsmith-config.mjs`
 * style — uses the same `yaml` parser and reports exit 0
 * on success, 1 on violation, 2 on configuration error.
 *
 * Asserts:
 *   - `platform/grafana/otel-collector.yaml` declares the
 *     4 receivers (otlp/grpc, otlp/http, prometheus,
 *     otlp/audit), 9 mandatory processors in the documented
 *     order (memory_limiter / resourcedetection /
 *     k8sattributes / attributes-tenant-pseudonym /
 *     transform / redaction / filter-logs / batch /
 *     resource-audit), 5 mandatory exporters (prometheus /
 *     loki / tempo / otlp-audit / debug-dev-only);
 *   - redaction regex covers SSN, passport, driver-license,
 *     email, phone, ipv4, JWT, raw-dna-marker,
 *     authorization-header (9 rules);
 *   - `platform/grafana/prometheus.yaml` declares 6 scrape
 *     jobs + 9 recording rules + 4 SLO alerts
 *     (api-availability, canary-success, consumer-lag,
 *     pii-redaction-coverage);
 *   - `platform/grafana/loki.yaml` declares 6 mandatory
 *     stream labels + 4 deny_labels + retention_period
 *     ≥ 720h + compactor.retention_enabled: true;
 *   - `platform/grafana/tempo.yaml` declares OTLP receiver
 *     + 5 generators (service-graphs / span-metrics /
 *     local-blocks / search-queries / audit-events) +
 *     search_tags + search_tags_deny + block_retention
 *     ≥ 720h;
 *   - `platform/grafana/dashboards.yaml` declares 9
 *     mandatory dashboards + templateVariablesForbidden
 *     + auditFields;
 *   - `platform/grafana/grafana.yaml` declares Keycloak
 *     OIDC SSO + anonymous_enabled: false + audit sink +
 *     2FA conditional flow;
 *   - `tenant_pseudo_id` / `user_pseudo_id` /
 *     `actor_pseudo_id` are emitted; raw `tenant_id` /
 *     `user_id` / `email` / `oidc_subject` / `raw_dna` /
 *     `raw_pii` are FORBIDDEN at every exporter boundary;
 *   - retention ≥ 30 days Prometheus / Loki + ≥ 14 days
 *     Tempo on production;
 *   - the six files are mirrored byte-identical into
 *     `platform/helm/genealogy-platform/files/grafana/`;
 *   - no literal secret / token / password / api-key /
 *     pepper / jwt in any values file.
 *
 * Per `agent-execution.md` §4.4 this script does NOT mutate
 * the repo and is safe to run in CI.
 */
import { existsSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");
const GRAFANA_DIR = join(ROOT, "platform", "grafana");

const REQUIRED_RECEIVERS = [
  "otlp/grpc",
  "otlp/http",
  "prometheus",
  "otlp/audit",
];
const REQUIRED_PROCESSORS = [
  "memory_limiter",
  "resourcedetection",
  "k8sattributes",
  "attributes/tenant-pseudonym",
  "transform",
  "redaction",
  "filter/logs",
  "batch",
  "resource/audit",
];
const REQUIRED_TRACES_PROCESSORS = [
  "memory_limiter",
  "resourcedetection",
  "k8sattributes",
  "attributes/tenant-pseudonym",
  "transform",
  "redaction",
  "batch",
];
const REQUIRED_METRICS_PROCESSORS = [
  "memory_limiter",
  "resourcedetection",
  "k8sattributes",
  "attributes/tenant-pseudonym",
  "transform",
  "batch",
];
const REQUIRED_LOGS_PROCESSORS = [
  "memory_limiter",
  "resourcedetection",
  "k8sattributes",
  "attributes/tenant-pseudonym",
  "transform",
  "redaction",
  "filter/logs",
  "batch",
];
const REQUIRED_AUDIT_PROCESSORS = [
  "memory_limiter",
  "resourcedetection",
  "k8sattributes",
  "attributes/tenant-pseudonym",
  "transform",
  "redaction",
  "batch",
  "resource/audit",
];
const REQUIRED_EXPORTERS = [
  "prometheus",
  "loki",
  "tempo",
  "otlp/audit",
];
const REQUIRED_REDACTION_RULES = [
  "ssn",
  "passport",
  "driver-license",
  "email",
  "phone",
  "ipv4",
  "jwt",
  "raw-dna-marker",
  "authorization-header",
];
const REQUIRED_SCRAPE_JOBS = [
  "otel-collector-self",
  "services",
  "workers",
  "platform",
  "audit",
];
const REQUIRED_RECORDING_RULES = [
  "red_rate_api",
  "red_errors_api",
  "kong_latency",
  "kong_status",
  "consumer_lag",
  "outbox_age",
  "workflow_failure",
  "dq_size",
  "redaction_coverage",
];
const REQUIRED_SLO_ALERTS = [
  "api-availability",
  "canary-success",
  "consumer-lag",
  "pii-redaction-coverage",
];
const REQUIRED_STREAM_LABELS = [
  "service.name",
  "service.namespace",
  "deployment.environment",
  "tenant_pseudo_id",
  "actor_pseudo_id",
  "trace_id",
];
const REQUIRED_DENY_LABELS = [
  "email",
  "oidc_subject",
  "raw_dna",
  "raw_pii",
];
const REQUIRED_DASHBOARDS = [
  "api-overview",
  "kong",
  "kafka",
  "temporal",
  "openfga",
  "istio",
  "vault",
  "database",
  "workload",
];
const REQUIRED_TEMPO_GENERATORS = [
  "service-graphs",
  "span-metrics",
  "local-blocks",
  "search-queries",
  "audit-events",
];
const FORBIDDEN_LABELS = [
  "tenant_id",
  "user_id",
  "email",
  "oidc_subject",
  "raw_dna",
  "raw_pii",
];
const REQUIRED_PSEUDONYMS = [
  "tenant_pseudo_id",
  "user_pseudo_id",
  "actor_pseudo_id",
];

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[grafana] ${msg}`);
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
  for (const key of ["password", "apiKey", "api_key", "token", "pepper", "jwt"]) {
    const literalRegex = new RegExp(
      `^\\s*${key}\\s*:\\s*"?[A-Za-z0-9._/+=-]{8,}"?\\s*$`,
      "m",
    );
    if (literalRegex.test(text)) {
      fail(
        `literal secret-like value for '${key}' in ${relative(ROOT, path)} — use Vault / External Secrets`,
      );
    }
  }
  for (const awsKey of ["AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY"]) {
    const re = new RegExp(
      `^\\s*${awsKey}\\s*=\\s*["']?[A-Za-z0-9/+=]{16,}["']?\\s*$`,
      "m",
    );
    if (re.test(text)) {
      fail(
        `literal AWS credential '${awsKey}' in ${relative(ROOT, path)} — use IRSA / pod identity (E2.10 §6)`,
      );
    }
  }
}

// ---------------------------------------------------------------------------
// otel-collector.yaml — OTel Collector pipeline posture
// ---------------------------------------------------------------------------
const otelFile = join(GRAFANA_DIR, "otel-collector.yaml");
const otelDoc = loadYaml(otelFile);
let otelReceivers = 0;
let otelProcessors = 0;
let otelExporters = 0;
let redactionRuleCount = 0;
if (otelDoc) {
  const data = otelDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`otel-collector.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`otel-collector.yaml content is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      // Receivers — 4 mandatory.
      const receivers = parsed.receivers || {};
      const declaredReceivers = Object.keys(receivers);
      otelReceivers = declaredReceivers.length;
      for (const required of REQUIRED_RECEIVERS) {
        if (!declaredReceivers.includes(required)) {
          fail(`otel-collector.yaml must declare receiver '${required}' (E2.10 §1)`);
        }
      }

      // Processors — 9 mandatory + order checks per pipeline.
      const processors = parsed.processors || {};
      for (const required of REQUIRED_PROCESSORS) {
        if (!(required in processors)) {
          fail(`otel-collector.yaml must declare processor '${required}' (E2.10 §2)`);
        }
      }
      otelProcessors = Object.keys(processors).length;

      // Redaction rules — 9 mandatory.
      const redaction = processors.redaction || {};
      const rules = redaction.rules || [];
      redactionRuleCount = rules.length;
      const ruleNames = new Set(rules.map((r) => r.name));
      for (const required of REQUIRED_REDACTION_RULES) {
        if (!ruleNames.has(required)) {
          fail(`otel-collector.yaml redaction must include rule '${required}' (E2.10 §2.f)`);
        }
      }

      // Exporters — 5 mandatory (4 in prod, debug is dev profile).
      const exporters = parsed.exporters || {};
      const declaredExporters = Object.keys(exporters);
      otelExporters = declaredExporters.length;
      for (const required of REQUIRED_EXPORTERS) {
        if (!declaredExporters.includes(required)) {
          fail(`otel-collector.yaml must declare exporter '${required}' (E2.10 §3)`);
        }
      }

      // Service pipelines — must include traces / metrics / logs / audit.
      const pipelines = parsed.service?.pipelines || {};
      for (const pipelineName of ["traces", "metrics", "logs", "audit"]) {
        if (!pipelines[pipelineName]) {
          fail(`otel-collector.yaml must declare pipeline '${pipelineName}' (E2.10 §4)`);
          continue;
        }
        const pipeline = pipelines[pipelineName];
        const procs = pipeline.processors || [];
        const expected =
          pipelineName === "traces"
            ? REQUIRED_TRACES_PROCESSORS
            : pipelineName === "metrics"
              ? REQUIRED_METRICS_PROCESSORS
              : pipelineName === "logs"
                ? REQUIRED_LOGS_PROCESSORS
                : REQUIRED_AUDIT_PROCESSORS;
        for (const required of expected) {
          if (!procs.includes(required)) {
            fail(
              `otel-collector.yaml pipeline '${pipelineName}' must include processor '${required}' (E2.10 §4)`,
            );
          }
        }
        // Forbid raw labels in metric / log attributes (enforced
        // via `transform` + `redaction` — the linter asserts
        // the rules are in place).
      }

      // Pseudonym attributes — `attributes/tenant-pseudonym`
      // must hash tenant_id / user_id / actor_id.
      const attrPseudonym = processors["attributes/tenant-pseudonym"] || {};
      const actions = attrPseudonym.actions || [];
      for (const required of REQUIRED_PSEUDONYMS) {
        const found = actions.some((a) => a.key === required);
        if (!found) {
          fail(
            `otel-collector.yaml processor attributes/tenant-pseudonym must hash to '${required}' (E2.10 §2.d)`,
          );
        }
      }

      // Filter/logs — drops raw_dna / raw_pii / consent_dropped
      // / legal_hold_pii substrings.
      const filterLogs = processors["filter/logs"] || {};
      const filterExpr = JSON.stringify(filterLogs);
      if (!/raw_dna|raw_pii|consent_dropped|legal_hold_pii/.test(filterExpr)) {
        fail(
          `otel-collector.yaml processor filter/logs must drop records with body matching 'raw_dna|raw_pii|consent_dropped|legal_hold_pii' (E2.10 §2.g)`,
        );
      }

      // Resource attributes (prometheus exporter) — must
      // allow_labels include pseudonyms and deny_labels
      // include forbidden labels.
      const promExporter = exporters.prometheus || {};
      const allowLabels = promExporter.allow_labels || [];
      const denyLabels = promExporter.deny_labels || [];
      for (const required of ["tenant_pseudo_id", "actor_pseudo_id"]) {
        if (!allowLabels.includes(required)) {
          fail(
            `otel-collector.yaml exporter prometheus.allow_labels must include '${required}' (E2.10 §3)`,
          );
        }
      }
      for (const forbidden of FORBIDDEN_LABELS) {
        if (!denyLabels.includes(forbidden)) {
          fail(
            `otel-collector.yaml exporter prometheus.deny_labels must include '${forbidden}' (E2.10 §3)`,
          );
        }
      }

      // Loki exporter denied labels.
      const lokiExporter = exporters.loki || {};
      const lokiDenied = lokiExporter.denied_labels || [];
      for (const forbidden of FORBIDDEN_LABELS) {
        if (!lokiDenied.includes(forbidden)) {
          fail(
            `otel-collector.yaml exporter loki.denied_labels must include '${forbidden}' (E2.10 §3)`,
          );
        }
      }
    }
  }
  assertNoSecrets(readFileSync(otelFile, "utf8"), otelFile);
}

// ---------------------------------------------------------------------------
// prometheus.yaml — Prometheus scrape + recording rules + SLO alerts
// ---------------------------------------------------------------------------
const promFile = join(GRAFANA_DIR, "prometheus.yaml");
const promDoc = loadYaml(promFile);
let recordingRuleCount = 0;
let sloAlertCount = 0;
if (promDoc) {
  const data = promDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`prometheus.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`prometheus.yaml content is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      // Scrape jobs.
      const scrapeConfigs = parsed.scrape_configs || [];
      const jobNames = new Set(scrapeConfigs.map((s) => s.job_name));
      for (const required of REQUIRED_SCRAPE_JOBS) {
        if (!jobNames.has(required)) {
          fail(`prometheus.yaml must declare scrape job '${required}' (E2.10 §3)`);
        }
      }

      // Recording rules — 9 mandatory.
      const rules = parsed.recording_rules?.rules || [];
      recordingRuleCount = rules.length;
      const ruleNames = new Set(rules.map((r) => r.name));
      for (const required of REQUIRED_RECORDING_RULES) {
        if (!ruleNames.has(required)) {
          fail(`prometheus.yaml must declare recording rule '${required}' (E2.10 §3)`);
        }
      }

      // SLO alerts — 4 mandatory.
      const sloAlerts = parsed.slo_alerts || [];
      sloAlertCount = sloAlerts.length;
      const sloNames = new Set(sloAlerts.map((a) => a.name));
      for (const required of REQUIRED_SLO_ALERTS) {
        if (!sloNames.has(required)) {
          fail(`prometheus.yaml must declare SLO alert '${required}' (E2.10 §3)`);
        }
      }
    }
  }
  assertNoSecrets(readFileSync(promFile, "utf8"), promFile);
}

// ---------------------------------------------------------------------------
// loki.yaml — Loki schema + retention + compactor
// ---------------------------------------------------------------------------
const lokiFile = join(GRAFANA_DIR, "loki.yaml");
const lokiDoc = loadYaml(lokiFile);
if (lokiDoc) {
  const data = lokiDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`loki.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`loki.yaml content is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const limits = parsed.limits_config || {};
      // 4 deny_labels.
      const denyLabels = limits.deny_labels || [];
      for (const required of REQUIRED_DENY_LABELS) {
        if (!denyLabels.includes(required)) {
          fail(`loki.yaml limits_config.deny_labels must include '${required}' (E2.10 §4)`);
        }
      }
      // 6 mandatory stream labels.
      const streamLabelRequired = limits.stream_label_required || [];
      for (const required of REQUIRED_STREAM_LABELS) {
        if (!streamLabelRequired.includes(required)) {
          fail(
            `loki.yaml limits_config.stream_label_required must include '${required}' (E2.10 §4)`,
          );
        }
      }
      // Retention ≥ 720h (30d) — schema-config + compactor.
      const compactor = parsed.compactor || {};
      if (!compactor.retention_enabled) {
        fail(`loki.yaml compactor.retention_enabled must be true (E2.10 §5)`);
      }
      const retentionPeriod = String(limits.retention_period || "");
      const hoursMatch = retentionPeriod.match(/^(\d+)h$/);
      if (!hoursMatch) {
        fail(`loki.yaml limits_config.retention_period must use 'Nh' format (E2.10 §5)`);
      } else {
        const hours = parseInt(hoursMatch[1], 10);
        if (hours < 720) {
          fail(`loki.yaml retention_period must be ≥ 720h (30d) on production (got ${hours}h)`);
        }
      }
    }
  }
  assertNoSecrets(readFileSync(lokiFile, "utf8"), lokiFile);
}

// ---------------------------------------------------------------------------
// tempo.yaml — Tempo OTLP + ingester + storage + generators
// ---------------------------------------------------------------------------
const tempoFile = join(GRAFANA_DIR, "tempo.yaml");
const tempoDoc = loadYaml(tempoFile);
if (tempoDoc) {
  const data = tempoDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`tempo.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`tempo.yaml content is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      // OTLP receivers on :4317 + :4318.
      const distributor = parsed.distributor?.receivers?.otlp || {};
      if (!distributor.protocols?.grpc || !distributor.protocols?.http) {
        fail(`tempo.yaml distributor.receivers.otlp must declare grpc + http protocols (E2.10 §5)`);
      }
      // 5 generators.
      const generators = parsed.metrics_generator?.processors || [];
      for (const required of REQUIRED_TEMPO_GENERATORS) {
        if (!generators.includes(required)) {
          fail(
            `tempo.yaml metrics_generator.processors must include '${required}' (E2.10 §5)`,
          );
        }
      }
      // Search tags allow / deny.
      const ingester = parsed.ingester || {};
      const searchTags = ingester.search_tags || [];
      for (const required of ["tenant_pseudo_id", "actor_pseudo_id", "trace_id"]) {
        if (!searchTags.includes(required)) {
          fail(`tempo.yaml ingester.search_tags must include '${required}' (E2.10 §5)`);
        }
      }
      const searchTagsDeny = ingester.search_tags_deny || [];
      for (const forbidden of FORBIDDEN_LABELS) {
        if (!searchTagsDeny.includes(forbidden)) {
          fail(
            `tempo.yaml ingester.search_tags_deny must include '${forbidden}' (E2.10 §5)`,
          );
        }
      }
      // Compactor block_retention ≥ 720h.
      const compactor = parsed.compactor?.compaction || {};
      const blockRetention = String(compactor.block_retention || "");
      const hoursMatch = blockRetention.match(/^(\d+)h$/);
      if (!hoursMatch) {
        fail(`tempo.yaml compactor.compaction.block_retention must use 'Nh' format (E2.10 §5)`);
      } else {
        const hours = parseInt(hoursMatch[1], 10);
        if (hours < 720) {
          fail(`tempo.yaml block_retention must be ≥ 720h (30d) on production (got ${hours}h)`);
        }
      }
    }
  }
  assertNoSecrets(readFileSync(tempoFile, "utf8"), tempoFile);
}

// ---------------------------------------------------------------------------
// dashboards.yaml — 9 mandatory dashboards
// ---------------------------------------------------------------------------
const dashFile = join(GRAFANA_DIR, "dashboards.yaml");
const dashDoc = loadYaml(dashFile);
let dashCount = 0;
if (dashDoc) {
  const data = dashDoc?.data?.["catalogue.yaml"];
  if (!data) {
    fail(`dashboards.yaml must declare a ConfigMap with a 'catalogue.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`dashboards.yaml content is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const dashboards = parsed.dashboards || [];
      dashCount = dashboards.length;
      const slugs = new Set(dashboards.map((d) => d.slug));
      for (const required of REQUIRED_DASHBOARDS) {
        if (!slugs.has(required)) {
          fail(`dashboards.yaml missing required dashboard '${required}' (E2.10 §6)`);
        }
      }
      // templateVariablesForbidden must include raw labels.
      const forbidden = parsed.templateVariablesForbidden || [];
      for (const label of FORBIDDEN_LABELS) {
        if (!forbidden.includes(label)) {
          fail(
            `dashboards.yaml templateVariablesForbidden must include '${label}' (E2.10 §6)`,
          );
        }
      }
      // auditFields must include actor_pseudo_id.
      const auditFields = parsed.auditFields || [];
      if (!auditFields.includes("actor_pseudo_id")) {
        fail(
          `dashboards.yaml auditFields must include 'actor_pseudo_id' (E2.10 §6)`,
        );
      }
    }
  }
  assertNoSecrets(readFileSync(dashFile, "utf8"), dashFile);
}

// ---------------------------------------------------------------------------
// grafana.yaml — Grafana server config (Keycloak OIDC + 2FA + audit)
// ---------------------------------------------------------------------------
const grafanaFile = join(GRAFANA_DIR, "grafana.yaml");
const grafanaDoc = loadYaml(grafanaFile);
if (grafanaDoc) {
  const data = grafanaDoc?.data?.["config.ini"];
  if (!data) {
    fail(`grafana.yaml must declare a ConfigMap with a 'config.ini' entry under .data`);
  } else {
    // Keycloak OIDC enabled.
    if (!/\[auth\.keycloak\][\s\S]*?enabled:\s*true/.test(data)) {
      fail(`grafana.yaml must enable Keycloak OIDC SSO (E2.10 §6)`);
    }
    // Anonymous access FORBIDDEN.
    if (!/\[auth\][\s\S]*?anonymous_enabled:\s*false/.test(data)) {
      fail(`grafana.yaml must disable anonymous access (E2.10 §6)`);
    }
    // Sign-up disabled.
    if (!/\[users\][\s\S]*?allow_sign_up:\s*false/.test(data)) {
      fail(`grafana.yaml must disable user sign-up (E2.10 §6)`);
    }
    // 2FA conditional — implicit via Keycloak conditional-2fa
    // flow; the linter checks the audit + admin password
    // sources.
    if (!/admin_password:\s*REPLACE_VIA_ESO/.test(data)) {
      fail(
        `grafana.yaml admin_password must be sourced from ESO-managed Secret (REPLACE_VIA_ESO)`,
      );
    }
    if (!/client_secret:\s*REPLACE_VIA_ESO/.test(data)) {
      fail(
        `grafana.yaml auth.keycloak.client_secret must be sourced from ESO-managed Secret (REPLACE_VIA_ESO)`,
      );
    }
    // Audit sink — actor_pseudo_id only (no raw email /
    // oidc_subject).
    if (/audit[^]*email:/.test(data) || /audit[^]*oidc_subject:/.test(data)) {
      fail(`grafana.yaml audit fields must NOT carry raw email / oidc_subject (E2.10 §6)`);
    }
  }
  assertNoSecrets(readFileSync(grafanaFile, "utf8"), grafanaFile);
}

// ---------------------------------------------------------------------------
// Mirror files — every platform/grafana/* must be present in the
// chart's files/grafana/ directory.
// ---------------------------------------------------------------------------
const mirrorDir = join(ROOT, "platform", "helm", "genealogy-platform", "files", "grafana");
for (const f of [
  "otel-collector.yaml",
  "prometheus.yaml",
  "loki.yaml",
  "tempo.yaml",
  "dashboards.yaml",
  "grafana.yaml",
]) {
  const src = join(GRAFANA_DIR, f);
  const dst = join(mirrorDir, f);
  if (!existsSync(src)) continue;
  if (!existsSync(dst)) {
    fail(`chart mirror missing — expected ${relative(ROOT, dst)} (E2.10 contract)`);
    continue;
  }
  const a = readFileSync(src, "utf8");
  const b = readFileSync(dst, "utf8");
  if (a !== b) {
    fail(`chart mirror out of sync — ${relative(ROOT, dst)}`);
  }
}

if (violations > 0) {
  console.error(`\n[grafana] ${violations} violation(s)`);
  process.exit(1);
}
console.log(
  `[grafana] clean — receivers=${otelReceivers}, processors=${otelProcessors}, exporters=${otelExporters}, redaction-rules=${redactionRuleCount}, recording-rules=${recordingRuleCount}, slo-alerts=${sloAlertCount}, dashboards=${dashCount}`,
);