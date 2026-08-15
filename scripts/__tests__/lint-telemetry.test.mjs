/**
 * scripts/__tests__/lint-telemetry.test.mjs
 *
 * Unit tests for `scripts/lint-telemetry.mjs` (E13.1).
 */
import { describe, it } from "node:test";
import { spawnSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

const SCRIPT = "scripts/lint-telemetry.mjs";

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

describe("lint-telemetry", () => {
  it("passes when the canonical contract is valid", () => {
    const result = runLinter({});
    if (result.status !== 0) {
      console.error(result.stdout);
      console.error(result.stderr);
      throw new Error(`linter exited ${result.status}, expected 0`);
    }
    if (!result.stdout.includes("E13.1 summary: OK")) {
      throw new Error(`missing success marker in:\n${result.stdout}`);
    }
  });

  it("rejects when the contract mirror drifts from the source", () => {
    const chartPath = resolve(
      "platform/helm/genealogy-platform/files/reliability/telemetry-policy.yaml",
    );
    const backup = readFileSync(chartPath, "utf8");
    try {
      writeFileSync(chartPath, backup + "\n# drift\n");
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted drift, expected failure");
      }
      if (!/byte-identity/.test(combined(result))) {
        throw new Error(`missing byte-identity message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(chartPath, backup);
    }
  });

  it("rejects when the redaction regexes are missing", () => {
    const contractPath = resolve(
      "contracts/reliability/telemetry-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const stripped = backup.replace(
        /    - name: rawDnaMarker\n      regex: "[^"]+"\n/,
        "",
      );
      writeFileSync(contractPath, stripped);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted missing redaction pattern");
      }
      if (!/telemetryRedactionPatterns/.test(combined(result))) {
        throw new Error(`missing violation message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });

  it("rejects when telemetryStateMatrix terminal status has transitions", () => {
    const contractPath = resolve(
      "contracts/reliability/telemetry-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace(
        "    - status: SHUTDOWN\n      transitions: []\n      terminal: true",
        "    - status: SHUTDOWN\n      transitions: [READY]\n      terminal: true",
      );
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted terminal with transitions");
      }
      if (!/telemetryStateMatrix/.test(combined(result))) {
        throw new Error(`missing state matrix message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });

  it("rejects when forbiddenKeywords closed-set is incomplete", () => {
    const contractPath = resolve(
      "contracts/reliability/telemetry-policy.yaml",
    );
    const backup = readFileSync(contractPath, "utf8");
    try {
      const tampered = backup.replace("    - skip_redaction\n", "");
      writeFileSync(contractPath, tampered);
      const result = runLinter({});
      if (result.status === 0) {
        throw new Error("linter accepted missing forbidden keyword");
      }
      if (!/forbiddenKeywords/.test(combined(result))) {
        throw new Error(`missing forbidden keyword message:\n${combined(result)}`);
      }
    } finally {
      writeFileSync(contractPath, backup);
    }
  });
});