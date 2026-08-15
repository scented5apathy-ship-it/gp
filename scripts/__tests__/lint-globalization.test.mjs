/**
 * scripts/__tests__/lint-globalization.test.mjs
 *
 * Unit tests for `scripts/lint-globalization.mjs` (E12.3).
 */
import { describe, it, before, after } from "node:test";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const SCRIPT = "scripts/lint-globalization.mjs";

function runLinter(env) {
  return spawnSync("node", [SCRIPT], {
    cwd: process.cwd(),
    env: { ...process.env, ...env },
    encoding: "utf8",
  });
}

describe("lint-globalization", () => {
  let tmpDir;
  before(() => {
    tmpDir = mkdtempSync(join(tmpdir(), "lint-e12-3-"));
  });
  after(() => {
    if (tmpDir) rmSync(tmpDir, { recursive: true, force: true });
  });

  it("passes when the canonical contract is valid", () => {
    const result = runLinter({});
    if (result.status !== 0) {
      console.error(result.stdout);
      console.error(result.stderr);
      throw new Error(`linter exited ${result.status}, expected 0`);
    }
    if (!result.stdout.includes("E12.3 summary: OK")) {
      throw new Error(`missing success marker in:\n${result.stdout}`);
    }
  });

  it("emits chart mirror byte-equal confirmation", () => {
    const result = runLinter({});
    if (!result.stdout.includes("chart mirror byte-equal")) {
      throw new Error(`expected chart mirror byte-equal confirmation`);
    }
  });

  it("emits supportedLocales closed-set confirmation (20 locales)", () => {
    const result = runLinter({});
    if (!result.stdout.includes("supportedLocales (20 values)")) {
      throw new Error(`expected 'supportedLocales (20 values)' line`);
    }
  });

  it("emits invariants confirmation (14 invariants)", () => {
    const result = runLinter({});
    if (!result.stdout.includes("invariants (14 invariants)")) {
      throw new Error(`expected 'invariants (14 invariants)' line`);
    }
  });

  it("fails when the contract is missing a closed-set vocabulary", () => {
    const broken = join(tmpDir, "broken-globalization.yaml");
    writeFileSync(
      broken,
      [
        "policyVersion: 1.0.0",
        "owner: pwa",
        "supportedLocales:",
        "  values: [en]",
        "",
      ].join("\n"),
      "utf8",
    );
    const result = runLinter({ LINT_ROOT: join(process.cwd(), tmpDir) });
    if (result.status === 0) {
      throw new Error(`linter unexpectedly passed for broken contract`);
    }
  });
});