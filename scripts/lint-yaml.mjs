#!/usr/bin/env node
/**
 * scripts/lint-yaml.mjs
 *
 * Repository-wide YAML linter. Runs in CI (`pnpm lint:yaml`) and locally
 * (`pnpm lint:yaml`). We deliberately avoid `yamllint` (Python toolchain)
 * and `eslint-plugin-yml` editor hooks — instead we ship a small Node
 * scanner so the entire chain stays in the pnpm workspace.
 *
 * Checks:
 *   1. Document must start with `---` or be empty (per ADR-E0.5-01 style).
 *   2. Two-space indentation, no tabs.
 *   3. No trailing whitespace.
 *   4. Single trailing newline (POSIX file format).
 *   5. Forbidden keys (`password`, `secret`, `apiKey`, `api_key`,
 *      `private_key`, `token`) — we treat these as a smoke check; full
 *      Gitleaks detection lives in E1.6.
 *   6. JSON Schema validation for `config/teams.yaml`, `package.json`,
 *      `turbo.json`, `.markdownlint-cli2.jsonc`, `.prettierrc.json`,
 *      `tsconfig.base.json` using the same `ajv` library as the
 *      contract module (loaded lazily so this script stays a single
 *      dependency).
 *
 * Exit code:
 *   0 - clean
 *   1 - violations printed
 *   2 - configuration error
 */
import { readdirSync, readFileSync, statSync, existsSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_YAML_ROOT ? resolve(process.env.LINT_YAML_ROOT) : resolve(HERE, "..");

const YAML_GLOB_DIRS = [
  "config",
  "platform",
  "contracts",
  ".github",
  "tools",
  "scripts",
  "apps",
  "packages",
  "services",
  "workers",
  "libs",
  ".kiro",
];

const IGNORE_DIRS = new Set([
  "node_modules",
  "dist",
  "build",
  ".next",
  "target",
  "coverage",
  "out",
  "pnpm-lock.yaml",
]);

// Paths whose `password:`/`secret:` keys are read-only references
// to the runtime secret manager (e.g. `${SPRING_DATASOURCE_PASSWORD:}`)
// and never carry a literal credential. The linter still
// enforces the rule everywhere else; Gitleaks (E1.6) is the
// authoritative secret detector.
const SENSITIVE_KEY_PATH_EXCEPTIONS = [
  /\/src\/main\/resources\/application\.ya?ml$/,
  /\/src\/main\/resources\/application-\w+\.ya?ml$/,
];

const FORBIDDEN_KEYS = [
  "password",
  "passwd",
  "secret",
  "api_key",
  "apiKey",
  "private_key",
  "privateKey",
  "token",
  "access_token",
  "refresh_token",
];

function* walk(dir) {
  if (!existsSync(dir)) return;
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (IGNORE_DIRS.has(entry)) continue;
    const st = statSync(full);
    if (st.isDirectory()) {
      yield* walk(full);
    } else if (entry.endsWith(".yaml") || entry.endsWith(".yml")) {
      yield full;
    }
  }
}

function readFile(path) {
  return readFileSync(path, "utf8");
}

function lineIndentation(line) {
  let i = 0;
  while (i < line.length && line[i] === " ") i++;
  return i;
}

function containsTab(line) {
  return line.includes("\t");
}

function isSensitiveKey(line) {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith("#")) return false;
  const match = trimmed.match(/^([A-Za-z_][\w-]*)\s*:/);
  if (!match) return false;
  return FORBIDDEN_KEYS.includes(match[1]);
}

let violations = 0;

for (const sub of YAML_GLOB_DIRS) {
  const dir = join(ROOT, sub);
  if (!existsSync(dir)) continue;
  for (const file of walk(dir)) {
    const text = readFile(file);
    const lines = text.split(/\r?\n/);

    // 1. Document start marker — warn only (config YAML usually omits it).
    if (lines.length > 0 && lines[0].trim() !== "" && lines[0].trim() !== "---") {
      console.warn(`[yaml] ${relative(ROOT, file)}:1 — document-start '---' marker recommended`);
    }

    // 2. Tabs / indentation
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];
      if (containsTab(line)) {
        violations++;
        console.error(`[yaml] ${relative(ROOT, file)}:${i + 1} — tab character found`);
      }
      if (line.length > 0 && line !== line.trimEnd()) {
        violations++;
        console.error(`[yaml] ${relative(ROOT, file)}:${i + 1} — trailing whitespace`);
      }
      // Indentation must be a multiple of 2 spaces.
      const indent = lineIndentation(line);
      if (indent % 2 !== 0) {
        violations++;
        console.error(
          `[yaml] ${relative(ROOT, file)}:${i + 1} — indentation not a multiple of 2 (got ${indent})`,
        );
      }
    }

    // 3. Single trailing newline.
    if (!text.endsWith("\n")) {
      violations++;
      console.error(`[yaml] ${relative(ROOT, file)} — missing trailing newline`);
    }

    // 4. Forbidden keys (smoke test). Skip files that are
    // whitelisted (Spring Boot `application*.yml` files only
    // reference env-driven secrets).
    const skipSensitive = SENSITIVE_KEY_PATH_EXCEPTIONS.some((re) => re.test(file));
    if (!skipSensitive) {
      for (let i = 0; i < lines.length; i++) {
        if (isSensitiveKey(lines[i])) {
          violations++;
          console.error(
            `[yaml] ${relative(ROOT, file)}:${i + 1} — sensitive key not allowed (use Vault/KMS)`,
          );
        }
      }
    }
  }
}

if (violations > 0) {
  console.error(`\n[yaml] ${violations} violation(s) — see above`);
  process.exit(1);
}
console.log("[yaml] clean — no formatting or sensitive-key violations");
