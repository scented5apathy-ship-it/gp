/**
 * scripts/__tests__/lint-onprem-bundle.test.mjs
 *
 * Unit tests for `scripts/lint-onprem-bundle.mjs` (E14.3).
 */
import { describe, it } from "node:test";
import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const SCRIPT = "scripts/lint-onprem-bundle.mjs";

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

describe("lint-onprem-bundle", () => {
  it("passes when the canonical contract is valid", () => {
    const result = runLinter({});
    if (result.status !== 0) {
      throw new Error(`linter exited ${result.status}, expected 0:\n${combined(result)}`);
    }
    if (!result.stdout.includes("E14.3 summary: OK")) {
      throw new Error(`missing success marker in:\n${result.stdout}`);
    }
  });

  it("rejects when chart mirror drifts", () => {
    const chartPath = resolve(
      "platform/helm/genealogy-platform/files/disaster-recovery/onprem-bundle-policy.yaml",
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

  it("rejects when registryMirrors closed-set is missing an entry", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/onprem-bundle-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - quay.io/genealogy\n",
        "    # removed: - quay.io/genealogy\n",
      );
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted missing registry mirror");
      }
      if (!/registryMirrors/.test(combined(result))) {
        throw new Error(`missing registry mirror message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });

  it("rejects when compatibility matrix is missing a component", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/onprem-bundle-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - component: kubernetes\n",
        "    # removed: - component: kubernetes\n",
      );
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted missing matrix component");
      }
      if (!/compatibilityMatrix/.test(combined(result))) {
        throw new Error(`missing matrix message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });

  it("rejects when invariants closed-set is incomplete", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/onprem-bundle-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - helmChartVersioningFollowsSemver\n",
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