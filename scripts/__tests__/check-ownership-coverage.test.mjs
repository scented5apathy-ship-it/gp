/**
 * Unit tests for scripts/check-ownership-coverage.mjs.
 */
import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, writeFileSync, rmSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const SCRIPT = new URL("../check-ownership-coverage.mjs", import.meta.url);

function runOn(cwd) {
  return spawnSync("node", [SCRIPT.pathname], {
    cwd,
    encoding: "utf8",
    env: { ...process.env, OWNERSHIP_ROOT: cwd },
  });
}

const ROOT_OWNERS = `\
# repo-wide owners
/                  @genealogy/platform
/services/tenant/  @genealogy/identity
`;

const GH_OWNERS = `\
# GitHub format
/                  @genealogy/platform
/services/tenant/  @genealogy/identity
`;

const TEAMS_YAML = `\
teams:
  - slug: @genealogy/platform
  - slug: @genealogy/identity
`;

describe("check-ownership-coverage", () => {
  it("passes with all required files", () => {
    const cwd = mkdtempSync(join(tmpdir(), "own-ok-"));
    try {
      writeFileSync(join(cwd, "OWNERS"), ROOT_OWNERS);
      mkdirSync(join(cwd, ".github"));
      writeFileSync(join(cwd, ".github", "CODEOWNERS"), GH_OWNERS);
      mkdirSync(join(cwd, "config"));
      writeFileSync(join(cwd, "config", "teams.yaml"), TEAMS_YAML);
      mkdirSync(join(cwd, "services", "tenant"), { recursive: true });
      writeFileSync(
        join(cwd, "services", "tenant", "OWNERS"),
        "# primary owner\n@genealogy/identity @genealogy/platform\n",
      );
      const res = runOn(cwd);
      assert.equal(res.status, 0, res.stderr);
    } finally {
      rmSync(cwd, { recursive: true, force: true });
    }
  });

  it("fails when service is missing OWNERS", () => {
    const cwd = mkdtempSync(join(tmpdir(), "own-missing-"));
    try {
      writeFileSync(join(cwd, "OWNERS"), ROOT_OWNERS);
      mkdirSync(join(cwd, ".github"));
      writeFileSync(join(cwd, ".github", "CODEOWNERS"), GH_OWNERS);
      mkdirSync(join(cwd, "config"));
      writeFileSync(join(cwd, "config", "teams.yaml"), TEAMS_YAML);
      mkdirSync(join(cwd, "services", "tenant"), { recursive: true });
      const res = runOn(cwd);
      assert.equal(res.status, 1);
      assert.match(res.stderr, /missing OWNERS/);
    } finally {
      rmSync(cwd, { recursive: true, force: true });
    }
  });

  it("fails when per-directory OWNERS lacks secondary", () => {
    const cwd = mkdtempSync(join(tmpdir(), "own-secondary-"));
    try {
      writeFileSync(join(cwd, "OWNERS"), ROOT_OWNERS);
      mkdirSync(join(cwd, ".github"));
      writeFileSync(join(cwd, ".github", "CODEOWNERS"), GH_OWNERS);
      mkdirSync(join(cwd, "config"));
      writeFileSync(join(cwd, "config", "teams.yaml"), TEAMS_YAML);
      mkdirSync(join(cwd, "services", "tenant"), { recursive: true });
      writeFileSync(
        join(cwd, "services", "tenant", "OWNERS"),
        "# primary owner\n@genealogy/identity\n",
      );
      const res = runOn(cwd);
      assert.equal(res.status, 1);
      assert.match(res.stderr, /secondary owner/);
    } finally {
      rmSync(cwd, { recursive: true, force: true });
    }
  });
});