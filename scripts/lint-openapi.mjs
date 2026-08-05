#!/usr/bin/env node
/**
 * scripts/lint-openapi.mjs
 *
 * Repository-wide OpenAPI 3.x linter. Wraps `@stoplight/spectral-cli` so
 * that `pnpm lint:openapi` works even when Spectral is not on $PATH —
 * the install lives in the root `node_modules/.bin` (managed by pnpm).
 *
 * Spectral lint ruleset: built-in `oas` plus a small `spectral:oas`
 * extension that flags:
 *   - missing `operationId`
 *   - missing `summary`
 *   - paths without kebab-case segments
 *   - security schemes without `bearerFormat`
 *   - responses without `description`
 *   - `servers` without `description`
 *
 * The ruleset file ships at `config/spectral.yaml`. If the contracts
 * tree is empty, the script prints a notice and exits 0 — E1.2 only
 * provides the lint harness, content lands in E1.3.
 */
import { existsSync, readdirSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, "..");
const CONTRACTS = join(ROOT, "contracts", "openapi");
const RULESET = join(ROOT, "config", "spectral.yaml");

if (!existsSync(CONTRACTS)) {
  console.log("[openapi] no contracts/openapi directory — skipping");
  process.exit(0);
}

function findSpecs(dir) {
  if (!existsSync(dir)) return [];
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...findSpecs(full));
    } else if (
      entry.name.endsWith(".yaml") ||
      entry.name.endsWith(".yml") ||
      entry.name.endsWith(".json")
    ) {
      out.push(full);
    }
  }
  return out;
}

const specs = findSpecs(CONTRACTS);
if (specs.length === 0) {
  console.log(
    "[openapi] contracts/openapi is empty — skipping (content lands in E1.3)",
  );
  process.exit(0);
}

if (!existsSync(RULESET)) {
  console.error(`[openapi] missing ruleset at ${relative(ROOT, RULESET)}`);
  process.exit(2);
}

const args = [
  "lint",
  "--ruleset",
  RULESET,
  "--format",
  "stylish",
  "--fail-severity",
  "error",
  "--display-only-failures",
  ...specs.map((s) => relative(ROOT, s)),
];

console.log(`[openapi] spectral ${args.join(" ")}`);
const proc = spawnSync("pnpm", ["exec", "spectral", ...args], {
  stdio: "inherit",
  cwd: ROOT,
  env: { ...process.env, FORCE_COLOR: "0" },
});
process.exit(proc.status ?? 1);