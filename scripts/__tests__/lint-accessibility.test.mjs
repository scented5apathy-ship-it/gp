/**
 * scripts/__tests__/lint-accessibility.test.mjs
 *
 * Unit tests for `scripts/lint-accessibility.mjs` (E12.4).
 */
import { describe, it, before, after } from "node:test";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const SCRIPT = "scripts/lint-accessibility.mjs";

function runLinter(env) {
  return spawnSync("node", [SCRIPT], {
    cwd: process.cwd(),
    env: { ...process.env, ...env },
    encoding: "utf8",
  });
}

describe("lint-accessibility", () => {
  let tmpDir;
  before(() => {
    tmpDir = mkdtempSync(join(tmpdir(), "lint-e12-4-"));
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
    if (!result.stdout.includes("E12.4 summary: OK")) {
      throw new Error(`missing success marker in:\n${result.stdout}`);
    }
  });

  it("emits chart mirror byte-equal confirmation", () => {
    const result = runLinter({});
    if (!result.stdout.includes("chart mirror byte-equal")) {
      throw new Error(`expected chart mirror byte-equal confirmation`);
    }
  });

  it("emits a11yAuditEvents closed-set confirmation (10 events)", () => {
    const result = runLinter({});
    if (!result.stdout.includes("a11yAuditEvents (10 values)")) {
      throw new Error(`expected 'a11yAuditEvents (10 values)' line`);
    }
  });

  it("emits wcagSuccessCriteria confirmation (16 criteria)", () => {
    const result = runLinter({});
    if (!result.stdout.includes("wcagSuccessCriteria (16 criteria)")) {
      throw new Error(`expected 'wcagSuccessCriteria (16 criteria)' line`);
    }
  });

  it("emits invariants confirmation (12 invariants)", () => {
    const result = runLinter({});
    if (!result.stdout.includes("invariants (12 invariants)")) {
      throw new Error(`expected 'invariants (12 invariants)' line`);
    }
  });

  it("fails when the contract is missing a closed-set vocabulary", () => {
    const broken = join(tmpDir, "broken-accessibility.yaml");
    writeFileSync(
      broken,
      [
        "policyVersion: 1.0.0",
        "owner: pwa",
        "a11yAuditEvents:",
        "  values: [a11y.focusTrapEntered]",
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