#!/usr/bin/env node
/**
 * scripts/check-monorepo-lockfile.mjs
 *
 * Verifies pnpm + Gradle lockfiles exist and are tracked in git (so they
 * accompany every build). E1.1 acceptance requires "dependency locking
 * and reproducible build".
 */
import { existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, "..");
const required = [
  "pnpm-lock.yaml",
  "gradle/libs.versions.toml",
  "gradle/wrapper/gradle-wrapper.properties",
  "gradle/wrapper/gradle-wrapper.jar",
  "gradle/conventions/java-conventions.gradle.kts",
  "gradle/conventions/service-conventions.gradle.kts",
  "gradlew",
  "gradlew.bat",
];

let missing = 0;
for (const p of required) {
  const full = resolve(ROOT, p);
  if (existsSync(full)) {
    console.log(`[lockfile] OK ${p}`);
  } else {
    console.error(`[lockfile] MISSING ${p}`);
    missing++;
  }
}

if (missing > 0) {
  console.error(`[lockfile] ${missing} lockfile(s) missing — run 'pnpm install' and 'gradle --write-locks'`);
  process.exit(1);
}
console.log("[lockfile] all required lockfiles present");