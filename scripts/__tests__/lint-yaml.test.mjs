/**
 * Unit tests for scripts/lint-yaml.mjs. Run with `node --test`.
 * Mirrors the style of Node's built-in test runner so the gate has no
 * extra dependencies (vitest/jest) at the repository root.
 */
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, writeFileSync, rmSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const SCRIPT = new URL("../lint-yaml.mjs", import.meta.url);

function runOn(cwd) {
  return spawnSync("node", [SCRIPT.pathname], {
    cwd,
    encoding: "utf8",
    env: { ...process.env, LINT_YAML_ROOT: cwd },
  });
}

describe("lint-yaml", () => {
  it("passes on a clean fixture", () => {
    const cwd = mkdtempSync(join(tmpdir(), "yaml-ok-"));
    try {
      mkdirSync(join(cwd, "config"));
      writeFileSync(join(cwd, "config", "ok.yaml"), "---\nkey: value\n");
      const res = runOn(cwd);
      assert.equal(res.status, 0, res.stderr);
    } finally {
      rmSync(cwd, { recursive: true, force: true });
    }
  });

  it("rejects tab characters", () => {
    const cwd = mkdtempSync(join(tmpdir(), "yaml-tab-"));
    try {
      mkdirSync(join(cwd, "config"));
      writeFileSync(join(cwd, "config", "bad.yaml"), "---\nkey:\tvalue\n");
      const res = runOn(cwd);
      assert.equal(res.status, 1);
      assert.match(res.stderr, /tab character/);
    } finally {
      rmSync(cwd, { recursive: true, force: true });
    }
  });

  it("rejects sensitive keys", () => {
    const cwd = mkdtempSync(join(tmpdir(), "yaml-secret-"));
    try {
      mkdirSync(join(cwd, "config"));
      writeFileSync(join(cwd, "config", "secret.yaml"), "---\npassword: hunter2\n");
      const res = runOn(cwd);
      assert.equal(res.status, 1);
      assert.match(res.stderr, /sensitive key/);
    } finally {
      rmSync(cwd, { recursive: true, force: true });
    }
  });
});