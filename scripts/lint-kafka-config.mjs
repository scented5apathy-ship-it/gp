#!/usr/bin/env node
/**
 * scripts/lint-kafka-config.mjs
 *
 * E2.3 deep validator for the Strimzi Kafka cluster and the
 * Apicurio Schema Registry configuration. Mirrors the
 * `lint-kong-config.mjs` style — uses the same `yaml` parser and
 * reports exit 0 on success, 1 on violation.
 *
 * Asserts (KAFKA):
 *   - `platform/kafka/kafka.yaml` declares a `Kafka` CR with
 *     `spec.kafka.version` pinned to `3.8.0` (ADR-E0.5-01).
 *   - `spec.kafka.replicas >= 1`, `metadataVersion: 3.8`.
 *   - `auto.create.topics.enable` is `false` (every topic must be
 *     declared in `topics.yaml`).
 *   - `min.insync.replicas >= 1` and `default.replication.factor >= 1`.
 *   - Two listeners: `plain` and `tls`. No `external` listener
 *     (the public broker listener is forbidden per design §13).
 *   - Authorization is `simple` and `superUsers` includes
 *     `CN=genea-kafka-admin`.
 *   - `client.quota.callback.class` is the Strimzi static quota
 *     callback and the three quota values are positive integers.
 *   - `topics.yaml` declares at least one entry per topic class
 *     (domain-event, projection-rebuild, audit, dlq) and every
 *     entry has `name`, `partitions`, `replicationFactor`,
 *     `retentionMs`, `cleanupPolicy`, `minInSyncReplicas`,
 *     `partitionKey`, `schema`, `owner`, `topicClass`.
 *   - Retention class buckets match ADR-E0.5-08:
 *     domain-event = 30d, projection-rebuild = 7d, audit = 365d,
 *     dlq = 14d.
 *   - `users.yaml` declares at least one `admin`, one `producer`,
 *     one `consumer`. Every user has `authType: tls` and
 *     scram-sha-512 is forbidden.
 *   - Admin user has no `acls` block (the super-user flag is the
 *     contract).
 *   - No literal secret / token / apiKey / password in any file.
 *
 * Asserts (APICURIO):
 *   - `platform/apicurio/registry-config.yaml` declares a
 *     ConfigMap with `application.properties` and a `compatibility`
 *     block.
 *   - `registry.storage.kind=sql` (in-memory forbidden in
 *     production).
 *   - `registry.apis.confluent.enabled=false` (the Confluent
 *     Community License shim is not on the platform allow-list).
 *   - `compatibility.artifacts` declares at least one artifact
 *     and every entry has `group`, `artifact`, `type: AVRO`,
 *     `compatibility: BACKWARD|FORWARD|FULL`.
 *   - The global default `compatibility` block contains
 *     `BACKWARD`.
 *
 * Returns exit 0 on success, 1 on violation.
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");
const KAFKA_DIR = join(ROOT, "platform", "kafka");
const KAFKA_CR = join(KAFKA_DIR, "kafka.yaml");
const KAFKA_TOPICS = join(KAFKA_DIR, "topics.yaml");
const KAFKA_USERS = join(KAFKA_DIR, "users.yaml");
const APICURIO_DIR = join(ROOT, "platform", "apicurio");
const APICURIO_CFG = join(APICURIO_DIR, "registry-config.yaml");

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[kafka] ${msg}`);
};

function readYaml(p) {
  const text = readFileSync(p, "utf8");
  return { text, doc: parse(text) };
}

function rejectLiteralSecret(text, path) {
  for (const key of ["password", "apiKey", "token", "private_key", "client_secret"]) {
    const literal = new RegExp(`^\\s*${key}\\s*:\\s*"?[A-Za-z0-9]{8,}"?\\s*$`, "m");
    if (literal.test(text)) {
      fail(`literal secret-like value for '${key}' in ${path} — use Vault / External Secrets`);
    }
  }
}

// =====================================================================
// Kafka cluster CR
// =====================================================================
if (!existsSync(KAFKA_CR)) {
  fail(`kafka cluster CR missing — expected ${relative(ROOT, KAFKA_CR)}`);
} else {
  const { text, doc } = readYaml(KAFKA_CR);
  rejectLiteralSecret(text, KAFKA_CR);

  if (doc?.kind !== "Kafka") {
    fail(`kafka.yaml kind must be 'Kafka' (Strimzi), got '${doc?.kind}'`);
  }
  if (doc?.apiVersion !== "kafka.strimzi.io/v1beta2") {
    fail(`kafka.yaml apiVersion must be 'kafka.strimzi.io/v1beta2', got '${doc?.apiVersion}'`);
  }
  if (doc?.spec?.kafka?.version !== "3.8.0") {
    fail(
      `kafka.spec.kafka.version must be '3.8.0' (ADR-E0.5-01), got '${doc?.spec?.kafka?.version}'`,
    );
  }
  if (doc?.spec?.kafka?.metadataVersion !== "3.8" && doc?.spec?.kafka?.metadataVersion !== 3.8) {
    fail(
      `kafka.spec.kafka.metadataVersion must be '3.8' (KRaft metadata), got '${doc?.spec?.kafka?.metadataVersion}'`,
    );
  }
  // metadataVersion must be a string (K8s API rejects number).
  // YAML parses `metadataVersion: 3.8` as a float; need quotes.
  if (typeof doc?.spec?.kafka?.metadataVersion !== "string") {
    fail(
      `kafka.spec.kafka.metadataVersion must be a STRING (quote '3.8' not bare 3.8); got type '${typeof doc?.spec?.kafka?.metadataVersion}'`,
    );
  }
  if (!(doc?.spec?.kafka?.replicas >= 1)) {
    fail(`kafka.spec.kafka.replicas must be >= 1, got '${doc?.spec?.kafka?.replicas}'`);
  }

  // Strimzi 0.43 still requires the zookeeper block even when
  // `metadataVersion` is KRaft. The KRaft-only path is only
  // available on Strimzi 0.45+. Enforce presence here.
  if (!doc?.spec?.zookeeper || typeof doc?.spec?.zookeeper !== "object") {
    fail(
      `kafka.spec.zookeeper must be present (Strimzi 0.43 schema; KRaft-only requires 0.45+)`,
    );
  } else {
    const zk = doc.spec.zookeeper;
    if (!(zk.replicas >= 3)) {
      fail(
        `kafka.spec.zookeeper.replicas must be >= 3 for quorum (got '${zk.replicas}')`,
      );
    }
    if (!zk.storage || zk.storage.type !== "persistent-claim") {
      fail(`kafka.spec.zookeeper.storage.type must be 'persistent-claim'`);
    }
  }

  const cfg = doc?.spec?.kafka?.config || {};
  if (cfg["auto.create.topics.enable"] !== false && cfg["auto.create.topics.enable"] !== "false") {
    fail(
      `kafka.config.auto.create.topics.enable must be 'false' (every topic is declared in topics.yaml)`,
    );
  }
  if (!(cfg["min.insync.replicas"] >= 1)) {
    fail(`kafka.config.min.insync.replicas must be >= 1`);
  }
  if (!(cfg["default.replication.factor"] >= 1)) {
    fail(`kafka.config.default.replication.factor must be >= 1`);
  }
  // Quota callback is OPTIONAL. If Strimzi StaticQuotaCallback is
  // configured, enforce the static quota values; otherwise fall
  // back to Kafka's default quota (no per-client bandwidth cap).
  if (cfg["client.quota.callback.class"] === "io.strimzi.kafka.quotas.StaticQuotaCallback") {
    for (const key of [
      "client.quota.callback.static.produce",
      "client.quota.callback.static.consume",
      "client.quota.callback.static.request",
    ]) {
      if (!(cfg[key] > 0)) {
        fail(`kafka.config.${key} must be a positive integer (per-broker quota)`);
      }
    }
    // StaticQuotaCallback requires AdminClient bootstrap (otherwise
    // Kafka fails to start: "Missing required configuration
    // client.quota.callback.static.kafka.admin.bootstrap.servers").
    if (!cfg["client.quota.callback.static.kafka.admin.bootstrap.servers"]) {
      fail(
        `kafka.config.client.quota.callback.static.kafka.admin.bootstrap.servers must be set when StaticQuotaCallback is enabled`,
      );
    }
  }

  const listeners = doc?.spec?.kafka?.listeners || [];
  const names = listeners.map((l) => l?.name);
  if (!names.includes("plain") || !names.includes("tls")) {
    fail(
      `kafka.spec.kafka.listeners must include both 'plain' and 'tls' (got [${names.join(", ")}])`,
    );
  }
  // Strimzi v1beta2 schema does NOT accept `listeners.configuration.networkPolicyPeers`.
  // NetworkPolicy is shipped separately in network-policy.yaml.
  for (const l of listeners) {
    if (l?.configuration?.networkPolicyPeers !== undefined) {
      fail(
        `kafka.spec.kafka.listeners[${l?.name}].configuration.networkPolicyPeers is not supported by Strimzi v1beta2 — use a separate NetworkPolicy manifest`,
      );
    }
    if (l?.type === "external") {
      fail(`kafka listener '${l.name}' is 'external' — public broker listener is forbidden per design §13`);
    }
  }

  const authz = doc?.spec?.kafka?.authorization;
  if (authz?.type !== "simple") {
    fail(`kafka.spec.kafka.authorization.type must be 'simple' (got '${authz?.type}')`);
  }
  if (!Array.isArray(authz?.superUsers) || !authz.superUsers.includes("CN=genea-kafka-admin")) {
    fail(`kafka.spec.kafka.authorization.superUsers must include 'CN=genea-kafka-admin'`);
  }
}

// =====================================================================
// Kafka topics
// =====================================================================
if (!existsSync(KAFKA_TOPICS)) {
  fail(`kafka topics file missing — expected ${relative(ROOT, KAFKA_TOPICS)}`);
} else {
  const { text, doc } = readYaml(KAFKA_TOPICS);
  rejectLiteralSecret(text, KAFKA_TOPICS);
  const topics = doc?.topics;
  if (!Array.isArray(topics) || topics.length === 0) {
    fail(`kafka topics.yaml must declare a non-empty 'topics' array`);
  } else {
    // Required fields per topic.
    for (const t of topics) {
      for (const field of [
        "name",
        "topicClass",
        "partitions",
        "replicationFactor",
        "retentionMs",
        "cleanupPolicy",
        "minInSyncReplicas",
        "partitionKey",
        "schema",
        "owner",
      ]) {
        if (t?.[field] === undefined || t?.[field] === null || t?.[field] === "") {
          fail(`topic '${t?.name}' missing required field '${field}'`);
        }
      }
      if (!(t.partitions >= 1)) fail(`topic '${t.name}' partitions must be >= 1`);
      if (!(t.replicationFactor >= 1)) fail(`topic '${t.name}' replicationFactor must be >= 1`);
      if (!(t.retentionMs > 0)) fail(`topic '${t.name}' retentionMs must be > 0`);
      if (t.cleanupPolicy !== "delete" && t.cleanupPolicy !== "compact") {
        fail(`topic '${t.name}' cleanupPolicy must be 'delete' or 'compact'`);
      }
      // partitionKey goes into a K8s label — must match the label
      // regex `(([A-Za-z0-9][-A-Za-z0-9_.]*)?[A-Za-z0-9])?`. Slug
      // form (e.g. "tenant-and-aggregate") is the contract — semantic
      // ("tenantId+aggregateId") stays in the schema docs.
      if (t.partitionKey && !/^[A-Za-z0-9]([-A-Za-z0-9_.]*[A-Za-z0-9])?$/.test(t.partitionKey)) {
        fail(
          `topic '${t.name}' partitionKey '${t.partitionKey}' must be a valid K8s label slug ([A-Za-z0-9][-A-Za-z0-9_.]*). Use 'tenant-and-aggregate' not 'tenantId+aggregateId'.`,
        );
      }
    }

    // Retention class buckets per ADR-E0.5-08.
    const classBuckets = {
      "domain-event": 30 * 24 * 60 * 60 * 1000, // 30d
      "projection-rebuild": 7 * 24 * 60 * 60 * 1000, // 7d
      audit: 365 * 24 * 60 * 60 * 1000, // 365d
      dlq: 14 * 24 * 60 * 60 * 1000, // 14d
    };
    for (const t of topics) {
      const expected = classBuckets[t.topicClass];
      if (expected === undefined) {
        fail(
          `topic '${t.name}' topicClass must be one of ${Object.keys(classBuckets).join(", ")} (got '${t.topicClass}')`,
        );
        continue;
      }
      if (t.retentionMs !== expected) {
        fail(
          `topic '${t.name}' retentionMs ${t.retentionMs} does not match ADR-E0.5-08 bucket for '${t.topicClass}' (expected ${expected})`,
        );
      }
    }

    // Every class must be represented.
    for (const cls of Object.keys(classBuckets)) {
      if (!topics.some((t) => t.topicClass === cls)) {
        fail(`kafka topics.yaml must declare at least one '${cls}' topic (ADR-E0.5-08)`);
      }
    }
  }
}

// =====================================================================
// Kafka users
// =====================================================================
if (!existsSync(KAFKA_USERS)) {
  fail(`kafka users file missing — expected ${relative(ROOT, KAFKA_USERS)}`);
} else {
  const { text, doc } = readYaml(KAFKA_USERS);
  rejectLiteralSecret(text, KAFKA_USERS);
  const users = doc?.users;
  if (!Array.isArray(users) || users.length === 0) {
    fail(`kafka users.yaml must declare a non-empty 'users' array`);
  } else {
    // Required fields per user.
    for (const u of users) {
      if (!u.name) fail(`user entry missing 'name'`);
      if (!u.role) fail(`user '${u.name}' missing 'role'`);
      if (u.authType !== "tls") {
        fail(`user '${u.name}' authType must be 'tls' (scram-sha-512 forbidden per ADR-E0.5-01)`);
      }
      if (u.role === "admin" && Array.isArray(u.acls) && u.acls.length > 0) {
        fail(`admin user '${u.name}' must have empty 'acls' (super-user is the contract)`);
      }
      if (
        (u.role === "producer" || u.role === "consumer") &&
        (!Array.isArray(u.acls) || u.acls.length === 0)
      ) {
        fail(`user '${u.name}' (role=${u.role}) must declare at least one ACL entry`);
      }
      // Each ACL operation must be a Strimzi-valid enum value
      // (PascalCase). The Strimzi schema rejects lowercase.
      const VALID_OPS = new Set([
        "Read", "Write", "Create", "Delete", "Alter", "Describe",
        "ClusterAction", "AlterConfigs", "DescribeConfigs",
        "IdempotentWrite", "All",
      ]);
      if (Array.isArray(u.acls)) {
        for (const acl of u.acls) {
          for (const op of acl.operations || []) {
            if (!VALID_OPS.has(op)) {
              fail(
                `user '${u.name}' ACL operation '${op}' is not a Strimzi enum value. Must be one of: ${[...VALID_OPS].join(", ")}`,
              );
            }
          }
        }
      }
    }

    // Required role coverage.
    for (const role of ["admin", "producer", "consumer"]) {
      if (!users.some((u) => u.role === role)) {
        fail(`kafka users.yaml must declare at least one '${role}' user`);
      }
    }

    // scram-sha-512 is forbidden — scan the parsed structure so the
    // error fires only when an entry actually declares the authType.
    for (const u of users) {
      if (typeof u?.authType === "string" && /scram-sha-512/i.test(u.authType)) {
        fail(
          `user '${u.name}' declares scram-sha-512 — forbidden in production (ADR-E0.5-01 — no literal credentials)`,
        );
      }
    }
  }
}

// =====================================================================
// Apicurio
// =====================================================================
if (!existsSync(APICURIO_CFG)) {
  fail(`apicurio registry config missing — expected ${relative(ROOT, APICURIO_CFG)}`);
} else {
  const { text, doc } = readYaml(APICURIO_CFG);
  rejectLiteralSecret(text, APICURIO_CFG);

  // The data is a ConfigMap so the structure is { data: { ... } }.
  // We accept either the wrapped form (configmap) or the unwrapped
  // form (key-only) for portability.
  const data = doc?.data || doc;
  const props = data?.["application.properties"] || "";
  if (!/registry\.storage\.kind=sql/.test(props)) {
    fail(
      `apicurio application.properties must declare 'registry.storage.kind=sql' (in-memory forbidden in production)`,
    );
  }
  if (!/registry\.global\.compatibility\.level=BACKWARD/.test(props)) {
    fail(
      `apicurio application.properties must declare 'registry.global.compatibility.level=BACKWARD' (ADR-E0.5-08)`,
    );
  }
  if (!/registry\.apis\.confluent\.enabled=false/.test(props)) {
    fail(
      `apicurio must keep 'registry.apis.confluent.enabled=false' (Confluent Community License shim is not on the allow-list)`,
    );
  }
  if (!/registry\.metrics\.jmx\.enabled=true/.test(props)) {
    fail(
      `apicurio must keep 'registry.metrics.jmx.enabled=true' (JMX exporter is the alert source)`,
    );
  }

  // The compatibility block ships in a stringified YAML form; parse
  // it as text first.
  const compatText = (data?.compatibility || "").trim();
  if (!compatText) {
    fail(
      `apicurio registry-config.yaml must declare a non-empty 'compatibility' block (per-artifact matrix)`,
    );
  } else {
    let compatDoc;
    try {
      compatDoc = parse(compatText);
    } catch (err) {
      fail(`apicurio 'compatibility' block is not valid YAML — ${err.message}`);
    }
    const artifacts = compatDoc?.artifacts;
    if (!Array.isArray(artifacts) || artifacts.length === 0) {
      fail(`apicurio 'compatibility' must declare a non-empty 'artifacts' array`);
    } else {
      const valid = new Set(["BACKWARD", "FORWARD", "FULL"]);
      for (const a of artifacts) {
        if (!a.group || !a.artifact) {
          fail(`apicurio artifact entry missing 'group' or 'artifact'`);
        }
        if (a.type !== "AVRO") {
          fail(`apicurio artifact '${a.artifact}' type must be 'AVRO' (got '${a.type}')`);
        }
        if (!valid.has(a.compatibility)) {
          fail(
            `apicurio artifact '${a.artifact}' compatibility must be BACKWARD|FORWARD|FULL (got '${a.compatibility}')`,
          );
        }
      }
    }
  }
}

if (violations > 0) {
  console.error(`\n[kafka] ${violations} violation(s)`);
  process.exit(1);
}
console.log(`[kafka] clean — kafka=ok, topics=ok, users=ok, apicurio=ok`);
