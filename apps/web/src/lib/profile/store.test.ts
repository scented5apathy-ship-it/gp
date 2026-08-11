/**
 * `apps/web/src/lib/profile/store.test.ts`
 *
 * Validates the E5.4 person editor state machine:
 *
 *   - load() emits a "loading" → "ready" sequence;
 *   - patchDraft() applies optimistic edits without firing a
 *     fetch;
 *   - commit() sends `If-Match=etag` and transitions to
 *     "saving" → "ready" on 200, "conflict" on 412,
 *     "stale" on 409;
 *   - canEditField() / canAct() mirror the server-side
 *     permission matrix;
 *   - validateQuery() enforces the opaque-id regex;
 *   - validatePatch() refuses oversize biography + identifiers.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  EMPTY_PERSON_SNAPSHOT,
  canAct,
  canEditField,
  validateQuery,
  PersonEditorStore,
  type PersonEditorQuery,
} from "./store";
import {
  type PersonBody,
  type PersonFetcher,
  type PersonPermissions,
  type PersonRawHttpResponse,
} from "@genealogy/api-client";

const PERSON: PersonBody = {
  personId: "00000000-0000-4000-8000-000000000000",
  treeId: "00000000-0000-4000-8000-000000000001",
  version: 7,
  displayName: "Ada Lovelace",
  livingStatus: "DECEASED",
  names: [
    {
      locale: "en",
      script: "Latn",
      parts: { given: "Ada", surname: "Lovelace" },
      isPrimary: true,
    },
  ],
  redaction: { reasonCodes: [], droppedFieldCount: 0 },
};

const PERMISSIONS: PersonPermissions = {
  personId: PERSON.personId,
  treeId: PERSON.treeId,
  fields: [
    { field: "displayName", allowed: true },
    { field: "names", allowed: true },
    { field: "identifiers", allowed: true },
    { field: "birth", allowed: true },
    { field: "death", allowed: true },
    { field: "biography", allowed: false },
    { field: "privacyLevel", allowed: true },
  ],
  actions: [
    { action: "person.view", allowed: true },
    { action: "person.edit", allowed: true },
    { action: "person.delete", allowed: false },
    { action: "person.merge", allowed: false },
    { action: "person.export", allowed: false },
    { action: "person.relink", allowed: false },
  ],
  reasonCodes: ["living_redacted"],
};

interface Call {
  method: keyof PersonFetcher;
  args: unknown;
  response: PersonRawHttpResponse;
}

function makeStub(responses: Call[]): { fetcher: PersonFetcher; calls: Call[] } {
  const calls: Call[] = [];
  let index = 0;
  const fetcher: PersonFetcher = {
    async getPerson(input: Parameters<PersonFetcher["getPerson"]>[0]) {
      const call = responses[index++];
      if (!call) throw new Error("no stub response queued");
      calls.push({ method: "getPerson", args: input, response: call.response });
      return call.response;
    },
    async updatePerson(input: Parameters<PersonFetcher["updatePerson"]>[0]) {
      const call = responses[index++];
      if (!call) throw new Error("no stub response queued");
      calls.push({ method: "updatePerson", args: input, response: call.response });
      return call.response;
    },
    async getPersonTimeline(input: Parameters<PersonFetcher["getPersonTimeline"]>[0]) {
      const call = responses[index++];
      if (!call) throw new Error("no stub response queued");
      calls.push({ method: "getPersonTimeline", args: input, response: call.response });
      return call.response;
    },
    async getPersonPermissions(input: Parameters<PersonFetcher["getPersonPermissions"]>[0]) {
      const call = responses[index++];
      if (!call) throw new Error("no stub response queued");
      calls.push({ method: "getPersonPermissions", args: input, response: call.response });
      return call.response;
    },
    async lookupPlace(input: Parameters<PersonFetcher["lookupPlace"]>[0]) {
      const call = responses[index++];
      if (!call) throw new Error("no stub response queued");
      calls.push({ method: "lookupPlace", args: input, response: call.response });
      return call.response;
    },
  };
  return { fetcher, calls };
}

function ok(body: unknown, headers: Record<string, string> = {}): PersonRawHttpResponse {
  return { status: 200, headers, parsed: body };
}

const QUERY: PersonEditorQuery = {
  treeId: PERSON.treeId,
  personId: PERSON.personId,
};

test("store: load() emits loading → ready + applies permissions", async () => {
  const stub = makeStub([
    { method: "getPerson", args: undefined, response: ok(PERSON, { etag: '"v7"' }) },
    { method: "getPersonPermissions", args: undefined, response: ok(PERMISSIONS) },
  ]);
  const store = new PersonEditorStore(EMPTY_PERSON_SNAPSHOT, { fetcher: stub.fetcher });
  const events: string[] = [];
  store.subscribe((snapshot) => events.push(snapshot.meta.status));
  await store.load(QUERY);
  const snapshot = store.getSnapshot();
  assert.equal(snapshot.meta.status, "ready");
  assert.equal(snapshot.body?.displayName, "Ada Lovelace");
  assert.equal(snapshot.etag, '"v7"');
  assert.equal(snapshot.permissions?.personId, PERSON.personId);
  const deduped = events.filter((status, index) => index === 0 || events[index - 1] !== status);
  assert.deepEqual(deduped, ["idle", "loading", "ready"]);
});

test("store: patchDraft() applies optimistic edits without firing a fetch", async () => {
  const stub = makeStub([
    { method: "getPerson", args: undefined, response: ok(PERSON, { etag: '"v7"' }) },
    { method: "getPersonPermissions", args: undefined, response: ok(PERMISSIONS) },
  ]);
  const store = new PersonEditorStore(EMPTY_PERSON_SNAPSHOT, { fetcher: stub.fetcher });
  await store.load(QUERY);
  store.patchDraft({ displayName: "Augusta Ada King-Noel" });
  const snapshot = store.getSnapshot();
  assert.equal(snapshot.draft.displayName, "Augusta Ada King-Noel");
  assert.equal(stub.calls.length, 2, "patchDraft must not call the fetcher");
});

test("store: commit() sends If-Match and transitions on 200", async () => {
  const updated = { ...PERSON, version: 8, displayName: "Augusta Ada King-Noel" };
  const stub = makeStub([
    { method: "getPerson", args: undefined, response: ok(PERSON, { etag: '"v7"' }) },
    { method: "getPersonPermissions", args: undefined, response: ok(PERMISSIONS) },
    { method: "updatePerson", args: undefined, response: ok(updated, { etag: '"v8"' }) },
  ]);
  const store = new PersonEditorStore(EMPTY_PERSON_SNAPSHOT, { fetcher: stub.fetcher });
  await store.load(QUERY);
  store.patchDraft({ displayName: "Augusta Ada King-Noel" });
  await store.commit();
  const updateCall = stub.calls.find((c) => c.method === "updatePerson");
  assert.ok(updateCall);
  const args = updateCall.args as { ifMatch: string; patch: { displayName: string } };
  assert.equal(args.ifMatch, '"v7"');
  assert.equal(args.patch.displayName, "Augusta Ada King-Noel");
  assert.equal(store.getSnapshot().meta.status, "ready");
  assert.equal(store.getSnapshot().etag, '"v8"');
});

test("store: commit() transitions to 'conflict' on 412 + stores server body", async () => {
  const conflict = { ...PERSON, version: 9, displayName: "Server version" };
  const stub = makeStub([
    { method: "getPerson", args: undefined, response: ok(PERSON, { etag: '"v7"' }) },
    { method: "getPersonPermissions", args: undefined, response: ok(PERMISSIONS) },
    {
      method: "updatePerson",
      args: undefined,
      response: { status: 412, headers: {}, parsed: conflict },
    },
  ]);
  const store = new PersonEditorStore(EMPTY_PERSON_SNAPSHOT, { fetcher: stub.fetcher });
  await store.load(QUERY);
  store.patchDraft({ displayName: "Local edit" });
  await store.commit();
  const snapshot = store.getSnapshot();
  assert.equal(snapshot.meta.status, "conflict");
  assert.equal(snapshot.conflict?.displayName, "Server version");
});

test("store: commit() transitions to 'stale' on 409", async () => {
  const stub = makeStub([
    { method: "getPerson", args: undefined, response: ok(PERSON, { etag: '"v7"' }) },
    { method: "getPersonPermissions", args: undefined, response: ok(PERMISSIONS) },
    {
      method: "updatePerson",
      args: undefined,
      response: { status: 409, headers: {}, parsed: undefined },
    },
  ]);
  const store = new PersonEditorStore(EMPTY_PERSON_SNAPSHOT, { fetcher: stub.fetcher });
  await store.load(QUERY);
  store.patchDraft({ displayName: "x" });
  await store.commit();
  assert.equal(store.getSnapshot().meta.status, "stale");
});

test("store: revert() clears draft + conflict", async () => {
  const stub = makeStub([
    { method: "getPerson", args: undefined, response: ok(PERSON, { etag: '"v7"' }) },
    { method: "getPersonPermissions", args: undefined, response: ok(PERMISSIONS) },
  ]);
  const store = new PersonEditorStore(EMPTY_PERSON_SNAPSHOT, { fetcher: stub.fetcher });
  await store.load(QUERY);
  store.patchDraft({ displayName: "Local edit" });
  store.revert();
  assert.deepEqual(store.getSnapshot().draft, {});
});

test("store: canEditField mirrors server-side permission matrix", () => {
  assert.equal(canEditField(PERMISSIONS, "biography"), false);
  assert.equal(canEditField(PERMISSIONS, "displayName"), true);
  assert.equal(canEditField(null, "displayName"), false);
});

test("store: canAct mirrors server-side action permissions", () => {
  assert.equal(canAct(PERMISSIONS, "person.edit"), true);
  assert.equal(canAct(PERMISSIONS, "person.delete"), false);
  assert.equal(canAct(null, "person.view"), false);
});

test("store: validateQuery refuses invalid ids", () => {
  assert.throws(() => validateQuery({ treeId: "tree 1", personId: "p1" }), /treeId/);
  assert.throws(() => validateQuery({ treeId: "tree-1", personId: "p 1" }), /personId/);
  assert.doesNotThrow(() => validateQuery(QUERY));
});

test("store: commit() refuses without baseline body + etag", async () => {
  const store = new PersonEditorStore(EMPTY_PERSON_SNAPSHOT, {
    fetcher: makeStub([]).fetcher,
  });
  await store.commit();
  assert.equal(store.getSnapshot().meta.status, "error");
  assert.match(store.getSnapshot().meta.lastError ?? "", /baseline body/);
});
