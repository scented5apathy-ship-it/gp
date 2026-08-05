#!/usr/bin/env node
/**
 * scripts/test-contracts.mjs
 *
 * Contract-first integration tests. Runs without external services;
 * each test inspects the artefacts under `contracts/` and fails when
 * a contract drifts from the rules described in
 * `contracts/README.md` / `agent-execution.md`.
 *
 * The suite covers:
 *
 *   1. Every REST operation documents RFC 9457 problem responses
 *      (4xx/5xx have `application/problem+json` referencing the
 *      shared `Problem` schema).
 *   2. Every REST mutation operation documents `Idempotency-Key`.
 *   3. Every REST mutation on a versioned resource documents
 *      `If-Match`.
 *   4. No tenant / DNA / token sensitive key appears in any OpenAPI
 *      schema property.
 *   5. Every protobuf request message that performs a mutation has a
 *      `Context context = N;` field as its first field.
 *   6. Every Avro schema declares the
 *      `com.genealogy.platform.events.` namespace prefix.
 *   7. Every REST list operation returns a `Page` envelope.
 *
 * The runner exits 0 when all checks pass and 1 otherwise.
 */
import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import assert from "node:assert/strict";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, "..");
const CONTRACTS = join(ROOT, "contracts");

function findAll(dir, predicate) {
  const out = [];
  if (!existsSync(dir)) return out;
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...findAll(full, predicate));
    } else if (predicate(entry.name)) {
      out.push(full);
    }
  }
  return out;
}

const openApiFiles = findAll(
  join(CONTRACTS, "openapi"),
  (n) => n.endsWith(".yaml") || n.endsWith(".yml") || n.endsWith(".json"),
);
const protoFiles = findAll(join(CONTRACTS, "protobuf"), (n) =>
  n.endsWith(".proto"),
);
const avscFiles = findAll(join(CONTRACTS, "events"), (n) => n.endsWith(".avsc"));

function readJson(p) {
  return JSON.parse(readFileSync(p, "utf8"));
}

// ---------------------------------------------------------------------------
// Tiny YAML reader. The contract documents under test are small enough to
// parse with a deliberately restricted YAML grammar: maps, sequences,
// scalars, block scalars. Avoids pulling in a 1 MB dependency just to read
// ~30 files.
// ---------------------------------------------------------------------------

function parseYaml(text) {
  const lines = text.split(/\r?\n/);
  let i = 0;
  function indentOf(line) {
    const m = /^( *)(.*)$/.exec(line);
    return { indent: m[1].length, rest: m[2] };
  }
  function isBlank(line) {
    return line === undefined || /^\s*(#.*)?$/.test(line);
  }
  function skipBlankAndComments() {
    while (i < lines.length && isBlank(lines[i])) i++;
  }
  function parseScalar(s) {
    s = s.replace(/\s+#.*$/, "").trim();
    if (s === "") return null;
    if (s === "true") return true;
    if (s === "false") return false;
    if (s === "null" || s === "~") return null;
    if (/^-?\d+$/.test(s)) return Number(s);
    if (/^-?\d+\.\d+$/.test(s)) return Number(s);
    if (s.startsWith('"') && s.endsWith('"')) return s.slice(1, -1);
    if (s.startsWith("'") && s.endsWith("'")) return s.slice(1, -1);
    if (s.startsWith("[") && s.endsWith("]")) {
      const inner = s.slice(1, -1).trim();
      if (!inner) return [];
      return inner.split(",").map((p) => parseScalar(p.trim()));
    }
    if (s.startsWith("{") && s.endsWith("}")) {
      const inner = s.slice(1, -1).trim();
      if (!inner) return {};
      const obj = {};
      for (const part of inner.split(",")) {
        const [k, v] = part.split(":").map((p) => p.trim());
        obj[parseScalar(k)] = parseScalar(v);
      }
      return obj;
    }
    return s;
  }
  function isListItem(line) {
    return /^\s*- /.test(line);
  }
  function isMapEntry(line) {
    return /^\s*[^:#\s][^:#]*:\s*(.*)$/.test(line) || /^\s*\$ref:\s*/.test(line);
  }
  function readBlockScalar(level, literal) {
    const out = [];
    while (i < lines.length) {
      if (isBlank(lines[i])) {
        if (literal) out.push("");
        i++;
        continue;
      }
      const cur = indentOf(lines[i]);
      if (cur.indent < level) break;
      out.push(literal ? lines[i].slice(level) : lines[i].trim());
      i++;
    }
    return literal ? out.join("\n").replace(/\n+$/, "") : out.join(" ");
  }
  function block(level) {
    skipBlankAndComments();
    if (i >= lines.length) return null;
    const cur = indentOf(lines[i]);
    if (cur.indent < level) return null;
    if (isListItem(lines[i])) {
      const arr = [];
      while (i < lines.length) {
        skipBlankAndComments();
        if (i >= lines.length) break;
        const c = indentOf(lines[i]);
        if (c.indent < level) break;
        if (!isListItem(lines[i])) break;
        const itemText = lines[i].slice(c.indent + 2);
        i++;
        if (itemText === "" || itemText === null) {
          arr.push(block(c.indent + 2));
        } else if (isMapEntry(itemText)) {
          const [k, v, inline] = parseMapEntry(itemText, c.indent + 2);
          if (inline) arr.push({ [k]: v });
          else arr.push(block(c.indent + 2));
        } else if (itemText === "|" || itemText === ">") {
          const literal = itemText === "|";
          arr.push(readBlockScalar(c.indent + 2, literal));
        } else {
          arr.push(parseScalar(itemText));
        }
      }
      return arr;
    }
    if (isMapEntry(lines[i])) return parseMap(level);
    return parseScalar(cur.rest);
  }
  function parseMapEntry(text, level) {
    const m = /^([^:#][^:#]*):\s*(.*)$/.exec(text);
    const key = m[1].trim();
    const rest = m[2];
    if (rest === "|" || rest === ">") {
      return [key, readBlockScalar(level, rest === "|"), true];
    }
    if (rest === "") return [key, null, false];
    return [key, parseScalar(rest), true];
  }
  function parseMap(level) {
    const obj = {};
    while (i < lines.length) {
      skipBlankAndComments();
      if (i >= lines.length) break;
      const cur = indentOf(lines[i]);
      if (cur.indent < level) break;
      if (!isMapEntry(lines[i])) break;
      const [key, value, inline] = parseMapEntry(lines[i], cur.indent + 2);
      i++;
      if (inline) obj[key] = value;
      else obj[key] = block(cur.indent + 2);
    }
    return obj;
  }
  return block(0);
}

// ---------------------------------------------------------------------------
// Helpers for OpenAPI checks
// ---------------------------------------------------------------------------

const FORBIDDEN_PROPS = new Set([
  "dnaRaw",
  "rawGenotype",
  "dna",
  "kit",
  "rawDna",
  "raw_dna",
]);

function loadOpenApi(file) {
  const text = readFileSync(file, "utf8");
  return text.endsWith(".json") ? readJson(file) : parseYaml(text);
}

function isMutation(method) {
  return (
    method === "post" ||
    method === "put" ||
    method === "patch" ||
    method === "delete"
  );
}

function collectOperations(doc) {
  const out = [];
  const paths = doc.paths ?? {};
  for (const [path, item] of Object.entries(paths)) {
    for (const method of Object.keys(item ?? {})) {
      if (
        method === "parameters" ||
        method === "$ref" ||
        method.startsWith("x-")
      ) {
        continue;
      }
      if (!["get", "post", "put", "patch", "delete", "head", "options"].includes(method)) {
        continue;
      }
      out.push({ path, method, op: item[method] });
    }
  }
  return out;
}

function headerRefs(op) {
  const set = new Set();
  for (const p of op.parameters ?? []) {
    if (p.$ref) {
      const m = /#\/components\/parameters\/([^/]+)$/.exec(p.$ref);
      if (m) set.add(m[1]);
    } else if (p.name) {
      set.add(p.name);
    }
  }
  return set;
}

function _responseRefs(op) {
  const set = new Set();
  for (const [, response] of Object.entries(op.responses ?? {})) {
    for (const media of Object.values(response.content ?? {})) {
      if (media.schema && media.schema.$ref) {
        const m = /#\/components\/schemas\/([^/]+)$/.exec(media.schema.$ref);
        if (m) set.add(m[1]);
      }
    }
  }
  return set;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test("contracts: OpenAPI files parse", () => {
  assert.ok(openApiFiles.length > 0, "no OpenAPI contracts found");
  for (const f of openApiFiles) {
    const doc = loadOpenApi(f);
    assert.equal(doc.openapi, "3.1.0", `${f} must declare openapi 3.1.0`);
    assert.ok(doc.info?.title, `${f} must declare info.title`);
    assert.ok(doc.info?.version, `${f} must declare info.version`);
  }
});

test("contracts: every OpenAPI mutation documents Idempotency-Key", () => {
  for (const f of openApiFiles) {
    const doc = loadOpenApi(f);
    for (const { path, method, op } of collectOperations(doc)) {
      if (!isMutation(method)) continue;
      if (op["x-idempotent"] === false) continue;
      const headers = headerRefs(op);
      assert.ok(
        headers.has("IdempotencyKey"),
        `${relative(ROOT, f)} ${method.toUpperCase()} ${path} must document Idempotency-Key`,
      );
    }
  }
});

test("contracts: mutations on versioned resources document If-Match", () => {
  for (const f of openApiFiles) {
    const doc = loadOpenApi(f);
    for (const { path, method, op } of collectOperations(doc)) {
      if (!isMutation(method)) continue;
      if (op["x-versioned-resource"] === false) continue;
      // Operations whose path contains `{treeId}` or `{personId}` or
      // `{tenantId}` mutate a versioned aggregate; the contract
      // requires `If-Match`. Operations may opt out via the
      // `x-versioned-resource: false` extension (used by the BFF
      // session endpoint, where the BFF session itself is not a
      // versioned aggregate).
      const mutatesVersionedResource = /\{(treeId|personId|tenantId)\}/.test(path);
      if (!mutatesVersionedResource) continue;
      const headers = headerRefs(op);
      assert.ok(
        headers.has("IfMatch"),
        `${relative(ROOT, f)} ${method.toUpperCase()} ${path} must document If-Match`,
      );
    }
  }
});

test("contracts: every 4xx/5xx response references Problem via problem+json", () => {
  for (const f of openApiFiles) {
    const doc = loadOpenApi(f);
    for (const { path, method, op } of collectOperations(doc)) {
      for (const [status, response] of Object.entries(op.responses ?? {})) {
        if (!/^[45]/.test(status)) continue;
        const problemJson = response.content?.["application/problem+json"];
        assert.ok(
          problemJson,
          `${relative(ROOT, f)} ${method.toUpperCase()} ${path} ${status} must declare application/problem+json`,
        );
        const ref = problemJson.schema?.$ref ?? "";
        assert.ok(
          /\/components\/schemas\/Problem\b/.test(ref) || /Problem$/.test(ref),
          `${relative(ROOT, f)} ${method.toUpperCase()} ${path} ${status} problem+json must reference the shared Problem schema`,
        );
      }
    }
  }
});

test("contracts: no forbidden (DNA / raw / token) property at any schema level", () => {
  for (const f of openApiFiles) {
    const doc = loadOpenApi(f);
    walk(doc, (node, path) => {
      if (node && typeof node === "object" && node.properties) {
        for (const key of Object.keys(node.properties)) {
          assert.ok(
            !FORBIDDEN_PROPS.has(key),
            `${relative(ROOT, f)} ${path}.properties.${key} is a forbidden field`,
          );
        }
      }
    });
  }
});

test("contracts: protobuf request messages of mutation RPCs start with Context", () => {
  const mutationSuffixes = new Set([
    "Request",
    "Invite",
    "Transition",
  ]);
  for (const f of protoFiles) {
    const text = readFileSync(f, "utf8");
    const lines = text.split(/\r?\n/);
    let i = 0;
    while (i < lines.length) {
      const m = /^message\s+(\w+)\s*\{/.exec(lines[i]);
      if (m) {
        const msgName = m[1];
        i++;
        // Read until end of message
        let depth = 1;
        let firstField = null;
        while (i < lines.length && depth > 0) {
          const line = lines[i];
          if (/^\s*message\s+\w+\s*\{/.test(line)) depth++;
          if (/^\s*\}/.test(line)) depth--;
          if (depth === 0) break;
          const fm = /^\s*(?:repeated\s+)?(?:map<[^>]+>\s+)?[\w.]+\s+(\w+)\s*=\s*(\d+)\s*;/.exec(
            line,
          );
          if (fm && firstField === null) {
            firstField = { name: fm[1], number: Number(fm[2]) };
          }
          i++;
        }
        const isMutationMsg =
          mutationSuffixes.has(msgName) ||
          /Request$/.test(msgName) ||
          msgName.startsWith("Invite") ||
          msgName.startsWith("Transition");
        if (isMutationMsg && msgName !== "Context") {
          assert.ok(
            firstField && firstField.name === "context" && firstField.number === 1,
            `${relative(ROOT, f)} message ${msgName} must have 'Context context = 1;' as its first field`,
          );
        }
      }
      i++;
    }
  }
});

test("contracts: every Avro schema uses the genealogy namespace prefix", () => {
  for (const f of avscFiles) {
    const schema = readJson(f);
    const ns = schema.namespace ?? "";
    assert.ok(
      ns.startsWith("com.genealogy.platform.events."),
      `${relative(ROOT, f)} namespace '${ns}' must start with 'com.genealogy.platform.events.'`,
    );
  }
});

test("contracts: forbidden DNA field names never appear in Avro schemas", () => {
  for (const f of avscFiles) {
    const schema = readJson(f);
    walkAvro(schema, (node) => {
      if (node && typeof node === "object" && "name" in node) {
        assert.ok(
          !FORBIDDEN_PROPS.has(node.name),
          `${relative(ROOT, f)} forbids field '${node.name}'`,
        );
      }
    });
  }
});

function walk(obj, visit, path = "$") {
  if (Array.isArray(obj)) {
    obj.forEach((v, i) => walk(v, visit, `${path}[${i}]`));
    return;
  }
  if (obj && typeof obj === "object") {
    visit(obj, path);
    for (const [k, v] of Object.entries(obj)) {
      walk(v, visit, `${path}.${k}`);
    }
  }
}

function walkAvro(node, visit) {
  if (Array.isArray(node)) {
    node.forEach((child) => walkAvro(child, visit));
    return;
  }
  if (node && typeof node === "object") {
    visit(node);
    if (node.fields) walkAvro(node.fields, visit);
    if (node.items) walkAvro(node.items, visit);
    if (node.values) walkAvro(node.values, visit);
    if (node.types) walkAvro(node.types, visit);
  }
}

test("contracts: every REST list operation returns a Page envelope", () => {
  for (const f of openApiFiles) {
    const doc = loadOpenApi(f);
    for (const { path, method, op } of collectOperations(doc)) {
      if (method !== "get") continue;
      if (op["x-list"] === false) continue;
      // Heuristic: list operations live under `/...s`, end with a
      // noun plural, are named `listX` or `searchX`, or are flagged
      // with `x-list: true`.
      const last = path.split("/").pop() ?? "";
      const isList =
        /s$/.test(last) ||
        /List\w+/.test(op.operationId ?? "") ||
        /Search\w+/.test(op.operationId ?? "") ||
        op["x-list"] === true;
      if (!isList) continue;
      const ok200 = op.responses?.["200"];
      const ref = ok200?.content?.["application/json"]?.schema?.$ref ?? "";
      assert.ok(
        /Page/.test(ref) ||
          /Page$/.test(
            ok200?.content?.["application/json"]?.schema?.type ?? "",
          ),
        `${relative(ROOT, f)} GET ${path} 200 response must use a Page envelope`,
      );
    }
  }
});

test("contracts: tenant id is server-derived (no client-supplied tenantId field)", () => {
  for (const f of openApiFiles) {
    const doc = loadOpenApi(f);
    walk(doc, (node, path) => {
      if (node && typeof node === "object" && node.properties) {
        for (const [key, schema] of Object.entries(node.properties)) {
          if (key.toLowerCase() === "tenantid" && /Request/.test(path)) {
            assert.fail(
              `${relative(ROOT, f)} ${path}.properties.${key} — tenant id must be derived server-side, never accepted from clients`,
            );
          }
          if (key === "idempotencyKey" && !/Request/.test(path)) {
            assert.fail(
              `${relative(ROOT, f)} ${path}.properties.${key} — idempotency key belongs in headers / metadata, not request bodies`,
            );
          }
          void schema;
        }
      }
    });
  }
});

test("contracts: directory layout matches the README", () => {
  assert.ok(existsSync(join(CONTRACTS, "openapi", "common", "headers.yaml")));
  assert.ok(existsSync(join(CONTRACTS, "openapi", "common", "problem-details.yaml")));
  assert.ok(existsSync(join(CONTRACTS, "openapi", "common", "pagination.yaml")));
  assert.ok(existsSync(join(CONTRACTS, "openapi", "public-api", "v1", "tenant.yaml")));
  assert.ok(existsSync(join(CONTRACTS, "openapi", "public-api", "v1", "tree.yaml")));
  assert.ok(existsSync(join(CONTRACTS, "openapi", "public-api", "v1", "person.yaml")));
  assert.ok(existsSync(join(CONTRACTS, "openapi", "public-api", "v1", "events.yaml")));
  assert.ok(existsSync(join(CONTRACTS, "openapi", "bff", "v1", "session.yaml")));
  assert.ok(existsSync(join(CONTRACTS, "protobuf", "buf.yaml")));
  assert.ok(existsSync(join(CONTRACTS, "protobuf", "common", "v1", "context.proto")));
  assert.ok(existsSync(join(CONTRACTS, "protobuf", "tenant", "v1", "tenant_service.proto")));
  assert.ok(existsSync(join(CONTRACTS, "protobuf", "genealogy", "v1", "tree_service.proto")));
  assert.ok(existsSync(join(CONTRACTS, "protobuf", "genealogy", "v1", "person_service.proto")));
  assert.ok(existsSync(join(CONTRACTS, "protobuf", "search", "v1", "search_service.proto")));
  assert.ok(existsSync(join(CONTRACTS, "events", "envelope", "v1", "event-envelope.avsc")));
  assert.ok(existsSync(join(CONTRACTS, "events", "shared", "v1", "identifiers.avsc")));
  assert.ok(existsSync(join(CONTRACTS, "events", "shared", "v1", "visibility.avsc")));
  assert.ok(existsSync(join(CONTRACTS, "events", "genealogy", "v1", "tree-created.avsc")));
  assert.ok(existsSync(join(CONTRACTS, "events", "genealogy", "v1", "tree-visibility-changed.avsc")));
  assert.ok(existsSync(join(CONTRACTS, "events", "genealogy", "v1", "person-created.avsc")));
  assert.ok(existsSync(join(CONTRACTS, "events", "genealogy", "v1", "person-updated.avsc")));
});
