/**
 * apps/web/bench/renderer/worker/layout.worker.mjs
 *
 * Web Worker prototype for the tree renderer benchmark
 * (E5.1 / ADR-E0.5-10 §Decision §Inputs). The Worker is
 * intentionally framework-free (no DOM, no React) — it accepts
 * a serialised `Neighborhood` and a `RendererOption`, runs the
 * layout pass, and posts the layout back to the main thread.
 *
 * The Node bench spawns this same module inside
 * `node:worker_threads` (see `client.mjs`) so the postMessage
 * round-trip is exercised even when running outside a browser.
 * This matches `design.md` §10.2 ("Layout chạy Web Worker khi
 * phù hợp") and lets the bench measure the worker boundary
 * cost without bringing up Chromium.
 *
 * The Worker is fully deterministic given the same inputs.
 */
import { parentPort } from "node:worker_threads";

import { assertOpaqueId, RENDERER_OPTIONS } from "../contract.mjs";
import { layoutNeighborhood } from "../layout-core.mjs";

if (!parentPort) {
  // The module is also importable from a non-worker context
  // (the bench harness self-test reuses `layoutNeighborhood`
  // directly), so we silently no-op when there is no
  // `parentPort`.
} else {
  parentPort.on("message", (msg) => {
    try {
      const { requestId, option, neighborhood, sampleRuns } = msg;
      if (!RENDERER_OPTIONS.includes(option)) {
        throw new Error(`unsupported option ${option}`);
      }
      if (!neighborhood || !Array.isArray(neighborhood.nodes)) {
        throw new Error("neighborhood.nodes must be an array");
      }
      for (const node of neighborhood.nodes) {
        assertOpaqueId(node.personId, "neighborhood.node.personId");
      }
      const layout = layoutNeighborhood(neighborhood, option, sampleRuns ?? 1);
      parentPort.postMessage({ requestId, ok: true, layout });
    } catch (err) {
      parentPort.postMessage({
        requestId: msg?.requestId,
        ok: false,
        error: err.message,
      });
    }
  });
}
