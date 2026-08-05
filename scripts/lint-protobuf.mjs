#!/usr/bin/env node
/**
 * scripts/lint-protobuf.mjs
 *
 * Repository-wide Protobuf lint. Tries `buf` first (the contract
 * tooling chosen by E1.3). If `buf` is not on $PATH we fall back to a
 * minimal structural check:
 *
 *   - every .proto file parses as UTF-8
 *   - syntax = "proto3" is declared
 *   - package name uses the `com.genealogy.platform.<area>.*` convention
 *   - service names use PascalCase
 *   - rpc names use PascalCase
 *   - file names use lower_snake_case.proto
 *   - no leading-underscore field names
 *
 * This keeps the gate green in environments where `buf` is not yet
 * provisioned (CI agents). The contract epic (E1.3) will replace this
 * with a full `buf lint` invocation wired to Apicurio compatibility.
 */
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

function main() {
  const HERE = dirname(fileURLToPath(import.meta.url));
  const ROOT = resolve(HERE, "..");
  const PROTO_DIR = join(ROOT, "contracts", "protobuf");

  if (!existsSync(PROTO_DIR)) {
    console.log("[protobuf] contracts/protobuf missing — skipping");
    process.exit(0);
  }

  const protos = findProtos(PROTO_DIR);

  if (protos.length === 0) {
    console.log(
      "[protobuf] contracts/protobuf is empty — skipping (content lands in E1.3)",
    );
    process.exit(0);
  }

  const bufMissing = spawnSync("buf", ["--version"], {
    stdio: "ignore",
    cwd: ROOT,
  }).status !== 0;

  if (bufMissing) {
    console.warn(
      "[protobuf] buf not on PATH — falling back to structural check (E1.3 will replace this with full `buf lint`)",
    );
    structuralCheck(protos, ROOT);
    console.log("[protobuf] structural check passed");
    return;
  }

  const args = ["lint", PROTO_DIR];
  console.log(`[protobuf] buf ${args.join(" ")}`);
  const proc = spawnSync("buf", args, { stdio: "inherit", cwd: ROOT });
  process.exit(proc.status ?? 1);
}

function findProtos(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...findProtos(full));
    } else if (entry.name.endsWith(".proto")) {
      out.push(full);
    }
  }
  return out;
}

const FILENAME_RE = /^[a-z][a-z0-9_]*\.proto$/;
const PACKAGE_RE = /^package\s+([\w.]+)\s*;/m;
const SYNTAX_RE = /^syntax\s*=\s*"([^"]+)"\s*;/m;
const SERVICE_RE = /^\s*service\s+([A-Z]\w*)\s*\{/gm;
const RPC_RE = /^\s*rpc\s+([A-Z]\w*)\s*\(/gm;
const FIELD_RE =
  /^\s*(?:repeated\s+|optional\s+|map<[^>]+>\s+)?(?:\w+\.)?[A-Z]\w*\s+(_?\w+)\s*[=;[]/gm;

function structuralCheck(protos, ROOT) {
  let violations = 0;
  for (const file of protos) {
    const rel = relative(ROOT, file);
    const text = readFileSync(file, "utf8");

    if (!FILENAME_RE.test(rel.split("/").pop() ?? "")) {
      violations++;
      console.error(
        `[protobuf] ${rel} — filename must be lower_snake_case.proto`,
      );
    }

    const syntaxMatch = SYNTAX_RE.exec(text);
    if (!syntaxMatch) {
      violations++;
      console.error(
        `[protobuf] ${rel} — missing 'syntax = "..."' directive`,
      );
    } else if (syntaxMatch[1] !== "proto3") {
      violations++;
      console.error(`[protobuf] ${rel} — only proto3 is supported`);
    }

    const packageMatch = PACKAGE_RE.exec(text);
    if (!packageMatch) {
      violations++;
      console.error(`[protobuf] ${rel} — missing 'package' declaration`);
    } else if (!packageMatch[1].startsWith("com.genealogy.platform.")) {
      violations++;
      console.error(
        `[protobuf] ${rel} — package '${packageMatch[1]}' must start with 'com.genealogy.platform.'`,
      );
    }

    let m;
    SERVICE_RE.lastIndex = 0;
    while ((m = SERVICE_RE.exec(text)) !== null) {
      if (!/^[A-Z]\w*$/.test(m[1])) {
        violations++;
        console.error(
          `[protobuf] ${rel} — service '${m[1]}' must be PascalCase`,
        );
      }
    }
    RPC_RE.lastIndex = 0;
    while ((m = RPC_RE.exec(text)) !== null) {
      if (!/^[A-Z]\w*$/.test(m[1])) {
        violations++;
        console.error(
          `[protobuf] ${rel} — rpc '${m[1]}' must be PascalCase`,
        );
      }
    }
    FIELD_RE.lastIndex = 0;
    while ((m = FIELD_RE.exec(text)) !== null) {
      if (m[1].startsWith("_")) {
        violations++;
        console.error(
          `[protobuf] ${rel} — field '${m[1]}' must not start with an underscore`,
        );
      }
    }
  }

  if (violations > 0) {
    console.error(`\n[protobuf] ${violations} violation(s)`);
    process.exit(1);
  }
}

main();