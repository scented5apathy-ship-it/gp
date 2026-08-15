/**
 * scripts/__tests__/lint-recovery-rollback-config.test.mjs
 *
 * Unit tests for `scripts/lint-recovery-rollback-config.mjs`
 * (E14.4).
 */
import { describe, it } from "node:test";
import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const SCRIPT = "scripts/lint-recovery-rollback-config.mjs";

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

describe("lint-recovery-rollback-config", () => {
  it("passes when the canonical contract is valid", () => {
    const result = runLinter({});
    if (result.status !== 0) {
      throw new Error(`linter exited ${result.status}, expected 0:\n${combined(result)}`);
    }
    if (!result.stdout.includes("E14.4 summary: OK")) {
      throw new Error(`missing success marker in:\n${result.stdout}`);
    }
  });

  it("rejects when chart mirror drifts", () => {
    const chartPath = resolve(
      "platform/helm/genealogy-platform/files/disaster-recovery/recovery-rollback-policy.yaml",
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

  it("rejects when migrationKinds closed-set is missing an entry", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/recovery-rollback-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - expand_column_add\n",
        "    # removed: - expand_column_add\n",
      );
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted missing migration kind");
      }
      if (!/migrationKinds/.test(combined(result))) {
        throw new Error(`missing migration kind message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });

  it("rejects when preUpgradeChecks is missing an entry", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/recovery-rollback-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - preflight_passed\n",
        "    # removed: - preflight_passed\n",
      );
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted missing pre-check");
      }
      if (!/preUpgradeChecks/.test(combined(result))) {
        throw new Error(`missing pre-check message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });

  it("rejects when invariants closed-set is incomplete", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/recovery-rollback-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - rawDnaBytesNeverInUpgradeBundle\n",
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