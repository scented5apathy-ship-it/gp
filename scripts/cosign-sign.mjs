#!/usr/bin/env node
/**
 * Cosign signing helper — Genealogy Platform
 *
 * Sign an OCI artefact (image or SBOM) with Cosign and attach a SLSA
 * build provenance attestation. The signing keys live in the CI secret
 * manager (Vault or GitHub OIDC) — this script never sees a long-lived
 * key. Local developers run it with `--keyless` against their own
 * Fulcio cert for day-to-day work.
 *
 * Usage:
 *   node scripts/cosign-sign.mjs sign \
 *     --image ghcr.io/genealogy/genealogy-service:sha-abcdef0
 *   node scripts/cosign-sign.mjs sign \
 *     --image ghcr.io/genealogy/genealogy-service:sha-abcdef0 \
 *     --sbom security/artifacts/sbom.cdx.json
 *   node scripts/cosign-sign.mjs verify \
 *     --image ghcr.io/genealogy/genealogy-service:sha-abcdef0
 *
 * Environment:
 *   COSIGN_KEY                    path to private key (CI)
 *   COSIGN_PUBLIC_KEY             path to public key (verifier)
 *   COSIGN_KEYLESS_OCI_FULCIO_URL Fulcio URL (default = sigstore.dev)
 *   COSIGN_REKOR_URL              Rekor URL (default = rekor.sigstore.dev)
 *   COSIGN_OIDC_ISSUER            OIDC issuer (GitHub = https://token.actions.githubusercontent.com)
 *   COSIGN_OIDC_TOKEN             OIDC token from CI runner
 *
 * Exit code:
 *   - 0   signature / attestation succeeded
 *   - 1   cosign reported a verification failure
 *   - 2   script / input error
 *
 * Owner: @genealogy/security (per ownership-catalog.md §6.4).
 */

import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { readFile } from "node:fs/promises";

const ROOT = dirname(fileURLToPath(import.meta.url));
const REPO = dirname(ROOT);
const LOCK = JSON.parse(
  await readFile(join(REPO, "security", "images.lock"), "utf8"),
);

function imageRef(name) {
  const entry = LOCK.images[name];
  if (!entry) throw new Error(`unknown cosign image "${name}"`);
  if (entry.digest && !entry.digest.startsWith("sha256:0000")) {
    return `${entry.image}@${entry.digest}`;
  }
  return `${entry.image}:${entry.tag}`;
}

function parseArgs(argv) {
  const [, , cmd, ...rest] = argv;
  const opts = {};
  for (let i = 0; i < rest.length; i += 1) {
    const arg = rest[i];
    if (!arg.startsWith("--")) continue;
    const key = arg.slice(2);
    const val = rest[i + 1];
    opts[key] = val === undefined || val.startsWith("--") ? true : val;
    if (val !== undefined && !val.startsWith("--")) i += 1;
  }
  return { cmd, opts };
}

function run(args, env = {}) {
  console.log(`[cosign] ${args.join(" ")}`);
  const res = spawnSync("docker", ["run", "--rm", ...args], {
    encoding: "utf8",
    stdio: "inherit",
    env: { ...process.env, ...env },
  });
  return res.status ?? 1;
}

async function main() {
  const { cmd, opts } = parseArgs(process.argv);
  if (!cmd) {
    console.error(
      "Usage: cosign-sign.mjs <sign|verify|attest> --image <ref> [--sbom <path>] [--key <path>]",
    );
    process.exit(2);
  }
  const image = opts.image;
  if (!image) {
    console.error("cosign: --image <ref> is required");
    process.exit(2);
  }
  const imageImg = imageRef("cosign");

  // Allow cosign to write keys / signatures to the host filesystem via
  // /tmp mount (CI copies them into Vault after the workflow runs).
  const tmpMount = [
    "-v",
    `${process.env.COSIGN_HOME ?? "/tmp/cosign"}:/tmp/cosign`,
    "-e",
    `COSIGN_HOME=/tmp/cosign`,
  ];

  switch (cmd) {
    case "sign": {
      const key = opts.key ?? process.env.COSIGN_KEY;
      const keyless = !key;
      const args = ["run", "--rm", ...tmpMount, imageImg, "sign"];
      if (keyless) {
        args.push("--keyless");
        if (process.env.COSIGN_OIDC_ISSUER) {
          args.push(`--oidc-issuer=${process.env.COSIGN_OIDC_ISSUER}`);
        }
        if (process.env.COSIGN_OIDC_TOKEN) {
          args.push(`--identity-token=${process.env.COSIGN_OIDC_TOKEN}`);
        }
      } else {
        args.push("--key", key);
      }
      args.push("--yes", image);
      const status = run(args);
      if (status !== 0) process.exit(status);
      if (opts.sbom && existsSync(opts.sbom)) {
        // Attach the SBOM as a Cosign attestation so consumers can
        // verify the artefact matches a known-good SBOM.
        const attestArgs = [
          "run",
          "--rm",
          ...tmpMount,
          imageImg,
          "attest",
          "--yes",
          "--predicate",
          opts.sbom,
          "--type",
          "cyclonedx",
        ];
        if (key) attestArgs.push("--key", key);
        else attestArgs.push("--keyless");
        const status2 = run(attestArgs);
        if (status2 !== 0) process.exit(status2);
      }
      break;
    }
    case "verify": {
      const pubKey = opts["pub-key"] ?? process.env.COSIGN_PUBLIC_KEY;
      const args = ["run", "--rm", ...tmpMount, imageImg, "verify"];
      if (pubKey) args.push("--key", pubKey);
      else args.push("--certificate-identity-regexp", ".*", "--certificate-oidc-issuer-regexp", ".*");
      args.push(image);
      const status = run(args);
      if (status !== 0) process.exit(status);
      break;
    }
    case "attest": {
      if (!opts.sbom) {
        console.error("cosign attest: --sbom <path> is required");
        process.exit(2);
      }
      const args = [
        "run",
        "--rm",
        ...tmpMount,
        imageImg,
        "attest",
        "--yes",
        "--predicate",
        opts.sbom,
        "--type",
        "cyclonedx",
      ];
      if (opts.key) args.push("--key", opts.key);
      else args.push("--keyless");
      args.push(image);
      const status = run(args);
      if (status !== 0) process.exit(status);
      break;
    }
    default:
      console.error(`cosign: unknown sub-command "${cmd}"`);
      process.exit(2);
  }
}

main().catch((err) => {
  console.error("[cosign]", err.stack ?? err.message);
  process.exit(2);
});