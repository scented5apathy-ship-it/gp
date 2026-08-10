#!/usr/bin/env node
/**
 * scripts/__tests__/lint-abac-config.test.mjs
 *
 * Unit tests for `scripts/lint-abac-config.mjs`. Each test uses
 * a temp directory that contains a copy of the canonical
 * `contracts/abac/` fixtures; mutations are applied in place so
 * the linter exits non-zero on a regression.
 *
 * Mirrors the structure of `scripts/__tests__/lint-openfga-config.test.mjs`
 * (E3.3). Run with `node --test scripts/__tests__/lint-abac-config.test.mjs`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import { mkdtempSync, mkdirSync, copyFileSync, rmSync, writeFileSync, readFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname, relative } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const LINTER = join(ROOT, "scripts", "lint-abac-config.mjs");

function copyFixtureContracts(tmp) {
    const contractsDst = join(tmp, "contracts", "abac");
    const mirrorDst = join(tmp, "platform", "helm", "genealogy-platform", "files");
    mkdirSync(contractsDst, { recursive: true });
    mkdirSync(mirrorDst, { recursive: true });
    for (const file of ["policy.yaml", "cache.yaml", "redaction.yaml"]) {
        copyFileSync(join(ROOT, "contracts", "abac", file),
                join(contractsDst, file));
        copyFileSync(join(ROOT, "platform", "helm", "genealogy-platform", "files",
                "abac-" + file),
                join(mirrorDst, "abac-" + file));
    }
}

function runLinter(tmp, mutation) {
    if (mutation) {
        mutation(tmp);
    }
    const result = spawnSync(process.execPath, [LINTER], {
        cwd: tmp,
        env: { ...process.env, LINT_ROOT: tmp },
        encoding: "utf8",
    });
    return {
        code: result.status,
        stdout: result.stdout || "",
        stderr: result.stderr || "",
    };
}

function makeTemp() {
    const tmp = mkdtempSync(join(tmpdir(), "abac-lint-"));
    copyFixtureContracts(tmp);
    return tmp;
}

test("clean fixture passes the linter", () => {
    const tmp = makeTemp();
    try {
        const res = runLinter(tmp);
        assert.equal(res.code, 0, `expected 0, got ${res.code}: ${res.stderr}`);
        assert.match(res.stdout, /OK — E3\.4 ABAC source-of-truth files conform to contract/);
    } finally {
        rmSync(tmp, { recursive: true, force: true });
    }
});

test("policy.yaml with dropped engineId is rejected", () => {
    const tmp = makeTemp();
    try {
        const res = runLinter(tmp, (dir) => {
            const path = join(dir, "contracts", "abac", "policy.yaml");
            const raw = readFileSync(path, "utf8");
            const mutated = raw.replaceAll("default-abac/v1", "default-abac/v2");
            writeFileSync(path, mutated);
            const mirror = join(dir, "platform", "helm", "genealogy-platform",
                    "files", "abac-policy.yaml");
            writeFileSync(mirror, mutated);
        });
        assert.equal(res.code, 1);
        assert.match(res.stderr, /spec\.engineId/);
    } finally {
        rmSync(tmp, { recursive: true, force: true });
    }
});

test("cache.yaml with ttlOnlyForbidden=false is rejected", () => {
    const tmp = makeTemp();
    try {
        const res = runLinter(tmp, (dir) => {
            const path = join(dir, "contracts", "abac", "cache.yaml");
            const raw = readFileSync(path, "utf8");
            const mutated = raw.replace("ttlOnlyForbidden: true",
                    "ttlOnlyForbidden: false");
            writeFileSync(path, mutated);
            writeFileSync(join(dir, "platform", "helm", "genealogy-platform",
                    "files", "abac-cache.yaml"), mutated);
        });
        assert.equal(res.code, 1);
        assert.match(res.stderr, /ttlOnlyForbidden/);
    } finally {
        rmSync(tmp, { recursive: true, force: true });
    }
});

test("redaction.yaml without rawDna is rejected", () => {
    const tmp = makeTemp();
    try {
        const res = runLinter(tmp, (dir) => {
            const path = join(dir, "contracts", "abac", "redaction.yaml");
            const raw = readFileSync(path, "utf8");
            // Strip `rawDna` + the preceding newline. The list has
            // `rawDna` first so removing it leaves `raw_dna` in a
            // state that re-parses cleanly.
            const mutated = raw.replace("    - rawDna\n", "    - raw_dna\n");
            writeFileSync(path, mutated);
            writeFileSync(join(dir, "platform", "helm", "genealogy-platform",
                    "files", "abac-redaction.yaml"), mutated);
        });
        assert.equal(res.code, 1);
        assert.match(res.stderr, /denyKeys missing rawDna/);
    } finally {
        rmSync(tmp, { recursive: true, force: true });
    }
});

test("chart mirror drift is rejected", () => {
    const tmp = makeTemp();
    try {
        const res = runLinter(tmp, (dir) => {
            const mirror = join(dir, "platform", "helm", "genealogy-platform",
                    "files", "abac-policy.yaml");
            writeFileSync(mirror, "# drifted\n");
        });
        assert.equal(res.code, 1);
        assert.match(res.stderr, /chart mirror drift/);
    } finally {
        rmSync(tmp, { recursive: true, force: true });
    }
});

test("missing contract file is rejected", () => {
    const tmp = makeTemp();
    try {
        const res = runLinter(tmp, (dir) => {
            rmSync(join(dir, "contracts", "abac", "redaction.yaml"));
        });
        assert.equal(res.code, 1);
        assert.match(res.stderr, /missing contract file/);
    } finally {
        rmSync(tmp, { recursive: true, force: true });
    }
});
