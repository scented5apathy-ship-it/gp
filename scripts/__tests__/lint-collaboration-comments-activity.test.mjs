/**
 * Unit tests for `scripts/lint-collaboration-comments-activity.mjs`.
 * Mirrors the structure of `lint-collaboration-mixed-policy.test.mjs`
 * (E6.3).
 *
 * Run with `node --test scripts/__tests__/lint-collaboration-comments-activity.test.mjs`.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";
import { copyFileSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = dirname(dirname(HERE));
const LINTER = join(ROOT, "scripts", "lint-collaboration-comments-activity.mjs");
const CONTRACT = "contracts/collaboration/comments-activity-policy.yaml";
const MIRROR = "platform/helm/genealogy-platform/files/collaboration-comments-activity-policy.yaml";

function copyFixture(tmp) {
  const contractDst = join(tmp, CONTRACT);
  const mirrorDst = join(tmp, MIRROR);
  mkdirSync(dirname(contractDst), { recursive: true });
  mkdirSync(dirname(mirrorDst), { recursive: true });
  copyFileSync(join(ROOT, CONTRACT), contractDst);
  copyFileSync(join(ROOT, MIRROR), mirrorDst);
}

function runLinter(tmp, mutation) {
  if (mutation) mutation(tmp);
  const r = spawnSync(process.execPath, [LINTER], {
    cwd: tmp,
    env: { ...process.env, LINT_ROOT: tmp },
    encoding: "utf8",
  });
  return { code: r.status, stdout: r.stdout || "", stderr: r.stderr || "" };
}

function replaceInBoth(work, find, replace) {
  const sp = join(work, CONTRACT);
  const mp = join(work, MIRROR);
  const mutated = readFileSync(sp, "utf8").replace(find, replace);
  writeFileSync(sp, mutated);
  writeFileSync(mp, mutated);
}

test("comments-activity: clean fixture exits 0", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-comments-clean-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp);
    assert.equal(r.code, 0, `stderr=${r.stderr}`);
    assert.match(r.stdout, /OK/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("comments-activity: missing ACTIVE commentStatus fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-comments-status-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "ACTIVE,", "ACTIVE_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.commentStatuses missing required value 'ACTIVE'/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("comments-activity: missing USER mentionTargetKind fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-comments-mention-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "USER,", "USER_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.mentionTargetKinds missing required value 'USER'/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("comments-activity: missing PROPOSAL watchScope fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-comments-watchscope-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "PROPOSAL,", "PROPOSAL_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.watchScopes missing required value 'PROPOSAL'/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("comments-activity: missing WATCHER assignmentRole fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-comments-assignrole-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "WATCHER,", "WATCHER_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.assignmentRoles missing required value 'WATCHER'/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("comments-activity: missing COMMENT_CREATED activityKind fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-comments-actkind-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "COMMENT_CREATED,", "COMMENT_CREATED_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.activityKinds missing required value 'COMMENT_CREATED'/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("comments-activity: missing DELIVERED notificationOutcome fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-comments-notifout-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "[DELIVERED, DROPPED,", "[DELIVERED_REMOVED, DROPPED,");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.notificationOutcomes missing required value 'DELIVERED'/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("comments-activity: missing LIVING_MINOR redactionReason fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-comments-redact-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(w, "LIVING_MINOR,", "LIVING_MINOR_REMOVED,");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /spec\.redactionReasons missing required value 'LIVING_MINOR'/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("comments-activity: activityFeedSnapshotRawPayloadAllowed toggle fails when enabled", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-comments-snapshot-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  activityFeedSnapshotRawPayloadAllowed: false\n",
        "  activityFeedSnapshotRawPayloadAllowed: true\n",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /activityFeedSnapshotRawPayloadAllowed must equal false/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("comments-activity: notificationHookNeverCopyRawPayload toggle fails when disabled", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-comments-notifno-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      replaceInBoth(
        w,
        "  notificationHookNeverCopyRawPayload: true\n",
        "  notificationHookNeverCopyRawPayload: false\n",
      );
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /notificationHookNeverCopyRawPayload must equal true/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});

test("comments-activity: chart mirror drift fails", () => {
  const tmp = mkdtempSync(join(tmpdir(), "collab-comments-mirror-"));
  try {
    copyFixture(tmp);
    const r = runLinter(tmp, (w) => {
      const mp = join(w, MIRROR);
      writeFileSync(mp, readFileSync(mp, "utf8") + "\n# drift\n");
    });
    assert.equal(r.code, 1);
    assert.match(r.stderr, /chart mirror/);
  } finally {
    rmSync(tmp, { recursive: true, force: true });
  }
});
