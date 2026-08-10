#!/usr/bin/env node
/**
 * apps/web/bench/bench-policy.mjs
 *
 * Loads `contracts/genealogy/tree-renderer-bench-policy.yaml`
 * for the bench harness. Reuses the same loose-YAML parser as
 * `scripts/lint-tree-renderer-bench.mjs` so the harness and
 * the linter agree on the shape. The linter remains the
 * authoritative validator; this loader only parses.
 *
 * The `LINT_ROOT` env var is honoured so the same root
 * resolution rule applies on monorepo-mounted CI runners.
 */
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const REPO_ROOT = process.env.LINT_ROOT
  ? resolve(process.env.LINT_ROOT)
  : resolve(__dirname, "..", "..", "..");

const DEFAULT_POLICY = join(REPO_ROOT, "contracts", "genealogy", "tree-renderer-bench-policy.yaml");

/** Same shape as the linter's parser — kept in sync. */
function parseYamlLoose(raw) {
  const out = {};
  const stack = [{ indent: -1, value: out }];
  const lines = raw.split(/\r?\n/);
  function parseFlowList(text) {
    const inner = text.trim().slice(1, -1).trim();
    if (!inner) return [];
    return inner.split(",").map((s) => stripQuotesValue(s.trim()));
  }
  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i];
    if (!line.trim() || line.trim().startsWith("#")) continue;
    const indent = line.match(/^ */)[0].length;
    while (stack.length > 1 && stack[stack.length - 1].indent >= indent) {
      stack.pop();
    }
    const parent = stack[stack.length - 1].value;
    const trimmed = line.trim();
    if (trimmed.startsWith("- ")) {
      const parentTop = stack[stack.length - 1];
      if (!Array.isArray(parentTop.value)) {
        if (Object.keys(parentTop.value).length === 0) {
          const arr = [];
          const parentOfTop = stack.length >= 2 ? stack[stack.length - 2].value : null;
          if (parentOfTop) {
            for (const k of Object.keys(parentOfTop)) {
              if (parentOfTop[k] === parentTop.value) {
                parentOfTop[k] = arr;
                parentTop.value = arr;
                break;
              }
            }
          } else {
            parentTop.value = arr;
          }
        } else {
          continue;
        }
      }
      const itemRaw = trimmed.slice(2).trim();
      const sub = itemRaw.match(/^([A-Za-z0-9_.\-]+)\s*:\s*(.*)$/);
      if (sub) {
        const obj = {};
        obj[sub[1]] = stripQuotesValue(sub[2]);
        parentTop.value.push(obj);
        stack.push({ indent, value: obj });
      } else {
        parentTop.value.push(stripQuotesValue(itemRaw));
      }
      continue;
    }
    const m = trimmed.match(/^([A-Za-z0-9_.\-]+)\s*:\s*(.*)$/);
    if (!m) continue;
    const key = m[1];
    let rhs = m[2];
    if (rhs === "" || rhs === undefined) {
      const next = {};
      parent[key] = next;
      stack.push({ indent, value: next });
      continue;
    }
    if (rhs.startsWith("[") && rhs.endsWith("]")) {
      parent[key] = parseFlowList(rhs);
      continue;
    }
    if (rhs === "true") {
      parent[key] = true;
      continue;
    }
    if (rhs === "false") {
      parent[key] = false;
      continue;
    }
    const numeric = Number(rhs);
    if (!Number.isNaN(numeric) && rhs.trim() !== "") {
      parent[key] = numeric;
      continue;
    }
    parent[key] = stripQuotesValue(rhs);
  }
  return out;
}

function stripQuotesValue(v) {
  if (v === undefined || v === null) return v;
  v = v.trim();
  if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
    return v.slice(1, -1);
  }
  return v;
}

/**
 * Parse the bench policy YAML and return the structured
 * object. Throws when the file is missing or invalid.
 */
export async function parseBenchPolicy(filePath = DEFAULT_POLICY) {
  const path = filePath ?? DEFAULT_POLICY;
  if (!existsSync(path)) {
    throw new Error(`policy file not found: ${path}`);
  }
  const raw = readFileSync(path, "utf8");
  return parseYamlLoose(raw);
}

const isMain = process.argv[1] && resolve(process.argv[1]) === resolve(__filename);
if (isMain) {
  parseBenchPolicy().then((p) => {
    console.log(JSON.stringify(p, null, 2));
  });
}
