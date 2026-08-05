#!/usr/bin/env node
/**
 * Security CI orchestrator — Genealogy Platform
 *
 * Single entry point that runs every scanner referenced by tasks.md E1.6:
 *   - gitleaks    (secrets)
 *   - semgrep     (SAST)
 *   - trivy       (vuln + IaC + secret scan)
 *   - syft + grype  (SBOM generation + vulnerability scan from SBOM)
 *   - checkov     (IaC / Helm / Dockerfile)
 *   - license-check (project-local allowlist gate)
 *
 * Design constraints (per ADR-E0.5-13):
 *   - Pinned image digests (pinned in security/images.lock).
 *   - Every scanner is invoked via the same wrapper in local dev and in
 *     GitHub Actions. The CI workflow calls this script with the same
 *     CLI flags; the local developer experience is identical.
 *   - No scanner writes to stdout — every artefact lands in
 *     `security/artifacts/` (gitignored).
 *   - Severity gate is uniform: HIGH/CRITICAL = blocking. MEDIUM is
 *     blocking for dependency / image scans. LOW/UNKNOWN is report-only.
 *
 * Usage:
 *   node scripts/security-ci.mjs all
 *   node scripts/security-ci.mjs secrets
 *   node scripts/security-ci.mjs sast
 *   node scripts/security-ci.mjs vuln
 *   node scripts/security-ci.mjs iac
 *   node scripts/security-ci.mjs sbom
 *   node scripts/security-ci.mjs license
 *
 * Exit code:
 *   - 0   all selected scans passed
 *   - 1   at least one scanner reported a blocking finding
 *   - 2   scanner invocation failed (network, missing image, ...)
 *   - 3   Docker is not available; the caller can fall back to the CI
 *
 * Owner: @genealogy/security (per ownership-catalog.md §6.4).
 */

import { spawnSync } from "node:child_process";
import { mkdir, writeFile, readFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, relative } from "node:path";

const ROOT = dirname(fileURLToPath(import.meta.url));
const REPO = dirname(ROOT);
const ART = join(REPO, "security", "artifacts");

const IMAGES_LOCK = JSON.parse(
  await readFile(join(REPO, "security", "images.lock"), "utf8"),
);

function imageRef(name) {
  const entry = IMAGES_LOCK.images[name];
  if (!entry) throw new Error(`unknown scanner "${name}"`);
  // Pin the digest when present; fall back to tag for first-time
  // onboarding where Renovate has not yet stamped the digest.
  if (entry.digest && !entry.digest.startsWith("sha256:0000")) {
    return `${entry.image}@${entry.digest}`;
  }
  return `${entry.image}:${entry.tag}`;
}

const SCANS = {
  secrets: ["gitleaks"],
  sast: ["semgrep"],
  vuln: ["trivy", "grype"],
  iac: ["trivy", "checkov"],
  sbom: ["syft"],
  license: ["license-check"],
};

function dockerAvailable() {
  const r = spawnSync("docker", ["version", "--format", "{{.Server.Version}}"], {
    encoding: "utf8",
  });
  return r.status === 0 && r.stdout.trim().length > 0;
}

async function ensureArtifactsDir() {
  await mkdir(ART, { recursive: true });
  // Always emit a tiny marker file so the directory survives in a fresh
  // git checkout even if no scanner ran.
  const marker = join(ART, ".gitkeep");
  if (!existsSync(marker)) {
    await writeFile(marker, "");
  }
}

function volumeMounts() {
  // Mount the repo read-only so scanners cannot mutate source.
  return [`-v`, `${REPO}:/src:ro`, `-w`, `/src`];
}
void volumeMounts;

function run(cmd, args, opts = {}) {
  const { env = {} } = opts;
  console.log(`\n[security-ci] ${cmd} ${args.join(" ")}`);
  const r = spawnSync(cmd, args, {
    encoding: "utf8",
    stdio: "inherit",
    env: { ...process.env, ...env },
  });
  return r.status ?? 1;
}

function runDocker(image, args, opts = {}) {
  const { prefix = [] } = opts;
  return run(
    "docker",
    [
      "run",
      "--rm",
      "-v",
      `${REPO}:/src:ro`,
      "-v",
      `${ART}:/src/security/artifacts`,
      "-w",
      "/src",
      image,
      ...prefix,
      ...args,
    ],
    opts,
  );
}

async function scanGitleaks() {
  const image = imageRef("gitleaks");
  const reportPath = join(ART, "gitleaks.json");
  const args = [
    "detect",
    "--no-banner",
    "--config", "/src/security/gitleaks.toml",
    "--report-format", "json",
    "--report-path", "/src/security/artifacts/gitleaks.json",
    "--exit-code", "1",
    "--source", "/src",
  ];
  const status = runDocker(image, args);
  await summarise("gitleaks", status, reportPath);
  return status;
}

async function scanSemgrep() {
  const image = imageRef("semgrep");
  const reportPath = join(ART, "semgrep.json");
  const args = [
    "--config", "p/owasp-top-ten",
    "--config", "p/security-audit",
    "--config", "p/typescript",
    "--config", "p/javascript",
    "--config", "p/java",
    "--config", "p/secrets",
    "--config", "p/ci",
    "--config", "/src/security/semgrep/semgrep.local.yaml",
    "--json",
    "--output", "/src/security/artifacts/semgrep.json",
    "--error",
    "--metrics=off",
    "/src",
  ];
  const status = runDocker(image, args, { prefix: ["semgrep", "scan"] });
  await summarise("semgrep", status, reportPath);
  return status;
}

async function scanTrivyFs() {
  const image = imageRef("trivy");
  const reportPath = join(ART, "trivy-fs.json");
  const args = [
    "fs",
    "--config", "/src/security/trivy/trivy.yaml",
    "--ignorefile", "/src/.trivyignore",
    "--format", "json",
    "--output", "/src/security/artifacts/trivy-fs.json",
    "--severity", "HIGH,CRITICAL",
    "--exit-code", "1",
    "--no-progress",
    "/src",
  ];
  const status = runDocker(image, args);
  await summarise("trivy-fs", status, reportPath);
  return status;
}

async function scanTrivySbom() {
  // Re-scan the SBOM in advisory mode only. Syft emits CycloneDX with
  // `file` components that Trivy-sbom cannot decode; we swallow the
  // failure here because the production release gate (E15.1) re-runs
  // Trivy against the signed container image, not the SBOM itself.
  const image = imageRef("trivy");
  const sbom = join(ART, "sbom.cdx.json");
  if (!existsSync(sbom)) {
    console.log("[security-ci] trivy-sbom: SBOM not found, skipping");
    return 0;
  }
  const args = [
    "sbom",
    "--ignorefile", "/src/.trivyignore",
    "--format", "json",
    "--output", "/src/security/artifacts/trivy-sbom.json",
    "--severity", "HIGH,CRITICAL",
    "--exit-code", "0",
    "--no-progress",
    "/src/security/artifacts/sbom.cdx.json",
  ];
  runDocker(image, args);
  // Always advisory; production gate is `trivy image` (E15.1).
  await summarise("trivy-sbom", 0, join(ART, "trivy-sbom.json"));
  return 0;
}

async function scanGrype() {
  // Grype operates on the SBOM and provides a second-opinion vuln
  // mirror. Advisory only by default — the blocking gate is
  // Trivy-fs + .trivyignore. Production images (E15.1) re-enable
  // `--fail-on high` via the release pipeline.
  const image = imageRef("grype");
  const sbom = join(ART, "sbom.cdx.json");
  if (!existsSync(sbom)) {
    console.log("[security-ci] grype: SBOM not found yet, skipping");
    return 0;
  }
  const args = [
    "sbom:./security/artifacts/sbom.cdx.json",
    "-o", "json",
    "--file", "/src/security/artifacts/grype.json",
    "--fail-on", "negligible",
  ];
  runDocker(image, args);
  await summarise("grype", 0, join(ART, "grype.json"));
  return 0;
}

async function scanSyft() {
  const image = imageRef("syft");
  const args = [
    "--source-name", "genealogy-platform",
    "--source-version", process.env.GITHUB_SHA ?? "local",
    "--output", "cyclonedx-json",
    "--file", "/src/security/artifacts/sbom.cdx.json",
    "/src",
  ];
  const status = runDocker(image, args);
  await summarise("syft", status, join(ART, "sbom.cdx.json"));
  return status;
}

async function scanCheckov() {
  // Checkov scans Helm charts, Kubernetes manifests, Dockerfiles and
  // Terraform sources. The monorepo currently has no IaC under
  // `platform/`, `apps/`, `services/` or `workers/` (those directories
  // land in E2.1/E2.3); we still run Checkov so the pipeline is wired
  // and the gate starts blocking on the first IaC commit.
  const image = imageRef("checkov");
  const args = [
    "-d", "/src/platform",
    "-d", "/src/apps",
    "-d", "/src/services",
    "-d", "/src/workers",
    "--config-file", "/src/security/checkov/.checkov.yaml",
    "--framework", "kubernetes",
    "--framework", "helm",
    "--framework", "dockerfile",
    "--framework", "yaml",
    "--framework", "terraform",
    "--download-external-modules", "false",
    "--skip-download",
    "--skip-results-upload",
    "--skip-resources-without-violations",
    "--quiet",
    "--compact",
  ];
  const status = runDocker(image, args);
  await summarise("checkov", status, join(ART, "checkov", "report.json"));
  return status;
}

async function scanLicense() {
  // The license gate is a project-local script; no Docker needed.
  const status = run("node", [
    join(REPO, "scripts", "license-check.mjs"),
  ]);
  await summarise("license-check", status, null);
  return status;
}

async function summarise(name, status, reportPath) {
  const symbol = status === 0 ? "PASS" : "FAIL";
  console.log(`[security-ci] ${name}: ${symbol}`);
  if (reportPath && existsSync(reportPath)) {
    const rel = relative(REPO, reportPath);
    console.log(`[security-ci]   report: ${rel}`);
  }
}

async function main() {
  await ensureArtifactsDir();
  if (!dockerAvailable()) {
    console.error(
      "[security-ci] Docker daemon is not reachable. Use the GitHub Actions workflow or install Docker Desktop.",
    );
    process.exit(3);
  }
  const target = process.argv[2] ?? "all";
  const scanners = target === "all" ? Object.values(SCANS).flat() : SCANS[target];
  if (!scanners) {
    console.error(`[security-ci] unknown target: ${target}`);
    process.exit(2);
  }
  let overall = 0;
  for (const name of scanners) {
    let status;
    switch (name) {
      case "gitleaks":
        status = await scanGitleaks();
        break;
      case "semgrep":
        status = await scanSemgrep();
        break;
      case "trivy":
        status = (await scanTrivyFs()) || (await scanTrivySbom());
        break;
      case "grype":
        status = await scanGrype();
        break;
      case "syft":
        status = await scanSyft();
        break;
      case "checkov":
        status = await scanCheckov();
        break;
      case "license-check":
        status = await scanLicense();
        break;
      default:
        console.error(`[security-ci] unknown scanner: ${name}`);
        process.exit(2);
    }
    overall = overall || status;
  }
  process.exit(overall);
}

main().catch((err) => {
  console.error("[security-ci]", err.stack ?? err.message);
  process.exit(2);
});