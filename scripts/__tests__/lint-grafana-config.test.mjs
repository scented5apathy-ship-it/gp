#!/usr/bin/env node
/**
 * scripts/__tests__/lint-grafana-config.test.mjs
 *
 * Unit tests for the E2.10 Grafana OSS stack deep
 * validator (`scripts/lint-grafana-config.mjs`). The test
 * harness writes ephemeral fixtures under a temp dir,
 * sets `LINT_ROOT=<tmp>`, and asserts exit 0 on clean
 * fixtures + exit 1 on each documented violation.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import { mkdtempSync, writeFileSync, readFileSync, mkdirSync, copyFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..", "..");
const SCRIPT = join(ROOT, "scripts", "lint-grafana-config.mjs");
const FIXTURE_SRC = join(ROOT, "platform", "grafana");
const FIXTURE_MIRROR = join(ROOT, "platform", "helm", "genealogy-platform", "files", "grafana");

function setupCleanTree() {
  const tmp = mkdtempSync(join(tmpdir(), "grafana-lint-"));
  mkdirSync(join(tmp, "platform", "grafana"), { recursive: true });
  mkdirSync(join(tmp, "platform", "helm", "genealogy-platform", "files", "grafana"), { recursive: true });
  for (const f of [
    "otel-collector.yaml",
    "prometheus.yaml",
    "loki.yaml",
    "tempo.yaml",
    "dashboards.yaml",
    "grafana.yaml",
  ]) {
    copyFileSync(join(FIXTURE_SRC, f), join(tmp, "platform", "grafana", f));
    copyFileSync(join(FIXTURE_SRC, f), join(tmp, "platform", "helm", "genealogy-platform", "files", "grafana", f));
  }
  return tmp;
}

function readFixture(tmp, name) {
  return join(tmp, "platform", "grafana", name);
}

function readMirror(tmp, name) {
  return join(tmp, "platform", "helm", "genealogy-platform", "files", "grafana", name);
}

function runLint(tmp, mutate) {
  if (mutate) mutate(tmp);
  return spawnSync("node", [SCRIPT], {
    env: { ...process.env, LINT_ROOT: tmp },
    encoding: "utf8",
  });
}

test("clean fixtures pass lint-grafana-config", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp);
    assert.equal(res.status, 0, `stderr: ${res.stderr}`);
    assert.match(res.stdout, /clean/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("otel-collector.yaml missing otlp/audit receiver fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "otel-collector.yaml");
      const mirror = readMirror(t, "otel-collector.yaml");
      const text = readFileSync(p, "utf8");
      const stripped = text.replace(/^      otlp\/audit:\n(?:        .*\n)*/m, "");
      writeFileSync(p, stripped);
      writeFileSync(mirror, stripped);
    });
    assert.equal(res.status, 1, "lint should fail");
    assert.match(res.stderr, /otlp\/audit/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("otel-collector.yaml missing redaction rule 'raw-dna-marker' fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "otel-collector.yaml");
      const mirror = readMirror(t, "otel-collector.yaml");
      const text = readFileSync(p, "utf8");
      const stripped = text.replace(
        / {10}- name: raw-dna-marker\n {12}pattern:[\s\S]*?replacement:[\s\S]*?\n/,
        "",
      );
      writeFileSync(p, stripped);
      writeFileSync(mirror, stripped);
    });
    assert.equal(res.status, 1);
    assert.match(res.stderr, /raw-dna-marker/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("otel-collector.yaml prometheus exporter missing deny_labels 'email' fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "otel-collector.yaml");
      const mirror = readMirror(t, "otel-collector.yaml");
      const text = readFileSync(p, "utf8");
      const stripped = text.replace(/- email\n {10}- oidc_subject\n/, "- oidc_subject\n");
      writeFileSync(p, stripped);
      writeFileSync(mirror, stripped);
    });
    assert.equal(res.status, 1);
    assert.match(res.stderr, /email/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("prometheus.yaml missing recording rule 'redaction_coverage' fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "prometheus.yaml");
      const mirror = readMirror(t, "prometheus.yaml");
      const text = readFileSync(p, "utf8");
      const stripped = text.replace(
        /        - name: redaction_coverage\n          metric:.*\n          expr:.*\n/,
        "",
      );
      writeFileSync(p, stripped);
      writeFileSync(mirror, stripped);
    });
    assert.equal(res.status, 1);
    assert.match(res.stderr, /redaction_coverage/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("loki.yaml missing stream_label_required 'tenant_pseudo_id' fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "loki.yaml");
      const mirror = readMirror(t, "loki.yaml");
      const text = readFileSync(p, "utf8");
      const stripped = text.replace(/- tenant_pseudo_id\n {8}- actor_pseudo_id/, "- actor_pseudo_id");
      writeFileSync(p, stripped);
      writeFileSync(mirror, stripped);
    });
    assert.equal(res.status, 1);
    assert.match(res.stderr, /tenant_pseudo_id/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("dashboards.yaml missing 'kong' dashboard fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "dashboards.yaml");
      const mirror = readMirror(t, "dashboards.yaml");
      const text = readFileSync(p, "utf8");
      const stripped = text.replace(
        / {6}- slug: kong\n {8}title:[\s\S]*?slo:\n {10}- edge-latency\n {10}- edge-error-rate\n/,
        "",
      );
      writeFileSync(p, stripped);
      writeFileSync(mirror, stripped);
    });
    assert.equal(res.status, 1);
    assert.match(res.stderr, /kong/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("grafana.yaml with anonymous_enabled: true fails", () => {
  const tmp = setupCleanTree();
  try {
    const res = runLint(tmp, (t) => {
      const p = readFixture(t, "grafana.yaml");
      const mirror = readMirror(t, "grafana.yaml");
      const text = readFileSync(p, "utf8");
      // The linter checks `\[auth\][\s\S]*?anonymous_enabled:\s*false`;
      // removing `false` is enough to flip the assertion. We
      // change `false` → `true` (the very first occurrence
      // after `[auth]`).
      const stripped = text.replace(
        "[auth]\n    # E2.10 §6 — anonymous access is FORBIDDEN. Keycloak OIDC\n    # SSO is the sole identity provider.\n    anonymous_enabled: false",
        "[auth]\n    # E2.10 §6 — anonymous access is FORBIDDEN. Keycloak OIDC\n    # SSO is the sole identity provider.\n    anonymous_enabled: true",
      );
      writeFileSync(p, stripped);
      writeFileSync(mirror, stripped);
    });
    assert.equal(res.status, 1, `stderr: ${res.stderr}`);
    assert.match(res.stderr, /anonymous/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("chart mirror drift fails", () => {
  const tmp = setupCleanTree();
  try {
    const mirror = readMirror(tmp, "prometheus.yaml");
    const text = readFileSync(mirror, "utf8");
    writeFileSync(mirror, text + "\n# drifted\n");
    const res = runLint(tmp);
    assert.equal(res.status, 1);
    assert.match(res.stderr, /mirror out of sync/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});