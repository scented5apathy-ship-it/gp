/**
 * scripts/__tests__/lint-privacy-export.test.mjs
 *
 * Unit tests for `scripts/lint-privacy-export.mjs`.
 */
import { describe, it } from "node:test";
import { spawnSync } from "node:child_process";

const SCRIPT = "scripts/lint-privacy-export.mjs";

function runLinter(env) {
  return spawnSync("node", [SCRIPT], {
    cwd: process.cwd(),
    env: { ...process.env, ...env },
    encoding: "utf8",
  });
}

describe("lint-privacy-export", () => {
  it("passes when the canonical contract is valid", () => {
    const result = runLinter({});
    if (result.status !== 0) {
      // eslint-disable-next-line no-console
      console.error(result.stdout);
      // eslint-disable-next-line no-console
      console.error(result.stderr);
    }
    if (result.status !== 0) throw new Error(`linter exited ${result.status}, expected 0`);
    if (!result.stdout.includes("E9.4 privacy-aware export policy contract OK")) {
      throw new Error(`missing success marker in:\n${result.stdout}`);
    }
  });

  it("confirms chart mirror byte-equality", () => {
    const result = runLinter({});
    if (!result.stdout.includes("chart mirror byte-equal")) {
      throw new Error(`expected chart mirror byte-equal confirmation`);
    }
  });
});