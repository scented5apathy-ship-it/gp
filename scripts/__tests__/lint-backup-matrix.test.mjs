/**
 * scripts/__tests__/lint-backup-matrix.test.mjs
 *
 * Unit tests for `scripts/lint-backup-matrix.mjs` (E14.1).
 */
import { describe, it } from "node:test";
import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const SCRIPT = "scripts/lint-backup-matrix.mjs";

function runLinter(env) {
  return spawnSync("node", [SCRIPT], {
    cwd: process.cwd(),
    env: { ...process.env, ...env },
    encoding: "utf8",
  });
}

function combined(result) {
  return `${result.stdout}\n${result.stderr}`;
}

describe("lint-backup-matrix", () => {
  it("passes when the canonical contract is valid", () => {
    const result = runLinter({});
    if (result.status !== 0) {
      throw new Error(`linter exited ${result.status}, expected 0:\n${combined(result)}`);
    }
    if (!result.stdout.includes("E14.1 summary: OK")) {
      throw new Error(`missing success marker in:\n${result.stdout}`);
    }
  });

  it("rejects when chart mirror drifts", () => {
    const chartPath = resolve(
      "platform/helm/genealogy-platform/files/disaster-recovery/backup-matrix-policy.yaml",
    );
    const backup = readFileSync(chartPath, "utf8");
    try {
      writeFileSync(chartPath, backup + "\n# drift\n");
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted drift");
      }
      if (!/byte-identity/.test(combined(result))) {
        throw new Error(`missing byte-identity:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(chartPath, backup);
    }
  });

  it("rejects when components closed-set is missing an entry", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/backup-matrix-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - name: postgresql\n",
        "    # removed: - name: postgresql\n",
      );
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted missing component");
      }
      if (!/components/.test(combined(result))) {
        throw new Error(`missing components message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });

  it("rejects when RTO exceeds SaaS budget", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/backup-matrix-policy.yaml",
    );
    const chartPath = resolve(
      "platform/helm/genealogy-platform/files/disaster-recovery/backup-matrix-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    const chartBackup = readFileSync(chartPath, "utf8");
    try {
      const tampered = backup.replaceAll(
        "      rtoSeconds: 14400\n",
        "      rtoSeconds: 86400\n",
      );
      writeFileSync(contractPath, tampered);
      writeFileSync(chartPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted over-budget RTO");
      }
      if (!/rtoSeconds/.test(combined(result))) {
        throw new Error(`missing RTO message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
      writeFileSync(chartPath, chartBackup);
    }
  });

  it("rejects when invariants closed-set is incomplete", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/backup-matrix-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - rawEmailNeverInBackupArtifact\n",
        "",
      );
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted missing invariant");
      }
      if (!/invariants/.test(combined(result))) {
        throw new Error(`missing invariants message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });
});