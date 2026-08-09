# Cài đặt & Triển khai — E2.3 Strimzi Kafka + Apicurio Registry

> Phạm vi: triển khai E2.3 (Kafka cluster + Apicurio Schema Registry)
> theo `tasks.md` E2.3 và `.kiro/specs/genealogy-platform/evidence/E2.3.md`.
>
> Toàn bộ cấu hình là **config-as-code trong repo** (Helm CRDs
> Strimzi + Apicurio Deployment), không có gói cài đặt binary
> độc lập ngoài Docker image của Strimzi operator và Apicurio
> container.

## 1. Tổng quan những gì được cài

| Thành phần               | Phiên bản (ADR-E0.5-01)  | Vai trò                                          |
| ------------------------ | ------------------------ | ------------------------------------------------ |
| Strimzi Kafka operator   | `0.43.0` (Kafka `3.8.0`) | Vận hành Kafka cluster, topic, user              |
| Apicurio Schema Registry | `2.6.0`                  | Schema store cho event (Avro)                    |
| Postgres (cho Apicurio)  | `16-alpine`              | SQL storage cho Apicurio                         |
| PrometheusRule           | n/a                      | Alert cho under-replication, lag, disk, registry |

> **Không cần** cài binary Strimzi hay Apicurio trực tiếp — Helm
> chart tự kéo Docker image khi `helm install`.

## 2. Cài đặt trên Kubernetes (production / staging)

### 2.1 Yêu cầu trước

- Kubernetes cluster `>=1.28.0` (theo `Chart.yaml`).
- Helm `>=3.14` (đã verify bằng `alpine/helm:3.16.3`).
- `kubectl` đã cấu hình trỏ vào cluster.
- Strimzi operator (xem 2.2) — bắt buộc cho CRD `Kafka` /
  `KafkaTopic` / `KafkaUser`.
- cert-manager nếu cần TLS cho Apicurio UI (không bắt buộc cho
  CR).
- PostgreSQL instance trong namespace `gp-data` (cho Apicurio).

### 2.2 Cài Strimzi operator (nếu chưa có)

E2.3 không ship Strimzi operator — operator cài **độc lập** (một
lần cho cả cluster). Hai lựa chọn:

**Cách A — Helm repo (khuyến nghị):**

```bash
helm repo add strimzi https://strimzi.io/charts/
helm repo update
helm install strimzi strimzi/strimzi-kafka-operator \
  --namespace strimzi --create-namespace \
  --version 0.43.0 \
  --set watchAnyNamespace=true
```

**Cách B — OperatorHub / OLM (OpenShift):**

```bash
cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: strimzi-kafka-operator
  namespace: openshift-operators
spec:
  channel: stable
  installPlanApproval: Manual
  name: strimzi-kafka-operator
  source: operatorhubio-catalog
  sourceNamespace: olm
  startingCSV: strimzi-cluster-operator.v0.43.0
EOF
```

Sau khi cài xong, verify:

```bash
kubectl get crd | grep kafka.strimzi.io
# Mong đợi: kafkas.kafka.strimzi.io, kafkatopics.kafka.strimzi.io,
# kafkausers.kafka.strimzi.io, ...
```

### 2.3 Tạo namespace + label theo baseline

`templates/baseline/namespaces.yaml` đã có sẵn 8 namespace; chạy
baseline trước khi apply E2.3:

```bash
# Từ repo root
helm template gp platform/helm/genealogy-platform \
  -f platform/helm/genealogy-platform/values-saas.yaml \
  > /tmp/gp-rendered.yaml

# Apply baseline trước — chỉ Namespace + ResourceQuota.
# Không phụ thuộc yq (dùng awk + grep thay thế — an toàn cho YAML
# multi-doc vì tên kind nằm ở đầu mỗi document).
awk '
  BEGIN { print_buffer = 0 }
  /^---$/ {
    if (print_buffer) printf "%s\n---\n", buffer
    buffer = ""
    print_buffer = 0
    next
  }
  /^kind:[[:space:]]*(Namespace|ResourceQuota)$/ {
    print_buffer = 1
  }
  { buffer = buffer $0 ORS }
  END { if (print_buffer) printf "%s\n", buffer }
' /tmp/gp-rendered.yaml | kubectl apply -f -
```

Nếu đã cài `yq` (Mike Farah / Go version):

```bash
kubectl apply -f <(yq 'select(.kind == "Namespace" or .kind == "ResourceQuota")' /tmp/gp-rendered.yaml)
```

Verify:

```bash
kubectl get ns -l app.kubernetes.io/part-of=genealogy-platform
# Mong đợi: 8 namespace (gp-platform, gp-edge, gp-bff, gp-services,
# gp-workers, gp-data, gp-observability, gp-argocd)
```

### 2.4 Cài Apicurio dependencies (Postgres)

Apicurio cần Postgres schema `genea_apicurio`:

```bash
# Ví dụ với Postgres đã có sẵn trong cluster
kubectl exec -n gp-data deploy/postgres -- \
  psql -U ${POSTGRES_USER} -c "CREATE DATABASE genea_apicurio;"
```

Tạo Secret chứa credentials (do External Secrets / Vault inject
trong production; command dưới chỉ để dev):

```bash
kubectl create namespace gp-data --dry-run=client -o yaml | kubectl apply -f -

kubectl -n gp-data create secret generic apicurio-postgres \
  --from-literal=username="${APICURIO_POSTGRES_USER}" \
  --from-literal=password="${APICURIO_POSTGRES_PASSWORD}"
```

### 2.5 Apply Kafka + Apicurio resources từ umbrella chart

**Cách A — Khuyến nghị, không cần yq** (dùng `--show-only`):

```bash
for tpl in \
  templates/components/kafka/kafka.yaml \
  templates/components/kafka/topics.yaml \
  templates/components/kafka/users.yaml \
  templates/components/kafka/metrics-configmap.yaml \
  templates/components/kafka/network-policy.yaml \
  templates/components/apicurio/registry.yaml; do
  kubectl apply -f <(helm template gp platform/helm/genealogy-platform \
    -f platform/helm/genealogy-platform/values-saas.yaml \
    --show-only "$tpl")
done
```

> **Tại sao Cách A an toàn nhất:** `helm template --show-only` chỉ
> render đúng một template file rồi pipe thẳng cho `kubectl apply`
> — không cần parse YAML trên máy dev, không bị lệ thuộc OpenAPI
> schema của cluster. Nếu `kubectl` không tìm được cluster,
> command sẽ fail với "connection refused" thay vì fail mơ hồ
> trong bước validate.

**Cách B — Render toàn bộ rồi filter bằng awk** (giống 2.3,
không phụ thuộc yq):

```bash
helm template gp platform/helm/genealogy-platform \
  -f platform/helm/genealogy-platform/values-saas.yaml \
  > /tmp/gp-rendered.yaml

awk '
  BEGIN { print_buffer = 0 }
  /^---$/ {
    if (print_buffer) printf "%s\n---\n", buffer
    buffer = ""
    print_buffer = 0
    next
  }
  /^kind:[[:space:]]*(Kafka|KafkaTopic|KafkaUser|Deployment|Service|ConfigMap|NetworkPolicy)$/ {
    print_buffer = 1
  }
  { buffer = buffer $0 ORS }
  END { if (print_buffer) printf "%s\n", buffer }
' /tmp/gp-rendered.yaml | kubectl apply -f -
```

**Cách C — Nếu đã cài `yq`** (Mike Farah / Go version):

```bash
kubectl apply -f <(yq 'select(.kind == "Kafka" or
              .kind == "KafkaTopic" or
              .kind == "KafkaUser" or
              .kind == "Deployment" or
              .kind == "Service" or
              .kind == "ConfigMap" or
              .kind == "NetworkPolicy")' /tmp/gp-rendered.yaml)
```

### 2.5.1 Nếu gặp lỗi `connection refused` tới `localhost:8080`

Lỗi mẫu:

```
error validating "STDIN": error validating data: failed to
download openapi: Get "http://localhost:8080/openapi/v2":
dial tcp [::1]:8080: connect: connection refused
```

Nghĩa là `kubectl` đang cố client-side validate qua OpenAPI
endpoint của cluster mà cluster không khả dụng (chưa có cluster
nào được tạo / context sai / cluster chưa lên).

Cách xử lý theo thứ tự ưu tiên:

1. **Kiểm tra context cluster hiện tại:**

   ```bash
   kubectl config current-context
   # Nếu trống → chưa có cluster nào; tạo kind / k3d trước
   # (xem 3.3) hoặc trỏ vào cluster có sẵn:
   kubectl config use-context <your-context>
   ```

2. **Bỏ qua OpenAPI validation** (CHỈ khi bạn chắc chắn YAML
   đúng — đã pass `helm lint` + `pnpm lint:kafka`):

   ```bash
   awk '...' /tmp/gp-rendered.yaml | kubectl apply --validate=false -f -
   ```

3. **Dry-run để xác nhận YAML parse được** trước khi apply:

   ```bash
   awk '...' /tmp/gp-rendered.yaml | kubectl apply --dry-run=client -f -
   # Nếu OK → YAML hợp lệ, vấn đề chỉ là cluster chưa lên
   ```

4. **Render ra file rồi review thủ công** rồi apply:
   ```bash
   awk '...' /tmp/gp-rendered.yaml > /tmp/gp-e23.yaml
   less /tmp/gp-e23.yaml
   kubectl apply -f /tmp/gp-e23.yaml
   ```

Hoặc dùng `--show-only`:

```bash
for tpl in \
  templates/components/kafka/kafka.yaml \
  templates/components/kafka/topics.yaml \
  templates/components/kafka/users.yaml \
  templates/components/kafka/metrics-configmap.yaml \
  templates/components/kafka/network-policy.yaml \
  templates/components/apicurio/registry.yaml; do
  kubectl apply -f <(helm template gp platform/helm/genealogy-platform \
    -f platform/helm/genealogy-platform/values-saas.yaml \
    --show-only $tpl)
done
```

### 2.6 Apply alert rules (PrometheusRule)

```bash
kubectl apply -f platform/observability/alerts/kafka-rules.yaml
```

### 2.7 Verify sau khi cài

```bash
# 1. Kafka cluster Ready
kubectl -n gp-data get kafka genea-kafka
# Mong đợi: READY=True sau ~3-5 phút

# 2. Topic list reconciled
kubectl -n gp-data get kafkatopics | wc -l
# Mong đợi: 11 (đúng danh sách trong topics.yaml)

# 3. User list reconciled
kubectl -n gp-data get kafkausers | wc -l
# Mong đợi: 14 (1 admin + 7 producer + 6 consumer)

# 4. Apicurio Ready
kubectl -n gp-data get deploy apicurio-registry
kubectl -n gp-data exec deploy/apicurio-registry -- \
  curl -s http://localhost:8080/apis/registry/v2/system/info

# 5. Alerts loaded
kubectl -n gp-observability get prometheusrule genea-kafka-rules
```

## 3. Cài đặt local (developer workstation)

Có 2 cách: Docker Compose (đơn giản) hoặc Helm + kind (đầy đủ).

### 3.1 Yêu cầu

- Docker Desktop / OrbStack / colima (đã verify Docker `29.6.1`).
- Node 22 LTS + pnpm 9 (để chạy `pnpm test:scripts`).
- Không cần Helm / kubectl cho local profile (Docker Compose).

### 3.2 Cách A — Docker Compose (khuyến nghị cho dev)

Compose file đã pin sẵn image:

- `quay.io/strimzi/kafka:0.43.0-kafka-3.8.0`
- `apicurio/apicurio-registry:2.6`
- Postgres `16-alpine` (cho Apicurio)

```bash
cd platform/local

# 1. Tạo .env.local với secret
cat > .env.local <<EOF
POSTGRES_PASSWORD=dev-postgres-pw
KEYCLOAK_ADMIN_PASSWORD=dev-kc-pw
MINIO_ROOT_PASSWORD=dev-minio-pw
EOF

# 2. Khởi động chỉ Kafka + Postgres + Apicurio (không cần full stack)
docker compose up -d postgres kafka apicurio

# 3. Verify
docker compose ps
docker compose logs kafka | head -20
docker compose logs apicurio | head -20

# 4. Tạo topic thủ công (auto.create.topics.enable=false theo E2.3)
docker exec gp-kafka \
  /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --create --topic genealogy.person.v1.v1 \
  --partitions 12 --replication-factor 1

# 5. Tạo artifact thủ công
curl -X POST http://localhost:8081/apis/registry/v2/groups/genea-person-v1/artifacts \
  -H "Content-Type: application/json" \
  -H "X-Registry-ArtifactId: genea-person-v1" \
  -d @<(echo '{
    "artifactType": "AVRO",
    "content": "{\"type\":\"record\",\"name\":\"Person\",\"fields\":[{\"name\":\"id\",\"type\":\"string\"}]}"
  }')

# 6. (Tùy chọn) Apply per-artifact compatibility rule
curl -X POST "http://localhost:8081/apis/registry/v2/admin/rules?type=COMPATIBILITY" \
  -H "Content-Type: application/json" \
  -d '{"config":"BACKWARD"}'
```

### 3.3 Cách B — Helm + kind

```bash
# 1. Tạo kind cluster với Strimzi operator
kind create cluster --name gp-e23 --config - <<EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
  - role: worker
  - role: worker
EOF

# 2. Cài Strimzi operator (xem 2.2 Cách A)
helm install strimzi strimzi/strimzi-kafka-operator \
  --namespace strimzi --create-namespace \
  --version 0.43.0 \
  --set watchAnyNamespace=true

# 3. Cài cert-manager (nếu cần cho Apicurio ingress)
helm install cert-manager jetstack/cert-manager \
  --namespace cert-manager --create-namespace --set installCRDs=true

# 4. Apply umbrella chart với dev profile
cd /Users/ngocshb/IdeaProjects/scented5apathy-ship-it/gp
helm template gp platform/helm/genealogy-platform \
  -f platform/helm/genealogy-platform/values-dev.yaml \
  > /tmp/gp-dev.yaml

kubectl apply -f /tmp/gp-dev.yaml

# 5. Verify (xem 2.7)
```

### 3.4 Lệnh tiện ích dev

```bash
# Reset Apicurio Postgres database
docker exec gp-postgres psql -U genealogy -d genea_apicurio \
  -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

# Reset Kafka (xóa volume + recreate)
docker compose down -v kafka
docker compose up -d kafka

# Tail logs
docker compose logs -f kafka apicurio
```

## 4. Validation sau khi cài

### 4.1 Static (không cần cluster chạy)

```bash
cd /Users/ngocshb/IdeaProjects/scented5apathy-ship-it/gp

# Deep check Kafka + Apicurio config
pnpm lint:kafka
# Kỳ vọng: [kafka] clean — kafka=ok, topics=ok, users=ok, apicurio=ok

# Helm chart lint
helm lint platform/helm/genealogy-platform --strict
# Kỳ vọng: 0 chart failed

# Helm template render
helm template gp platform/helm/genealogy-platform \
  -f platform/helm/genealogy-platform/values-dev.yaml \
  > /tmp/rendered.yaml
grep -E "^kind:" /tmp/rendered.yaml | sort | uniq -c
# Kỳ vọng: 1 Kafka, 11 KafkaTopic, 14 KafkaUser, 1 Deployment (apicurio)

# Baseline invariants (E2.1+E2.2+E2.3)
pnpm check:platform:baseline
# Kỳ vọng: [baseline] clean — namespaces=8, envs=3, versions=13

# Unit tests
pnpm test:scripts
# Kỳ vọng: 31/31 tests pass
```

### 4.2 Live (cần Apicurio container)

```bash
pnpm smoke:apicurio
# Kỳ vọng: 4/4 PASS — registry live, artifact CRUD works,
#           additive version accepted
```

Script này tự động:

1. Khởi động `apicurio/apicurio-registry:latest` qua Docker nếu
   chưa chạy.
2. Probe cả port 8080 (main) và 9000 (management trên Apicurio
   3.x).
3. Tạo Avro artifact và thêm version mới (additive, backward
   compatible).
4. Dọn dẹp container khi xong.

## 5. Cấu hình sau khi cài

### 5.1 Bootstrap per-artifact compatibility rules

Apicurio mặc định global = `BACKWARD` (theo chart). Mỗi topic
schema cần rule riêng (đặc biệt `genea-search-rebuild-v1` =
`FORWARD`, `genea-public-rebuild-v1` = `FULL`).

Chạy seed script (E2.3 đã ship trong chart `compatibility:` block;
seed script `scripts/seed-apicurio.mjs` cần bổ sung nếu muốn tự
động):

```bash
# Ví dụ seed thủ công cho 1 artifact
curl -X PUT "http://apicurio.gp-data:8080/apis/registry/v2/groups/genea-search-rebuild-v1/artifacts/genea-search-rebuild-v1/rules" \
  -H "Content-Type: application/json" \
  -d '{"ruleType":"COMPATIBILITY","config":"FORWARD"}'
```

### 5.2 Tạo KafkaUser credential

User identity đến từ Istio mTLS cert (CN=<user-name>). Producer
gọi Kafka qua Istio sidecar; broker enforce `simple` authorizer
đối chiếu CN với `KafkaUser.spec.authentication.type: tls`.

Không cần thao tác thủ công — Istio + Strimzi tự xử lý.

### 5.3 Tune quota theo môi trường

Trong `values-<env>.yaml`:

```yaml
components:
  kafka:
    quota:
      produceBytesPerSec: 52428800 # 50 MB/s per client
      consumeBytesPerSec: 52428800
      requestsPerSec: 200000
```

> Lưu ý: Per-broker quota (StaticQuotaCallback) là tổng chia cho
> tất cả client. Client có thể vượt quota nếu producer pool phân
> tán đều.

## 6. Nâng cấp / Rollback

### 6.1 Nâng cấp Strimzi operator

```bash
# Upgrade operator (CRDs preserved)
helm upgrade strimzi strimzi/strimzi-kafka-operator \
  --namespace strimzi --version 0.43.1

# Kafka cluster tự rolling-restart nếu Kafka version trong spec
# đã tương thích. Nếu bump version (vd 3.8.0 → 3.9.0) phải
# edit spec.kafka.version + metadataVersion.
```

### 6.2 Nâng cấp Apicurio

```bash
# 1. Backup Postgres trước
kubectl exec -n gp-data deploy/postgres -- \
  pg_dump -U genealogy genea_apicurio > apicurio-$(date +%F).sql

# 2. Edit values-<env>.yaml: tag: 2.6.0 → 2.7.0
# 3. helm upgrade
helm upgrade gp platform/helm/genealogy-platform \
  -f platform/helm/genealogy-platform/values-saas.yaml
```

### 6.3 Rollback topic

```bash
# Pause consumer trước
kubectl -n gp-data scale deploy/<consumer> --replicas=0

# Recreate topic với config cũ
kubectl -n gp-data delete kafkatopic genealogy.person.v1.v1
kubectl apply -f <(helm template ... --show-only templates/components/kafka/topics.yaml)

# Resume consumer
kubectl -n gp-data scale deploy/<consumer> --replicas=<N>
```

### 6.4 Rollback schema

```bash
# Apicurio giữ lịch sử version; restore bằng cách publish lại
# version cũ hoặc dùng rule override
curl -X PUT "http://apicurio.gp-data:8080/apis/registry/v2/groups/<g>/artifacts/<a>/rules" \
  -d '{"ruleType":"COMPATIBILITY","config":"NONE"}'
```

## 7. Khắc phục sự cố

### 7.1 Kafka cluster không lên Ready

```bash
kubectl -n gp-data describe kafka genea-kafka
kubectl -n gp-data logs -l strimzi.io/cluster=genea-kafka --tail=100
```

Nguyên nhân thường gặp:

- PVC chưa bound (kiểm tra `kubectl get pvc -n gp-data`).
- StorageClass `gp-data-ssd` chưa tồn tại (chạy baseline trước).
- NetworkPolicy chặn Strimzi operator pod → check operator log.

### 7.2 Apicurio không Ready

```bash
kubectl -n gp-data logs deploy/apicurio-registry
# Tìm "Database connections health check" — nếu DOWN thì Postgres
# chưa reachable
kubectl -n gp-data get secret apicurio-postgres -o yaml
# Kiểm tra username / password khớp với Postgres
```

### 7.3 Topic bị thiếu sau khi apply

`auto.create.topics.enable=false` (theo E2.3) nghĩa là service
không tự tạo topic. Mọi topic phải được khai báo trong
`platform/kafka/topics.yaml` và reconcile qua `KafkaTopic` CR.

### 7.4 Alert bắn liên tục

```bash
# Check consumer lag
kubectl -n gp-data exec -it deploy/<consumer> -- \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server genea-kafka-kafka-bootstrap:9092 \
  --describe --group <group-id>
```

Nếu consumer bị stuck → restart pod, kiểm tra DB connection,
kiểm tra `min.insync.replicas` vs `replicationFactor`.

## 8. Liên kết

- `.kiro/specs/genealogy-platform/evidence/E2.3.md` — bằng chứng
  hoàn thành task.
- `.kiro/specs/genealogy-platform/architecture-decisions.md`
  ADR-E0.5-01 (pin version), ADR-E0.5-08 (Avro + BACKWARD).
- `.kiro/specs/genealogy-platform/design.md` §7.3 (Kafka eventing),
  §13 (platform operations).
- `platform/kafka/README.md` + `platform/apicurio/README.md` —
  tài liệu ngắn gọn cho 2 thư mục config-as-code.
- `docs/local-toolchain-setup.md` — cài Docker, pnpm, helm cơ bản.
