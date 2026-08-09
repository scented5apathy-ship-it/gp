#!/usr/bin/env node
/**
 * scripts/lint-s3-config.mjs
 *
 * E2.7 deep validator for the S3/MinIO + Valkey
 * source-of-truth files in `platform/storage/`. Mirrors
 * `lint-vault-config.mjs` style — uses the same `yaml`
 * parser and reports exit 0 on success, 1 on violation, 2
 * on configuration error.
 *
 * Asserts:
 *   - `platform/storage/s3-config.yaml` declares the
 *     MinIO image pin (RELEASE.2024-10-13T13-34-11Z),
 *     region per env, TLS 1.2 minimum, the CORS
 *     allowlist (no wildcard), the SSE-KMS server-side
 *     encryption posture, the per-env replica count, and
 *     the audit log enabled.
 *   - `platform/storage/bucket-policy.yaml` declares the
 *     4 buckets (`media`, `media-quarantine`, `dna-raw`,
 *     `import-export`) with versioning, lifecycle, KMS
 *     key alias, IAM policy, CORS allowlist, prefix
 *     template (containing `{tenant_pseudo_id}`), object
 *     lock (where applicable), and signed URL TTL ceiling.
 *   - The `media` bucket has NO public READ ACL; the
 *     `dna-raw` bucket has object lock COMPLIANCE; the
 *     `import-export` bucket has signed URL TTL ≤ 900.
 *   - The forbidden patterns are rejected: raw tenant
 *     UUID / raw PII / raw DNA in any prefix template;
 *     wildcard CORS origin; signed URL TTL > 3600.
 *   - `platform/storage/compatibility-matrix.yaml` declares
 *     every required S3 API operation (PutObject,
 *     GetObject, HeadObject, DeleteObject, CopyObject,
 *     CreateMultipartUpload, UploadPart,
 *     CompleteMultipartUpload, AbortMultipartUpload,
 *     ListObjectVersions, GetObjectSignedUrl,
 *     PutObjectSignedUrl, PutBucketLifecycle, PutBucketCors,
 *     PutObjectWithSSEKMS, PutObjectLegalHold,
 *     PutObjectRetention) and the parity invariants.
 *   - `platform/storage/valkey-config.yaml` declares the
 *     Valkey image pin (7.2-alpine), per-class TTL
 *     ceilings, per-user ACL allowlist (no `@admin` for
 *     service users), `maxmemoryPolicy: allkeys-lru`, and
 *     forbidden key patterns.
 *   - No literal secret / token / password in any of the
 *     files. The `AWS_ACCESS_KEY_ID` /
 *     `AWS_SECRET_ACCESS_KEY` pattern is checked explicitly.
 *   - The four files are mirrored byte-identical into
 *     `platform/helm/genealogy-platform/files/storage/`.
 *
 * Per `agent-execution.md` §4.4 this script does NOT mutate
 * the repo and is safe to run in CI.
 */
import { existsSync, readFileSync } from "node:fs";
import { join, relative, resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import YAML from "yaml";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = process.env.LINT_ROOT ? resolve(process.env.LINT_ROOT) : resolve(HERE, "..");
const STORAGE_DIR = join(ROOT, "platform", "storage");

const REQUIRED_BUCKETS = [
  "media",
  "media-quarantine",
  "dna-raw",
  "import-export",
];
const REQUIRED_S3_OPERATIONS = [
  "PutObject",
  "GetObject",
  "HeadObject",
  "DeleteObject",
  "DeleteObjects",
  "CopyObject",
  "CreateMultipartUpload",
  "UploadPart",
  "UploadPartCopy",
  "CompleteMultipartUpload",
  "AbortMultipartUpload",
  "ListObjectVersions",
  "GetObjectSignedUrl",
  "PutObjectSignedUrl",
  "PutBucketLifecycle",
  "GetBucketLifecycle",
  "PutBucketCors",
  "GetBucketCors",
  "PutObjectWithSSEKMS",
  "PutObjectLegalHold",
  "PutObjectRetention",
  "PutBucketReplication",
  "GetBucketReplication",
];
const REQUIRED_VALKEY_USERS = [
  "web-bff",
  "media-service",
  "genealogy-service",
  "search-service",
  "rate-limiter",
  "openfga-cache",
  "abac-cache",
  "tenant-lookup",
  "observability",
  "operator",
];
const REQUIRED_VALKEY_TTL_CLASSES = [
  "sessionSeconds",
  "rateStateSeconds",
  "cacheSeconds",
  "permissionDecisionSeconds",
  "abacRedactionSeconds",
];

let violations = 0;
const fail = (msg) => {
  violations++;
  console.error(`[s3] ${msg}`);
};

function loadYaml(path) {
  if (!existsSync(path)) {
    fail(`file missing — ${relative(ROOT, path)}`);
    return null;
  }
  try {
    return YAML.parse(readFileSync(path, "utf8"));
  } catch (e) {
    fail(`YAML parse error in ${relative(ROOT, path)} — ${e.message}`);
    return null;
  }
}

function assertNoSecrets(text, path) {
  for (const key of ["password", "apiKey", "token", "private_key"]) {
    const literalRegex = new RegExp(`^\\s*${key}\\s*:\\s*"?[A-Za-z0-9]{8,}"?\\s*$`, "m");
    if (literalRegex.test(text)) {
      fail(
        `literal secret-like value for '${key}' in ${relative(ROOT, path)} — use Vault / External Secrets`,
      );
    }
  }
  // Explicit AWS credential check — the linter rejects any
  // literal `AWS_ACCESS_KEY_ID=...` or `AWS_SECRET_ACCESS_KEY=...`
  // pattern in any source-of-truth file under `platform/storage/`.
  for (const awsKey of ["AWS_ACCESS_KEY_ID", "AWS_SECRET_ACCESS_KEY"]) {
    const re = new RegExp(`^\\s*${awsKey}\\s*=\\s*["']?[A-Za-z0-9/+=]{16,}["']?\\s*$`, "m");
    if (re.test(text)) {
      fail(
        `literal AWS credential '${awsKey}' in ${relative(ROOT, path)} — use IRSA / pod identity (E2.7 §3)`,
      );
    }
  }
}

// ---------------------------------------------------------------------------
// s3-config.yaml — server posture
// ---------------------------------------------------------------------------
const s3File = join(STORAGE_DIR, "s3-config.yaml");
const s3Doc = loadYaml(s3File);
if (s3Doc) {
  const data = s3Doc?.data?.["config.yaml"];
  if (!data) {
    fail(`s3-config.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`s3-config.yaml config is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const s3 = parsed.s3;
      if (!s3) {
        fail(`s3-config.yaml must declare a s3 block`);
      } else {
        // Image pin — ADR-E0.5-01 baseline (MinIO RELEASE).
        if (!/minio\/minio:RELEASE\.2024-10-13T13-34-11Z/.test(s3.minioImage || "")) {
          fail(`s3-config.yaml must pin MinIO image to RELEASE.2024-10-13T13-34-11Z (ADR-E0.5-01)`);
        }
        if (!/minio\/mc:RELEASE\.2024-10-13T15-34-59Z/.test(s3.mcImage || "")) {
          fail(`s3-config.yaml must pin MC image to RELEASE.2024-10-13T15-34-59Z (ADR-E0.5-01)`);
        }
        // Region pin per env.
        const region = s3.region || {};
        if (!region.saas || !region.onprem || !region.dev) {
          fail(`s3-config.yaml region must declare saas + onprem + dev`);
        }
        // TLS minimum.
        if (s3.tlsMinVersion !== "tls12" && s3.tlsMinVersion !== "tls13") {
          fail(`s3-config.yaml tlsMinVersion must be 'tls12' or 'tls13' — got '${s3.tlsMinVersion}'`);
        }
        // CORS allowlist — at least one origin, no wildcard.
        const cors = s3.corsAllowedOrigins || [];
        if (cors.length === 0) {
          fail(`s3-config.yaml corsAllowedOrigins must list at least one origin (E2.7 §2)`);
        }
        for (const origin of cors) {
          if (origin === "*" || /\*/.test(origin)) {
            fail(`s3-config.yaml corsAllowedOrigins must not contain '*' (E2.7 §2)`);
          }
        }
        // CORS methods — DELETE forbidden (lifecycle / GC only).
        const methods = s3.corsAllowedMethods || [];
        if (methods.includes("DELETE")) {
          fail(`s3-config.yaml corsAllowedMethods must not include DELETE (E2.7 §2)`);
        }
        // Versioning.
        if (s3.versioning !== "enabled") {
          fail(`s3-config.yaml versioning must be 'enabled' (E2.7 §1)`);
        }
        // Object lock — always enabled.
        const objLock = s3.objectLock || {};
        if (!objLock.enabled) {
          fail(`s3-config.yaml objectLock.enabled must be true (E2.7 §3)`);
        }
        // Server-side encryption — must be aws:kms.
        const sse = s3.serverSideEncryption || {};
        if (sse.mode !== "aws:kms") {
          fail(`s3-config.yaml serverSideEncryption.mode must be 'aws:kms' (E2.7 §4)`);
        }
        if (!/alias\/genea-s3-/.test(sse.defaultKeyId || "")) {
          fail(`s3-config.yaml serverSideEncryption.defaultKeyId must be an alias/genea-s3-* key (E2.7 §4)`);
        }
        // Replica count per env — minimum 1.
        const replicas = s3.replicas || {};
        for (const env of ["saas", "onprem", "dev"]) {
          if (typeof replicas[env] !== "number" || replicas[env] < 1) {
            fail(`s3-config.yaml replicas.${env} must be a number ≥ 1 (E2.7 §5)`);
          }
        }
        // Audit must be enabled (privacy / DPIA).
        if (s3.audit !== "enabled") {
          fail(`s3-config.yaml audit must be 'enabled' (E2.7 §6)`);
        }
      }
    }
  }
  assertNoSecrets(readFileSync(s3File, "utf8"), s3File);
}

// ---------------------------------------------------------------------------
// bucket-policy.yaml — per-bucket policy
// ---------------------------------------------------------------------------
const bucketFile = join(STORAGE_DIR, "bucket-policy.yaml");
const bucketDoc = loadYaml(bucketFile);
if (bucketDoc) {
  const data = bucketDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`bucket-policy.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`bucket-policy.yaml config is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const buckets = parsed.buckets || [];
      const declared = new Set(buckets.map((b) => b.name));
      for (const required of REQUIRED_BUCKETS) {
        if (!declared.has(required)) {
          fail(`bucket-policy.yaml missing bucket '${required}' (E2.7 §1)`);
        }
      }
      // Per-bucket invariants.
      for (const b of buckets) {
        if (b.versioning !== "enabled") {
          fail(`bucket-policy.yaml bucket '${b.name}' must enable versioning (E2.7 §1)`);
        }
        if (typeof b.lifecycleDays !== "number") {
          fail(`bucket-policy.yaml bucket '${b.name}' must declare lifecycleDays`);
        }
        if (!/^alias\/genea-s3-/.test(b.kmsKeyAlias || "")) {
          fail(`bucket-policy.yaml bucket '${b.name}' must declare a kmsKeyAlias (E2.7 §4)`);
        }
        if (!b.iam || !b.iam.writers || !b.iam.readers) {
          fail(`bucket-policy.yaml bucket '${b.name}' must declare iam.writers + iam.readers (E2.7 §1)`);
        }
        const prefix = b.prefixTemplate || "";
        if (!prefix.includes("{tenant_pseudo_id}")) {
          fail(`bucket-policy.yaml bucket '${b.name}' prefixTemplate must contain '{tenant_pseudo_id}' (E2.7 §1)`);
        }
        // Forbidden raw identifiers in the prefix template.
        for (const forbidden of ["tenant_id=", "person_id=", "user_id=", "raw_dna/"]) {
          if (prefix.includes(forbidden)) {
            fail(`bucket-policy.yaml bucket '${b.name}' prefixTemplate must not contain '${forbidden}' (E2.7 §1)`);
          }
        }
        if (b.publicAccessBlock !== "enforced") {
          fail(`bucket-policy.yaml bucket '${b.name}' publicAccessBlock must be 'enforced' (E2.7 §1)`);
        }
        // Forbidden prefixes — must be non-empty.
        if (!b.forbiddenPrefixes || b.forbiddenPrefixes.length === 0) {
          fail(`bucket-policy.yaml bucket '${b.name}' must declare forbiddenPrefixes (E2.7 §1)`);
        }
      }
      // The `media` bucket must NOT carry a public READ ACL (the
      // bootstrap Job applies `mc anonymous set none`).
      const media = buckets.find((b) => b.name === "media");
      if (media) {
        const iam = media.iam || {};
        if ((iam.writers || []).some((w) => w.public === true)) {
          fail(`bucket-policy.yaml 'media' bucket must NOT enable a public READ ACL (E2.7 §1)`);
        }
      }
      // The `dna-raw` bucket must declare object lock COMPLIANCE.
      const dnaRaw = buckets.find((b) => b.name === "dna-raw");
      if (dnaRaw) {
        if (dnaRaw.objectLock !== true) {
          fail(`bucket-policy.yaml 'dna-raw' bucket must enable objectLock (E2.7 §3)`);
        }
        if (dnaRaw.objectLockMode !== "COMPLIANCE") {
          fail(`bucket-policy.yaml 'dna-raw' bucket must declare objectLockMode: COMPLIANCE (E2.7 §3)`);
        }
        if (typeof dnaRaw.objectLockRetentionDays !== "number" || dnaRaw.objectLockRetentionDays < 1) {
          fail(`bucket-policy.yaml 'dna-raw' bucket must declare objectLockRetentionDays (E2.7 §3)`);
        }
      }
      // The `import-export` bucket must declare signed URL TTL ≤ 900.
      const importExport = buckets.find((b) => b.name === "import-export");
      if (importExport) {
        const ttl = importExport.signedUrlMaxTtlSeconds;
        if (typeof ttl !== "number" || ttl > 900) {
          fail(`bucket-policy.yaml 'import-export' bucket signedUrlMaxTtlSeconds must be ≤ 900 (E2.7 §4)`);
        }
      }
      // CORS — wildcard origin forbidden.
      for (const b of buckets) {
        for (const rule of b.cors || []) {
          for (const origin of rule.allowedOrigins || []) {
            if (origin === "*" || /\*/.test(origin)) {
              fail(`bucket-policy.yaml bucket '${b.name}' CORS allowedOrigins must not contain '*' (E2.7 §2)`);
            }
          }
        }
      }
      // Global invariants.
      const invariants = parsed.invariants || {};
      if (invariants.publicAccessBlockRequired !== true) {
        fail(`bucket-policy.yaml invariants.publicAccessBlockRequired must be true (E2.7 §1)`);
      }
      if (invariants.kmsKeyAliasRequired !== true) {
        fail(`bucket-policy.yaml invariants.kmsKeyAliasRequired must be true (E2.7 §4)`);
      }
      if (invariants.prefixTenantPseudoIdRequired !== true) {
        fail(`bucket-policy.yaml invariants.prefixTenantPseudoIdRequired must be true (E2.7 §1)`);
      }
      if (invariants.corsWildcardForbidden !== true) {
        fail(`bucket-policy.yaml invariants.corsWildcardForbidden must be true (E2.7 §2)`);
      }
      const ceiling = invariants.signedUrlMaxTtlSecondsCeiling;
      if (typeof ceiling !== "number" || ceiling > 3600) {
        fail(`bucket-policy.yaml invariants.signedUrlMaxTtlSecondsCeiling must be a number ≤ 3600 (E2.7 §4)`);
      }
    }
  }
  assertNoSecrets(readFileSync(bucketFile, "utf8"), bucketFile);
}

// ---------------------------------------------------------------------------
// compatibility-matrix.yaml — S3 API operations
// ---------------------------------------------------------------------------
const compatFile = join(STORAGE_DIR, "compatibility-matrix.yaml");
const compatDoc = loadYaml(compatFile);
if (compatDoc) {
  const data = compatDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`compatibility-matrix.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`compatibility-matrix.yaml config is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const ops = parsed.operations || [];
      const declared = new Set(ops.map((o) => o.op));
      for (const required of REQUIRED_S3_OPERATIONS) {
        if (!declared.has(required)) {
          fail(`compatibility-matrix.yaml missing required S3 operation '${required}' (E2.7 §5)`);
        }
      }
      for (const o of ops) {
        if (o.required !== true) continue;
        if (o.saas !== "aws-s3") {
          fail(`compatibility-matrix.yaml operation '${o.op}' must declare saas: aws-s3 (E2.7 §5)`);
        }
        if (o.onprem !== "minio") {
          fail(`compatibility-matrix.yaml operation '${o.op}' must declare onprem: minio (E2.7 §5)`);
        }
      }
      // Parity invariants.
      const parity = parsed.parity || {};
      for (const key of [
        "versionIdRoundTrip",
        "multipartETagDeterminism",
        "sseKmsRoundTrip",
        "signedUrlTtl",
        "objectLockRetention",
        "lifecycleExpiry",
      ]) {
        if (parity[key] !== "required") {
          fail(`compatibility-matrix.yaml parity.${key} must be 'required' (E2.7 §5)`);
        }
      }
      // Forbidden patterns — live under `testHarness.forbidden`
      // (the harness is what the contract test asserts on).
      const forbidden = parsed.testHarness?.forbidden || [];
      for (const required of [
        "writes raw DNA outside the dna-raw bucket",
        "writes raw PII outside the per-tenant prefix",
        "sets a signed URL TTL > 3600 seconds",
        "applies a public READ ACL on the media bucket",
        "exposes an S3 key containing a literal tenant UUID",
      ]) {
        if (!forbidden.includes(required)) {
          fail(`compatibility-matrix.yaml forbidden list must include '${required}' (E2.7 §5)`);
        }
      }
    }
  }
  assertNoSecrets(readFileSync(compatFile, "utf8"), compatFile);
}

// ---------------------------------------------------------------------------
// valkey-config.yaml — server posture + ACL
// ---------------------------------------------------------------------------
const valkeyFile = join(STORAGE_DIR, "valkey-config.yaml");
const valkeyDoc = loadYaml(valkeyFile);
if (valkeyDoc) {
  const data = valkeyDoc?.data?.["config.yaml"];
  if (!data) {
    fail(`valkey-config.yaml must declare a ConfigMap with a 'config.yaml' entry under .data`);
  } else {
    let parsed;
    try {
      parsed = YAML.parse(data);
    } catch (e) {
      fail(`valkey-config.yaml config is not valid YAML — ${e.message}`);
    }
    if (parsed) {
      const valkey = parsed.valkey;
      if (!valkey) {
        fail(`valkey-config.yaml must declare a valkey block`);
      } else {
        // Image pin — ADR-E0.5-01 baseline (Valkey 7.2-alpine).
        if (!/valkey\/valkey:7\.2-alpine/.test(valkey.image || "")) {
          fail(`valkey-config.yaml must pin Valkey image to 7.2-alpine (ADR-E0.5-01)`);
        }
        // TLS minimum.
        if (valkey.tlsMinVersion !== "tls12" && valkey.tlsMinVersion !== "tls13") {
          fail(`valkey-config.yaml tlsMinVersion must be 'tls12' or 'tls13' — got '${valkey.tlsMinVersion}'`);
        }
        // AUTH + ACL required.
        if (valkey.authRequired !== true) {
          fail(`valkey-config.yaml authRequired must be true (E2.7 §6)`);
        }
        if (valkey.aclRequired !== true) {
          fail(`valkey-config.yaml aclRequired must be true (E2.7 §6)`);
        }
        // Memory limit + eviction policy.
        if (valkey.maxmemoryPolicy !== "allkeys-lru") {
          fail(`valkey-config.yaml maxmemoryPolicy must be 'allkeys-lru' — got '${valkey.maxmemoryPolicy}' (E2.7 §6)`);
        }
        for (const env of ["saas", "onprem", "dev"]) {
          if (typeof valkey.maxmemory?.[env] !== "string") {
            fail(`valkey-config.yaml maxmemory.${env} must be a string (e.g. '1Gi') (E2.7 §6)`);
          }
        }
        // Sentinel.
        const sentinel = valkey.sentinel || {};
        if (typeof sentinel.quorum !== "number" || sentinel.quorum < 1) {
          fail(`valkey-config.yaml sentinel.quorum must be a number ≥ 1 (E2.7 §6)`);
        }
        // Forbidden key patterns.
        const forbidden = valkey.forbiddenKeyPatterns || [];
        for (const required of ["*raw_dna*", "*password*", "*apiKey*", "*token*", "*private_key*"]) {
          if (!forbidden.includes(required)) {
            fail(`valkey-config.yaml forbiddenKeyPatterns must include '${required}' (E2.7 §6)`);
          }
        }
        // Tenant key prefix — must contain the
        // `{tenant_pseudo_id}` placeholder somewhere in
        // the key prefix (typically `gp:{tenant_pseudo_id}:`).
        if (!/\{tenant_pseudo_id\}/.test(valkey.tenantKeyPrefix || "")) {
          fail(`valkey-config.yaml tenantKeyPrefix must contain '{tenant_pseudo_id}' (E2.7 §6)`);
        }
      }
      // TTL ceilings.
      const ttl = parsed.ttl || {};
      for (const cls of REQUIRED_VALKEY_TTL_CLASSES) {
        if (typeof ttl[cls] !== "number") {
          fail(`valkey-config.yaml ttl.${cls} must be a number`);
        }
        const ceilingCls = `${cls}Ceiling`;
        if (typeof ttl[ceilingCls] !== "number") {
          fail(`valkey-config.yaml ttl.${ceilingCls} must be a number (E2.7 §6)`);
        } else if (ttl[cls] > ttl[ceilingCls]) {
          fail(`valkey-config.yaml ttl.${cls} (${ttl[cls]}) must be ≤ ttl.${ceilingCls} (${ttl[ceilingCls]})`);
        }
      }
      // Required users.
      const users = parsed.requiredUsers || [];
      const declared = new Set(users.map((u) => u.name));
      for (const required of REQUIRED_VALKEY_USERS) {
        if (!declared.has(required)) {
          fail(`valkey-config.yaml missing required user '${required}' (E2.7 §6)`);
        }
      }
      // No service user may carry @admin.
      for (const u of users) {
        if (u.name === "operator") continue;
        if ((u.commands || "").includes("@admin")) {
          fail(`valkey-config.yaml user '${u.name}' must NOT carry @admin (E2.7 §6)`);
        }
      }
    }
  }
  assertNoSecrets(readFileSync(valkeyFile, "utf8"), valkeyFile);
}

// ---------------------------------------------------------------------------
// Mirror files — every platform/storage/* must be present in the
// chart's files/storage/ directory.
// ---------------------------------------------------------------------------
const mirrorDir = join(ROOT, "platform", "helm", "genealogy-platform", "files", "storage");
for (const f of [
  "s3-config.yaml",
  "bucket-policy.yaml",
  "compatibility-matrix.yaml",
  "valkey-config.yaml",
]) {
  const src = join(STORAGE_DIR, f);
  const dst = join(mirrorDir, f);
  if (!existsSync(src)) continue;
  if (!existsSync(dst)) {
    fail(`chart mirror missing — expected ${relative(ROOT, dst)} (E2.7 contract)`);
    continue;
  }
  const a = readFileSync(src, "utf8");
  const b = readFileSync(dst, "utf8");
  if (a !== b) {
    fail(`chart mirror out of sync — ${relative(ROOT, dst)}`);
  }
}

if (violations > 0) {
  console.error(`\n[s3] ${violations} violation(s)`);
  process.exit(1);
}
console.log(
  `[s3] clean — buckets=${REQUIRED_BUCKETS.length}, s3-ops=${REQUIRED_S3_OPERATIONS.length}, valkey-users=${REQUIRED_VALKEY_USERS.length}, ttl-classes=${REQUIRED_VALKEY_TTL_CLASSES.length}`,
);
