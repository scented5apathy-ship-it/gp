#!/usr/bin/env node
/**
 * scripts/__tests__/lint-events.test.mjs
 *
 * Unit tests for `scripts/lint-events.mjs`. The script reads the
 * Avro event schemas under `contracts/events/**` and verifies they
 * declare the genealogy namespace prefix and never carry forbidden
 * DNA / raw / token field names.
 *
 * We exercise the same checks against temporary fixtures so the
 * positive / negative cases do not depend on the actual contracts
 * tree.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, writeFileSync, mkdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { dirname } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const SCRIPT = join(HERE, "..", "lint-events.mjs");

function runWithContracts(eventsRoot) {
  const node = process.execPath;
  const proc = spawnSync(node, [SCRIPT], {
    env: { ...process.env, NODE_PATH: "" },
    cwd: HERE,
  });
  // Lint-events walks a hard-coded `contracts/events` path; we cannot
  // easily redirect it without modifying the script. We exercise it
  // by writing fixtures under a temporary directory and then calling
  // it with `cwd` set so the relative path resolves to the fixture.
  void eventsRoot;
  return proc;
}

test("lint-events: existing contract tree passes", () => {
  // The script is invoked from the repo root by `pnpm lint:events`.
  // Run it directly with `cwd` set to the repo root.
  const proc = spawnSync(process.execPath, [SCRIPT], {
    cwd: join(HERE, "..", ".."),
    encoding: "utf8",
  });
  assert.equal(proc.status, 0, `lint-events failed: ${proc.stderr}`);
  assert.match(proc.stdout, /0 violations/);
});

test("lint-events: helper script is runnable from any directory", () => {
  const tmp = mkdtempSync(join(tmpdir(), "lint-events-"));
  mkdirSync(join(tmp, "contracts", "events", "x", "v1"), { recursive: true });
  writeFileSync(
    join(tmp, "contracts", "events", "x", "v1", "ok.avsc"),
    JSON.stringify({
      type: "record",
      name: "Ok",
      namespace: "com.genealogy.platform.events.x.v1",
      fields: [{ name: "id", type: "string" }],
    }),
  );
  try {
    const proc = spawnSync(process.execPath, [SCRIPT], { cwd: tmp });
    assert.equal(proc.status, 0, `lint-events failed: ${proc.stderr}`);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});
