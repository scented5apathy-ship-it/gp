#!/usr/bin/env node
/**
 * scripts/check-gradle-lockfile.mjs
 *
 * Enforces the E1.2 quality gate that every Gradle subproject has a
 * `gradle.lockfile` committed. E1.1 only generated lockfiles for two
 * services; this task closes the gap for the remaining 19 subprojects.
 *
 * If `dependencyLocking { lockAllConfigurations() }` is enabled in the
 * subproject's `build.gradle.kts` (or via the shared convention),
 * then `gradle.lockfile` MUST exist; otherwise the build will produce
 * non-reproducible artefacts on CI.
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
const ROOT = process.env.GRADLE_LOCKFILE_ROOT
  ? resolve(process.env.GRADLE_LOCKFILE_ROOT)
  : resolve(HERE, "..");

let violations = 0;

function* walk(dir) {
  if (!existsSync(dir)) return;
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    const st = statSync(full);
    if (st.isDirectory()) {
      if (
        entry === "node_modules" ||
        entry === "build" ||
        entry === "dist" ||
        entry === ".gradle" ||
        entry === "target" ||
        entry === ".idea"
      )
        continue;
      yield* walk(full);
    } else if (entry === "build.gradle.kts") {
      yield full;
    }
  }
}

const rootSettings = readFileSync(join(ROOT, "settings.gradle.kts"), "utf8");
const rootSettingsHasSubprojects = rootSettings.includes("include(");
if (!rootSettingsHasSubprojects) {
  console.error("[gradle-lockfile] settings.gradle.kts does not declare any include() — aborting");
  process.exit(2);
}

for (const buildFile of walk(ROOT)) {
  const text = readFileSync(buildFile, "utf8");
  const lockAll =
    text.includes("lockAllConfigurations") ||
    text.includes("dependencyLocking");
  if (!lockAll) continue;
  const dir = dirname(buildFile);
  const lockfile = join(dir, "gradle.lockfile");
  if (!existsSync(lockfile)) {
    violations++;
    console.error(
      `[gradle-lockfile] ${relative(ROOT, dir)} — gradle.lockfile missing (run \`./gradlew :<path>:dependencies --write-locks\`)`,
    );
  }
}

if (violations > 0) {
  console.error(
    `\n[gradle-lockfile] ${violations} subproject(s) missing gradle.lockfile`,
  );
  process.exit(1);
}
console.log("[gradle-lockfile] clean — every lockAll subproject has gradle.lockfile");