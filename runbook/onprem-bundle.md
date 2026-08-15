# On-Premise Bundle — Operator Runbook

> **Status:** E14.3 baseline; evidence in
> `.kiro/specs/genealogy-platform/evidence/E14.3.md`.
> Mirrors `contracts/disaster-recovery/onprem-bundle-policy.yaml`.

## 1. Scope

The on-prem bundle packages the entire platform stack
(Kong + Keycloak + OpenFGA + Temporal + Strimzi +
PostgreSQL + Vault + Flagsmith + Istio + Argo CD + Grafana
+ MinIO + ClamAV / libvips / FFmpeg / Tesseract / Gotenberg
+ media-service / tenant-service / etc.) as a signed OCI
artifact. Every customer deployment consumes the SAME
binary; on-prem is a configuration difference, not a code
fork (NFR6).

## 2. Registry mirror

Closed-set of registry mirrors:

- `quay.io/genealogy` — default.
- `customer-internal-registry` — air-gap / regulated
  customer.

Anything else is **rejected** by the runtime.

## 3. Compatibility matrix

| Component | Default version |
| --------- | --------------- |
| Kubernetes | 1.31 |
| PostgreSQL | 16 |
| Kafka | 3.8 |
| Object storage | MinIO 2025 |
| Keycloak | 26.0 |
| OpenFGA | 1.9 |
| Temporal | 1.24 |
| Vault | 1.17 |
| Flagsmith | 2.5 |

Older versions within the supported range are still
allowed; the linter refuses a version outside the
closed-set.

## 4. Preflight checks

The bundle MUST pass seven preflight checks before any
install or upgrade:

- `kubernetesVersion` (must be in 1.29..1.32)
- `storageClass` (CSI provisioner required)
- `dnsResolution` (CoreDNS reachable)
- `certificateValidity` (≥ 30 days remaining)
- `cpuCapacity` (≥ 16 vCPU total)
- `memoryCapacity` (≥ 64 GiB total)
- `externalDependencyReachability` (customer-managed
  PostgreSQL / Kafka / object storage)

`tools/onprem/preflight.sh` runs every check and exits 0
only when all pass.

## 5. SBOM + signature + attestation

Every bundle MUST ship:

- CycloneDX 1.5 or SPDX 2.3 SBOM.
- Cosign signature (root key in customer KMS).
- SLSA provenance v1 attestation.
- Image annotations: `org.opencontainers.image.source`,
  `org.opencontainers.image.revision`,
  `org.opencontainers.image.created`,
  `org.opencontainers.image.licenses`.

A bundle without these is **rejected** at install.

## 6. Air-gap rules

Air-gap mode enforces six rules:

- All images in bundle.
- SBOM in bundle.
- Signatures in bundle.
- Helm charts in bundle.
- Vendor licenses in bundle.
- No runtime internet call.

The preflight script verifies each rule by hashing the
expected files; the runtime fails any rule check.

## 7. Failure handling

- `cosign verify` fails → SEV1, refuse install.
- SBOM / attestation missing → SEV1, refuse install.
- Preflight fails → SEV2, request capacity / DNS / cert
  fix from customer.
- Air-gap rule missing → SEV1, refuse install (no runtime
  internet allowed).
- Bundle size > 50 GB → SEV2, split or trim customer
  payload.

## 8. Evidence anchors

- `.kiro/specs/genealogy-platform/evidence/E14.3.md` — DoD.
- `tools/onprem/preflight.sh` — preflight runner.
- `platform/helm/genealogy-platform/Chart.yaml` — top-level
  Helm chart.