/**
 * Unit tests for scripts/check-gradle-lockfile.mjs.
 */
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, writeFileSync, rmSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const SCRIPT = new URL("../check-gradle-lockfile.mjs", import.meta.url);

function runOn(cwd) {
  return spawnSync("node", [SCRIPT.pathname], {
    cwd,
    encoding: "utf8",
    env: { ...process.env, GRADLE_LOCKFILE_ROOT: cwd },
  });
}

describe("check-gradle-lockfile", () => {
  it("passes when subproject with lockAll has gradle.lockfile", () => {
    const cwd = mkdtempSync(join(tmpdir(), "gl-ok-"));
    try {
      writeFileSync(join(cwd, "settings.gradle.kts"), "include(\":foo\")\n");
      mkdirSync(join(cwd, "foo"));
      writeFileSync(join(cwd, "foo", "build.gradle.kts"), "dependencyLocking { lockAllConfigurations() }\n");
      writeFileSync(join(cwd, "foo", "gradle.lockfile"), "# empty\n");
      const res = runOn(cwd);
      assert.equal(res.status, 0, res.stderr);
    } finally {
      rmSync(cwd, { recursive: true, force: true });
    }
  });

  it("fails when lockAll subproject is missing gradle.lockfile", () => {
    const cwd = mkdtempSync(join(tmpdir(), "gl-missing-"));
    try {
      writeFileSync(join(cwd, "settings.gradle.kts"), "include(\":foo\")\n");
      mkdirSync(join(cwd, "foo"));
      writeFileSync(join(cwd, "foo", "build.gradle.kts"), "dependencyLocking { lockAllConfigurations() }\n");
      const res = runOn(cwd);
      assert.equal(res.status, 1);
      assert.match(res.stderr, /gradle\.lockfile missing/);
    } finally {
      rmSync(cwd, { recursive: true, force: true });
    }
  });
});