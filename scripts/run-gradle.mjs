#!/usr/bin/env node
/**
 * scripts/run-gradle.mjs
 *
 * Wrapper around `./gradlew` that ensures JAVA_HOME points at a JDK
 * 21 (the only version ADR-E0.5-01 allows). On CI agents the env var
 * is already pinned. On developer workstations the script falls back
 * to the Microsoft Build OpenJDK 21 install that ships with Visual
 * Studio for Mac — overriding the system default JDK 25 keeps
 * Gradle 8.10's embedded Kotlin DSL bootstrap compatible.
 *
 * Usage: `node scripts/run-gradle.mjs <gradle args...>`
 */
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";

const FALLBACK_JDK21 = "/Users/os_ngocnq/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home";

function resolveJavaHome() {
  if (process.env.JAVA_HOME && existsSync(process.env.JAVA_HOME)) {
    return process.env.JAVA_HOME;
  }
  if (existsSync(FALLBACK_JDK21)) {
    return FALLBACK_JDK21;
  }
  // On Linux/CI agents, JAVA_HOME is set externally. Fall through.
  return process.env.JAVA_HOME;
}

const javaHome = resolveJavaHome();
if (!javaHome) {
  console.error("[run-gradle] JAVA_HOME is not set and no fallback JDK 21 found.");
  console.error("             Install JDK 21 or set JAVA_HOME=/path/to/jdk-21");
  process.exit(1);
}

const args = process.argv.slice(2);
const proc = spawnSync("./gradlew", args, {
  stdio: "inherit",
  env: { ...process.env, JAVA_HOME: javaHome },
});
process.exit(proc.status ?? 1);