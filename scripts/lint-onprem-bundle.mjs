#!/usr/bin/env node
/**
 * scripts/lint-onprem-bundle.mjs
 *
 * E14.3 deep validator for the on-premise bundle contract
 * at `contracts/disaster-recovery/onprem-bundle-policy.yaml`
 * and the platform mirror at
 * `platform/helm/genealogy-platform/files/disaster-recovery/
 *  onprem-bundle-policy.yaml`.
 *
 * Asserts:
 *   - 2 registry mirrors (quay.io/genealogy,
 *     customer-internal-registry);
 *   - 4 Kubernetes minor versions, 3 PostgreSQL majors,
 *     2 Kafka versions, 3 object stores, 3 Keycloak
 *     versions, 2 OpenFGA versions, 3 Temporal versions,
 *     3 Vault versions, 2 Flagsmith versions;
 *   - 9 compatibility matrix rows;
 *   - 14 required + 3 optional Helm values keys;
 *   - 2 SBOM formats + 1 signature kind + 1 attestation +
 *     4 image annotations;
 *   - 7 preflight checks;
 *   - 6 air-gap rules;
 *   - 1 state matrix (bundleStateMatrix initial STAGED,
 *     8 statuses incl. 1 terminal);
 *   - 14 numeric bounds, 16 invariants, 8 capability
 *     boundaries, 32 forbidden keywords, 5 runtime
 *     helpers;
 *   - byte-identity between contract file and helm chart
 *     mirror.
 *
 * Exits 0 on success, 1 on violation, 2 on configuration
 * error.
 */
import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";
import { loadYaml, asArray, assertClosedSet, assertStateMatrix } from "./lint-yaml.mjs";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT = process.env.LINT_ROOT
  ? resolve(process.env.LINT_ROOT)
  : resolve(__dirname, "..");

const CONTRACT = join(
  ROOT,
  "contracts/disaster-recovery/onprem-bundle-policy.yaml",
);
const CHART_FILE = join(
  ROOT,
  "platform/helm/genealogy-platform/files/disaster-recovery/onprem-bundle-policy.yaml",
);

const REQUIRED_REGISTRY_MIRRORS = [
  "quay.io/genealogy", "customer-internal-registry",
];
const REQUIRED_K8S = ["1.29", "1.30", "1.31", "1.32"];
const REQUIRED_PG = ["15", "16", "17"];
const REQUIRED_KAFKA = ["3.7", "3.8"];
const REQUIRED_OBJ = ["aws_s3", "minio_2024", "minio_2025"];
const REQUIRED_KEYCLOAK = ["24.0", "25.0", "26.0"];
const REQUIRED_OPENFGA = ["1.8", "1.9"];
const REQUIRED_TEMPORAL = ["1.22", "1.23", "1.24"];
const REQUIRED_VAULT = ["1.15", "1.16", "1.17"];
const REQUIRED_FLAGSMITH = ["2.4", "2.5"];
const REQUIRED_COMPONENTS = [
  "kubernetes", "postgresql", "kafka", "object_storage",
  "keycloak", "openfga", "temporal", "vault", "flagsmith",
];
const REQUIRED_SBOM_FORMATS = ["cyclonedx_1_5", "spdx_2_3"];
const REQUIRED_SIGNATURES = ["cosign"];
const REQUIRED_ATTESTATIONS = ["slsa_provenance_v1"];
const REQUIRED_IMAGE_ANNOTATIONS = [
  "org.opencontainers.image.source",
  "org.opencontainers.image.revision",
  "org.opencontainers.image.created",
  "org.opencontainers.image.licenses",
];
const REQUIRED_AIRGAP_RULES = [
  "allImagesInBundle", "sbomInBundle", "signaturesInBundle",
  "helmChartsInBundle", "vendorLicensesInBundle",
  "noRuntimeInternetCall",
];
const REQUIRED_HELM_REQUIRED_KEYS = [
  "clusterName", "environment", "registryMirror", "airGapEnabled",
  "cosignPublicKey", "telemetrySink", "tenantDatabaseHost",
  "kafkaBootstrapServers", "objectStoreEndpoint", "keycloakIssuerUrl",
  "openfgaApiUrl", "temporalFrontendUrl", "vaultAddress",
  "flagsmithApiUrl",
];
const REQUIRED_HELM_OPTIONAL_KEYS = [
  "extraAnnotations", "extraLabels", "podDisruptionBudgetOverride",
];
const REQUIRED_BUNDLE_STATUSES = [
  "STAGED", "PREFLIGHT_RUNNING", "VERIFIED",
  "INSTALLING", "INSTALLED", "UPGRADING", "FAILED", "SUPERSEDED",
];
const REQUIRED_INVARIANTS = [
  "bundleSignedWithCosign", "sbomAttachedAsCycloneDxOrSpdx",
  "slsaProvenanceAttached",
  "helmValuesSchemaRequiredKeysPresent",
  "compatibilityMatrixCoversAllComponents",
  "registryMirrorFromClosedSet",
  "airGapBundlesShipAllDependencies",
  "preflightChecksMustPassBeforeInstall",
  "noForkApplicationCode",
  "cpuMemoryCapacityMinimumsRespected",
  "storageClassRwxSupported", "dnsResolutionRespected",
  "certificateValidityAtLeastThirtyDays",
  "externalDependenciesReachable",
  "bundleRetentionAtLeastThreeYears",
  "helmChartVersioningFollowsSemver",
];
const REQUIRED_CAPABILITY = [
  "oci_registry quay_or_customer_internal_only",
  "image_signing cosign_only",
  "sbom_format cyclonedx_or_spdx_only",
  "helm_packaging helm_only",
  "provenance_attestation slsa_provenance_v1_only",
  "no_custom_oci_registry forbidden",
  "no_custom_image_signer forbidden",
  "no_fork_application_code forbidden",
];
const REQUIRED_FORBIDDEN_KEYWORDS = [
  "raw_dna_bytes", "raw_genotype", "raw_fastq", "raw_bam", "raw_vcf",
  "production_pii", "prod_tenant_id", "staging_tenant_id",
  "raw_email", "raw_phone", "raw_passport", "raw_ssn",
  "dev_secret", "shared_admin_password",
  "inline_jwt", "inline_access_token", "inline_refresh_token",
  "inline_session_cookie", "inline_oauth_client_secret",
  "inline_stripe_api_key", "inline_license_file",
  "tree_viewer_bypass", "bypass_authorization",
  "skip_consent", "skip_dna_isolation", "skip_audit", "skip_redaction",
  "skip_preflight", "skip_cosign_verify", "skip_sbom_attestation",
  "ad_hoc_registry", "fork_application_code",
];
const REQUIRED_HELPERS = [
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/onprem/BundleGuard.java",
  "services/operations-service/src/main/java/com/genealogy/platform/services/operations/onprem/E14OnpremLimits.java",
  "runbook/onprem-bundle.md",
  "tools/onprem/preflight.sh",
  "platform/helm/genealogy-platform/Chart.yaml",
];
const REQUIRED_NUMERIC_KEYS = [
  "maxBundleSizeGigabytes", "preflightTimeoutSeconds",
  "installTimeoutSeconds", "upgradeTimeoutSeconds",
  "cosignSignatureTtlDays", "sbomRetentionDays",
  "minSupportedKubernetesMinor", "maxSupportedKubernetesMinor",
  "minSupportedPostgresqlMajor", "maxSupportedPostgresqlMajor",
  "minCpuMillicoresRequired", "minMemoryKibibytesRequired",
  "minStorageClassRwxSupport", "registryMirrorMaximumEntries",
];

let violations = 0;
const oks = [];
const ok = (msg) => oks.push(msg);
const fail = (msg) => { violations += 1; console.error(`FAIL: ${msg}`); };

function read(path) { return readFileSync(path, "utf8"); }
function asObject(v) {
  if (!v) return {};
  if (typeof v === "object" && !Array.isArray(v)) return v;
  return {};
}

const text = read(CONTRACT);
const doc = loadYaml(text);
const chartText = read(CHART_FILE);

if (text !== chartText) {
  fail(`byte-identity: contract and chart mirror differ (chart=${CHART_FILE})`);
} else {
  ok(`byte-identity: contract mirrors chart (${text.length} bytes)`);
}

assertClosedSet(
  "registryMirrors", REQUIRED_REGISTRY_MIRRORS,
  asArray(doc.registryMirrors?.values),
  "E14.3 registryMirrors", ok, fail,
);

assertClosedSet(
  "supportedKubernetesVersions", REQUIRED_K8S,
  asArray(doc.supportedKubernetesVersions?.values),
  "E14.3 supportedKubernetesVersions", ok, fail,
);

assertClosedSet(
  "supportedPostgresqlVersions", REQUIRED_PG,
  asArray(doc.supportedPostgresqlVersions?.values),
  "E14.3 supportedPostgresqlVersions", ok, fail,
);

assertClosedSet(
  "supportedKafkaVersions", REQUIRED_KAFKA,
  asArray(doc.supportedKafkaVersions?.values),
  "E14.3 supportedKafkaVersions", ok, fail,
);

assertClosedSet(
  "supportedObjectStores", REQUIRED_OBJ,
  asArray(doc.supportedObjectStores?.values),
  "E14.3 supportedObjectStores", ok, fail,
);

assertClosedSet(
  "supportedKeycloakVersions", REQUIRED_KEYCLOAK,
  asArray(doc.supportedKeycloakVersions?.values),
  "E14.3 supportedKeycloakVersions", ok, fail,
);

assertClosedSet(
  "supportedOpenfgaVersions", REQUIRED_OPENFGA,
  asArray(doc.supportedOpenfgaVersions?.values),
  "E14.3 supportedOpenfgaVersions", ok, fail,
);

assertClosedSet(
  "supportedTemporalVersions", REQUIRED_TEMPORAL,
  asArray(doc.supportedTemporalVersions?.values),
  "E14.3 supportedTemporalVersions", ok, fail,
);

assertClosedSet(
  "supportedVaultVersions", REQUIRED_VAULT,
  asArray(doc.supportedVaultVersions?.values),
  "E14.3 supportedVaultVersions", ok, fail,
);

assertClosedSet(
  "supportedFlagsmithVersions", REQUIRED_FLAGSMITH,
  asArray(doc.supportedFlagsmithVersions?.values),
  "E14.3 supportedFlagsmithVersions", ok, fail,
);

const compatArr = asArray(doc.compatibilityMatrix?.values);
const compatComps = compatArr.map((c) => asObject(c).component).sort();
assertClosedSet(
  "compatibilityMatrix", REQUIRED_COMPONENTS, compatComps,
  "E14.3 compatibilityMatrix", ok, fail,
);
for (const c of compatArr) {
  const o = asObject(c);
  if (typeof o.version !== "string" || o.version.length === 0) {
    fail(`E14.3 compatibilityMatrix.${o.component}: version MUST be non-blank string`);
  }
  if (typeof o.contractRef !== "string"
      || !o.contractRef.startsWith("design.md")) {
    fail(`E14.3 compatibilityMatrix.${o.component}: contractRef MUST point to design.md (got ${o.contractRef})`);
  }
}

assertClosedSet(
  "helmValuesSchema.required", REQUIRED_HELM_REQUIRED_KEYS,
  asArray(doc.helmValuesSchema?.required),
  "E14.3 helmValuesSchema.required", ok, fail,
);

assertClosedSet(
  "helmValuesSchema.optional", REQUIRED_HELM_OPTIONAL_KEYS,
  asArray(doc.helmValuesSchema?.optional),
  "E14.3 helmValuesSchema.optional", ok, fail,
);

assertClosedSet(
  "sbomAndSignatures.formats", REQUIRED_SBOM_FORMATS,
  asArray(doc.sbomAndSignatures?.formats),
  "E14.3 sbomAndSignatures.formats", ok, fail,
);

assertClosedSet(
  "sbomAndSignatures.signatures", REQUIRED_SIGNATURES,
  asArray(doc.sbomAndSignatures?.signatures),
  "E14.3 sbomAndSignatures.signatures", ok, fail,
);

assertClosedSet(
  "sbomAndSignatures.attestation", REQUIRED_ATTESTATIONS,
  asArray(doc.sbomAndSignatures?.attestation),
  "E14.3 sbomAndSignatures.attestation", ok, fail,
);

assertClosedSet(
  "sbomAndSignatures.requiredAnnotations",
  REQUIRED_IMAGE_ANNOTATIONS,
  asArray(doc.sbomAndSignatures?.requiredAnnotations),
  "E14.3 sbomAndSignatures.requiredAnnotations", ok, fail,
);

const preflightArr = asArray(doc.preflightChecks?.values);
if (preflightArr.length < 7) {
  fail(`E14.3 preflightChecks: MUST have at least 7 entries (got ${preflightArr.length})`);
} else {
  ok(`E14.3 preflightChecks (${preflightArr.length} entries)`);
  for (const p of preflightArr) {
    const o = asObject(p);
    if (typeof o.name !== "string" || o.name.length === 0) {
      fail(`E14.3 preflightChecks: name MUST be non-blank string`);
    }
    if (typeof o.command !== "string" || o.command.length === 0) {
      fail(`E14.3 preflightChecks.${o.name}: command MUST be non-blank string`);
    }
    if (o.mustPass !== true) {
      fail(`E14.3 preflightChecks.${o.name}: mustPass MUST be true`);
    }
  }
}

assertClosedSet(
  "airGapRules", REQUIRED_AIRGAP_RULES,
  asArray(doc.airGapRules?.values),
  "E14.3 airGapRules", ok, fail,
);

assertClosedSet(
  "invariants", REQUIRED_INVARIANTS,
  asArray(doc.invariants?.values),
  "E14.3 invariants", ok, fail,
);

assertClosedSet(
  "capabilityBoundaries",
  REQUIRED_CAPABILITY,
  asArray(doc.capabilityBoundaries?.values).map((v) => {
    const o = asObject(v);
    return `${o.name || ""} ${o.spec || ""}`.trim();
  }),
  "E14.3 capabilityBoundaries", ok, fail,
);

assertClosedSet(
  "forbiddenKeywords", REQUIRED_FORBIDDEN_KEYWORDS,
  asArray(doc.forbiddenKeywords?.values),
  "E14.3 forbiddenKeywords", ok, fail,
);

assertClosedSet(
  "requiredRuntimeHelpers", REQUIRED_HELPERS,
  asArray(doc.requiredRuntimeHelpers?.values),
  "E14.3 requiredRuntimeHelpers", ok, fail,
);

for (const helper of REQUIRED_HELPERS) {
  if (!existsSync(helper)) {
    fail(`E14.3 runtime helper missing on disk: ${helper}`);
  } else {
    ok(`E14.3 runtime helper exists: ${helper}`);
  }
}

const numericArr = asArray(doc.numericBounds?.values);
const numericMap = {};
for (const n of numericArr) {
  const obj = asObject(n);
  if (obj.name) numericMap[obj.name] = obj.value;
}
const missingNumeric = REQUIRED_NUMERIC_KEYS.filter((k) => !(k in numericMap));
if (missingNumeric.length > 0) {
  fail(`E14.3 numericBounds: missing ${missingNumeric.join(",")}`);
} else {
  ok(`E14.3 numericBounds (${REQUIRED_NUMERIC_KEYS.length} keys)`);
  for (const [k, v] of Object.entries(numericMap)) {
    if (typeof v !== "number") {
      fail(`E14.3 numericBounds.${k}: not a number (${v})`);
    }
  }
}

assertStateMatrix(
  "E14.3 bundleStateMatrix",
  doc.bundleStateMatrix,
  REQUIRED_BUNDLE_STATUSES,
  "STAGED",
  ok, fail,
);

const requiredCharts = doc.requiredSourceMirror?.chartPath;
if (requiredCharts !== "platform/helm/genealogy-platform/files/disaster-recovery/onprem-bundle-policy.yaml") {
  fail(`E14.3 requiredSourceMirror.chartPath: must equal platform/helm/genealogy-platform/files/disaster-recovery/onprem-bundle-policy.yaml (got ${requiredCharts})`);
} else {
  ok(`E14.3 requiredSourceMirror.chartPath`);
}

if (violations === 0) {
  console.log(`E14.3 summary: OK`);
  console.log(`  ${oks.length} assertions passed`);
  for (const line of oks) console.log(`    ✓ ${line}`);
  process.exit(0);
} else {
  console.error(`E14.3 summary: FAIL (${violations} violations, ${oks.length} passed)`);
  process.exit(1);
}