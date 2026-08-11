/**
 * apps/web/src/lib/print/export-job.test.ts
 *
 * Unit tests for the asynchronous print/export state machine.
 * Pure functions only — no network, no React.
 */
import { test } from "node:test";
import { strict as assert } from "node:assert";

import {
  TERMINAL_EXPORT_STATUSES,
  assertExportInput,
  assertStatus,
  buildAuditMetadata,
  previewJobId,
  reduceExportJob,
  resolveWatermarkMode,
  type ExportJobInput,
  type ExportJobSnapshot,
} from "./export-job";

const baseInput: ExportJobInput = {
  tenantId: "tenant-1",
  treeId: "tree-1",
  rootPersonId: "p-1",
  scope: "subtree",
  format: "PDF",
  privacy: "PUBLIC",
  includeLiving: true,
  pageBreakBehaviour: "per-generation",
  layout: "A4-portrait",
  ttlSeconds: 3_600,
  hasShareToken: false,
  nodeCount: 250,
};

function idleSnapshot(): ExportJobSnapshot {
  return {
    status: "idle",
    jobId: "",
    idempotencyKey: "00000000-0000-4000-8000-000000000000",
    input: baseInput,
    createdAt: "2026-08-11T12:00:00.000Z",
    updatedAt: "2026-08-11T12:00:00.000Z",
    audit: {
      correlationId: "00000000-0000-4000-8000-000000000000",
      tenantId: "tenant-1",
      treeId: "tree-1",
      rootPersonId: "p-1",
      actorPseudoId: "user-pseudo",
      obligations: [],
      watermark: "tenant-id",
      issuedAt: "2026-08-11T12:00:00.000Z",
    },
    obligations: [],
    signedUrl: null,
    signedUrlExpiresAt: null,
    reasonCode: null,
    resumeToken: null,
    expiredAt: null,
  };
}

test("export-job: assertExportInput accepts a valid input", () => {
  assert.deepEqual(assertExportInput(baseInput), { ok: true });
});

test("export-job: assertExportInput rejects unknown scope / format / privacy", () => {
  assert.notEqual(
    assertExportInput({ ...baseInput, scope: "galaxy" as unknown as "subtree" }).ok,
    true,
  );
  assert.notEqual(assertExportInput({ ...baseInput, format: "svg" as unknown as "PDF" }).ok, true);
  assert.notEqual(
    assertExportInput({ ...baseInput, privacy: "OPEN" as unknown as "PUBLIC" }).ok,
    true,
  );
});

test("export-job: PRIVATE scope must be currentPerson only", () => {
  const tooBroad = assertExportInput({ ...baseInput, privacy: "PRIVATE" });
  assert.equal(tooBroad.ok, false);
  if (!tooBroad.ok) assert.equal(tooBroad.reasonCode, "private-scope-too-broad");
  const narrow = assertExportInput({
    ...baseInput,
    privacy: "PRIVATE",
    scope: "currentPerson",
  });
  assert.deepEqual(narrow, { ok: true });
});

test("export-job: resolveWatermarkMode falls back to privacy-driven default", () => {
  assert.equal(resolveWatermarkMode(baseInput), "tenant-id");
  assert.equal(
    resolveWatermarkMode({ ...baseInput, privacy: "UNLISTED", hasShareToken: true }),
    "token-hash",
  );
  assert.equal(
    resolveWatermarkMode({ ...baseInput, watermark: "tenant-id-and-token-hash" }),
    "tenant-id-and-token-hash",
  );
});

test("export-job: buildAuditMetadata emits deterministic fields", () => {
  const audit = buildAuditMetadata({
    input: baseInput,
    correlationId: "corr-1",
    actorPseudoId: "user-pseudo-1",
    issuedAt: new Date("2026-08-11T12:00:00.000Z"),
  });
  assert.equal(audit.correlationId, "corr-1");
  assert.equal(audit.tenantId, "tenant-1");
  assert.equal(audit.treeId, "tree-1");
  assert.equal(audit.rootPersonId, "p-1");
  assert.equal(audit.actorPseudoId, "user-pseudo-1");
  assert.equal(audit.watermark, "tenant-id");
  assert.equal(audit.issuedAt, "2026-08-11T12:00:00.000Z");
  assert.deepEqual([...audit.obligations], []);
});

test("export-job: idle → queued → running → ready lifecycle", () => {
  const idle = idleSnapshot();
  const submitted = reduceExportJob(idle, {
    kind: "submit",
    jobId: "job-1",
    at: new Date("2026-08-11T12:01:00.000Z"),
  });
  assert.equal(submitted.status, "queued");
  assert.equal(submitted.idempotencyKey, idle.idempotencyKey);

  const polled = reduceExportJob(submitted, {
    kind: "poll",
    status: "running",
    resumeToken: "rt-1",
    at: new Date("2026-08-11T12:02:00.000Z"),
  });
  assert.equal(polled.status, "running");
  if (polled.status === "running") assert.equal(polled.resumeToken, "rt-1");

  const ready = reduceExportJob(polled, {
    kind: "ready",
    signedUrl: "https://minio.genealogy-minio.internal/x?sig=abc",
    ttlSeconds: 600,
    obligations: [
      {
        reasonCode: "LIVING",
        droppedFieldCount: 2,
        appliedAt: "2026-08-11T12:03:00.000Z",
      },
    ],
    at: new Date("2026-08-11T12:03:00.000Z"),
  });
  assert.equal(ready.status, "ready");
  if (ready.status === "ready") {
    assert.equal(ready.signedUrl, "https://minio.genealogy-minio.internal/x?sig=abc");
    assert.equal(ready.signedUrlExpiresAt, "2026-08-11T12:13:00.000Z");
    assert.equal(ready.obligations.length, 1);
    assert.equal(ready.audit.obligations.length, 1);
  }
});

test("export-job: TTL is clamped inside [60s, 24h]", () => {
  const submitted = reduceExportJob(idleSnapshot(), {
    kind: "submit",
    jobId: "job-2",
    at: new Date("2026-08-11T12:01:00.000Z"),
  });
  const ready = reduceExportJob(submitted, {
    kind: "ready",
    signedUrl: "https://cdn.genealogy-platform.com/x?sig=abc",
    ttlSeconds: 10, // below min → must clamp to 60s
    obligations: [],
    at: new Date("2026-08-11T12:01:00.000Z"),
  });
  if (ready.status === "ready") {
    assert.equal(ready.signedUrlExpiresAt, "2026-08-11T12:02:00.000Z");
  }
});

test("export-job: ready → expired transition", () => {
  const submitted = reduceExportJob(idleSnapshot(), {
    kind: "submit",
    jobId: "job-3",
    at: new Date("2026-08-11T12:01:00.000Z"),
  });
  const ready = reduceExportJob(submitted, {
    kind: "ready",
    signedUrl: "https://minio.genealogy-minio.internal/x?sig=xyz",
    ttlSeconds: 600,
    obligations: [],
    at: new Date("2026-08-11T12:02:00.000Z"),
  });
  const expired = reduceExportJob(ready, {
    kind: "expired",
    at: new Date("2026-08-11T12:15:00.000Z"),
  });
  assert.equal(expired.status, "expired");
});

test("export-job: failed transition carries reasonCode", () => {
  const submitted = reduceExportJob(idleSnapshot(), {
    kind: "submit",
    jobId: "job-4",
    at: new Date("2026-08-11T12:01:00.000Z"),
  });
  const failed = reduceExportJob(submitted, {
    kind: "failed",
    reasonCode: "gotenberg-unavailable",
    at: new Date("2026-08-11T12:01:30.000Z"),
  });
  assert.equal(failed.status, "failed");
  if (failed.status === "failed") {
    assert.equal(failed.reasonCode, "gotenberg-unavailable");
  }
});

test("export-job: submit from queued throws (state-machine invariant)", () => {
  const submitted = reduceExportJob(idleSnapshot(), {
    kind: "submit",
    jobId: "job-5",
    at: new Date("2026-08-11T12:01:00.000Z"),
  });
  assert.throws(() =>
    reduceExportJob(submitted, {
      kind: "submit",
      jobId: "job-6",
      at: new Date("2026-08-11T12:02:00.000Z"),
    }),
  );
});

test("export-job: reset returns to idle preserving idempotency key", () => {
  const submitted = reduceExportJob(idleSnapshot(), {
    kind: "submit",
    jobId: "job-7",
    at: new Date("2026-08-11T12:01:00.000Z"),
  });
  const reset = reduceExportJob(submitted, {
    kind: "reset",
    at: new Date("2026-08-11T12:02:00.000Z"),
  });
  assert.equal(reset.status, "idle");
  assert.equal(reset.idempotencyKey, idleSnapshot().idempotencyKey);
});

test("export-job: assertStatus throws on unknown status", () => {
  assert.equal(assertStatus("ready"), "ready");
  assert.throws(() => assertStatus("stranger"));
});

test("export-job: previewJobId is deterministic per second", () => {
  const t1 = new Date("2026-08-11T12:00:00.000Z");
  const t2 = new Date("2026-08-11T12:00:00.500Z");
  assert.equal(previewJobId("corr", t1), previewJobId("corr", t2));
  const t3 = new Date("2026-08-11T12:00:01.000Z");
  assert.notEqual(previewJobId("corr", t1), previewJobId("corr", t3));
});

test("export-job: TERMINAL_EXPORT_STATUSES lists ready / failed / expired", () => {
  assert.deepEqual([...TERMINAL_EXPORT_STATUSES], ["ready", "failed", "expired"]);
});
