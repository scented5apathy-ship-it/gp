#!/usr/bin/env node
/**
 * scripts/__tests__/lint-kafka-config.test.mjs
 *
 * Unit tests for `scripts/lint-kafka-config.mjs` (E2.3).
 *
 * Strategy: drive the linter with `LINT_ROOT` pointed at a tempdir
 * that mirrors `platform/kafka/` and `platform/apicurio/`. Assert:
 *   1. The shipped kafka + apicurio config passes.
 *   2. A synthetic kafka.yaml without the `Kafka` kind fails.
 *   3. A topics.yaml that drops the `audit` class fails.
 *   4. A topics.yaml that declares an unknown class fails.
 *   5. A users.yaml that wires scram-sha-512 fails.
 *   6. A users.yaml without the admin user fails.
 *   7. An apicurio config that flips the Confluent shim back on
 *      fails.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  mkdtempSync,
  mkdirSync,
  readdirSync,
  readFileSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const SCRIPT = join(ROOT, "scripts", "lint-kafka-config.mjs");

function runScript(env) {
  return spawnSync(process.execPath, [SCRIPT], {
    cwd: ROOT,
    env: { ...process.env, ...env },
    encoding: "utf8",
  });
}

function copyTree(from, to) {
  mkdirSync(to, { recursive: true });
  for (const entry of readdirSync(from)) {
    const src = join(from, entry);
    const dst = join(to, entry);
    if (statSync(src).isDirectory()) {
      copyTree(src, dst);
    } else {
      const text = readFileSync(src, "utf8");
      mkdirSync(dirname(dst), { recursive: true });
      writeFileSync(dst, text);
    }
  }
}

function makeFixture() {
  const dir = mkdtempSync(join(tmpdir(), "kafka-"));
  copyTree(join(ROOT, "platform", "kafka"), join(dir, "platform", "kafka"));
  copyTree(join(ROOT, "platform", "apicurio"), join(dir, "platform", "apicurio"));
  return dir;
}

test("kafka: shipped config passes", () => {
  const proc = runScript({});
  assert.equal(
    proc.status,
    0,
    `expected exit 0, got ${proc.status}\nstdout=${proc.stdout}\nstderr=${proc.stderr}`,
  );
  assert.match(proc.stdout, /\[kafka\] clean/);
});

test("kafka: kafka.yaml without Kafka kind fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kafka", "kafka.yaml");
    const text = readFileSync(target, "utf8").replace(/^kind:\s*Kafka$/m, "kind: NotKafka");
    writeFileSync(target, text);
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /kind must be 'Kafka'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kafka: topics.yaml missing audit class fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kafka", "topics.yaml");
    const text = readFileSync(target, "utf8").replace(
      /\n\s*-\s*name:\s*genealogy\.audit\.v1\.v1[\s\S]*?(?=\n\s*-\s*name:|\n[a-z])/,
      "\n",
    );
    writeFileSync(target, text);
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /at least one 'audit' topic/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kafka: topics.yaml unknown class fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kafka", "topics.yaml");
    const text = readFileSync(target, "utf8").replace(
      /topicClass:\s*domain-event/,
      "topicClass: wrong-class",
    );
    writeFileSync(target, text);
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /topicClass must be one of/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kafka: partitionKey with + fails (K8s label regex)", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kafka", "topics.yaml");
    const text = readFileSync(target, "utf8").replace(
      /partitionKey: tenant-and-aggregate/,
      "partitionKey: tenantId+aggregateId",
    );
    writeFileSync(target, text);
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /must be a valid K8s label slug/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kafka: metadataVersion as number fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kafka", "kafka.yaml");
    // Drop the quotes to simulate the bug — `metadataVersion: 3.8`
    // parses as a float in YAML.
    const text = readFileSync(target, "utf8").replace(
      /metadataVersion:\s*"3\.8"/,
      "metadataVersion: 3.8",
    );
    writeFileSync(target, text);
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /metadataVersion must be a STRING/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kafka: zookeeper block missing fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kafka", "kafka.yaml");
    const text = readFileSync(target, "utf8").replace(
      /  zookeeper:[\s\S]*?    resources:[\s\S]*?      requests:[\s\S]*?        cpu: 200m[\s\S]*?        memory: 512Mi/,
      "",
    );
    writeFileSync(target, text);
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /spec\.zookeeper must be present/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kafka: networkPolicyPeers in listener fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kafka", "kafka.yaml");
    const text = readFileSync(target, "utf8").replace(
      /      - name: plain\n        port: 9092\n        type: internal\n        tls: false/,
      `      - name: plain\n        port: 9092\n        type: internal\n        tls: false\n        configuration:\n          networkPolicyPeers:\n            - podSelector:\n                matchLabels:\n                  foo: bar`,
    );
    writeFileSync(target, text);
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /networkPolicyPeers is not supported/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kafka: users.yaml with scram-sha-512 fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kafka", "users.yaml");
    const text = readFileSync(target, "utf8").replace(
      /authType:\s*tls/,
      "authType: scram-sha-512",
    );
    writeFileSync(target, text);
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /scram-sha-512/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kafka: users.yaml with lowercase ACL operation fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kafka", "users.yaml");
    // Replace first `[Write, Describe]` with `[write, describe]`
    // (lowercase). Strimzi schema requires PascalCase.
    const text = readFileSync(target, "utf8").replace(
      /\[Write, Describe\]/,
      "[write, describe]",
    );
    writeFileSync(target, text);
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /is not a Strimzi enum value/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kafka: users.yaml missing admin user fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "kafka", "users.yaml");
    const text = readFileSync(target, "utf8").replace(
      /role:\s*admin/,
      "role: ops",
    );
    writeFileSync(target, text);
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /must declare at least one 'admin' user/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("kafka: apicurio confluent shim re-enabled fails", () => {
  const dir = makeFixture();
  try {
    const target = join(dir, "platform", "apicurio", "registry-config.yaml");
    const text = readFileSync(target, "utf8").replace(
      /registry\.apis\.confluent\.enabled=false/,
      "registry.apis.confluent.enabled=true",
    );
    writeFileSync(target, text);
    const proc = runScript({ LINT_ROOT: dir });
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /confluent\.enabled=false/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
