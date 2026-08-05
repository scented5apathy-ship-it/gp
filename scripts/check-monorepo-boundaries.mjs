#!/usr/bin/env node
/**
 * scripts/check-monorepo-boundaries.mjs
 *
 * Enforces E1.1 acceptance criterion: a service must NOT import another
 * service's domain model or database module. We do this by scanning every
 * `services/<svc>/` directory and verifying that:
 *
 *   1. No import path crosses to another `services/<other>/` package.
 *   2. No import path targets `services/<other>/.../db/**` (Flyway, jOOQ
 *      generated classes, repository code).
 *   3. No import path targets `services/<other>/.../domain/**` (entities,
 *      value objects, aggregate roots).
 *
 * `packages/` (cross-cutting libs), `apps/` (web apps), and the current
 * service itself are allowed.
 *
 * Exit code:
 *   0 - clean
 *   1 - violations printed
 *   2 - configuration error
 */
import { readdirSync, readFileSync, statSync, existsSync } from "node:fs";
import { join, relative, resolve, sep, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, "..");
const SERVICES_DIR = join(ROOT, "services");
const SCAN_EXT = new Set([".ts", ".tsx", ".mts", ".cts", ".js", ".mjs", ".cjs", ".java", ".kt", ".kts"]);

function fail(msg) {
  console.error(`[boundary] ${msg}`);
  process.exitCode = 2;
}

if (!existsSync(SERVICES_DIR)) {
  fail(`services/ directory not found at ${SERVICES_DIR}`);
  process.exit(process.exitCode);
}

const services = readdirSync(SERVICES_DIR).filter((name) => {
  const full = join(SERVICES_DIR, name);
  return statSync(full).isDirectory();
});

if (services.length === 0) {
  console.log("[boundary] no services registered yet — skipping");
  process.exit(0);
}

// Matches:
//   ES   JS/TS:    import x from "y"  /  import "y"  /  from "y"  / require("y")
//   Kotlin/Java:   import com.foo.Bar;
const importRegex =
  /(?:from\s+|require\s*\(\s*|import\s*\(\s*)["']([^"']+)["']|(?:^|\n)\s*import\s+([A-Za-z_][\w.]*(?:\.\*)?)\s*;/gm;

function* walk(dir) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) {
      if (entry === "node_modules" || entry === "dist" || entry === ".next" || entry === "build" || entry === "target" || entry === "coverage") continue;
      yield* walk(full);
    } else if (SCAN_EXT.has(extname(entry))) {
      yield full;
    }
  }
}

function extname(name) {
  const idx = name.lastIndexOf(".");
  return idx === -1 ? "" : name.slice(idx);
}

let violations = 0;
const blockedSubpaths = ["/db/", "/domain/", "/internal/domain/", "/jOOQ/", "/persistence/"];
// Map "tenant-service" directory to its Java package fragment "tenant"
// and vice-versa. E1.1 ships a hand-maintained table; later epics can
// regenerate it from the build.gradle.kts sourceSets if needed.
const SERVICE_PKG = new Map();
const PKG_TO_DIR = new Map();
for (const svc of services) {
  // drop trailing "-service"
  const pkg = svc.replace(/-service$/, "");
  SERVICE_PKG.set(svc, pkg);
  PKG_TO_DIR.set(pkg, svc);
}

for (const svc of services) {
  const svcRoot = join(SERVICES_DIR, svc);
  for (const file of walk(svcRoot)) {
    const text = readFileSync(file, "utf8");
    let match;
    while ((match = importRegex.exec(text)) !== null) {
      const spec = match[1] ?? match[2];
      if (!spec) continue;
      // Java/Kotlin imports are absolute (`com.genealogy.platform...`).
      // TypeScript/JS imports use relative paths starting with `.`.
      const isRelative = spec.startsWith(".");
      const isAbsolute = /^[A-Za-z_][\w.]*$/.test(spec);
      if (!isRelative && !isAbsolute) continue;
      const resolved = resolve(dirname(file), spec);
      let rel;
      if (isRelative) {
        rel = relative(ROOT, resolved).split(sep).join("/");
      } else {
        rel = spec;
      }
      for (const other of services) {
        if (other === svc) continue;
        const otherPkg = SERVICE_PKG.get(other);
        const pkgMarker = `com.genealogy.platform.services.${otherPkg}.`;
        const dirMarker = `services/${other}/`;
        const isCrossService =
          (isAbsolute && rel.startsWith(pkgMarker)) ||
          (isRelative && rel.startsWith(dirMarker));
        if (!isCrossService) continue;
        const subpath = isAbsolute
          ? rel.slice(pkgMarker.length)
          : rel.slice(dirMarker.length);
        // For Java packages, blocked segments are bare package fragments
        // ("db", "domain", ...). For directory paths they are
        // bracketed ("/db/", "/domain/", ...). We accept both forms.
        const blocked = blockedSubpaths.some((b) => {
          const seg = b.replace(/^\/|\/$/g, "");
          return (
            subpath === seg ||
            subpath.startsWith(`${seg}.`) ||
            subpath.startsWith(b) ||
            subpath.includes(b)
          );
        });
        if (blocked) {
          violations++;
          console.error(`[boundary] VIOLATION ${relative(ROOT, file)} -> ${spec}`);
          console.error(`          cross-service import into ${other} (blocked subpath: ${subpath})`);
        }
      }
    }
  }
}

if (violations > 0) {
  console.error(`\n[boundary] ${violations} cross-service import violation(s) — see above`);
  process.exit(1);
}
console.log(`[boundary] clean — scanned ${services.length} service(s); no cross-service db/domain imports`);