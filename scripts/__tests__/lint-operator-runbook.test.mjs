/**
 * scripts/__tests__/lint-operator-runbook.test.mjs
 *
 * Unit tests for `scripts/lint-operator-runbook.mjs`
 * (E14.5).
 */
import { describe, it } from "node:test";
import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const SCRIPT = "scripts/lint-operator-runbook.mjs";

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

describe("lint-operator-runbook", () => {
  it("passes when the canonical contract is valid", () => {
    const result = runLinter({});
    if (result.status !== 0) {
      throw new Error(`linter exited ${result.status}, expected 0:\n${combined(result)}`);
    }
    if (!result.stdout.includes("E14.5 summary: OK")) {
      throw new Error(`missing success marker in:\n${result.stdout}`);
    }
  });

  it("rejects when chart mirror drifts", () => {
    const chartPath = resolve(
      "platform/helm/genealogy-platform/files/disaster-recovery/operator-runbook-policy.yaml",
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

  it("rejects when mandatoryProcedures closed-set is missing an entry", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/operator-runbook-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - install\n",
        "    # removed: - install\n",
      );
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted missing procedure");
      }
      if (!/mandatoryProcedures/.test(combined(result))) {
        throw new Error(`missing procedure message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });

  it("rejects when supportBundleRedactions is missing an entry", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/operator-runbook-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - redact_tree_viewer_bypass\n",
        "    # removed: - redact_tree_viewer_bypass\n",
      );
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted missing redaction");
      }
      if (!/supportBundleRedactions/.test(combined(result))) {
        throw new Error(`missing redaction message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });

  it("rejects when invariants closed-set is incomplete", () => {
    const contractPath = resolve(
      "contracts/disaster-recovery/operator-runbook-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - sharedAdminPasswordNeverInSupportBundle\n",
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