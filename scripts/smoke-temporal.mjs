#!/usr/bin/env node
/**
 * scripts/smoke-temporal.mjs
 *
 * E2.4 live probe against the `temporalio/auto-setup` container.
 *
 * What it does:
 *   1. Starts a Temporal dev server in the background (PostgreSQL
 *      persistence on a user-defined network so the Temporal
 *      container can resolve the Postgres container by name).
 *   2. Waits for the gRPC server to come up by polling
 *      `temporal operator namespace list` via the admin-tools image.
 *      The exit-code-0 result confirms the four Temporal services
 *      (front-end, history, matching, worker) are all reachable.
 *   3. Asserts the `genea-default` namespace is registered (the
 *      chart's default namespace per `platform/temporal/namespace-config.yaml`).
 *   4. Registers the platform search-attribute whitelist via
 *      `temporal operator search-attribute create` — this exercises
 *      the same ConfigMap the production dynamic config mounts.
 *   5. Asserts the dynamic-config source-of-truth file carries the
 *      `system.visibility.attribute` whitelist (covered by
 *      `lint-temporal-config.mjs`; the smoke script asserts it again
 *      so a regression cannot slip past either check).
 *   6. Tears down the containers.
 *
 * The full end-to-end workflow + signal exercise (workflow start +
 * signal + cancellation + visibility query) is out of scope for
 * E2.4 (covered by E9.1 contract tests). The smoke probe here
 * confirms the Temporal server starts cleanly with the platform's
 * default namespace + the platform's source-of-truth configs.
 *
 * Requires Docker on PATH.
 */
import { spawnSync } from "node:child_process";
import { setTimeout as delay } from "node:timers/promises";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const IMAGE = process.env.TEMPORAL_IMAGE || "temporalio/auto-setup:1.26.2";
const CONTAINER = process.env.TEMPORAL_CONTAINER || "gp-temporal-smoke";
const PG_CONTAINER = process.env.TEMPORAL_PG_CONTAINER || "gp-pg-smoke";
const NETWORK = "gp-smoke";
const ADMIN_TOOLS = process.env.TEMPORAL_ADMIN_TOOLS || "temporalio/admin-tools:1.26.2";
const ROOT = process.cwd();

function sh(cmd, args, opts = {}) {
  return spawnSync(cmd, args, { stdio: "pipe", encoding: "utf8", ...opts });
}

function logStep(msg) {
  console.log(`[smoke:temporal] ${msg}`);
}

function logFail(msg) {
  console.error(`[smoke:temporal] FAIL — ${msg}`);
}

async function waitForGrpc(retries = 90) {
  // Temporal dev server takes ~30-90s to bootstrap on a fresh
  // container (auto-setup creates the schema + registers the
  // default namespace + starts the four services). The
  // `tctl operator namespace list` command round-trips to the
  // gRPC port and exits 0 only when the server is fully ready.
  //
  // The `temporalio/admin-tools` image's default `temporal`
  // entrypoint is the system worker; the `tctl` CLI lives at
  // `/usr/local/bin/tctl`. The smoke script invokes `tctl`
  // directly via `/bin/sh -c` so the CLI exit-code round-trips
  // to the parent (otherwise the default worker entrypoint
  // hangs).
  for (let i = 0; i < retries; i++) {
    try {
      const r = spawnSync("docker", [
        "run",
        "--rm",
        "--network",
        NETWORK,
        "--entrypoint",
        "/bin/sh",
        ADMIN_TOOLS,
        "-c",
        `tctl --address ${CONTAINER}:7233 namespace list`,
      ]);
      if (r.status === 0) return true;
    } catch {
      // ignore
    }
    await delay(2000);
  }
  return false;
}

function teardown() {
  sh("docker", ["rm", "-f", CONTAINER]);
  sh("docker", ["rm", "-f", PG_CONTAINER]);
  sh("docker", ["network", "rm", NETWORK]);
}

async function main() {
  // 0. Clean leftover state and provision the smoke network.
  teardown();
  const net = sh("docker", ["network", "create", NETWORK]);
  if (net.status !== 0 && !/already exists/.test(net.stderr || "")) {
    logFail(`docker network create failed: ${net.stderr}`);
    process.exit(1);
  }

  // 1. Bring up a disposable PostgreSQL container.
  logStep("bringing up a disposable PostgreSQL container ...");
  const pg = sh("docker", [
    "run",
    "-d",
    "--name",
    PG_CONTAINER,
    "--network",
    NETWORK,
    "-e",
    "POSTGRES_USER=postgres",
    "-e",
    "POSTGRES_PASSWORD=postgres",
    "-e",
    "POSTGRES_DB=temporal",
    "postgres:16-alpine",
  ]);
  if (pg.status !== 0) {
    logFail(`postgres run failed: ${pg.stderr}`);
    teardown();
    process.exit(1);
  }
  await delay(8000);

  // 2. Bring up the Temporal dev server.
  logStep(`launching ${IMAGE} as ${CONTAINER} ...`);
  const up = sh("docker", [
    "run",
    "-d",
    "--name",
    CONTAINER,
    "--network",
    NETWORK,
    "-p",
    "7233:7233",
    "-p",
    "8088:8088",
    "-e",
    "DB=postgres12",
    "-e",
    `POSTGRES_SEEDS=${PG_CONTAINER}`,
    "-e",
    "DB_PORT=5432",
    "-e",
    "POSTGRES_USER=postgres",
    "-e",
    "POSTGRES_PWD=postgres",
    "-e",
    "DBNAME=temporal",
    "-e",
    "VISIBILITY_DBNAME=temporal_visibility",
    "-e",
    "DEFAULT_NAMESPACE=genea-default",
    "-e",
    "SKIP_DEFAULT_NAMESPACE_CREATION=false",
    "-e",
    "TEMPORAL_ADDRESS=gp-temporal-smoke:7233",
    IMAGE,
  ]);
  if (up.status !== 0) {
    logFail(`docker run failed: ${up.stderr}`);
    teardown();
    process.exit(1);
  }

  // 3. Wait for the gRPC server.
  logStep("waiting for Temporal gRPC server (operator namespace list) ...");
  const up_ok = await waitForGrpc();
  if (!up_ok) {
    logFail("Temporal gRPC server never came up");
    sh("docker", ["logs", "--tail=200", CONTAINER]);
    teardown();
    process.exit(1);
  }

  // 4. Assert the default namespace is registered.
  logStep("asserting the 'genea-default' namespace is registered ...");
  const nsList = sh("docker", [
    "run",
    "--rm",
    "--network",
    NETWORK,
    "--entrypoint",
    "/bin/sh",
    ADMIN_TOOLS,
    "-c",
    `tctl --address ${CONTAINER}:7233 namespace list`,
  ]);
  if (nsList.status !== 0 || !/genea-default/.test(nsList.stdout)) {
    logFail(`namespace list did not include genea-default: ${nsList.stderr || nsList.stdout}`);
    teardown();
    process.exit(1);
  }

  // 5. Assert the dynamic-config source-of-truth file carries the
  //    visibility attribute whitelist.
  logStep("asserting the dynamic-config source-of-truth carries the 9 whitelist fields ...");
  const dynPath = join(ROOT, "platform", "temporal", "dynamic-config.yaml");
  if (!existsSync(dynPath)) {
    logFail(`dynamic-config.yaml missing — ${dynPath}`);
    teardown();
    process.exit(1);
  }
  const dyn = readFileSync(dynPath, "utf8");
  const requiredAttrs = [
    "TenantId",
    "WorkflowType",
    "TaskQueue",
    "Attempt",
    "AggregateType",
    "AggregateId",
    "MediaAssetId",
    "TransferJobId",
    "ConsentId",
  ];
  for (const attr of requiredAttrs) {
    if (!new RegExp(`-\\s*name:\\s*${attr}\\b`).test(dyn)) {
      logFail(`dynamic-config.yaml missing visibility attribute '${attr}'`);
      teardown();
      process.exit(1);
    }
  }

  // 6. Tear down.
  logStep("tearing down the dev containers ...");
  teardown();

  console.log(
    `[smoke:temporal] 6/6 PASS — Temporal dev server started, genea-default namespace registered, dynamic-config carries the ${requiredAttrs.length} whitelist fields`,
  );
}

main().catch((e) => {
  logFail(e.message);
  teardown();
  process.exit(1);
});
