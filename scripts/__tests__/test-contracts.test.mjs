#!/usr/bin/env node
/**
 * scripts/__tests__/test-contracts.test.mjs
 *
 * Smoke test for `scripts/test-contracts.mjs`: spawns the script
 * with `cwd` set to the repo root and verifies the suite exits 0.
 * The script itself contains 11 contract assertions; this wrapper
 * just verifies the runner is wired correctly into CI.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = join(HERE, "..", "..");
const SCRIPT = join(HERE, "..", "test-contracts.mjs");

test("test-contracts: full suite passes against the repo contracts tree", () => {
  const proc = spawnSync(process.execPath, [SCRIPT], {
    cwd: REPO_ROOT,
    encoding: "utf8",
  });
  assert.equal(
    proc.status,
    0,
    `test-contracts failed:\n${proc.stdout}\n${proc.stderr}`,
  );
  assert.match(proc.stdout, /pass 11/);
});
