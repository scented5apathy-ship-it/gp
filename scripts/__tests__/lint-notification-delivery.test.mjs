/**
 * scripts/__tests__/lint-notification-delivery.test.mjs
 *
 * Unit tests for `scripts/lint-notification-delivery.mjs`.
 * Mirrors the E10 linter test pattern: spawn the linter against the
 * canonical contract and assert exit 0 + success marker, then
 * against an ad-hoc broken contract to assert exit 1.
 */
import { describe, it, before, after } from "node:test";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const SCRIPT = "scripts/lint-notification-delivery.mjs";

function runLinter(env) {
  return spawnSync("node", [SCRIPT], {
    cwd: process.cwd(),
    env: { ...process.env, ...env },
    encoding: "utf8",
  });
}

describe("lint-notification-delivery", () => {
  let tmpDir;
  before(() => {
    tmpDir = mkdtempSync(join(tmpdir(), "lint-ndel-"));
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
    if (!result.stdout.includes("E11.2 Privacy-safe delivery contract OK")) {
      throw new Error(`missing success marker in:\n${result.stdout}`);
    }
  });

  it("fails when the contract is missing a closed-set vocabulary", () => {
    const broken = join(tmpDir, "broken.yaml");
    writeFileSync(
      broken,
      [
        "policyVersion: 1.0.0",
        "owner: notifications",
        "preferenceStates:",
        "  values: [OPT_IN]",
        "",
      ].join("\n"),
      "utf8",
    );
    const result = runLinter({ LINT_ROOT: join(process.cwd(), tmpDir) });
    if (result.status === 0) {
      throw new Error(`linter unexpectedly passed for broken contract`);
    }
  });

  it("emits chart mirror byte-equal confirmation", () => {
    const result = runLinter({});
    if (!result.stdout.includes("chart mirror byte-equal")) {
      throw new Error(`expected chart mirror byte-equal confirmation`);
    }
  });

  it("emits forbidden payload keys confirmation", () => {
    const result = runLinter({});
    if (!result.stdout.includes("forbiddenPayloadKeys")) {
      throw new Error(`expected 'forbiddenPayloadKeys' line in output`);
    }
  });
});