#!/usr/bin/env node
/**
 * Unit tests for the E3.1 Keycloak config-as-code deep
 * validator (`scripts/lint-keycloak-config.mjs`).
 *
 * The fixture copies the five source-of-truth + mirror files
 * into a temp tree, then mutates one invariant per test. The
 * clean fixture must exit 0; every mutation must exit 1 with
 * a recognizable violation message.
 */
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  mkdtempSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname } from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const SCRIPT = join(ROOT, "scripts", "lint-keycloak-config.mjs");
const FILES = [
  "realm-strategy.yaml",
  "realm-export.yaml",
  "client-configs.yaml",
  "federation.yaml",
  "key-rotation.yaml",
];

function runScript(root) {
  return spawnSync(process.execPath, [SCRIPT], {
    cwd: ROOT,
    env: { ...process.env, LINT_ROOT: root },
    encoding: "utf8",
  });
}

function makeFixture() {
  const dir = mkdtempSync(join(tmpdir(), "keycloak-lint-"));
  const srcDir = join(dir, "platform", "keycloak");
  const mirrorDir = join(dir, "platform", "helm", "genealogy-platform", "files", "keycloak");
  mkdirSync(srcDir, { recursive: true });
  mkdirSync(mirrorDir, { recursive: true });
  for (const f of FILES) {
    const text = readFileSync(join(ROOT, "platform", "keycloak", f), "utf8");
    writeFileSync(join(srcDir, f), text);
    writeFileSync(join(mirrorDir, f), text);
  }
  return dir;
}

function mutate(dir, file, replacement) {
  const src = join(dir, "platform", "keycloak", file);
  const mirror = join(dir, "platform", "helm", "genealogy-platform", "files", "keycloak", file);
  const text = readFileSync(src, "utf8");
  const next = replacement(text);
  writeFileSync(src, next);
  writeFileSync(mirror, next);
}

test("keycloak linter: clean fixture passes", () => {
  const dir = makeFixture();
  try {
    const proc = runScript(dir);
    assert.equal(
      proc.status,
      0,
      `expected exit 0, got ${proc.status}\nstdout=${proc.stdout}\nstderr=${proc.stderr}`,
    );
    assert.match(proc.stdout, /E3\.1 Keycloak source-of-truth files conform to contract/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("keycloak linter: rejects realm topology drift", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "realm-strategy.yaml", (text) =>
      text.replace("realmTopology: realm-per-tenant-group", "realmTopology: realm-per-tenant"),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /realmTopology must be 'realm-per-tenant-group'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("keycloak linter: rejects custom SPI provider", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "realm-strategy.yaml", (text) =>
      text.replace("customSpiAllowed: false", "customSpiAllowed: true"),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /customSpiAllowed must be false/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("keycloak linter: rejects public registration", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "realm-export.yaml", (text) =>
      text.replace("registrationAllowed: false", "registrationAllowed: true"),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /registrationAllowed must be false/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("keycloak linter: rejects password grant", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "realm-export.yaml", (text) =>
      text.replace(
        "requirement: DISABLED\n          - name: direct-grant-validate-password",
        "requirement: REQUIRED\n          - name: direct-grant-validate-password",
      ),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /direct-grant.*DISABLED/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("keycloak linter: rejects missing PKCE S256", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "client-configs.yaml", (text) =>
      text.replace(
        "clientId: web-app\n        name: Genealogy Web App",
        "clientId: web-app\n        name: Genealogy Web App",
      ).replace(
        /clientId: web-app[\s\S]*?pkce\.code\.challenge\.method: S256/,
        (match) => match.replace("pkce.code.challenge.method: S256", "pkce.code.challenge.method: plain"),
      ),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /pkce\.code\.challenge\.method = S256/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("keycloak linter: rejects missing mandatory client", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "client-configs.yaml", (text) =>
      text.replace("clientId: web-app", "clientId: web-app-removed"),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /missing mandatory client 'web-app'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("keycloak linter: rejects SAML provider without deprecated marker", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "federation.yaml", (text) =>
      text.replace(
        /alias: okta-saml[\s\S]*?deprecatedPath: true/,
        (match) => match.replace("deprecatedPath: true", "deprecatedPath: false"),
      ),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /SAML provider.*deprecatedPath: true/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("keycloak linter: rejects signing-key rotation outside policy", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "key-rotation.yaml", (text) =>
      text.replace("rotationDays: 90", "rotationDays: 730"),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /realmSigningKey\.rotationDays must be 30-365/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("keycloak linter: rejects JWKS algorithm none", () => {
  const dir = makeFixture();
  try {
    mutate(dir, "key-rotation.yaml", (text) =>
      text.replace(
        "jwksAlgorithmsAllowed:\n        - RS256",
        "jwksAlgorithmsAllowed:\n        - none\n        - RS256",
      ),
    );
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /jwksAlgorithmsAllowed MUST NOT include 'none'/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("keycloak linter: rejects mirror drift", () => {
  const dir = makeFixture();
  try {
    const mirror = join(dir, "platform", "helm", "genealogy-platform", "files", "keycloak", "realm-export.yaml");
    writeFileSync(mirror, readFileSync(mirror, "utf8") + "\n# drift\n");
    const proc = runScript(dir);
    assert.equal(proc.status, 1);
    assert.match(proc.stderr, /chart mirror drift/);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
