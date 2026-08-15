package com.genealogy.platform.services.operations.onprem;

import java.util.Set;

/**
 * Closed-set + numeric catalogue for the E14.3 on-premise
 * bundle contract. Mirrors
 * <code>contracts/disaster-recovery/onprem-bundle-policy.yaml</code>.
 */
public final class E14OnpremLimits {

  public static final Set<String> REGISTRY_MIRRORS = Set.of(
      "quay.io/genealogy", "customer-internal-registry");

  public static final Set<String> KUBERNETES_VERSIONS = Set.of(
      "1.29", "1.30", "1.31", "1.32");
  public static final Set<String> POSTGRESQL_VERSIONS = Set.of(
      "15", "16", "17");
  public static final Set<String> KAFKA_VERSIONS = Set.of(
      "3.7", "3.8");
  public static final Set<String> OBJECT_STORES = Set.of(
      "aws_s3", "minio_2024", "minio_2025");
  public static final Set<String> KEYCLOAK_VERSIONS = Set.of(
      "24.0", "25.0", "26.0");
  public static final Set<String> OPENFGA_VERSIONS = Set.of(
      "1.8", "1.9");
  public static final Set<String> TEMPORAL_VERSIONS = Set.of(
      "1.22", "1.23", "1.24");
  public static final Set<String> VAULT_VERSIONS = Set.of(
      "1.15", "1.16", "1.17");
  public static final Set<String> FLAGSMITH_VERSIONS = Set.of(
      "2.4", "2.5");

  public static final Set<String> COMPONENTS = Set.of(
      "kubernetes", "postgresql", "kafka", "object_storage",
      "keycloak", "openfga", "temporal", "vault", "flagsmith");

  public static final Set<String> SBOM_FORMATS = Set.of(
      "cyclonedx_1_5", "spdx_2_3");
  public static final Set<String> SIGNATURES = Set.of("cosign");
  public static final Set<String> ATTESTATIONS = Set.of(
      "slsa_provenance_v1");
  public static final Set<String> IMAGE_ANNOTATIONS = Set.of(
      "org.opencontainers.image.source",
      "org.opencontainers.image.revision",
      "org.opencontainers.image.created",
      "org.opencontainers.image.licenses");
  public static final Set<String> AIR_GAP_RULES = Set.of(
      "allImagesInBundle", "sbomInBundle", "signaturesInBundle",
      "helmChartsInBundle", "vendorLicensesInBundle",
      "noRuntimeInternetCall");
  public static final Set<String> HELM_REQUIRED_KEYS = Set.of(
      "clusterName", "environment", "registryMirror", "airGapEnabled",
      "cosignPublicKey", "telemetrySink", "tenantDatabaseHost",
      "kafkaBootstrapServers", "objectStoreEndpoint",
      "keycloakIssuerUrl", "openfgaApiUrl", "temporalFrontendUrl",
      "vaultAddress", "flagsmithApiUrl");
  public static final Set<String> HELM_OPTIONAL_KEYS = Set.of(
      "extraAnnotations", "extraLabels",
      "podDisruptionBudgetOverride");

  public static final Set<String> BUNDLE_STATUSES = Set.of(
      "STAGED", "PREFLIGHT_RUNNING", "VERIFIED",
      "INSTALLING", "INSTALLED", "UPGRADING", "FAILED", "SUPERSEDED");

  public static final int MAX_BUNDLE_SIZE_GB = 50;
  public static final int PREFLIGHT_TIMEOUT_SECONDS = 1800;
  public static final int INSTALL_TIMEOUT_SECONDS = 7200;
  public static final int UPGRADE_TIMEOUT_SECONDS = 7200;
  public static final int COSIGN_SIGNATURE_TTL_DAYS = 365;
  public static final int SBOM_RETENTION_DAYS = 1095;
  public static final int MIN_KUBERNETES_MINOR = 29;
  public static final int MAX_KUBERNETES_MINOR = 32;
  public static final int MIN_POSTGRESQL_MAJOR = 15;
  public static final int MAX_POSTGRESQL_MAJOR = 17;
  public static final long MIN_CPU_MILLICORES_REQUIRED = 16000L;
  public static final long MIN_MEMORY_KIBIBYTES_REQUIRED = 67108864L;
  public static final int MIN_STORAGE_CLASS_RWX_SUPPORT = 1;
  public static final int REGISTRY_MIRROR_MAX_ENTRIES = 2;

  private E14OnpremLimits() {
    throw new UnsupportedOperationException("constants holder");
  }
}