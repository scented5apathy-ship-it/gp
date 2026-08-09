# Cài đặt & Triển khai — E2.5 Istio service mesh

> Phạm vi: triển khai E2.5 (Istio service mesh với STRICT mTLS +
> deny-by-default AuthorizationPolicy + disjoint retry/timeout +
> OTel telemetry) theo `tasks.md` E2.5 và
> `.kiro/specs/genealogy-platform/evidence/E2.5.md`.
>
> Toàn bộ cấu hình là **config-as-code trong repo** (Helm
> ConfigMaps + bootstrap Job + ServiceAccount/RBAC + NetworkPolicy),
> không có gói cài đặt binary độc lập ngoài 2 Docker image:
> `docker.io/istio/operator:1.23.2` (control plane `istiod`,
> do umbrella subchart pull) và `bitnami/kubectl:1.31.1`
> (Helm-hook Job apply 4 source-of-truth manifests).

## 1. Tổng quan những gì được cài

| Thành phần                          | Phiên bản (ADR-E0.5-01) | Vai trò                                                          |
| ----------------------------------- | ----------------------- | ---------------------------------------------------------------- |
| Istio control plane (`istiod`)      | `1.23.2`                | mTLS CA, xDS push, Pilot                                         |
| Istio sidecar (Envoy)               | `1.23.2`                | Auto-injected vào mọi workload namespace (gp-edge, gp-bff, ...)  |
| MeshConfig                          | n/a                     | REGISTRY_ONLY outbound, MUTUAL_TLS inbound, no mesh-level retry  |
| PeerAuthentication                  | n/a                     | STRICT mTLS trên 8 workload namespace (PERMISSIVE/DISABLE cấm)   |
| AuthorizationPolicy                 | n/a                     | 7 mandatory rules (deny-by-default + dna/media isolation)        |
| Telemetry policy                    | n/a                     | Disjoint retry (mesh=null, app=3) + OTel driver + JSON accesslog |
| Helm-hook bootstrap Job             | n/a                     | Apply 4 source-of-truth manifests qua `kubectl apply --server-side` |
| ServiceAccount + ClusterRole        | n/a                     | Quyền tối thiểu cho bootstrap Job đọc/ghi mesh CRDs              |
| PrometheusRule                      | n/a                     | 9 alerts (control plane, mTLS, authz, retry, bootstrap)           |

> **Không cần** cài Istio binary trực tiếp trên node — Helm
> chart tự pull Docker image khi `helm install`. Operator
> trên workstation chỉ cần `kubectl` + `helm` + (tuỳ chọn)
> `istioctl` để debug mesh runtime.

## 2. Cài đặt trên Kubernetes (production / staging)

### 2.1 Yêu cầu trước

- Kubernetes cluster `>=1.28.0` (theo `platform/helm/genealogy-platform/Chart.yaml`).
- Helm `>=3.14` (đã verify bằng `alpine/helm:3.16.3`).
- `kubectl` đã cấu hình trỏ vào cluster.
- **8 namespace platform** đã tạo từ E2.1: `gp-platform`,
  `gp-edge`, `gp-bff`, `gp-services`, `gp-workers`, `gp-data`,
  `gp-observability`, `gp-argocd`. Chart Istio sẽ áp STRICT mTLS
  trên 8 namespace này.
- **cert-manager** đã cài (E2.1 baseline) — Istio cần platform
  CA chain anchor workload identity.
- **E2.3 Kafka + Apicurio** đã chạy (Istio NetworkPolicy default-
  deny cần kết nối đã được allow); **E2.4 Temporal** đã chạy
  (worker identity dùng Istio mTLS); **E2.2 Kong** đã chạy (route
  từ Kong → BFF qua SPIFFE principal).
- (Tuỳ chọn) `istioctl 1.23.x` để debug mesh runtime — KHÔNG bắt
  buộc cho Helm-driven deploy.

### 2.2 Kiểm tra prerequisites

```bash
# Cluster version
kubectl version --short
# Kỳ vọng: Client v1.30+, Server v1.28+

# Helm version
helm version --short
# Kỳ vọng: v3.14.0+

# 8 namespace baseline
kubectl get ns -l app.kubernetes.io/part-of=genealogy-platform
# Kỳ vọng: 8 ns (gp-platform, gp-edge, gp-bff, gp-services,
# gp-workers, gp-data, gp-observability, gp-argocd)

# cert-manager đã cài (Istio cần platform CA chain)
kubectl -n gp-platform get deploy cert-manager
# Kỳ vọng: cert-manager READY

# E2.5 source-of-truth đã có trong repo
ls platform/istio/
# Kỳ vọng: mesh-config.yaml, peer-auth.yaml, authz-policies.yaml,
# telemetry.yaml, OWNERS, README.md
```

### 2.3 Lint trước khi cài (CI gate)

```bash
# Deep validator — 4 source-of-truth files + mirror
pnpm lint:istio
# Kỳ vọng: [istio] clean — namespaces=8, authz-rules=7

# Platform baseline (E2.5 invariants)
pnpm check:platform:baseline 2>&1 | grep -i istio
# Kỳ vọng: (không có lỗi istio liên quan)

# Smoke probe — source-of-truth files carry E2.5 contract
pnpm smoke:istio
# Kỳ vọng: 5/5 PASS

# Unit tests cho linter
node --test scripts/__tests__/lint-istio-config.test.mjs
# Kỳ vọng: 6/6 tests pass

# Helm structural lint (cần `helm` CLI; nếu thiếu thì chỉ chạy
# structural check)
pnpm lint:helm
# Kỳ vọng: [helm] clean — 1 chart(s)
```

### 2.4 Render Helm template (dry-run)

```bash
# Từ repo root
helm template genealogy-platform \
  platform/helm/genealogy-platform \
  -f platform/helm/genealogy-platform/values-saas.yaml \
  --set components.istio.enabled=true \
  | grep -E "kind:|name:" | head -40
# Kỳ vọng: thấy ConfigMap genea-istio-mesh-config,
# ConfigMap genea-istio-peer-auth, ConfigMap genea-istio-authz-policies,
# ConfigMap genea-istio-telemetry, ServiceAccount istio-bootstrap,
# Role/ClusterRole/RoleBinding/ClusterRoleBinding istio-bootstrap,
# Job istio-bootstrap (với helm.sh/hook annotation),
# ConfigMap istio-bootstrap-scripts, NetworkPolicy
# istio-bootstrap-default-deny, ConfigMap istio-contract-stub
```

### 2.5 Cài đặt qua Helm

```bash
# Argo CD / GitOps (khuyến nghị cho production)
# Tạo Application manifest:
cat <<'YAML' > /tmp/istio-app.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: genealogy-platform-istio
  namespace: gp-argocd
spec:
  project: platform
  source:
    repoURL: https://github.com/genealogy/platform
    targetRevision: main
    path: platform/helm/genealogy-platform
    helm:
      valueFiles:
        - values.yaml
        - values-saas.yaml
  destination:
    server: https://kubernetes.default.svc
    namespace: gp-platform
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=false
      - ServerSideApply=true
YAML
kubectl apply -f /tmp/istio-app.yaml

# HOẶC cài trực tiếp bằng helm (không khuyến nghị cho production)
helm repo add istio https://istio-release.storage.googleapis.com/charts
helm repo update

# Step 1: cài istio-operator subchart (control plane)
helm upgrade --install istio-base istio/base \
  -n gp-platform --create-namespace

helm upgrade --install istiod istio/istiod \
  -n gp-platform \
  --set meshConfig.outboundTrafficPolicy.mode=REGISTRY_ONLY \
  --set meshConfig.inboundTrafficPolicy.mode=MUTUAL_TLS \
  --set meshConfig.trustDomain=cluster.local \
  --wait

# Step 2: cài umbrella chart — chỉ phần istio (helm hook Job
# sẽ tự động apply 4 source-of-truth manifests)
helm upgrade --install genealogy-platform \
  platform/helm/genealogy-platform \
  -n gp-platform \
  -f platform/helm/genealogy-platform/values.yaml \
  -f platform/helm/genealogy-platform/values-saas.yaml \
  --set components.istio.enabled=true \
  --wait
```

### 2.6 Xác minh cài đặt

```bash
# 1. Control plane Ready
kubectl -n gp-platform get pods -l app=istiod
# Kỳ vọng: istiod-xxx Running 1/1

# 2. Bootstrap Job đã chạy thành công
kubectl -n gp-platform get jobs -l app.kubernetes.io/component=istio-bootstrap
# Kỳ vọng: istio-bootstrap Complete 1/1

# 3. 4 ConfigMap rendered
kubectl -n gp-platform get configmap -l app.kubernetes.io/component=istio
# Kỳ vọng: genea-istio-mesh-config, genea-istio-peer-auth,
#           genea-istio-authz-policies, genea-istio-telemetry,
#           istio-bootstrap-scripts, istio-contract-stub

# 4. 4 Mesh CRDs đã apply
kubectl get peerauthentications.security.istio.io -A | grep gp-
# Kỳ vọng: 8 entry (một per namespace)

kubectl get authorizationpolicies.security.istio.io -A | grep -E "deny-plaintext|kong-to-bff|dna-service|media-worker|dna-worker"
# Kỳ vọng: 7 mandatory policies

# 5. STRICT mTLS trên từng namespace
for ns in gp-platform gp-edge gp-bff gp-services gp-workers gp-data gp-observability gp-argocd; do
  mode=$(kubectl -n $ns get peerauthentication default \
    -o jsonpath='{.spec.mtls.mode}' 2>/dev/null)
  echo "$ns: $mode"
done
# Kỳ vọng: tất cả in ra "STRICT"

# 6. Workload identity test (optional, cần workload đã inject sidecar)
kubectl -n gp-services exec -c istio-proxy deploy/genealogy-service \
  -- curl -s http://istiod.gp-platform:15014/version
# Kỳ vọng: trả về version info từ istiod (mesh kết nối OK)
```

### 2.7 Smoke probe end-to-end (nếu có kind)

```bash
# Smoke probe đầy đủ — cần kind + kubectl trên PATH
KIND_CLUSTER=gp-istio-smoke pnpm smoke:istio
# Kỳ vọng: 7/7 PASS — istiod Ready, 4 source-of-truth manifests applied,
#           STRICT mTLS enforced trên mọi namespace

# Teardown kind cluster (optional)
SMOKE_KIND_TEARDOWN=1 KIND_CLUSTER=gp-istio-smoke pnpm smoke:istio
```

## 3. Cài đặt trên local (developer workstation)

### 3.1 Phương án A — dùng kind cluster (khuyến nghị cho Istio)

Local stack trong `platform/local/docker-compose.yml` chỉ render
Postgres / Keycloak / OpenFGA / Kafka / Apicurio / Temporal /
MinIO / Valkey / Flagsmith / OTel Collector — KHÔNG bao gồm
Istio. Để có mesh trên local, dùng `kind`:

```bash
# 1. Tạo kind cluster
kind create cluster --name gp-local --config - <<'YAML'
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30000
        hostPort: 30000
networking:
  kubeProxyMode: none
YAML

# 2. Cài Istio (giống production nhưng single-node)
istioctl install --set profile=demo -y
kubectl label namespace default istio-injection=enabled

# 3. Apply 8 namespace baseline từ E2.1
kubectl apply -f platform/helm/genealogy-platform/templates/baseline/namespaces.yaml

# 4. Cài umbrella chart — phần Istio
helm upgrade --install genealogy-platform \
  platform/helm/genealogy-platform \
  -f platform/helm/genealogy-platform/values.yaml \
  -f platform/helm/genealogy-platform/values-dev.yaml \
  --set components.istio.enabled=true \
  --wait

# 5. Verify
kubectl -n gp-platform get jobs -l app.kubernetes.io/component=istio-bootstrap
# Kỳ vọng: Complete 1/1
```

### 3.2 Phương án B — bỏ Istio trên local

Nếu chỉ dev domain service mà không cần mesh:

```bash
helm upgrade --install genealogy-platform \
  platform/helm/genealogy-platform \
  -f platform/helm/genealogy-platform/values.yaml \
  -f platform/helm/genealogy-platform/values-dev.yaml \
  --set components.istio.enabled=false \
  --wait
# Domain services chạy không có sidecar; NetworkPolicy của E2.1
# baseline vẫn áp dụng (default-deny + per-namespace allow).
```

## 4. Rollback

```bash
# Xem lịch sử release
helm history genealogy-platform -n gp-platform

# Rollback về revision trước
helm rollback genealogy-platform <REVISION> -n gp-platform
# Helm-hook Job sẽ tự động re-apply 4 source-of-truth manifests
# của revision cũ qua kubectl apply --server-side.

# Nếu cần xoá hoàn toàn Istio CRDs (trước khi uninstall chart)
kubectl delete peerauthentications.security.istio.io --all -A
kubectl delete authorizationpolicies.security.istio.io --all -A
kubectl delete telemetries.telemetry.istio.io --all -A

helm uninstall genealogy-platform -n gp-platform
helm uninstall istiod -n gp-platform
helm uninstall istio-base -n gp-platform
```

## 5. ADR / supersession

- **ADR-E0.5-01** pin Istio 1.23.x. Bump patch qua Renovate.
  Minor / major bump cần supersession ADR.
- **ADR-E0.5-03** (DEFERRED) sẽ bổ sung region-aware trust
  domain (`cluster.local.<region>`) cho cross-region failover.
- **ADR new (E2.5.x) follow-up #1:** ambient mode rollout.
  MeshConfig đã pin `ISTIO_META_ENABLE_HBONE: "true"` nhưng
  chart vẫn ship sidecar-injection default.
- **ADR new (E2.5.x) follow-up #2:** `authz-codegen` tool để
  derive allow rules từ `services/<svc>/OWNERS` +
  `ownership-catalog.md` RACI.

## 6. Tham chiếu

- `.kiro/specs/genealogy-platform/evidence/E2.5.md` — Completion Evidence.
- `.kiro/specs/genealogy-platform/design.md` §13 (GitOps / mTLS).
- `.kiro/specs/genealogy-platform/architecture-decisions.md`
  ADR-E0.5-01, ADR-E0.5-03.
- `platform/istio/README.md` — Source-of-truth layout + runtime.
- `runbook/istio.md` — 6 alert playbooks.
- `scripts/lint-istio-config.mjs` — Deep validator.
- `scripts/smoke-istio.mjs` — Live smoke probe.