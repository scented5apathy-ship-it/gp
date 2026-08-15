/**
 * scripts/__tests__/lint-performance-capacity.test.mjs
 *
 * Unit tests for `scripts/lint-performance-capacity.mjs` (E13.3).
 */
import { describe, it } from "node:test";
import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const SCRIPT = "scripts/lint-performance-capacity.mjs";

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

describe("lint-performance-capacity", () => {
  it("passes when the canonical contract is valid", () => {
    const result = runLinter({});
    if (result.status !== 0) {
      throw new Error(`linter exited ${result.status}, expected 0:\n${combined(result)}`);
    }
    if (!result.stdout.includes("E13.3 summary: OK")) {
      throw new Error(`missing success marker in:\n${result.stdout}`);
    }
  });

  it("rejects when chart mirror drifts", () => {
    const chartPath = resolve(
      "platform/helm/genealogy-platform/files/reliability/performance-capacity-policy.yaml",
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

  it("rejects when workloadClasses is missing an entry", () => {
    const contractPath = resolve(
      "contracts/reliability/performance-capacity-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - name: browse_tree\n",
        "    # removed: - name: browse_tree\n",
      );
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted missing workload");
      }
      if (!/workloadClasses/.test(combined(result))) {
        throw new Error(`missing workload message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });

  it("rejects when performanceRunStateMatrix terminal has transitions", () => {
    const contractPath = resolve(
      "contracts/reliability/performance-capacity-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - status: COMPLETED\n      transitions: []\n      terminal: true",
        "    - status: COMPLETED\n      transitions: [RUNNING]\n      terminal: true",
      );
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted terminal with transitions");
      }
      if (!/performanceRunStateMatrix/.test(combined(result))) {
        throw new Error(`missing run matrix message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });

  it("rejects when invariants closed-set is incomplete", () => {
    const contractPath = resolve(
      "contracts/reliability/performance-capacity-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - numericBoundsRespected\n",
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