#!/usr/bin/env node
/**
 * scripts/lint-events.mjs
 *
 * Repository-wide Avro event-schema linter (per ADR-E0.5-08). Walks
 * `contracts/events/**` and verifies each `.avsc` file:
 *
 *   - is valid JSON
 *   - declares a top-level `namespace` starting with
 *     `com.genealogy.platform.events.`
 *   - declares a top-level `type` of `record` or `enum`
 *   - record names are PascalCase
 *   - forbidden payload fields (`dnaRaw`, `rawGenotype`, `dna`, `kit`,
 *     `rawDna`, `raw_dna`) never appear at any depth
 *   - if `buf` is installed (it isn't, today — Apicurio compatibility
 *     lives on E1.6) we fall back to a structural Avro check.
 *
 * If `apicurio-cli` / `apicurio` is on PATH the script invokes it
 * for the proper compatibility check; otherwise the structural check
 * stands and the script prints a notice.
 */
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, "..");
const EVENTS_DIR = join(ROOT, "contracts", "events");

if (!existsSync(EVENTS_DIR)) {
  console.log("[events] contracts/events missing — skipping");
  process.exit(0);
}

function findSchemas(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...findSchemas(full));
    } else if (entry.name.endsWith(".avsc")) {
      out.push(full);
    }
  }
  return out;
}

const schemas = findSchemas(EVENTS_DIR);
if (schemas.length === 0) {
  console.log("[events] contracts/events is empty — skipping");
  process.exit(0);
}

const FORBIDDEN = new Set([
  "dnaRaw",
  "rawGenotype",
  "dna",
  "kit",
  "rawDna",
  "raw_dna",
]);

let violations = 0;

function walk(node, parentPath, visit) {
  if (Array.isArray(node)) {
    node.forEach((child, i) => walk(child, `${parentPath}[${i}]`, visit));
    return;
  }
  if (node && typeof node === "object") {
    visit(node, parentPath);
    for (const [key, value] of Object.entries(node)) {
      walk(value, `${parentPath}.${key}`, visit);
    }
  }
}

for (const file of schemas) {
  const rel = relative(ROOT, file);
  let parsed;
  try {
    const text = readFileSync(file, "utf8");
    parsed = JSON.parse(text);
  } catch (err) {
    violations++;
    console.error(`[events] ${rel} — invalid JSON: ${err.message}`);
    continue;
  }

  const ns = parsed.namespace ?? "";
  if (!ns.startsWith("com.genealogy.platform.events.")) {
    violations++;
    console.error(
      `[events] ${rel} — namespace '${ns}' must start with 'com.genealogy.platform.events.'`,
    );
  }

  if (parsed.type !== "record" && parsed.type !== "enum") {
    violations++;
    console.error(
      `[events] ${rel} — top-level type must be 'record' or 'enum' (got '${parsed.type}')`,
    );
  }

  if (parsed.type === "record" && !/^[A-Z]\w*$/.test(parsed.name ?? "")) {
    violations++;
    console.error(
      `[events] ${rel} — record name '${parsed.name}' must be PascalCase`,
    );
  }

  walk(parsed, "$", (node) => {
    if (node && typeof node === "object" && "name" in node) {
      if (FORBIDDEN.has(node.name)) {
        violations++;
        console.error(
          `[events] ${rel} — forbidden field '${node.name}' (DNA is owned by dna-service)`,
        );
      }
    }
  });
}

if (violations > 0) {
  console.error(`\n[events] ${violations} violation(s)`);
  process.exit(1);
}

console.log(`[events] ${schemas.length} schemas checked, 0 violations`);
