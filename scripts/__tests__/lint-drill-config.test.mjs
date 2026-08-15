/**
 * scripts/__tests__/lint-drill-config.test.mjs
 *
 * Unit tests for `scripts/lint-drill-config.mjs` (E14.2).
 */
import { describe, it } from "node:test";
import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const SCRIPT = "scripts/lint-drill-config.mjs";

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

describe("lint-drill-config", () => {
  it("passes when the canonical contract is valid", () => {
    const result = runLinter({});
    if (result.status !== 0) {
      throw new Error(`linter exited ${result.status}, expected 0:\n${combined(result)}`);
    }
    if (!result.stdout.includes("E14.2 summary: OK")) {
      throw new Error(`missing success marker in:\n${result.stdout}`);
    }
  });

  it("rejects when chart mirror drifts", () => {
    const chartPath = resolve(
      "platform/helm/genealogy-platform/files/disaster-recovery/drill-policy.yaml",
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

  it("rejects when drillKinds closed-set is missing an entry", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/drill-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - name: cluster_loss\n",
        "    # removed: - name: cluster_loss\n",
      );
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted missing drill kind");
      }
      if (!/drillKinds/.test(combined(result))) {
        throw new Error(`missing drillKinds message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });

  it("rejects when replay mode diverges from redacted_metrics_only", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/drill-policy.yaml",
    );
    const chartPath = resolve(
      "platform/helm/genealogy-platform/files/disaster-recovery/drill-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    const chartBackup = readFileSync(chartPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - redacted_metrics_only\n",
        "    - full_payload\n",
      );
      writeFileSync(contractPath, tampered);
      writeFileSync(chartPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted full_payload replay mode");
      }
      if (!/replayLogCaptureModes/.test(combined(result))) {
        throw new Error(`missing replay mode message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
      writeFileSync(chartPath, chartBackup);
    }
  });

  it("rejects when invariants closed-set is incomplete", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/drill-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - noAdHocRegionFailover\n",
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