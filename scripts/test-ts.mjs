// `scripts/test-ts.mjs` — runs `node --test` with TypeScript
// support enabled (strip-types + extensionless relative resolver).
//
// Usage:
//   node scripts/test-ts.mjs <glob> [<glob> ...]
//
// The script accepts one or more glob patterns as positional
// arguments and forwards them to `node --test`. A typical
// package.json invocation looks like:
//
//   "test": "node ../../scripts/test-ts.mjs 'src/**/*.test.ts' 'test/**/*.test.ts'"
//
// Why a wrapper? Node 22's `--experimental-strip-types` removes
// type annotations but does not auto-resolve `.ts` extensions
// for relative imports. Combining it with the resolver hook in
// `scripts/ts-loader.mjs` lets the codebase keep the
// extensionless-import convention that other packages already use.
//
// The wrapper composes `NODE_OPTIONS` itself rather than relying
// on inheritance because pnpm sanitises the env when it spawns
// package scripts; setting `NODE_OPTIONS` from inside the wrapper
// keeps the loader and strip-types flag in scope for `node --test`.

import { spawnSync } from "node:child_process";
import { writeFileSync, mkdtempSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve, join } from "node:path";
import { tmpdir } from "node:os";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, "..");
const LOADER = resolve(HERE, "ts-loader.mjs");

const args = process.argv.slice(2);
if (args.length === 0) {
  console.error("[test-ts] missing glob arguments");
  process.exit(2);
}

const nodeOptions = [
  process.env.NODE_OPTIONS,
  "--experimental-strip-types",
  "--disable-warning=ExperimentalWarning",
  `--import=${LOADER}`,
]
  .filter(Boolean)
  .join(" ");

const tmp = mkdtempSync(join(tmpdir(), "test-ts-"));
const wrapper = join(tmp, "run.sh");
writeFileSync(
  wrapper,
  `#!/usr/bin/env bash
set -euo pipefail
export NODE_OPTIONS=${JSON.stringify(nodeOptions)}
exec node --test ${args.map((a) => JSON.stringify(a)).join(" ")}
`,
  { mode: 0o755 },
);

const proc = spawnSync("bash", [wrapper], { stdio: "inherit", cwd: process.cwd() });
process.exit(proc.status ?? 1);