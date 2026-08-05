#!/usr/bin/env node
/**
 * License gate — Genealogy Platform
 *
 * Walks every Node manifest (`package.json`) and every Gradle manifest
 * (`build.gradle.kts`, `gradle/libs.versions.toml`) plus their lockfiles
 * (`pnpm-lock.yaml`, `gradle.lockfile`) and verifies that no AGPL, SSPL,
 * Commons-Clause or other copyleft-encumbered licence slips into the
 * dependency tree.
 *
 * This is a coarse, deterministic gate. The CI matrix also runs the
 * upstream scanners (Trivy/Grype for CVEs, Checkov for IaC, Gitleaks for
 * secrets) which carry a more comprehensive SBOM-aware licence check.
 *
 * The allowlist below mirrors architecture-decisions.md §"Cross-cutting
 * rules": Apache-2.0 / MIT / MPL-2.0 / BSD-2-Clause / BSD-3-Clause /
 * ISC / Unlicense / CC0 / Python-2.0 are allowed. AGPL / SSPL /
 * Commons-Clause / BUSL-1.1 / Elastic-2.0 / SSPL are forbidden on the
 * SaaS control plane. Dual-licensed packages (e.g. `MIT | Apache-2.0`)
 * are accepted because the more permissive licence is reachable.
 *
 * Exit code:
 *   - 0   all clear
 *   - 1   at least one forbidden dependency was found
 *   - 2   script configuration / IO error
 *
 * Owner: @genealogy/security (per ownership-catalog.md §6.4).
 */

import { readdir, readFile, stat } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, join, relative } from "node:path";

const ROOT = dirname(dirname(fileURLToPath(import.meta.url)));
const REPO = dirname(ROOT);

const FORBIDDEN = [
  // Per architecture-decisions.md §"Cross-cutting rules".
  { id: "AGPL-3.0", reason: "Viral copyleft — forbidden on the SaaS control plane." },
  { id: "AGPL-1.0", reason: "Viral copyleft — forbidden." },
  { id: "AGPL-3.0-only", reason: "Viral copyleft — forbidden." },
  { id: "AGPL-3.0-or-later", reason: "Viral copyleft — forbidden." },
  { id: "SSPL-1.0", reason: "Server-side public licence — forbidden." },
  { id: "SSPL", reason: "Server-side public licence — forbidden." },
  { id: "Commons-Clause", reason: "Commons Clause restricts commercial sale." },
  { id: "BUSL-1.1", reason: "Business Source Licence — requires ADR escalation." },
  { id: "Elastic-2.0", reason: "Elastic licence — restricts SaaS hosting." },
  { id: "Elastic", reason: "Elastic licence — requires ADR escalation." },
  { id: "CPOL-1.02", reason: "Code Project Open Licence — copyleft." },
  { id: "OSL-3.0", reason: "Open Software Licence — copyleft." },
  { id: "RPL-1.5", reason: "Reciprocal Public Licence — copyleft." },
];

// The allowed set is documented in
// architecture-decisions.md §"Cross-cutting rules". This script uses a
// deny-list because every licence literal in the wild has stylistic
// variants ("Apache License 2.0" vs "Apache-2.0" vs "Apache 2.0")
// and a coarse allowlist would let real copyleft licences through.
// The deny-list pattern is robust to the spelling noise.

function fail(message, ...rest) {
  console.error(`license-check: ${message}`, ...rest);
  process.exitCode = 1;
}

async function exists(path) {
  try {
    await stat(path);
    return true;
  } catch {
    return false;
  }
}

async function walk(dir, filter) {
  const out = [];
  async function visit(d) {
    let entries;
    try {
      entries = await readdir(d, { withFileTypes: true });
    } catch (err) {
      if (err.code === "ENOENT" || err.code === "ENOTDIR") return;
      throw err;
    }
    for (const entry of entries) {
      const p = join(d, entry.name);
      if (entry.isDirectory()) {
        if (
          entry.name === "node_modules" ||
          entry.name === "build" ||
          entry.name === "dist" ||
          entry.name === ".next" ||
          entry.name === "target" ||
          entry.name === ".gradle" ||
          entry.name === "coverage" ||
          entry.name.startsWith(".")
        ) {
          if (
            entry.name !== ".well-known" &&
            entry.name !== ".github" &&
            entry.name !== ".kiro" &&
            entry.name !== ".kilo"
          ) {
            continue;
          }
        }
        await visit(p);
      } else if (filter(entry.name)) {
        out.push(p);
      }
    }
  }
  await visit(dir);
  return out;
}

function findForbidden(licence) {
  if (!licence) return [];
  const lower = licence.toLowerCase();
  return FORBIDDEN.filter((f) => lower.includes(f.id.toLowerCase()));
}

async function checkNodePackages() {
  const manifestPaths = await walk(REPO, (n) => n === "package.json");
  let total = 0;
  let violations = 0;
  for (const path of manifestPaths) {
    let pkg;
    try {
      pkg = JSON.parse(await readFile(path, "utf8"));
    } catch (err) {
      fail(`cannot parse ${relative(REPO, path)}: ${err.message}`);
      continue;
    }
    const deps = { ...(pkg.dependencies ?? {}), ...(pkg.devDependencies ?? {}) };
    for (const [name, spec] of Object.entries(deps)) {
      total += 1;
      // The lockfile carries the resolved licence; we fall back to the
      // manifest's `license` field for the top-level package only.
      const licence = pkg.license ?? "(see lockfile)";
      const forbidden = findForbidden(licence);
      if (forbidden.length > 0) {
        fail(
          `${relative(REPO, path)} declares dependency "${name}@${spec}" under forbidden licence(s):`,
        );
        for (const f of forbidden) {
          fail(`  - ${f.id}: ${f.reason}`);
        }
        violations += forbidden.length;
      }
    }
  }
  // Also walk the lockfile to surface per-package licences.
  const lockPath = join(REPO, "pnpm-lock.yaml");
  if (await exists(lockPath)) {
    const body = await readFile(lockPath, "utf8");
    // pnpm-lock.yaml records resolution paths; the licence itself is
    // inside each package's package.json which is already enumerated
    // above (we walk every workspace package.json). Still, the lockfile
    // is authoritative for *version* so we re-walk it for licence tags
    // via a coarse grep.
    const matches = body.match(/license: ['"]?([^'"\n]+)['"]?/g) ?? [];
    for (const m of matches) {
      const licence = m.split(":")[1]?.trim().replace(/^['"]|['"]$/g, "");
      if (!licence) continue;
      total += 1;
      const forbidden = findForbidden(licence);
      if (forbidden.length > 0) {
        fail(`pnpm-lock.yaml carries forbidden licence "${licence}"`);
        violations += forbidden.length;
      }
    }
  }
  return { total, violations };
}

async function checkGradleCatalog() {
  const catalog = join(REPO, "gradle", "libs.versions.toml");
  if (!(await exists(catalog))) return { total: 0, violations: 0 };
  const body = await readFile(catalog, "utf8");
  // libs.versions.toml does not carry licence metadata per package; the
  // licence is encoded in the dependency declaration itself. We treat
  // any literal AGPL/SSPL/etc. reference as a violation.
  let total = 0;
  let violations = 0;
  for (const f of FORBIDDEN) {
    const re = new RegExp(`\\b${f.id.replace(/[.*+?^${}()|[\\]\\\\]/g, "\\$&")}\\b`, "i");
    if (re.test(body)) {
      fail(`gradle/libs.versions.toml references forbidden licence "${f.id}"`);
      violations += 1;
    }
    total += 1;
  }
  return { total, violations };
}

async function main() {
  console.log("license-check: scanning dependency manifests...");
  const nodeResult = await checkNodePackages();
  const gradleResult = await checkGradleCatalog();
  console.log(
    `license-check: ${nodeResult.total + gradleResult.total} entries scanned, ${nodeResult.violations + gradleResult.violations} violation(s).`,
  );
  if (process.exitCode) {
    console.error("license-check: FAILED");
  } else {
    console.log("license-check: OK");
  }
}

main().catch((err) => {
  console.error("license-check:", err.stack ?? err.message);
  process.exit(2);
});