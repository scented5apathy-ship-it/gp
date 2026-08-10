#!/usr/bin/env node
/**
 * scripts/lint-trusted-context.mjs
 *
 * E3.5 deep validator for the trusted tenant context contract
 * under `contracts/trusted-context/` and the platform mirror
 * under `platform/helm/genealogy-platform/files/`. Mirrors the
 * structure of `lint-abac-config.mjs` (E3.4): parse + structural
 * assertions + forbidden-token scan + chart mirror byte-equality.
 *
 * Asserts:
 *   - `contracts/trusted-context/policy.yaml` declares
 *     `spec.policyId: trusted-context/v1`,
 *     `spec.sources.rest.tenantId.from: X-Tenant-Id`,
 *     `spec.sources.grpc.tenantId.from: bffMetadata.x-tenant-id`
 *     with `rejectIfContextFieldSet: true`,
 *     `spec.refuseClientSupplied.rest` + `spec.refuseClientSupplied.grpc`
 *     each contain ≥ 6 / 3 entries (matching the E3.5 contract),
 *     `spec.mtls.mode: STRICT`,
 *     `spec.mtls.expectedPeerPattern` matches the BFF SPIFFE shape,
 *     `spec.reconciliation.membershipStatusRequired: ACTIVE`,
 *     `spec.reconciliation.mismatchResponse.restStatus: 404`,
 *     `spec.reconciliation.mismatchResponse.grpcCode: NOT_FOUND`,
 *     `spec.forwardedHeaders` contains `X-Tenant-Id` + `X-Correlation-Id`
 *       + `X-Idempotency-Key` + `User-Agent`,
 *     `spec.grpcMetadataKeys` declares `tenantId` / `actorId` /
 *       `actorRole` / `correlationId` / `idempotencyKey` /
 *       `userAgent`;
 *   - no literal secret / token / password / DSN in any source-
 *     of-truth file;
 *   - the contract is mirrored byte-identical into
 *     `platform/helm/genealogy-platform/files/trusted-context-policy.yaml`.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration error.
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");
const CONTRACTS_DIR = join(ROOT, "contracts", "trusted-context");
const HELM_FILES_DIR = join(ROOT, "platform", "helm", "genealogy-platform", "files");
const CONTRACT_FILE = "policy.yaml";
const MIRROR_FILE = "trusted-context-policy.yaml";

const REQUIRED_REST_REFUSE_KEYS = [
  "request.body.tenantId",
  "request.body.tenant_id",
  "request.query.tenantId",
  "request.query.tenant_id",
  "request.params.tenantId",
  "request.params.tenant_id",
  "request.body.role",
  "request.body.actor_role",
  "request.body.subject",
  "request.body.actor_id",
];

const REQUIRED_GRPC_REFUSE_KEYS = [
  "message.context.tenant_id",
  "message.context.actor_id",
  "message.context.actor_role",
];

const REQUIRED_FORWARDED_HEADERS = [
  "X-Tenant-Id",
  "X-Correlation-Id",
  "X-Idempotency-Key",
  "User-Agent",
];

const REQUIRED_GRPC_METADATA_KEYS = [
  "tenantId",
  "actorId",
  "actorRole",
  "correlationId",
  "idempotencyKey",
  "userAgent",
];

const FORBIDDEN_LITERALS = [
  /password\s*[:=]\s*['"]?[^'"\s]+/i,
  /token\s*[:=]\s*['"]?eyJ/i,
  /dsn\s*[:=]\s*['"]?jdbc:/i,
];

let violations = 0;
const messages = [];

function fail(msg) {
  violations += 1;
  if (Array.isArray(msg)) {
    messages.push(...msg);
  } else {
    messages.push(msg);
  }
}

function readYaml(path) {
  const raw = readFileSync(path, "utf8");
  try {
    return { doc: YAML.parse(raw), raw };
  } catch (err) {
    fail(`failed to parse ${relative(ROOT, path)}: ${err.message}`);
    return { doc: null, raw };
  }
}

const contractPath = join(CONTRACTS_DIR, CONTRACT_FILE);
if (!existsSync(contractPath)) {
  fail(`missing contract file: ${relative(ROOT, contractPath)}`);
} else {
  const { doc, raw } = readYaml(contractPath);
  const spec = doc?.spec;

  if (spec?.policyId !== "trusted-context/v1") {
    fail(`${CONTRACT_FILE}: spec.policyId must be "trusted-context/v1", got "${spec?.policyId}"`);
  }

  const restTenantId = spec?.sources?.rest?.tenantId;
  if (restTenantId?.from !== "X-Tenant-Id") {
    fail(
      `${CONTRACT_FILE}: spec.sources.rest.tenantId.from must be "X-Tenant-Id", got "${restTenantId?.from}"`,
    );
  }
  if (restTenantId?.reconcileWith !== "membership") {
    fail(`${CONTRACT_FILE}: spec.sources.rest.tenantId.reconcileWith must be "membership"`);
  }
  if (restTenantId?.refuseIfMismatch !== true) {
    fail(`${CONTRACT_FILE}: spec.sources.rest.tenantId.refuseIfMismatch must be true`);
  }

  const restActorId = spec?.sources?.rest?.actorId;
  if (restActorId?.from !== "jwt.sub") {
    fail(`${CONTRACT_FILE}: spec.sources.rest.actorId.from must be "jwt.sub"`);
  }
  if (restActorId?.refuseIfClientSet !== true) {
    fail(`${CONTRACT_FILE}: spec.sources.rest.actorId.refuseIfClientSet must be true`);
  }

  const restActorRole = spec?.sources?.rest?.actorRole;
  if (restActorRole?.from !== "membership.role") {
    fail(`${CONTRACT_FILE}: spec.sources.rest.actorRole.from must be "membership.role"`);
  }
  if (restActorRole?.refuseIfClientSet !== true) {
    fail(`${CONTRACT_FILE}: spec.sources.rest.actorRole.refuseIfClientSet must be true`);
  }

  const grpcTenantId = spec?.sources?.grpc?.tenantId;
  if (grpcTenantId?.from !== "bffMetadata.x-tenant-id") {
    fail(`${CONTRACT_FILE}: spec.sources.grpc.tenantId.from must be "bffMetadata.x-tenant-id"`);
  }
  if (grpcTenantId?.rejectIfContextFieldSet !== true) {
    fail(`${CONTRACT_FILE}: spec.sources.grpc.tenantId.rejectIfContextFieldSet must be true`);
  }

  const grpcActorId = spec?.sources?.grpc?.actorId;
  if (grpcActorId?.from !== "jwt.sub") {
    fail(`${CONTRACT_FILE}: spec.sources.grpc.actorId.from must be "jwt.sub"`);
  }
  if (grpcActorId?.rejectIfContextFieldSet !== true) {
    fail(`${CONTRACT_FILE}: spec.sources.grpc.actorId.rejectIfContextFieldSet must be true`);
  }

  const grpcActorRole = spec?.sources?.grpc?.actorRole;
  if (grpcActorRole?.from !== "bffMetadata.x-actor-role") {
    fail(`${CONTRACT_FILE}: spec.sources.grpc.actorRole.from must be "bffMetadata.x-actor-role"`);
  }
  if (grpcActorRole?.rejectIfContextFieldSet !== true) {
    fail(`${CONTRACT_FILE}: spec.sources.grpc.actorRole.rejectIfContextFieldSet must be true`);
  }

  const grpcCorrelation = spec?.sources?.grpc?.correlationId;
  if (grpcCorrelation?.generateIfAbsent !== true) {
    fail(`${CONTRACT_FILE}: spec.sources.grpc.correlationId.generateIfAbsent must be true`);
  }

  const restRefuse = new Set(
    Array.isArray(spec?.refuseClientSupplied?.rest) ? spec.refuseClientSupplied.rest : [],
  );
  for (const required of REQUIRED_REST_REFUSE_KEYS) {
    if (!restRefuse.has(required)) {
      fail(`${CONTRACT_FILE}: spec.refuseClientSupplied.rest missing ${required}`);
    }
  }

  const grpcRefuse = new Set(
    Array.isArray(spec?.refuseClientSupplied?.grpc) ? spec.refuseClientSupplied.grpc : [],
  );
  for (const required of REQUIRED_GRPC_REFUSE_KEYS) {
    if (!grpcRefuse.has(required)) {
      fail(`${CONTRACT_FILE}: spec.refuseClientSupplied.grpc missing ${required}`);
    }
  }

  if (spec?.mtls?.enabled !== true) {
    fail(`${CONTRACT_FILE}: spec.mtls.enabled must be true`);
  }
  if (spec?.mtls?.mode !== "STRICT") {
    fail(`${CONTRACT_FILE}: spec.mtls.mode must be "STRICT", got "${spec?.mtls?.mode}"`);
  }
  const expectedPattern = spec?.mtls?.expectedPeerPattern;
  if (typeof expectedPattern !== "string" || !expectedPattern.includes("gp-bff")) {
    fail(
      `${CONTRACT_FILE}: spec.mtls.expectedPeerPattern must constrain the SPIFFE peer to gp-bff/sa/...`,
    );
  }

  if (spec?.reconciliation?.membershipStatusRequired !== "ACTIVE") {
    fail(`${CONTRACT_FILE}: spec.reconciliation.membershipStatusRequired must be "ACTIVE"`);
  }
  if (spec?.reconciliation?.mismatchResponse?.restStatus !== 404) {
    fail(
      `${CONTRACT_FILE}: spec.reconciliation.mismatchResponse.restStatus must be 404 (E3.2d DoD — avoid tenant-existence leak)`,
    );
  }
  if (spec?.reconciliation?.mismatchResponse?.grpcCode !== "NOT_FOUND") {
    fail(`${CONTRACT_FILE}: spec.reconciliation.mismatchResponse.grpcCode must be "NOT_FOUND"`);
  }
  if (spec?.reconciliation?.mismatchResponse?.leakAvoidance !== "avoid-tenant-existence-leak") {
    fail(
      `${CONTRACT_FILE}: spec.reconciliation.mismatchResponse.leakAvoidance must be "avoid-tenant-existence-leak"`,
    );
  }

  const forwarded = new Set(Array.isArray(spec?.forwardedHeaders) ? spec.forwardedHeaders : []);
  for (const required of REQUIRED_FORWARDED_HEADERS) {
    if (!forwarded.has(required)) {
      fail(`${CONTRACT_FILE}: spec.forwardedHeaders missing ${required}`);
    }
  }

  const grpcMetaKeys = spec?.grpcMetadataKeys || {};
  for (const required of REQUIRED_GRPC_METADATA_KEYS) {
    if (typeof grpcMetaKeys[required] !== "string" || grpcMetaKeys[required].trim() === "") {
      fail(
        `${CONTRACT_FILE}: spec.grpcMetadataKeys.${required} must declare a non-empty metadata key`,
      );
    }
  }

  for (const pattern of FORBIDDEN_LITERALS) {
    if (pattern.test(raw)) {
      fail(`${CONTRACT_FILE}: forbidden literal detected: ${pattern}`);
    }
  }
}

const mirrorPath = join(HELM_FILES_DIR, MIRROR_FILE);
if (!existsSync(mirrorPath)) {
  fail(`chart mirror missing — expected ${relative(ROOT, mirrorPath)} (E3.5 contract)`);
} else {
  const a = readFileSync(join(CONTRACTS_DIR, CONTRACT_FILE), "utf8");
  const b = readFileSync(mirrorPath, "utf8");
  if (a !== b) {
    fail(
      `chart mirror drift — ${relative(ROOT, join(CONTRACTS_DIR, CONTRACT_FILE))} differs from ${relative(ROOT, mirrorPath)}`,
    );
  }
}

if (violations === 0) {
  console.log(
    "[trusted-context] OK — E3.5 trusted-context source-of-truth files conform to contract",
  );
  process.exit(0);
} else {
  console.error(`[trusted-context] ${violations} violation(s) — see messages above`);
  for (const m of messages) {
    console.error(`  - ${m}`);
  }
  process.exit(1);
}
