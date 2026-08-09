# Cài Kubernetes Cluster Local + Verify — E2.3 / platform validation

> Mục đích: dựng cluster Kubernetes local đầy đủ (kind) để chạy
> E2.3 end-to-end (Strimzi Kafka + Apicurio Registry + alert rules)
> trên máy dev. Không cần cloud account, không cần `sudo`.
>
> Các bước **bắt buộc đi theo thứ tự** — mỗi bước phụ thuộc bước
> trước. Verify ở cuối mỗi bước trước khi đi tiếp.

## Bảng port dùng chung với các doc khác

Tài liệu này **chia sẻ host port** với `docs/local-toolchain-setup.md`
(E2.2 smoke Kong) vì cả hai đều chạy trên Docker host. Mỗi tài
liệu pick một dải port khác nhau để tránh xung đột:

| Tài liệu                                | Service                    | Host port                                      | Cluster port (NodePort)   |
| --------------------------------------- | -------------------------- | ---------------------------------------------- | ------------------------- |
| `local-toolchain-setup.md` (E2.2 smoke) | Kong `gp-kong-smoke`       | `8000` (HTTP), `8443` (HTTPS), `8100` (status) | n/a (container trực tiếp) |
| `local-k8s-setup.md` (E2.3 smoke)       | Apicurio                   | `8090` (HTTP), `8453` (HTTPS)                  | `30080`, `30443`          |
| `local-toolchain-setup.md` Step 8       | Kong admin (loopback only) | `8444`                                         | n/a                       |

> **Nếu đã chạy E2.2 smoke trước**: `gp-kong-smoke` chiếm port
> 8000/8443/8100. KHÔNG xung đột với doc này. Bạn có thể chạy
> đồng thời cả 2 smoke (Kong trên host + kind cluster NodePort).
>
> **Nếu gặp `Bind for 0.0.0.0:8090/8453 failed`**: có service
> khác chiếm port này. Đổi sang `8091/8454` trong config (Step 3.1)
> rồi áp dụng lại.

## Step 0 — Điều kiện tiên quyết

- macOS (Apple Silicon hoặc Intel), shell `zsh`.
- Đã cài toolchain theo `docs/local-toolchain-setup.md` (Node 22,
  pnpm 9, Helm 3.16, Docker Desktop / OrbStack / colima).
- ~5 GB ổ đĩa trống (kind image + Strimzi operator + Kafka cluster
  - Apicurio Postgres).
- RAM: tối thiểu 8 GB (cluster 4 node × 2 GB control-plane).
- Cổng Internet ra ngoài (GitHub releases cho kind/kubectl, Docker
  Hub cho image Strimzi / Apicurio).

Verify trước khi bắt đầu:

```sh
docker info | head -3    # Server phải chạy
helm version             # v3.16.3
node --version           # v22.x.x
```

## Step 1 — Verify toolchain (đã cài theo `local-toolchain-setup.md`)

Nếu bất kỳ lệnh nào dưới fail, quay lại `docs/local-toolchain-setup.md`:

```sh
node --version    # v22.x.x
pnpm --version    # 9.12.0
helm version      # v3.16.x
docker --version  # 29.x.x (Docker Desktop / OrbStack)
docker info | grep "Server Version"
```

Kỳ vọng: tất cả lệnh đều in version. Nếu `docker info` lỗi —
khởi động Docker Desktop / OrbStack trước.

## Step 2 — Cài kind binary (không cần Homebrew)

`kind` (Kubernetes IN Docker) chạy Kubernetes cluster trong Docker
container — không cần VM, không cần HyperKit, khởi động <30s.

### 2.1 Tải tarball chính thức

Apple Silicon:

```sh
mkdir -p ~/.local/bin
curl -fsSL https://kind.sigs.k8s.io/dl/v0.24.0/kind-darwin-arm64 \
  -o ~/.local/bin/kind
chmod +x ~/.local/bin/kind
```

Intel Mac (nếu dùng `darwin-amd64`):

```sh
mkdir -p ~/.local/bin
curl -fsSL https://kind.sigs.k8s.io/dl/v0.24.0/kind-darwin-amd64 \
  -o ~/.local/bin/kind
chmod +x ~/.local/bin/kind
```

### 2.2 Verify

```sh
kind --version
# Kỳ vọng: kind v0.24.0
```

Nếu `kind: command not found`:

```sh
source ~/.zshrc    # nạp PATH (đã có ~/.local/bin trong local-toolchain-setup.md)
```

## Step 3 — Tạo cluster với config 3 node + port mapping

Strimzi Kafka cần HA (replication factor 3 theo E2.3) — tạo cluster
có 1 control-plane + 3 worker. Map 2 port từ host vào cluster để
test Apicurio qua `localhost`.

### 3.0 Kiểm tra port conflict trước

Port dùng cho doc này: `6443` (kube-apiserver), `8090` (Apicurio
HTTP), `8453` (Apicurio HTTPS). Mặc định doc này KHÔNG xung đột
với E2.2 Kong smoke (dùng 8000/8443/8100) — nhưng nếu có service
khác chiếm 8090 hoặc 8453 thì phải đổi.

```sh
# Kiểm tra 3 port quan trọng có bị chiếm không
for port in 6443 8090 8453; do
  if lsof -iTCP:$port -sTCP:LISTEN -P -n 2>/dev/null | grep -q LISTEN; then
    echo "PORT $port: BUSY"
    lsof -iTCP:$port -sTCP:LISTEN -P -n | head -3
  else
    echo "PORT $port: free"
  fi
done
```

Nếu 8090 hoặc 8453 bận:

```sh
# Xem container nào chiếm
docker ps --format "table {{.Names}}\t{{.Ports}}" | grep -E ":(8090|8453)"

# Nếu không cần container đó nữa, kill:
# docker kill <container-name>

# Hoặc đổi port trong config (Step 3.1) sang 8091/8454, 8092/8455, ...
# rồi áp dụng lại từ đầu Step 3.
```

### 3.1 Tạo file config

```sh
mkdir -p /tmp/gp-kind
cat > /tmp/gp-kind/kind-config.yaml <<'EOF'
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    kubeadmConfigPatches:
      - |
        kind: InitConfiguration
        nodeRegistration:
          kubeletExtraArgs:
            node-labels: "ingress-ready=true"
    extraPortMappings:
      # 8090/8453 — dải riêng của doc này, không xung đột với
      # E2.2 Kong smoke (dùng 8000/8443/8100).
      # Mapping: host:8090 → NodePort 30080 (Apicurio main).
      - containerPort: 30080
        hostPort: 8090
        protocol: TCP
        listenAddress: "127.0.0.1"
      - containerPort: 30443
        hostPort: 8453
        protocol: TCP
        listenAddress: "127.0.0.1"
  - role: worker
  - role: worker
  - role: worker
networking:
  apiServerAddress: "127.0.0.1"
  apiServerPort: 6443
  podSubnet: "10.244.0.0/16"
  serviceSubnet: "10.96.0.0/16"
EOF
```

> **Port mapping:** host:8090/8453 → NodePort 30080/30443 trong
> cluster. Sau khi apply chart, Apicurio sẽ truy cập qua
> `http://localhost:8090` (xem Step 10). ListenAddress
> `127.0.0.1` để không public ra LAN. Nếu port 8090/8453 bận
> (Step 3.0), đổi cả host port và ghi nhớ cho Step 8.5 + Step 10.2.

### 3.2 Tạo cluster

```sh
kind create cluster --name gp-e23 \
  --config /tmp/gp-kind/kind-config.yaml \
  --image docker.io/kindest/node:v1.30.0
```

Output kỳ vọng:

```
Creating cluster "gp-e23" ...
 • Ensuring node images (docker.io/kindest/node:v1.30.0) 🖼
 • Preparing nodes 📦 📦 📦 📦
 • Writing configuration 📜
 • Starting control-plane 🕹️
 • Installing CNI 🔌
 • Installing StorageClass 💾
 • Joining worker nodes 🚀
Set kubectl context to "kind-gp-e23"
```

### 3.3 Verify cluster đã lên

```sh
kind get clusters
# Kỳ vọng: gp-e23

kubectl cluster-info --context kind-gp-e23
# Kỳ vọng: Kubernetes control plane is running at https://127.0.0.1:6443

kubectl get nodes
# Kỳ vọng: 4 nodes Ready (1 control-plane, 3 worker)
```

> Nếu `kubectl: command not found` — bước 4 dưới đây.

## Step 4 — Cài kubectl (nếu chưa có)

`kind` KHÔNG tự cài `kubectl`. Cài bằng curl chính thức:

### 4.1 Tải kubectl

Apple Silicon:

```sh
curl -fsSLo ~/.local/bin/kubectl \
  https://dl.k8s.io/release/v1.30.0/bin/darwin/arm64/kubectl
chmod +x ~/.local/bin/kubectl
```

Intel Mac:

```sh
curl -fsSLo ~/.local/bin/kubectl \
  https://dl.k8s.io/release/v1.30.0/bin/darwin/amd64/kubectl
chmod +x ~/.local/bin/kubectl
```

### 4.2 Verify

```sh
source ~/.zshrc
kubectl version --client
# Kỳ vọng: Client Version: v1.30.0

kubectl get nodes --context kind-gp-e23
# Kỳ vọng: 4 nodes Ready
```

### 4.3 Set context mặc định (tùy chọn, tiện cho dev)

```sh
kubectl config use-context kind-gp-e23
```

Verify:

```sh
kubectl config current-context
# Kỳ vọng: kind-gp-e23
```

## Step 5 — Cài cert-manager (prerequisite cho Strimzi mTLS)

Strimzi dùng cert-manager để cấp cert cho broker / client TLS
listener. Tuy E2.3 không bắt buộc TLS listener cho local dev
(mặc định dùng plain listener), cert-manager vẫn cần cho
Apicurio readiness probe + future TLS.

### 5.1 Cài cert-manager

```sh
kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.15.0/cert-manager.yaml
```

### 5.2 Verify

```sh
kubectl -n cert-manager get pods
# Đợi tất cả Ready (~30s):
# cert-manager              1/1     Running
# cert-manager-cainjector   1/1     Running
# cert-manager-webhook      1/1     Running

kubectl wait --for=condition=Ready pods -l app.kubernetes.io/instance=cert-manager -n cert-manager --timeout=120s
# Kỳ vọng: cert-manager.condition met
```

## Step 6 — Cài Strimzi operator

Strimzi operator chạy trong namespace riêng, theo dõi CRD `Kafka`
trên toàn cluster.

### 6.1 Tạo namespace + cài operator

```sh
# Tạo namespace (idempotent)
kubectl create namespace strimzi --dry-run=client -o yaml | kubectl apply -f -

# Nếu đã thử install trước đó mà fail (vd lỗi memory limit), Helm
# vẫn giữ release secret → phải uninstall trước khi install lại:
#   helm uninstall strimzi -n strimzi

# Thêm Helm repo (idempotent — skip nếu đã có)
helm repo add strimzi https://strimzi.io/charts/
helm repo update

# Cài operator bằng Helm
# Lưu ý: KHÔNG override resources — chart 0.45.x có memory limit
# mặc định 384Mi; nếu set request 512Mi sẽ fail với
# "must be less than or equal to memory limit". Dùng chart defaults.
helm install strimzi strimzi/strimzi-kafka-operator \
  --namespace strimzi \
  --version 0.45.2 \
  --set watchAnyNamespace=true
```

### 6.2 Verify

```sh
kubectl -n strimzi get pods
# Đợi Ready:
# strimzi-cluster-operator-xxx   1/1   Running   0   30s

kubectl get crd | grep kafka.strimzi.io
# Kỳ vọng: kafkas.kafka.strimzi.io
#          kafkatopics.kafka.strimzi.io
#          kafkausers.kafka.strimzi.io
```

## Step 7 — Apply baseline namespace + StorageClass

Chart `platform/helm/genealogy-platform` ship 8 namespace + quota.
Apply baseline trước khi apply E2.3 resources.

### 7.1 Render chart với dev values

```sh
cd /Users/ngocshb/IdeaProjects/scented5apathy-ship-it/gp
helm template gp platform/helm/genealogy-platform \
  -f platform/helm/genealogy-platform/values-dev.yaml \
  > /tmp/gp-e23-rendered.yaml
```

### 7.2 Filter + apply chỉ Namespace + StorageClass + ResourceQuota

**Dry-run trước** để bắt validate error (label sai regex, schema
sai, v.v.) trước khi apply thật:

```sh
awk '
  BEGIN { print_buffer = 0 }
  /^---$/ {
    if (print_buffer) printf "%s\n---\n", buffer
    buffer = ""
    print_buffer = 0
    next
  }
  /^kind:[[:space:]]*(Namespace|ResourceQuota|StorageClass)$/ {
    print_buffer = 1
  }
  { buffer = buffer $0 ORS }
  END { if (print_buffer) printf "%s\n", buffer }
' /tmp/gp-e23-rendered.yaml | kubectl apply --dry-run=server -f -
# Nếu mọi resource OK → output "configured" cho từng item, exit 0
# Nếu có resource invalid → in "Error from server" + exit 1
```

Khi dry-run PASS, apply thật:

```sh
awk '
  BEGIN { print_buffer = 0 }
  /^---$/ {
    if (print_buffer) printf "%s\n---\n", buffer
    buffer = ""
    print_buffer = 0
    next
  }
  /^kind:[[:space:]]*(Namespace|ResourceQuota|StorageClass)$/ {
    print_buffer = 1
  }
  { buffer = buffer $0 ORS }
  END { if (print_buffer) printf "%s\n", buffer }
' /tmp/gp-e23-rendered.yaml | kubectl apply -f -
```

### 7.3 Verify

```sh
kubectl get ns -l app.kubernetes.io/part-of=genealogy-platform
# Kỳ vọng: 8 namespace
#   gp-platform   Active
#   gp-edge       Active
#   gp-bff        Active
#   gp-services   Active
#   gp-workers    Active
#   gp-data       Active
#   gp-observability   Active
#   gp-argocd     Active

kubectl -n gp-data get storageclass
# Kỳ vọng: gp-data-ssd (default)

kubectl -n gp-data get resourcequota
# Kỳ vọng: gp-data-quota
```

## Step 8 — Apply Kafka + Apicurio + verify Ready

### 8.1 Tạo Postgres cho Apicurio

> **Pattern: nếu `helm install` fail và báo `cannot re-use a name
that is still in use`** — release secret đã ghi, dù chart
> chưa apply. Chạy `helm uninstall <name> -n <ns>` rồi install
> lại. Pattern này áp dụng cho MỌI Helm install trong doc này
> (Step 6.1 Strimzi, Step 8.1 Postgres, Step 5.1 cert-manager).

```sh
# Apply Postgres Operator (CloudNativePG) hoặc dùng plain
# Deployment. Để đơn giản, dùng bitnami postgres helm chart:
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# Nếu install trước đó fail, chạy trước:
# helm uninstall postgres -n gp-data

helm install postgres bitnami/postgresql \
  --namespace gp-data \
  --set auth.postgresPassword=dev-postgres-pw \
  --set auth.database=genea_apicurio \
  --set primary.persistence.size=5Gi \
  --set primary.persistence.storageClass=standard \
  --set primary.resources.requests.cpu=200m \
  --set primary.resources.requests.memory=256Mi \
  --set primary.resources.limits.cpu=500m \
  --set primary.resources.limits.memory=512Mi \
  --set fullnameOverride=postgres
  # Đổi fullname thành `postgres` để match DNS mà chart
  # Apicurio hardcode: `postgres.gp-data.svc.cluster.local`.
  # Mặc định Bitnami tạo `postgres-postgresql` → không resolve.
  #
  # storageClass=standard (không phải gp-data-ssd) vì kind
  # cluster không có provisioner cho gp-data-ssd (provisioner
  # là `kubernetes.io/no-provisioner`, chỉ dùng cho cluster
  # production với cloud volume). Trên kind, `standard` map
  # với rancher.io/local-path built-in.

kubectl -n gp-data wait --for=condition=Ready pods -l app.kubernetes.io/instance=postgres --timeout=180s
# Kỳ vọng: postgres-0 Ready (không phải postgres-postgresql-0)

kubectl -n gp-data get svc
# Kỳ vọng: service `postgres` ClusterIP 5432/TCP

# Lưu ý quan trọng về resource limits:
# `gp-data-quota` (do chart baseline tạo ở Step 7) enforce
# LimitRange mặc định của namespace — pod phải có `limits.cpu`
# và `limits.memory`, không chỉ requests. Bitnami chart mặc
# định KHÔNG set limits nếu không override. Nếu gặp
# "must specify limits.cpu/limits.memory for: postgresql" → set
# cả requests và limits như trên.
```

Tạo Secret cho Apicurio:

```sh
kubectl -n gp-data create secret generic apicurio-postgres \
  --from-literal=username=postgres \
  --from-literal=password=dev-postgres-pw
```

### 8.2 Apply Kafka CR + topics + users

```sh
cd /Users/ngocshb/IdeaProjects/scented5apathy-ship-it/gp
for tpl in \
  templates/components/kafka/kafka.yaml \
  templates/components/kafka/topics.yaml \
  templates/components/kafka/users.yaml \
  templates/components/kafka/metrics-configmap.yaml \
  templates/components/kafka/network-policy.yaml; do
  kubectl apply -f <(helm template gp platform/helm/genealogy-platform \
    -f platform/helm/genealogy-platform/values-dev.yaml \
    --show-only "$tpl")
done
```

> **Lưu ý quan trọng về E2.3 chart contract:**
>
> - **Strimzi 0.43 + KRaft metadataVersion vẫn cần ZK block** — Strimzi
>   schema yêu cầu `spec.zookeeper` đầy đủ (replicas + storage) cho dù
>   `metadataVersion: "3.8"` đã là KRaft. KRaft-only path chỉ có từ
>   Strimzi 0.45+. Chart đã wire block đầy đủ; chỉ cần `kafka.replicas: 3`
>   trong `values-dev.yaml` để ZK form quorum.
> - **metadataVersion phải là string `"3.8"`** — bare `3.8` parse thành
>   float, Strimzi reject. Chart đã quote đúng.
> - **StorageClass `gp-data-ssd` không provision trên kind** —
>   `provisioner: kubernetes.io/no-provisioner` chỉ dùng cho cloud
>   cluster. Trên kind dùng `standard` (local-path built-in) qua
>   `kafkaStorageClass: standard` trong `values-dev.yaml`.
> - **KafkaUser ACL operation phải PascalCase** (`Read`, `Write`)
>   — lowercase bị Strimzi reject. Chart đã dùng đúng enum.
> - **partitionKey phải là K8s label slug** — dấu `+` bị reject.
>   Dùng `tenant-and-aggregate` thay vì `tenantId+aggregateId`.
> - **Per-broker StaticQuotaCallback DISABLED — Strimzi 0.45.2 vẫn
>   forbidden-list.** Phiên bản 0.43.0 và 0.45.2 đều silently strip
>   `client.quota.callback.static.kafka.admin.bootstrap.servers`,
>   `client.quota.callback.static.produce`, và
>   `client.quota.callback.static.excluded.principal.name.list` khỏi
>   broker ConfigMap (operator log: "forbidden and will be ignored").
>   Strimzi docs nói forbidden-list lifted ở 0.46.x line — chart đã
>   verify lại sau khi bump 0.45.2 vẫn bị. Nếu set
>   `client.quota.callback.class` thì broker crash ngay khi start
>   với `Missing required configuration
client.quota.callback.static.kafka.admin.bootstrap.servers`.
>   Chart đã loại bỏ toàn bộ block `client.quota.callback.*`.
>   Quota enforcement chuyển sang `KafkaUser.spec.quotas` + Kong
>   edge rate-limit (E2.2). Track re-enable trong ADR-E0.5-08
>   supersession khi Strimzi 0.46.x được adopt platform-wide.
> - **Entity-operator DISABLED on dev via `values-dev.yaml`**
>   toggle `entityOperator.enabled: false`. Strimzi 0.45.2 entity-
>   operator has Admin API silent bug on kind cluster (topic +
>   user reconciliation runs in <100ms without actual Admin API
>   call). Production / staging keep default `enabled: true` so
>   entity-operator reconciles KafkaTopic + KafkaUser CRs as
>   designed. Dev bootstrap topics manually via Step 10.4.

### 8.3 Apply Apicurio Deployment + Service + ConfigMap + NetworkPolicy

```sh
kubectl apply -f <(helm template gp platform/helm/genealogy-platform \
  -f platform/helm/genealogy-platform/values-dev.yaml \
  --show-only templates/components/apicurio/registry.yaml)
```

> **Lưu ý quan trọng về Apicurio 3.x:**
>
> - **Docker Hub đã drop tag `2.6.x`** — ADR-E0.5-01 cần update từ
>   `apicurio-registry:2.6.0` sang `3.x`. `values-dev.yaml` dùng
>   `tag: "3.3"` (chuỗi, không phải số). Production values cần ADR
>   mới.
> - **Apicurio 3.x chạy trên Quarkus** — ConfigMap mount path
>   `/deployments/config` không đọc. Config được truyền qua env
>   vars `QUARKUS_DATASOURCE_*` (JDBC) + `APICURIO_*` (app).
> - **Probe `/q/health/ready` trả 404 trong prod profile** — chart
>   dùng `/apis/registry/v2/system/info` (luôn 200 khi SQL store
>   ready).
> - **Chart hardcode DNS `postgres.gp-data.svc.cluster.local`** —
>   Bitnami `fullnameOverride=postgres` (Step 8.1) match DNS này.
> - **NetworkPolicy chặn ingress từ ngoài namespace** — smoke từ
>   dev host phải qua `kubectl exec` (không port-forward) hoặc cài
>   Apicurio UI thông qua Kong/proxy trong `gp-services` /
>   `gp-observability`.

### 8.4 Verify Kafka Ready (đợi 3-5 phút)

```sh
# Nếu Kafka CR fail rollout, debug bằng:
kubectl -n strimzi rollout restart deploy/strimzi-cluster-operator
# (operator đôi khi stuck sau khi chart update; restart giúp nó
# reconcile lại từ đầu)

kubectl -n gp-data get kafka genea-kafka
# Theo dõi cột READY — từ False → True (~3-5 phút)

kubectl -n gp-data wait --for=condition=Ready kafka/genea-kafka --timeout=600s
# Kỳ vọng: kafka.kafka.strimzi.io/genea-kafka condition met

kubectl -n gp-data get kafkatopics --no-headers | wc -l
# Production / staging: Kỳ vọng 11 (entity-operator enabled, mỗi
#   KafkaTopic CR reconcile thành công)
# Dev local (entity-operator disabled): Kỳ vọng 0 (chart không
#   apply KafkaTopic CRs — bootstrap thủ công qua Step 10.4)

kubectl -n gp-data get kafkausers --no-headers | wc -l
# Production / staging: Kỳ vọng 14 (entity-operator enabled, 1 admin
#   + 7 producer + 6 consumer)
# Dev local (entity-operator disabled): Kỳ vọng 0 (chart không apply
#   KafkaUser CRs — admin user cert không được issue, PLAIN listener
#   anonymous auth được chấp nhận sau khi disable authorization)
```

> **⚠️ Trên dev local**, với `entityOperator.enabled: false` trong
> `values-dev.yaml` (workaround cho Strimzi 0.45.2 entity-operator
> silent bug trên kind cluster — xem Step 8.2 note + Step 12), cả
> `KafkaTopic` lẫn `KafkaUser` CRs đều KHÔNG được apply. Cả hai count
> trên đều = 0. Topics + ACLs được bootstrap thủ công qua Step 10.4.
>
> **Trên production / staging**, với default `entityOperator.enabled:
true`, kỳ vọng:
>
> ```sh
> # 11 KafkaTopic CR với cột READY = True
> kubectl -n gp-data get kafkatopics
>
> # User Secrets được entity-operator issue (1 secret / KafkaUser)
> kubectl -n gp-data get secrets -l strimzi.io/cluster=genea-kafka \
>   -l strimzi.io/kind=KafkaUser --no-headers | wc -l
> # Production kỳ vọng = số KafkaUser CRs (1 admin + 7 producer + 6 consumer)
> # Dev local kỳ vọng = 0 (entity-operator disabled — xem dưới)
> ```
>
> Nếu chạy production chart trên cluster thật mà `READY` trống /
> user Secrets = 0 → escalate lên ADR-E0.5-01 supersession lần 3:
> bump Strimzi 0.46.1+ (KRaft stability fixes).

### 8.5 Verify Apicurio Ready

```sh
kubectl -n gp-data get deploy apicurio-registry
# Kỳ vọng: READY 1/1

kubectl -n gp-data wait --for=condition=Available deploy/apicurio-registry --timeout=300s
# Kỳ vọng: deployment.apps/apicurio-registry condition met

# Probe Apicurio — exec vào pod (vì NetworkPolicy chặn ingress
# từ dev host):
kubectl -n gp-data exec deploy/apicurio-registry -- \
  curl -s http://localhost:8080/apis/registry/v2/system/info | head -c 200
# Kỳ vọng: {"name":"Apicurio Registry (SQL)", ...}

# Probe qua NodePort (Step 3 map 8090 → 30080) — CHỈ work nếu
# NetworkPolicy cho phép ingress từ dev host. Trên dev, dùng
# kubectl exec ở trên:
# sleep 5
# curl -s http://127.0.0.1:8090/apis/registry/v2/system/info | head -c 200

# Probe qua port-forward (alternative — work với NetworkPolicy
# vì kubectl tunnel đi qua API server):
# kubectl -n gp-data port-forward svc/apicurio-registry 8090:8080 &
# sleep 8
# curl -s http://127.0.0.1:8090/apis/registry/v2/system/info
```

## Step 9 — Apply alert rules + verify Prometheus load

### 9.1 Apply PrometheusRule

```sh
cd /Users/ngocshb/IdeaProjects/scented5apathy-ship-it/gp
kubectl apply -f platform/observability/alerts/kafka-rules.yaml
```

### 9.2 Verify

```sh
kubectl -n gp-observability get prometheusrule
# Kỳ vọng: genea-kafka-rules

# Verify 8 alerts khai báo
kubectl -n gp-observability get prometheusrule genea-kafka-rules -o jsonpath='{.spec.groups[*].rules[*].alert}'
# Kỳ vọng: KafkaUnderReplicatedPartitions KafkaOfflinePartitions
#          KafkaBrokerLogDiskPressure KafkaBrokerOutOfDisk
#          KafkaConsumerLag KafkaConsumerLagCritical
#          ApicurioRegistryDown ApicurioRegistryArtifactFailures
```

> Nếu chưa cài Prometheus Operator, lệnh `kubectl apply` cho
> `PrometheusRule` sẽ fail với `no matches for kind`. Để test
> smoke alert rules, cài thêm `kube-prometheus-stack`:
>
> ```sh
> helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
> helm install kube-prom prometheus-community/kube-prometheus-stack \
>   --namespace gp-observability --create-namespace
> ```

## Step 10 — Smoke test end-to-end

> **⚠️ Trước khi chạy Step 10.1, validate rằng topic-operator đã
> thật sự tạo topic trong Kafka — không chỉ KafkaTopic CR objects:**
>
> ```sh
> kubectl -n gp-data exec genea-kafka-kafka-0 -- \
>   /opt/kafka/bin/kafka-topics.sh \
>   --bootstrap-server localhost:9092 --list
> ```
>
> Kỳ vọng: **11 dòng bắt đầu bằng `genealogy.`** (cộng 4 dòng
> Strimzi internal `__consumer_offsets`, `strimzi.cruisecontrol.*`).
>
> Nếu chỉ thấy 4 dòng Strimzi internal mà KHÔNG có `genealogy.*`
> → entity-operator Admin API bug (xem Step 12 troubleshooting).
> **ĐỪNG** chạy Probe 2/3 — sẽ fail với `UNKNOWN_TOPIC_OR_PARTITION`
> / `TopicAuthorizationException`. Recreate cluster sạch (Step 11.3
>
> - Step 3 trở lên) trước khi tiếp tục.

### 10.1 Kafka cluster hoạt động

```sh
# Probe 1: exec vào 1 Kafka broker pod, list topic
kubectl -n gp-data exec genea-kafka-kafka-0 -- \
  /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
# Kỳ vọng: 11 topic names (genealogy.person.v1.v1, ...)

# Probe 2: tạo test event qua kafka-console-producer
kubectl -n gp-data exec genea-kafka-kafka-0 -- \
  bash -c 'echo "{\"eventId\":\"smoke-1\"}" | /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic genealogy.dlq.v1.v1'
# Kỳ vọng: không có error output

# Probe 3: consume back
kubectl -n gp-data exec genea-kafka-kafka-0 -- \
  /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic genealogy.dlq.v1.v1 \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 5000
# Kỳ vọng: {"eventId":"smoke-1"}

# Alternative: probe qua port-forward + Strimzi image
# kubectl -n gp-data port-forward svc/genea-kafka-kafka-bootstrap 9092:9092 &
# docker run --rm --network host \
#   quay.io/strimzi/kafka:0.45.2-kafka-3.8.0 \
#   /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
# kill $KAFKA_PF 2>/dev/null
```

### 10.2 Apicurio contract probe

> **NetworkPolicy ingress:** Apicurio NetworkPolicy (chart) chỉ
> allow ingress từ `gp-services`, `gp-workers`, `gp-observability`.
> Probe từ dev host phải exec vào pod hoặc port-forward (đi qua
> API server nên bypass NetworkPolicy).

```sh
# Probe 1: system info (verify SQL store)
kubectl -n gp-data exec deploy/apicurio-registry -- \
  curl -s http://localhost:8080/apis/registry/v2/system/info | head -c 200
# Kỳ vọng: {"name":"Apicurio Registry (SQL)", "version":"3.3.1", ...}

# Probe 2: tạo artifact
kubectl -n gp-data exec deploy/apicurio-registry -- \
  curl -s -X POST http://localhost:8080/apis/registry/v2/groups/genea-person-v1/artifacts \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: genea-person-v1" \
  -d '{"artifactType":"AVRO","content":"{\"type\":\"record\",\"name\":\"Person\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}"}'
# Kỳ vọng: 200 + {"id":"genea-person-v1", "version":"1", ...}

# Probe 3: tạo version mới (additive — BACKWARD compatible)
kubectl -n gp-data exec deploy/apicurio-registry -- \
  curl -s -X POST http://localhost:8080/apis/registry/v2/groups/genea-person-v1/artifacts/genea-person-v1/versions \
  -H "Content-Type: application/json" \
  -d '{"artifactType":"AVRO","content":"{\"type\":\"record\",\"name\":\"Person\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"},{\"name\":\"name\",\"type\":\"string\"}]}"}'
# Kỳ vọng: 200 + {"version":"2", ...}

# Probe 4 (optional): Verify Confluent shim OFF (chart contract)
kubectl -n gp-data exec deploy/apicurio-registry -- \
  curl -sI http://localhost:8080/apis/ccompat/v7/subjects | head -1
# Kỳ vọng: HTTP/1.1 404 Not Found

# Alternative: probe từ dev host qua port-forward
# kubectl -n gp-data port-forward svc/apicurio-registry 8090:8080 &
# APICURIO_PF=$!
# sleep 8
# curl -s http://127.0.0.1:8090/apis/registry/v2/system/info
# kill $APICURIO_PF 2>/dev/null
```

### 10.3 Static validation (không cần cluster)

```sh
cd /Users/ngocshb/IdeaProjects/scented5apathy-ship-it/gp

pnpm lint:kafka
# Kỳ vọng: [kafka] clean — kafka=ok, topics=ok, users=ok, apicurio=ok

helm lint platform/helm/genealogy-platform --strict
# Kỳ vọng: 0 chart failed

pnpm check:platform:baseline
# Kỳ vọng: [baseline] clean — namespaces=8, envs=3, versions=13

pnpm test:scripts
# Kỳ vọng: 36/36 tests pass (12 kafka + 5 baseline + 12 kong + 3 lint-yaml
# + 1 test-contracts + 1 lint-events + 1 generated)
```

### 10.4 Manual topic bootstrap (chỉ khi chạy dev local với `entityOperator.enabled: false`)

> **Khi nào dùng Step này:** Trên dev local, `values-dev.yaml` đã set
> `entityOperator.enabled: false` để workaround Strimzi 0.45.2
> entity-operator bug trên kind (xem Step 8.2 note + Step 12
> troubleshooting). Lúc đó chart chỉ deploy Kafka cluster + ZK,
> KHÔNG tạo KafkaTopic CRs và KHÔNG issue KafkaUser certs. Step này
> tạo topic thủ công qua `kafka-topics.sh` exec từ broker pod.
> Production / staging KHÔNG chạy Step này (entity-operator reconcile
> KafkaTopic/KafkaUser CR tự động).

Trước tiên disable `simple` ACL trên dev (vì không có user cert):

```sh
kubectl -n gp-data patch kafka genea-kafka --type=json \
  -p='[{"op":"remove","path":"/spec/kafka/authorization"}]'
kubectl -n gp-data wait --for=condition=Ready kafka/genea-kafka --timeout=300s
```

Tạo 11 topic match source-of-truth `platform/kafka/topics.yaml`:

```sh
for t in \
  genealogy.person.v1.v1:12 \
  genealogy.tree.v1.v1:6 \
  genealogy.relationship.v1.v1:12 \
  genealogy.audit.v1.v1:6 \
  genealogy.media.v1.v1:6 \
  genealogy.research.v1.v1:6 \
  genealogy.collaboration.v1.v1:6 \
  genealogy.notification.v1.v1:6 \
  genealogy.dlq.v1.v1:3 \
  genealogy.public.rebuild.v1.v1:3 \
  genealogy.search.rebuild.v1.v1:6; do
  name="${t%:*}"; parts="${t#*:}"
  kubectl -n gp-data exec genea-kafka-kafka-0 -- \
    /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
    --create --topic "$name" --partitions "$parts" \
    --replication-factor 1
done
```

Verify:

```sh
kubectl -n gp-data exec genea-kafka-kafka-0 -- \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list \
  | grep -c "^genealogy\."
# Kỳ vọng: 11
```

> **Production path (mặc định `entityOperator.enabled: true`):** Apply
> KafkaTopic + KafkaUser CRs từ chart `templates/components/kafka/` —
> entity-operator tự reconcile. Step 10.4 chỉ dành cho dev local
> workaround bug Strimzi 0.45.2.

## Step 11 — Cleanup + uninstall

### 11.1 Xoá E2.3 resources (giữ cluster)

```sh
cd /Users/ngocshb/IdeaProjects/scented5apathy-ship-it/gp

# Xoá Apicurio
kubectl delete -f <(helm template gp platform/helm/genealogy-platform \
  -f platform/helm/genealogy-platform/values-dev.yaml \
  --show-only templates/components/apicurio/registry.yaml)

# Xoá Kafka
for tpl in \
  templates/components/kafka/kafka.yaml \
  templates/components/kafka/topics.yaml \
  templates/components/kafka/users.yaml \
  templates/components/kafka/metrics-configmap.yaml \
  templates/components/kafka/network-policy.yaml; do
  kubectl delete -f <(helm template gp platform/helm/genealogy-platform \
    -f platform/helm/genealogy-platform/values-dev.yaml \
    --show-only "$tpl") --ignore-not-found
done

# Xoá alert rules
kubectl delete -f platform/observability/alerts/kafka-rules.yaml

# Xoá baseline
awk '...' /tmp/gp-e23-rendered.yaml | kubectl delete -f -
```

### 11.2 Uninstall Strimzi + Postgres + cert-manager

```sh
helm uninstall strimzi -n strimzi
helm uninstall postgres -n gp-data
kubectl delete -f https://github.com/cert-manager/cert-manager/releases/download/v1.15.0/cert-manager.yaml
```

### 11.3 Xoá toàn bộ cluster

```sh
kind delete cluster --name gp-e23

kind get clusters
# Kỳ vọng: (trống — cluster đã xoá)
```

### 11.4 Xoá kind binary (nếu muốn)

```sh
rm -f ~/.local/bin/kind
rm -f ~/.local/bin/kubectl
rm -rf /tmp/gp-kind /tmp/gp-e23-rendered.yaml
```

## Step 12 — Troubleshooting

| Vấn đề                                                                                                                                                                                                                    | Cách sửa                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `docker info` báo "Cannot connect to Docker daemon"                                                                                                                                                                       | Khởi động Docker Desktop / OrbStack trước                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `kind create cluster` fail với `Bind for 0.0.0.0:8090/8453 failed: port is already allocated`                                                                                                                             | Docker host port 8090 hoặc 8453 bị chiếm bởi service khác (không phải `gp-kong-smoke` — Kong dùng 8443). Chạy lại Step 3.0, kill container chiếm port hoặc đổi sang 8091/8454 trong config (cả host port lẫn ghi nhớ cho Step 8.5 + Step 10.2)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `helm install strimzi` fail với `Deployment.apps "strimzi-cluster-operator" is invalid: spec.template.spec.containers[0].resources.requests: Invalid value: "512Mi": must be less than or equal to memory limit of 384Mi` | Chart `strimzi-kafka-operator` 0.45.x có memory **limit** mặc định 384Mi. KHÔNG override `resources.requests.memory` > 384Mi khi không tăng limit tương ứng. Bỏ `--set resources.requests.memory=…` và dùng chart defaults, hoặc thêm `--set resources.limits.memory=512Mi` đồng thời                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `helm install strimzi` fail với `cannot re-use a name that is still in use` mặc dù `kubectl -n strimzi get pods` trống                                                                                                    | Lần install trước đã ghi Helm release secret dù Deployment chưa apply được. Chạy `helm uninstall strimzi -n strimzi` rồi `helm install` lại                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `kubectl apply` fail với `Namespace "gp-XXX" is invalid: metadata.labels: Invalid value: "..."`                                                                                                                           | Giá trị label chứa ký tự không hợp lệ theo regex K8s (`[A-Za-z0-9][-A-Za-z0-9_.]*`). Bug này đã được fix trong chart (chuyển `gp.genealogy/purpose` sang annotation — annotation chấp nhận chuỗi tự do). Nếu gặp, nâng cấp chart rồi `helm template` lại. Bảo vệ bằng `--dry-run=server` ở Step 7.2                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| Postgres pod `FailedCreate` với `must specify limits.cpu/limits.memory for: postgresql`                                                                                                                                   | `gp-data-quota` (chart baseline) yêu cầu pod phải có cả `limits.cpu` và `limits.memory`, không chỉ requests. Bitnami chart không tự set limits nếu không override. Set cả 4 field: `primary.resources.requests.cpu`, `primary.resources.requests.memory`, `primary.resources.limits.cpu`, `primary.resources.limits.memory`. Step 8.1 đã update                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| Postgres pod `Pending` mãi với `0/4 nodes are available` + `didn't find available persistent volumes to bind`                                                                                                             | StorageClass `gp-data-ssd` dùng `provisioner: kubernetes.io/no-provisioner` — chỉ dùng cho cluster production với cloud volume. Trên kind cluster dùng `standard` (map với `rancher.io/local-path` built-in): `--set primary.persistence.storageClass=standard`. Xóa PVC cũ trước khi reinstall: `kubectl delete pvc -n gp-data data-postgres-0`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| Apicurio log `Connection to postgres.gp-data.svc.cluster.local:5432 refused`                                                                                                                                              | DNS không resolve vì Bitnami chart tạo service `postgres-postgresql`, không phải `postgres`. Fix: `--set fullnameOverride=postgres` khi install Postgres. Step 8.1 đã update                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `kubectl: command not found`                                                                                                                                                                                              | Quay lại Step 4, cài `kubectl` rồi `source ~/.zshrc`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| Cluster tạo xong nhưng `kubectl get nodes` trống                                                                                                                                                                          | `kind export logs --name gp-e23` rồi xem log container                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `Kafka CR Ready=False` mãi (>10 phút)                                                                                                                                                                                     | `kubectl -n gp-data describe kafka genea-kafka` — thường là PVC chưa bound (dùng `standard` SC thay vì `gp-data-ssd`), ZK thiếu replicas (Strimzi 0.43 enforce `>= 3`), hoặc Strimzi operator chưa reconcile. Nếu stuck, `kubectl -n strimzi rollout restart deploy/strimzi-cluster-operator` để operator reconcile lại từ đầu                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `KafkaTopic` / `KafkaUser` không reconcile                                                                                                                                                                                | `kubectl get crd \| grep kafka.strimzi.io` — nếu trống, Step 6 fail. Nếu CRD có mà `kubectl get kafkatopics` trống, operator chưa nhìn thấy Kafka CR — check `kubectl -n strimzi logs deploy/strimzi-cluster-operator --tail=50`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| `KafkaTopic` reject với `metadata.labels: Invalid value: "tenantId+aggregateId"`                                                                                                                                          | Ký tự `+` không hợp lệ trong K8s label regex. Chart đã fix thành `tenant-and-aggregate`. Nếu gặp, cập nhật topics.yaml rồi `helm template` lại                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `KafkaUser` reject với `Unsupported value: "read"` / `"write"`                                                                                                                                                            | Strimzi schema enum là PascalCase (`Read`, `Write`, `Describe`). Chart đã fix. Nếu gặp, cập nhật users.yaml rồi `helm template` lại                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| Kafka CR reject với `metadataVersion: Invalid value: "number"`                                                                                                                                                            | Bare `metadataVersion: 3.8` parse thành float. Phải quote: `metadataVersion: "3.8"`. Chart đã fix                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| Kafka pod crash với `Missing required configuration client.quota.callback.static.kafka.admin.bootstrap.servers`                                                                                                           | **Strimzi 0.43 / 0.45 forbidden-list bug.** Operator silently strip `client.quota.callback.static.kafka.admin.bootstrap.servers` (và 2 key khác) khỏi ConfigMap — set `client.quota.callback.class` không thôi là KHÔNG ĐỦ. Chart đã fix bằng cách xoá toàn bộ block `client.quota.callback.*` (xem Step 8.2 note). Nếu gặp trên chart cũ: `kubectl -n gp-data patch kafka genea-kafka --type=json -p '[{"op":"remove","path":"/spec/kafka/config/client.quota.callback.class"},{"op":"remove","path":"/spec/kafka/config/client.quota.callback.static.kafka.admin.bootstrap.servers"},{"op":"remove","path":"/spec/kafka/config/client.quota.callback.static.produce"},{"op":"remove","path":"/spec/kafka/config/client.quota.callback.static.consume"},{"op":"remove","path":"/spec/kafka/config/client.quota.callback.static.request"},{"op":"remove","path":"/spec/kafka/config/client.quota.callback.static.excluded.principal.name.list"}]'` rồi `kubectl -n strimzi rollout restart deploy/strimzi-cluster-operator` để operator regenerate cm. Forbidden-list lifted only in Strimzi 0.46.x — bump lên 0.46.1+ sẽ cho phép restore block này. |
| Apicurio pod CrashLoopBackOff                                                                                                                                                                                             | `kubectl -n gp-data logs deploy/apicurio-registry --tail=30 \| grep -i error` — kiểm tra 5 nguyên nhân phổ biến: (1) Postgres Secret `apicurio-postgres` sai/missing, (2) Postgres service tên không phải `postgres` (cần `fullnameOverride=postgres`), (3) Apicurio dùng Apicurio 2.6.x image tag đã bị Docker Hub xoá (dùng `3.3`), (4) ConfigMap mount path sai (Apicurio 3.x bỏ), (5) Probe path `/q/health/ready` 404 (dùng `/apis/registry/v2/system/info`)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| Apicurio log `Driver does not support URL jdbc:h2:mem:`                                                                                                                                                                   | Apicurio 3.x không đọc ConfigMap `application.properties` ở `/deployments/config`. Phải truyền env var `QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://...` cho Postgres. Chart đã fix                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| Apicurio probe fail với 404 trên `/q/health/ready`                                                                                                                                                                        | Apicurio 3.x profile prod disable Quarkus health endpoints. Chart dùng `/apis/registry/v2/system/info` cho cả readiness + liveness. Nếu gặp, update linter scripts/check-platform-baseline.mjs để match path mới                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| Port-forward refused                                                                                                                                                                                                      | Kiểm tra `kubectl -n gp-data get svc apicurio-registry` — Service có sẵn chưa. Apicurio NetworkPolicy chặn ingress từ ngoài `gp-services` / `gp-workers` / `gp-observability`, nên phải exec vào pod để probe                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `connection refused` khi `kubectl apply`                                                                                                                                                                                  | Xem `docs/e23-kafka-apicurio-setup.md` §2.5.1 (lỗi OpenAPI validate)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `OutOfMemory` trên kind cluster                                                                                                                                                                                           | Tăng Docker Desktop memory (Settings → Resources → Memory = 8 GB) rồi `kind delete cluster` + recreate                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| Strimzi operator pod lỗi "webhook timeout"                                                                                                                                                                                | cert-manager chưa Ready — kiểm tra `kubectl -n cert-manager get pods`                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| Strimzi operator stuck không rollout sau khi apply chart mới                                                                                                                                                              | `kubectl -n strimzi rollout restart deploy/strimzi-cluster-operator` — operator đôi khi stuck sau khi chart update; restart giúp nó reconcile lại từ đầu                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `kafka-topics.sh --list` từ broker pod chỉ trả `__consumer_offsets` + `strimzi.cruisecontrol.*`, KHÔNG có 11 topic `genealogy.*` mặc dù `kubectl get kafkatopics` liệt kê đủ 11 với `READY` trống                         | **Strimzi 0.45.2 entity-operator Admin API bug** trên kind cluster — đã workaround bằng toggle `entityOperator.enabled: false` trong `values-dev.yaml` (chart chỉ deploy Kafka cluster, topic + ACL bootstrap thủ công qua Step 10.4). Recreate cluster sạch nếu đang chạy chart cũ: `kind delete cluster --name gp-e23` rồi chạy lại từ Step 3. Production giữ `enabled: true` (entity-operator reconcile KafkaTopic/KafkaUser CRs tự động). Nếu bug này xuất hiện trên staging / production (cluster thật), escalate lên ADR-E0.5-01 supersession lần 3: bump Strimzi 0.46.1+ (KRaft stability fixes)                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |

## Liên kết

- `docs/local-toolchain-setup.md` — bước 0 (cài Node, pnpm, Helm,
  Docker).
- `docs/e23-kafka-apicurio-setup.md` — chi tiết schema, retention
  policy, upgrade path cho E2.3.
- `.kiro/specs/genealogy-platform/evidence/E2.3.md` — bằng chứng
  hoàn thành task E2.3 (validation + security review).
- `platform/kafka/README.md` + `platform/apicurio/README.md` — tóm
  tắt source-of-truth cho 2 thư mục config-as-code.
