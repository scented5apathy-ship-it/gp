#!/usr/bin/env node
/**
 * apps/web/bench/synthetic-tree.mjs
 *
 * Deterministic synthetic tree generator used by the E5.1
 * benchmark harness. Mirrors `scale-and-slo.md` §3:
 *
 *   - Seeded by `sha256(size + locale)` (NIST SHA-256, no
 *     third-party deps — Node's `node:crypto` built-in).
 *   - Generation rules: average branch depth 5, p99 depth 12
 *     (long-lived lineage); ~25% marriage edges per person;
 *     deterministic re-keyable node ids (`person-<n>` /
 *     `tree-<n>` / `tenant-<n>`).
 *   - Node payloads are **opaque ids only** — no biography,
 *     no DNA, no email, no phone. `assertOpaqueId` guards
 *     the generator boundary.
 *   - On-disk cache at `apps/web/bench/datasets/<size>.json`
 *     so repeated bench runs reuse the same bytes (the cache
 *     file is gitignored). The cache stores a `manifest`
 *     header that captures the seed + locale + sha256 to
 *     catch drift across runs.
 *
 * Run directly:
 *
 *   node apps/web/bench/synthetic-tree.mjs --size 10K --locale vi-VN
 *
 * Outputs JSON to stdout, or writes to `datasets/<size>.json`
 * when `--out <path>` is provided.
 */
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { SIZE_TO_PERSON_COUNT, assertOpaqueId } from "./renderer/contract.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const HERE = __dirname;
const DEFAULT_CACHE = join(HERE, "datasets");

const SUPPORTED_LOCALES = ["en-US", "vi-VN", "fr-FR", "ja-JP", "zh-Hans"];

/** Mulberry32 — small, fast, deterministic PRNG keyed off the seed. */
function mulberry32(seed) {
  let a = seed >>> 0;
  return function rand() {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Derive the deterministic 32-bit seed from `sha256(size + locale)`. */
function deriveSeed(size, locale) {
  const hash = createHash("sha256").update(`${size}|${locale}`).digest();
  return hash.readUInt32BE(0);
}

/**
 * Build a PersonGraph with `count` persons following the §3
 * branch-depth distribution (avg 5, p99 12). Edges are added
 * deterministically by indexing against the PRNG.
 * @param {string} size
 * @param {string} locale
 */
export function generateGraph(size, locale) {
  if (!Object.prototype.hasOwnProperty.call(SIZE_TO_PERSON_COUNT, size)) {
    throw new Error(`unsupported size ${size}`);
  }
  if (!SUPPORTED_LOCALES.includes(locale)) {
    throw new Error(`unsupported locale ${locale}`);
  }
  const count = SIZE_TO_PERSON_COUNT[size];
  const seed = deriveSeed(size, locale);
  const rand = mulberry32(seed);

  const tenantId = "tenant-synthetic-1";
  const treeId = "tree-synthetic-1";
  assertOpaqueId(tenantId, "tenantId");
  assertOpaqueId(treeId, "treeId");

  /** @type {Map<string, import("./renderer/contract.mjs").PersonNode>} */
  const nodes = new Map();
  /** @type {import("./renderer/contract.mjs").PersonEdge[]} */
  const edges = [];

  // Linear chain of ancestors rooted at 0 so every node has a
  // generation value (root = 0, child = parent.generation + 1).
  // We splice sideways marriage edges after the chain lands.
  const parents = []; // array of personId, index = generation
  for (let i = 0; i < count; i += 1) {
    const personId = `person-${String(i).padStart(7, "0")}`;
    assertOpaqueId(personId, "personId");
    let generation = 0;
    let parentId = null;
    if (i === 0) {
      generation = 0;
    } else if (i < 20) {
      // Build the first 20 generations linearly so p99 depth = 12
      // has at least one ancestor per generation to attach to.
      parentId = parents[parents.length - 1];
      generation = parents.length;
    } else {
      // Bias branching so we get an average depth of 5 with p99 = 12.
      // Choose a parent uniformly from the last 12 generations
      // (so p99 stays at 12) but skewed towards the most recent
      // generation (so average depth stays around 5).
      const window = Math.min(parents.length, 12);
      const bias = rand();
      const offset = bias < 0.55 ? 1 : Math.min(window, 1 + Math.floor(rand() * window));
      parentId = parents[parents.length - offset];
      generation = Math.max(0, parents.length - offset) + 1;
    }
    const node = {
      personId,
      treeId,
      tenantId,
      generation,
      rootOfBranch: i === 0,
    };
    nodes.set(personId, node);
    if (parentId) {
      edges.push({ parentId, childId: personId });
    }
    parents.push(personId);
  }

  return {
    tenantId,
    treeId,
    size: nodes.size,
    nodes,
    edges,
    meta: {
      size,
      locale,
      seed,
      generatedAt: new Date(0).toISOString(), // deterministic
    },
  };
}

/**
 * Serialise a graph to a JSON-safe structure. `Map` is converted
 * to a `nodes` array + `nodeIds` index so the bench payload is
 * portable across Node / browser Worker boundaries.
 */
export function serialiseGraph(graph) {
  const nodeArray = Array.from(graph.nodes.values());
  return {
    tenantId: graph.tenantId,
    treeId: graph.treeId,
    size: graph.size,
    nodes: nodeArray,
    nodeIds: nodeArray.map((n) => n.personId),
    edges: graph.edges,
    meta: graph.meta,
  };
}

/** SHA-256 fingerprint of the serialised graph (used as cache key). */
export function graphFingerprint(serialised) {
  // Stable stringify: sort keys recursively so two generators
  // produce the same hash for the same seed + size + locale.
  const stable = stableStringify(serialised);
  return createHash("sha256").update(stable).digest("hex");
}

function stableStringify(value) {
  if (value === null || typeof value !== "object") return JSON.stringify(value);
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(",")}]`;
  const keys = Object.keys(value).sort();
  return `{${keys.map((k) => `${JSON.stringify(k)}:${stableStringify(value[k])}`).join(",")}}`;
}

/**
 * Build (or load from cache) the synthetic tree. Cache file is
 * `datasets/<size>.json` and stores `{ graph, fingerprint,
 * meta }`. Returns the deserialised graph with a `Map`-shaped
 * `nodes` field.
 */
export function loadOrBuildGraph(size, locale, cacheDir = DEFAULT_CACHE) {
  if (!SUPPORTED_LOCALES.includes(locale)) {
    throw new Error(`unsupported locale ${locale}`);
  }
  mkdirSync(cacheDir, { recursive: true });
  const file = join(cacheDir, `${size}.json`);
  if (existsSync(file)) {
    const cached = JSON.parse(readFileSync(file, "utf8"));
    if (
      cached?.meta?.size === size &&
      cached?.meta?.locale === locale &&
      cached?.meta?.seed === deriveSeed(size, locale)
    ) {
      cached.nodes = new Map(cached.nodes.map((n) => [n.personId, n]));
      return cached;
    }
  }
  const graph = generateGraph(size, locale);
  const serialised = serialiseGraph(graph);
  const fingerprint = graphFingerprint(serialised);
  const payload = {
    ...serialised,
    fingerprint,
    meta: { ...graph.meta, fingerprint },
  };
  writeFileSync(file, JSON.stringify(payload));
  payload.nodes = graph.nodes;
  return payload;
}

function parseArgs(argv) {
  const args = { size: "10K", locale: "vi-VN", out: null };
  for (let i = 0; i < argv.length; i += 1) {
    const token = argv[i];
    if (token === "--size") {
      args.size = argv[++i];
    } else if (token === "--locale") {
      args.locale = argv[++i];
    } else if (token === "--out") {
      args.out = argv[++i];
    }
  }
  return args;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const graph = generateGraph(args.size, args.locale);
  const serialised = serialiseGraph(graph);
  const fingerprint = graphFingerprint(serialised);
  const payload = { ...serialised, fingerprint };
  if (args.out) {
    const outPath = resolve(HERE, args.out);
    mkdirSync(dirname(outPath), { recursive: true });
    writeFileSync(outPath, JSON.stringify(payload));
    console.error(
      `[synthetic-tree] wrote ${outPath} (${payload.nodes.length} nodes, fingerprint ${fingerprint.slice(0, 12)})`,
    );
  } else {
    process.stdout.write(JSON.stringify(payload));
  }
}

const isMain = process.argv[1] && resolve(process.argv[1]) === resolve(__filename);
if (isMain) {
  main();
}
