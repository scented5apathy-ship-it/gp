/**
 * scripts/__tests__/lint-dna-isolation.test.mjs
 *
 * Unit tests for `scripts/lint-dna-isolation.mjs`.
 * Mirrors the E9 linter test pattern: spawn the linter against
 * the canonical contract and assert exit 0, then against an
 * ad-hoc empty/missing contract to assert exit 1.
 */
import { describe, it, before, after } from "node:test";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";

const SCRIPT = "scripts/lint-dna-isolation.mjs";

function runLinter(env) {
  return spawnSync("node", [SCRIPT], {
    cwd: process.cwd(),
    env: { ...process.env, ...env },
    encoding: "utf8",
  });
}

describe("lint-dna-isolation", () => {
  let tmpDir;

  before(() => {
    tmpDir = mkdtempSync(join(tmpdir(), "lint-dna-iso-"));
  });

  after(() => {
    if (tmpDir) rmSync(tmpDir, { recursive: true, force: true });
  });

  it("passes when the canonical contract is valid", () => {
    const result = runLinter({});
    if (result.status !== 0) {
      // eslint-disable-next-line no-console
      console.error(result.stdout);
      // eslint-disable-next-line no-console
      console.error(result.stderr);
    }
    if (result.status !== 0) {
      throw new Error(`linter exited ${result.status}, expected 0`);
    }
    if (!result.stdout.includes("E10.2 DNA isolation policy contract OK")) {
      throw new Error(`missing success marker in:\n${result.stdout}`);
    }
  });

  it("fails when the contract is missing a closed-set vocabulary", () => {
    const broken = join(tmpDir, "broken.yaml");
    writeFileSync(
      broken,
      [
        "policyVersion: 1.0.0",
        "owner: dna",
        "dnaDatabaseRoles:",
        "  values: [dna_app_ro]",
        "",
      ].join("\n"),
      "utf8",
    );
    const result = runLinter({
      LINT_ROOT: join(process.cwd(), tmpDir),
    });
    if (result.status === 0) {
      throw new Error(`linter unexpectedly passed for broken contract`);
    }
  });

  it("emits forbidden payload patterns confirmation", () => {
    const result = runLinter({});
    if (!result.stdout.includes("forbidden payload patterns")) {
      throw new Error(`expected 'forbidden payload patterns' line in output`);
    }
  });

  it("confirms chart mirror byte-equality", () => {
    const result = runLinter({});
    if (!result.stdout.includes("chart mirror byte-equal")) {
      throw new Error(`expected chart mirror byte-equal confirmation`);
    }
  });
});