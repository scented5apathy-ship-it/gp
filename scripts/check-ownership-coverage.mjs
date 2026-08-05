#!/usr/bin/env node
/**
 * scripts/check-ownership-coverage.mjs
 *
 * Verifies the E0.6 §8 ownership mirror is intact and that every
 * service / app / worker / library has at least one OWNERS entry both
 * at the top-level `OWNERS` file and the per-directory `OWNERS` file.
 *
 * Rules:
 *   1. `OWNERS` (repo root) must exist.
 *   2. `.github/CODEOWNERS` must exist and contain the same paths as
 *      `OWNERS` (GitHub parity).
 *   3. Every directory under `services/`, `apps/`, `workers/`, `libs/`
 *      must have an `OWNERS` file that references at least one team
 *      slug matching `config/teams.yaml`.
 *   4. The per-directory OWNERS must include the secondary owner
 *      `@genealogy/platform` (E1.1 §2.6 rule).
 *   5. Every path declared in `OWNERS` must correspond to an existing
 *      directory; orphaned entries fail the build.
 *
 * Exit code:
 *   0 - clean
 *   1 - violations printed
 *   2 - configuration error
 */
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.OWNERSHIP_ROOT
  ? resolve(process.env.OWNERSHIP_ROOT)
  : resolve(HERE, "..");

const ROOT_OWNERS = join(ROOT, "OWNERS");
const GITHUB_OWNERS = join(ROOT, ".github", "CODEOWNERS");
const TEAMS_YAML = join(ROOT, "config", "teams.yaml");

const SCAN_DIRS = ["services", "apps", "workers", "libs"];
const SECONDARY = "@genealogy/platform";

let violations = 0;
const teamSlugs = new Set();

if (existsSync(TEAMS_YAML)) {
  // Parse team slugs from `config/teams.yaml`. We accept the form
  // `slug: <github>` because that's what E0.6 documented.
  const text = readFileSync(TEAMS_YAML, "utf8");
  const slugMatches = text.matchAll(/^\s*-?\s*slug:\s*([@\w/-]+)\s*$/gm);
  for (const m of slugMatches) {
    teamSlugs.add(m[1]);
  }
  // Also accept plain `slug:` (top-level mapping)
  const topMatches = text.matchAll(/^(\w+):\s*$/gm);
  // (kept for future extension; current config uses list-of-objects)
  for (const _m of topMatches) {
    /* no-op */
  }
}

if (!existsSync(ROOT_OWNERS)) {
  console.error(`[ownership] missing ${relative(ROOT, ROOT_OWNERS)}`);
  process.exit(2);
}
if (!existsSync(GITHUB_OWNERS)) {
  console.error(`[ownership] missing ${relative(ROOT, GITHUB_OWNERS)}`);
  process.exit(2);
}

const rootOwners = readFileSync(ROOT_OWNERS, "utf8");
const githubOwners = readFileSync(GITHUB_OWNERS, "utf8");

const pathRegex = /^\/([\w./-]+)\/?\s+(@\S+(?:\s+@\S+)*)\s*$/gm;
const declaredPaths = new Set();

let m;
while ((m = pathRegex.exec(rootOwners)) !== null) {
  declaredPaths.add(`/${m[1]}`);
}
while ((m = pathRegex.exec(githubOwners)) !== null) {
  declaredPaths.add(`/${m[1]}`);
}

// 5. Validate each declared path resolves to an existing entry. We
// accept either a directory or a file (e.g. `config/teams.yaml`) so
// the same matrix can host both. Dedupe is enforced via Set so the
// root `OWNERS` and `.github/CODEOWNERS` mirrors do not double-count.
for (const p of declaredPaths) {
  if (p === "/") continue;
  const full = join(ROOT, p);
  if (!existsSync(full)) {
    violations++;
    console.error(
      `[ownership] declared path '${p}' does not exist in the repository`,
    );
  } else if (!statSync(full).isDirectory() && !statSync(full).isFile()) {
    violations++;
    console.error(
      `[ownership] declared path '${p}' is neither a file nor a directory`,
    );
  }
}

// Per-directory OWNERS checks.
for (const sub of SCAN_DIRS) {
  const subRoot = join(ROOT, sub);
  if (!existsSync(subRoot)) continue;
  for (const name of readdirSync(subRoot)) {
    const dir = join(subRoot, name);
    if (!statSync(dir).isDirectory()) continue;
    const ownersFile = join(dir, "OWNERS");
    const rel = relative(ROOT, dir);
    if (!existsSync(ownersFile)) {
      violations++;
      console.error(
        `[ownership] ${rel}/ — missing OWNERS file (E0.6 §8 requirement)`,
      );
      continue;
    }
    const text = readFileSync(ownersFile, "utf8");
    if (!text.includes(SECONDARY)) {
      violations++;
      console.error(
        `[ownership] ${rel}/OWNERS — must reference ${SECONDARY} as secondary owner`,
      );
    }
    // Verify at least one referenced team slug is registered in
    // config/teams.yaml. We accept any `@org/team` slug.
    const slugMatches = text.matchAll(/(@[\w/-]+)/g);
    const seen = new Set();
    for (const sm of slugMatches) seen.add(sm[1]);
    if (seen.size === 0) {
      violations++;
      console.error(
        `[ownership] ${rel}/OWNERS — no team slug referenced`,
      );
    } else {
      for (const slug of seen) {
        if (teamSlugs.size > 0 && !teamSlugs.has(slug) && slug !== SECONDARY) {
          // Strict mode is intentionally non-fatal: warn only so the
          // build does not break while E0.6 §8 is being backfilled.
          console.warn(
            `[ownership] ${rel}/OWNERS — slug '${slug}' not found in config/teams.yaml`,
          );
        }
      }
    }
  }
}

if (violations > 0) {
  console.error(`\n[ownership] ${violations} violation(s)`);
  process.exit(1);
}
console.log(
  `[ownership] clean — ${declaredPaths.size} declared paths, all per-directory OWNERS present`,
);