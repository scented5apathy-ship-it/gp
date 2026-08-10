/**
 * apps/web/bench/renderer/worker/client.mjs
 *
 * Worker client used by the Node bench harness. Wraps the
 * Worker in a Promise-based `runLayout(neighborhood, option)`
 * helper that returns the layout + the postMessage round-trip
 * latency. The production browser client would use a real
 * `Worker` (`new Worker(new URL("./layout.worker.mjs", ...))`),
 * but the Node bench uses `node:worker_threads` so the
 * boundary cost is measured without Chromium.
 *
 * Stability: request ids are monotonic per client instance so
 * concurrent calls do not interleave their responses.
 */
import { Worker } from "node:worker_threads";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const WORKER_FILE = resolve(__dirname, "layout.worker.mjs");

/**
 * @param {{tenantId:string, treeId:string, rootPersonId:string, direction:string, depth:number, nodes:Array<{personId:string,treeId:string,tenantId:string,generation:number,rootOfBranch:boolean}>, edges:Array<{parentId:string,childId:string}>}} neighborhood
 * @param {string} option
 * @param {{sampleRuns?:number, timeoutMs?:number}} [opts]
 */
export async function runLayoutInWorker(neighborhood, option, opts = {}) {
  const sampleRuns = opts.sampleRuns ?? 1;
  const timeoutMs = opts.timeoutMs ?? 30_000;
  const worker = new Worker(WORKER_FILE);
  const requestId = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  let sentAt = null;
  try {
    const layout = await new Promise((resolveLayout, rejectLayout) => {
      const timer = setTimeout(() => {
        worker.terminate().catch(() => {});
        rejectLayout(new Error(`worker timed out after ${timeoutMs}ms`));
      }, timeoutMs);
      worker.once("message", (msg) => {
        clearTimeout(timer);
        if (msg.requestId !== requestId) {
          rejectLayout(new Error(`unexpected requestId ${msg.requestId}`));
          return;
        }
        if (!msg.ok) {
          rejectLayout(new Error(msg.error ?? "unknown worker error"));
          return;
        }
        resolveLayout(msg.layout);
      });
      worker.once("error", (err) => {
        clearTimeout(timer);
        rejectLayout(err);
      });
      sentAt = process.hrtime.bigint();
      worker.postMessage({ requestId, option, neighborhood, sampleRuns });
    });
    const receivedAt = process.hrtime.bigint();
    return {
      layout,
      roundTripNs: Number(receivedAt - sentAt),
    };
  } finally {
    await worker.terminate().catch(() => {});
  }
}

/**
 * Convenience helper for the bench harness: build a
 * `Neighborhood` (depth-bounded slice of the synthetic graph)
 * and run it through the Worker in a single call.
 *
 * @param {{nodes:Map<string,{personId:string,treeId:string,tenantId:string,generation:number,rootOfBranch:boolean}>, edges:Array<{parentId:string,childId:string}>, tenantId:string, treeId:string}} graph
 * @param {{rootPersonId?:string, depth?:number, direction?:string}} opts
 */
export function buildNeighborhood(graph, opts = {}) {
  const root = opts.rootPersonId ?? "person-0000000";
  const depth = opts.depth ?? 4;
  const direction = opts.direction ?? "DESCENDANTS";
  /** @type {Set<string>} */
  const visited = new Set([root]);
  /** @type {Array<{parentId:string,childId:string}>} */
  const edges = [];
  /** @type {Array<{personId:string,treeId:string,tenantId:string,generation:number,rootOfBranch:boolean}>} */
  const nodes = [];
  const rootNode = graph.nodes.get(root);
  if (!rootNode) {
    throw new Error(`root personId ${root} not found in graph`);
  }
  nodes.push(rootNode);

  /** @type {Array<{personId:string, generation:number}>} */
  const frontier = [{ personId: root, generation: 0 }];
  for (let d = 0; d < depth; d += 1) {
    /** @type {Array<{personId:string, generation:number}>} */
    const next = [];
    for (const f of frontier) {
      const children =
        direction === "ANCESTORS"
          ? graph.edges
              .filter((e) => e.childId === f.personId)
              .map((e) => ({ id: e.parentId, gen: f.generation - 1 }))
          : graph.edges
              .filter((e) => e.parentId === f.personId)
              .map((e) => ({ id: e.childId, gen: f.generation + 1 }));
      for (const c of children) {
        if (!visited.has(c.id)) {
          visited.add(c.id);
          const childNode = graph.nodes.get(c.id);
          if (childNode) {
            nodes.push({ ...childNode, generation: c.gen });
          }
          edges.push(
            direction === "ANCESTORS"
              ? { parentId: c.id, childId: f.personId }
              : { parentId: f.personId, childId: c.id },
          );
          next.push({ personId: c.id, generation: c.gen });
        }
      }
    }
    frontier.length = 0;
    frontier.push(...next);
    if (frontier.length === 0) break;
  }

  return {
    rootPersonId: root,
    direction,
    depth,
    nodes,
    edges,
  };
}
