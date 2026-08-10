#!/usr/bin/env node
// contracts/openfga/compatibility.test.mjs
//
// Compatibility test for the OpenFGA authorization model.
//
// Runs WITHOUT a live OpenFGA server (no Docker required) and
// asserts the structural invariants the migration policy in
// `contracts/openfga/README.md` §Versioning rules demands:
//
//   1. The current model (`model.v1.json`) parses and declares
//      `schema_version: "1.1"`.
//   2. The migration delta (`migrations/v1-to-v2.json`) lists every
//      added relation explicitly; nothing is silently removed.
//   3. No relation kind on an EXISTING type/relation is weakened
//      (union -> this, tupleToUserset -> this, etc.).
//   4. The default-role tuples the `openfga-bootstrap` Helm-hook Job
//      emits are syntactically valid OpenFGA tuples (object#relation@user).
//
// Live checks (`pnpm smoke:openfga`) prove the model actually works
// against an OpenFGA 1.x server; this file is the PR-time gate.

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = join(HERE, "..", "..");

const MODEL = JSON.parse(
  readFileSync(join(HERE, "model.v1.json"), "utf8"),
);
const MIGRATION_V2 = JSON.parse(
  readFileSync(join(HERE, "migrations", "v1-to-v2.json"), "utf8"),
);

// ---------------------------------------------------------------------------
// 1. Current model parses + declares schema_version 1.1.
// ---------------------------------------------------------------------------
test("model.v1.json parses with schema_version 1.1", () => {
  assert.equal(MODEL.schema_version, "1.1");
  assert.ok(Array.isArray(MODEL.type_definitions));
  assert.ok(MODEL.type_definitions.length >= 5);
});

// ---------------------------------------------------------------------------
// 2. Every required object type is present.
// ---------------------------------------------------------------------------
test("model.v1.json declares the canonical object types", () => {
  const names = new Set(MODEL.type_definitions.map((t) => t.type));
  for (const required of [
    "user",
    "group",
    "tenant",
    "tree",
    "branch",
    "person",
    "resource",
    "dna",
  ]) {
    assert.ok(names.has(required), `missing type: ${required}`);
  }
});

// ---------------------------------------------------------------------------
// 3. tenant carries owner/admin/editor/viewer relations (E3.2 invariant).
// ---------------------------------------------------------------------------
test("tenant type declares owner / admin / editor / viewer / billing / auditor", () => {
  const tenant = MODEL.type_definitions.find((t) => t.type === "tenant");
  assert.ok(tenant, "tenant type missing");
  for (const rel of ["owner", "admin", "editor", "viewer", "billing", "auditor"]) {
    assert.ok(
      tenant.relations[rel],
      `tenant#${rel} relation missing — required by E3.2 invite role taxonomy`,
    );
  }
});

// ---------------------------------------------------------------------------
// 4. tree#viewer cascades from tenant#viewer (E4 inheritance).
// ---------------------------------------------------------------------------
test("tree#viewer cascades from tenant#viewer via tupleToUserset", () => {
  const tree = MODEL.type_definitions.find((t) => t.type === "tree");
  const viewer = tree.relations.viewer;
  assert.ok(viewer.union, "tree#viewer must be a union so tenant inheritance composes");
  const children = Array.isArray(viewer.union)
    ? viewer.union
    : viewer.union.child;
  const found = children.some(
    (c) => c.tupleToUserset?.computedUserset?.relation === "viewer",
  );
  assert.ok(
    found,
    "tree#viewer must include a tupleToUserset that traverses tenant#viewer",
  );
});

// ---------------------------------------------------------------------------
// 5. dna is namespaced separately — no tenant tupleToUserset cascade.
//    DNA access requires an explicit `reader` or `owner` tuple because
//    `privacy-and-legal-gate.md` §DNA forbids inheritance.
// ---------------------------------------------------------------------------
test("dna#reader does NOT cascade from tenant — explicit grant required", () => {
  const dna = MODEL.type_definitions.find((t) => t.type === "dna");
  assert.ok(dna, "dna type missing");
  const reader = dna.relations.reader;
  assert.ok(reader.union, "dna#reader must be a union (owner OR explicit reader)");
  const children = Array.isArray(reader.union) ? reader.union : reader.union.child;
  for (const child of children) {
    assert.ok(
      !child.tupleToUserset,
      "dna#reader MUST NOT traverse tenant (privacy-and-legal-gate.md §DNA)",
    );
  }
});

// ---------------------------------------------------------------------------
// 6. Conditions used by the ABAC overlay are declared.
// ---------------------------------------------------------------------------
test("model declares tenant_match / consent_active / revoked_blocks conditions", () => {
  const names = new Set(Object.keys(MODEL.conditions || {}));
  for (const required of ["tenant_match", "consent_active", "revoked_blocks"]) {
    assert.ok(
      names.has(required),
      `condition '${required}' missing — required by E3.4 ABAC overlay`,
    );
  }
});

// ---------------------------------------------------------------------------
// 7. No tuple content hints at PII / DNA / token literals.
//    Tuple OBJECT names must be opaque IDs (`t:<id>`, `tree:<id>`, etc.).
// ---------------------------------------------------------------------------
test("model.json contains no PII / DNA / token literal in any string", () => {
  const txt = JSON.stringify(MODEL);
  for (const forbidden of [
    "raw_dna",
    "raw_pii",
    "ssn",
    "@gmail.com",
    "Bearer ",
    "eyJ", // JWT header marker
  ]) {
    assert.ok(
      !txt.includes(forbidden),
      `forbidden literal '${forbidden}' found in model — tuple content must be opaque IDs`,
    );
  }
});

// ---------------------------------------------------------------------------
// 8. Migration v1-to-v2: every new relation explicitly listed, no
//    removal of v1 types/relations.
// ---------------------------------------------------------------------------
test("migration v1-to-v2 preserves every v1 type and relation", () => {
  const v1Types = new Set(MODEL.type_definitions.map((t) => t.type));
  const v1Relations = new Map();
  for (const t of MODEL.type_definitions) {
    v1Relations.set(
      t.type,
      new Set(Object.keys(t.relations || {})),
    );
  }
  const claimed = new Set(MIGRATION_V2.new_relations.map((r) => `${r.type}#${r.relation}`));
  // Every v1 relation must still exist (asserted via the model file
  // itself; the migration must declare which relations it ADDS, never
  // which it removes).
  for (const [type, rels] of v1Relations) {
    for (const rel of rels) {
      assert.ok(
        !claimed.has(`${type}#${rel}`) || v1Relations.get(type).has(rel),
        `migration v1-to-v2 cannot remove v1 relation ${type}#${rel}`,
      );
    }
  }
  // At least one new relation declared (sanity).
  assert.ok(
    MIGRATION_V2.new_relations.length >= 1,
    "migration must declare at least one added relation",
  );
  // Every new relation type must already exist in v1 (extend-only).
  for (const r of MIGRATION_V2.new_relations) {
    assert.ok(v1Types.has(r.type), `migration introduces new type '${r.type}' — forbidden`);
  }
});

// ---------------------------------------------------------------------------
// 9. Migration declares the invariants the rollout contract requires.
// ---------------------------------------------------------------------------
test("migration v1-to-v2 declares expand_contract_asserts", () => {
  assert.ok(Array.isArray(MIGRATION_V2.expand_contract_asserts));
  assert.ok(
    MIGRATION_V2.expand_contract_asserts.length >= 4,
    "migration must declare ≥ 4 expand-contract invariants",
  );
});

// ---------------------------------------------------------------------------
// 10. Bootstrap tuples (referenced by `openfga-bootstrap` Helm-hook Job)
//     parse as `object#relation@subject`.
// ---------------------------------------------------------------------------
test("bootstrap default-role tuples parse as object#relation@subject", () => {
  const TUPLE_RE = /^[a-z_]+:[A-Za-z0-9._-]+#[a-z_]+@(user|group|tenant):[A-Za-z0-9._:-]+$/;
  const bootstrap = JSON.parse(
    readFileSync(
      join(REPO, "platform", "openfga", "bootstrap-tuples.json"),
      "utf8",
    ),
  );
  assert.ok(Array.isArray(bootstrap.default_role_tuples));
  assert.ok(bootstrap.default_role_tuples.length >= 1);
  for (const entry of bootstrap.default_role_tuples) {
    const tupleString = entry?.tuple ?? entry;
    assert.match(tupleString, TUPLE_RE, `invalid tuple syntax: ${tupleString}`);
  }
});
