# Platform — Istio (E2.5)

Source-of-truth Istio configuration for the genealogy platform.

## Layout

- `mesh-config.yaml` — single `MeshConfig` resource with
  `outboundTrafficPolicy: REGISTRY_ONLY` + `inboundTrafficPolicy:
  MUTUAL_TLS`, extension providers (Zipkin, Prometheus), and
  the platform trust domain. **No mesh-level retry / timeout**
  per `design.md` §13.
- `peer-auth.yaml` — PeerAuthentication CRD list: one STRICT-mTLS
  entry per workload namespace (mesh-wide default in
  `gp-platform` plus per-namespace pins in `gp-edge`, `gp-bff`,
  `gp-services`, `gp-workers`, `gp-data`, `gp-observability`,
  `gp-argocd`). PERMISSIVE / DISABLE are forbidden.
- `authz-policies.yaml` — AuthorizationPolicy CRD list:
  deny-by-default for forged principal + allow rules for the
  documented service-to-service paths + dedicated DENY blocks
  for `dna-service` (E10.2) and `media-worker` (E7.2) egress
  isolation.
- `telemetry.yaml` — retry / timeout / tracing / metrics /
  accesslog policy contract. Documents the **disjoint retry
  budget** (mesh = none, app = 3 attempts) that prevents retry
  amplification.

The same files are mirrored into the umbrella chart's
`files/istio/` directory so `helm template` can render without
reading outside the chart root. Anything you change here MUST
be mirrored into the chart or the umbrella will drift.

## Runtime

The umbrella chart installs Istio via the `istio-operator`
Helm subchart. The mesh-level `MeshConfig` and the per-namespace
`PeerAuthentication` / `AuthorizationPolicy` CRDs are applied
via the `istio-mesh-config-configmap.yaml`,
`istio-peer-auth-configmap.yaml`,
`istio-authz-policies-configmap.yaml`, and
`istio-telemetry-configmap.yaml` templates. The
`istio-bootstrap-job` Helm-hook Job calls `istioctl install`
+ `kubectl apply` against the rendered manifests.

## Privacy / security

- **No mesh-level retry** — prevents retry amplification across
  cascade failures (E13.4 chaos drill verifies).
- **STRICT mTLS** — every pod-to-pod connection is
  SPIFFE-authenticated. Plaintext is rejected at the proxy.
- **Deny-by-default AuthorizationPolicy** — forged principal
  is rejected before the request reaches the destination
  service. The destination service still re-validates
  authorization (OpenFGA + ABAC) per `design.md` §4.1.
- **DNA service isolation** — `dna-service` is the only
  workload that may reach the DNA PostgreSQL database and
  the `dna-raw` object prefix. Egress to public internet
  is denied at the mesh (`REGISTRY_ONLY`) AND at the
  AuthorizationPolicy layer (E10.2 isolation contract).
- **Media worker sandbox** — `media-worker` egress is denied
  to the public internet; only the ClamAV / Tika / FFmpeg /
  Gotenberg sandbox pods are reachable (E7.2 sandbox
  contract).
- **Access log is JSON, includes the SPIFFE principal** —
  the OTel Collector log pipeline redacts PII / DNA per
  `privacy-and-legal-gate.md` §11.

## Validation

- `pnpm lint:istio` — deep validation of the four YAML files
  + the per-file invariants (STRICT mTLS on every
  namespace, no mesh-level retry, disjoint retry/timeout,
  dna-service / media-worker DENY blocks present).
- `pnpm check:platform:baseline` — extended with E2.5
  invariants (chart templates present, image pin,
  per-env overrides, mesh config / peer-auth / authz /
  telemetry ConfigMaps, alert rules, local profile pin).

## Smoke

- `pnpm smoke:istio` — local Docker smoke probe that brings
  up the platform Istio control plane (`istio-operator`) on
  a disposable kind cluster and asserts the MeshConfig /
  PeerAuthentication / AuthorizationPolicy CRDs are applied
  end-to-end (see `scripts/smoke-istio.mjs`).

## Ownership

`OWNERS` mirrors `config/teams.yaml`. Primary = `platform`,
secondary = `@genealogy/security`, on-call = `platform-primary`.
