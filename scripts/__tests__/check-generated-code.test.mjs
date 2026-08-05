/**
 * Unit tests for scripts/check-generated-code.mjs. Run with `node --test`.
 */
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, writeFileSync, rmSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const SCRIPT = new URL("../check-generated-code.mjs", import.meta.url);

function runOn(cwd) {
  return spawnSync("node", [SCRIPT.pathname], {
    cwd,
    encoding: "utf8",
    env: { ...process.env, GENERATED_ROOT: cwd },
  });
}

describe("check-generated-code", () => {
  it("passes when generated file carries @generated marker", () => {
    const cwd = mkdtempSync(join(tmpdir(), "gen-ok-"));
    try {
      const dir = join(cwd, "src", "generated");
      mkdirSync(dir, { recursive: true });
      writeFileSync(
        join(dir, "Foo.generated.ts"),
        "// @generated\n// do not edit by hand\nexport const x = 1;\n",
      );
      const res = runOn(cwd);
      assert.equal(res.status, 0, res.stderr);
    } finally {
      rmSync(cwd, { recursive: true, force: true });
    }
  });

  it("rejects generated file without marker", () => {
    const cwd = mkdtempSync(join(tmpdir(), "gen-bad-"));
    try {
      const dir = join(cwd, "src", "generated");
      mkdirSync(dir, { recursive: true });
      writeFileSync(
        join(dir, "Bar.generated.ts"),
        "export const x = 1;\n",
      );
      const res = runOn(cwd);
      assert.equal(res.status, 1);
      assert.match(res.stderr, /@generated/);
    } finally {
      rmSync(cwd, { recursive: true, force: true });
    }
  });
});