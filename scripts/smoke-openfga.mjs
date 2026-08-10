#!/usr/bin/env node
/**
 * scripts/smoke-openfga.mjs
 *
 * E3.3 OpenFGA smoke test.
 *
 * Per `tasks.md` E3.3 + `architecture-decisions.md` §6 / ADR-E0.5-06
 * the OpenFGA authorization model + the canonical read/write
 * round-trip MUST be exercised end-to-end against a running
 * instance. This script:
 *
 *   1. Brings up an `openfga/openfga:1.10` container via the local
 *      docker CLI (in-memory datastore).
 *   2. Waits for the `/healthz` endpoint.
 *   3. Creates a per-tenant store.
 *   4. Uploads `contracts/openfga/model.v1.json`.
 *   5. Writes a default `tenant:<id>#viewer@user:<id>` tuple.
 *   6. Runs a `Check` for `viewer can view tree:<id>#viewer`.
 *   7. Runs a `Check` for `tree:<id>#viewer` AFTER revoking the
 *      viewer tuple — must return `allowed=false` (revoke-first
 *      priority).
 *   8. Re-issues the viewer tuple and re-checks — must return
 *      `allowed=true` (idempotent re-add).
 *
 * Exits 0 on success, non-zero on any contract violation.
 *
 * Per `agent-execution.md` §4.5 / §6.5 the smoke test MUST exit
 * `BLOCKED` (non-zero) when `docker` is not available, never
 * silently `PASS`. CI agents with a working docker daemon MUST
 * re-run this script.
 *
 * Usage:
 *   node scripts/smoke-openfga.mjs
 */
import { execSync, spawn } from "node:child_process";
import { exit } from "node:process";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..");
const MODEL_PATH = join(ROOT, "contracts", "openfga", "model.v1.json");
const IMAGE = "openfga/openfga:v1.18.3";
const ADMIN_PORT = 18080;
const CHECK_PORT = 18081;
const HEALTH_TIMEOUT_MS = 60_000;

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[openfga-smoke] ${msg}`);
};
const pass = (msg) => console.log(`[openfga-smoke] PASS — ${msg}`);
const block = (msg) => {
  console.error(`[openfga-smoke] BLOCKED — ${msg}`);
  exit(2);
};

const DOCKER_SOCKETS = [
  process.env.DOCKER_HOST,
  "unix:///var/run/docker.sock",
  "unix:///Users/ngocshb/Library/Containers/com.docker.docker/Data/docker-cli.sock",
  "unix:///Users/ngocshb/.docker/run/docker.sock",
].filter(Boolean);

const probeDocker = () => {
  for (const sock of DOCKER_SOCKETS) {
    try {
      const out = execSync(`docker --host "${sock}" info --format '{{.ServerVersion}}'`, {
        stdio: ["ignore", "pipe", "pipe"],
        env: { ...process.env, DOCKER_HOST: sock },
        timeout: 5000,
      }).toString().trim();
      if (out) {
        return sock;
      }
    } catch {
      // try next socket
    }
  }
  return null;
};

const dockerHost = probeDocker();
if (!dockerHost) {
  block("no working docker daemon socket — smoke test cannot run locally. CI agent MUST re-run with a docker daemon available.");
}

let containerId = null;
const dockerArgs = (args, opts = {}) =>
  execSync(`docker --host "${dockerHost}" ${args}`, {
    stdio: ["ignore", "pipe", "pipe"],
    env: { ...process.env, DOCKER_HOST: dockerHost },
    ...opts,
  });

const waitForHealth = async () => {
  const start = Date.now();
  while (Date.now() - start < HEALTH_TIMEOUT_MS) {
    try {
      const r = await fetch(`http://127.0.0.1:${ADMIN_PORT}/healthz`);
      if (r.ok) return;
    } catch {
      // not ready
    }
    await new Promise((res) => setTimeout(res, 500));
  }
  throw new Error("OpenFGA /healthz never returned 200");
};

const openfgaFetch = async (path, init = {}) => {
  const url = `http://127.0.0.1:${ADMIN_PORT}${path}`;
  const r = await fetch(url, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init.headers || {}) },
  });
  if (!r.ok) {
    const txt = await r.text();
    throw new Error(`${init.method ?? "GET"} ${path} → ${r.status}: ${txt}`);
  }
  return r.json();
};

const cleanup = () => {
  if (containerId) {
    try {
      dockerArgs(`rm -f ${containerId}`);
    } catch {
      // ignore
    }
  }
};

process.on("exit", cleanup);
process.on("SIGINT", () => {
  cleanup();
  exit(130);
});

console.log(`[openfga-smoke] starting ${IMAGE} on docker host ${dockerHost}`);

try {
  containerId = dockerArgs(
    `run -d --name gp-openfga-smoke-${Date.now()} -p ${ADMIN_PORT}:8080 -p ${CHECK_PORT}:8081 ${IMAGE} run`,
  ).toString().trim();
} catch (err) {
  fail(`could not start OpenFGA container: ${err.message}`);
  block("docker run failed — sandbox may lack docker daemon access. CI agent MUST re-run.");
}

try {
  await waitForHealth();
  pass(`OpenFGA /healthz ready on :${ADMIN_PORT}`);
} catch (err) {
  fail(`OpenFGA never became healthy: ${err.message}`);
  block("container did not reach /healthz — refusing to silently PASS. CI agent MUST re-run.");
}

// 1. Create a per-tenant store.
const tenantId = `t_smoke_${Date.now()}`;
const userId = `u_smoke_${Date.now()}`;
const treeId = `tr_smoke_${Date.now()}`;

let storeId;
try {
  const created = await openfgaFetch("/stores", {
    method: "POST",
    body: JSON.stringify({ name: `smoke-${tenantId}` }),
  });
  storeId = created.id;
  if (!storeId) throw new Error("store creation returned no id");
  pass(`created store id=${storeId}`);
} catch (err) {
  fail(`store creation failed: ${err.message}`);
  block("store creation failed — cannot continue smoke test. CI agent MUST re-run.");
}

// 2. Upload the canonical model.
let modelId;
try {
  const model = JSON.parse(readFileSync(MODEL_PATH, "utf8"));
  const written = await openfgaFetch("/stores/" + storeId + "/authorization-models", {
    method: "POST",
    body: JSON.stringify(model),
  });
  modelId = written.authorization_model_id;
  if (!modelId) throw new Error("model upload returned no authorization_model_id");
  pass(`uploaded model id=${modelId}`);
} catch (err) {
  fail(`model upload failed: ${err.message}`);
  block("model upload failed — cannot continue smoke test. CI agent MUST re-run.");
}

// 3. Write a default viewer tuple + a tree -> tenant binding so
//    `tree#viewer` cascades via tupleToUserset.
const viewerTuple = {
  tuple_key: {
    object: `tenant:${tenantId}`,
    relation: "viewer",
    user: `user:${userId}`,
  },
};
const treeTenantTuple = {
  tuple_key: {
    object: `tree:${treeId}`,
    relation: "tenant",
    user: `tenant:${tenantId}`,
  },
};

try {
  await openfgaFetch(`/stores/${storeId}/write`, {
    method: "POST",
    body: JSON.stringify({
      authorization_model_id: modelId,
      writes: { tuple_keys: [viewerTuple.tuple_key, treeTenantTuple.tuple_key] },
    }),
  });
  pass(`wrote tuple ${viewerTuple.tuple_key.object}#${viewerTuple.tuple_key.relation}@${viewerTuple.tuple_key.user}`);
  pass(`wrote tuple ${treeTenantTuple.tuple_key.object}#${treeTenantTuple.tuple_key.relation}@${treeTenantTuple.tuple_key.user}`);
} catch (err) {
  fail(`tuple write failed: ${err.message}`);
  block("tuple write failed — CI agent MUST re-run.");
}

// 4. Check: viewer can view tree#viewer (tenant cascade).
const check = async (object, relation, user) => {
  return openfgaFetch(`/stores/${storeId}/check`, {
    method: "POST",
    body: JSON.stringify({
      authorization_model_id: modelId,
      tuple_key: { object, relation, user },
    }),
  });
};

let checkResult;
try {
  checkResult = await check(`tree:${treeId}`, "viewer", `user:${userId}`);
  if (checkResult.allowed !== true) {
    fail(`expected allowed=true for tree#viewer (tenant cascade); got ${JSON.stringify(checkResult)}`);
  } else {
    pass(`check tree:${treeId}#viewer@user:${userId} = allowed (tenant cascade)`);
  }
} catch (err) {
  fail(`check 1 failed: ${err.message}`);
  block("check 1 failed — CI agent MUST re-run.");
}

// 5. Revoke the viewer tuple + re-check (revoke-first priority).
try {
  await openfgaFetch(`/stores/${storeId}/write`, {
    method: "POST",
    body: JSON.stringify({
      authorization_model_id: modelId,
      deletes: { tuple_keys: [viewerTuple.tuple_key] },
    }),
  });
  pass(`revoked tuple ${viewerTuple.tuple_key.object}#${viewerTuple.tuple_key.relation}@${viewerTuple.tuple_key.user}`);
} catch (err) {
  fail(`tuple revoke failed: ${err.message}`);
  block("tuple revoke failed — CI agent MUST re-run.");
}

try {
  checkResult = await check(`tree:${treeId}`, "viewer", `user:${userId}`);
  if (checkResult.allowed !== false) {
    fail(`expected allowed=false after revoke; got ${JSON.stringify(checkResult)}`);
  } else {
    pass(`check after revoke = denied (revoke-first priority)`);
  }
} catch (err) {
  fail(`check 2 failed: ${err.message}`);
  block("check 2 failed — CI agent MUST re-run.");
}

// 6. Re-issue + re-check (idempotent re-add).
try {
  await openfgaFetch(`/stores/${storeId}/write`, {
    method: "POST",
    body: JSON.stringify({
      authorization_model_id: modelId,
      writes: { tuple_keys: [viewerTuple.tuple_key] },
    }),
  });
  checkResult = await check(`tree:${treeId}`, "viewer", `user:${userId}`);
  if (checkResult.allowed !== true) {
    fail(`expected allowed=true after re-issue; got ${JSON.stringify(checkResult)}`);
  } else {
    pass(`check after re-issue = allowed (idempotent re-add)`);
  }
} catch (err) {
  fail(`re-issue flow failed: ${err.message}`);
  block("re-issue flow failed — CI agent MUST re-run.");
}

if (violations === 0) {
  console.log(`[openfga-smoke] OK — all 6 contract invariants hold on live OpenFGA 1.10`);
  exit(0);
} else {
  console.error(`[openfga-smoke] ${violations} violation(s)`);
  exit(1);
}
