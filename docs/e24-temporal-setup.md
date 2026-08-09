# Cài đặt & Triển khai — E2.4 Temporal cluster

> Phạm vi: triển khai E2.4 (Temporal self-hosted cluster với
> namespace + retention + task queue + worker identity + visibility
> policy) theo `tasks.md` E2.4 và
> `.kiro/specs/genealogy-platform/evidence/E2.4.md`.
>
> Toàn bộ cấu hình là **config-as-code trong repo** (Helm
> StatefulSet + 2 Helm-hook Jobs + NetworkPolicy + ConfigMaps),
> không có gói cài đặt binary độc lập ngoài Docker image
> `temporalio/temporal:1.26.2` + `temporalio/admin-tools:1.26.2` +
> `temporalio/ui:2.5.0` (chart tự pull).

## 1. Tổng quan những gì được cài

| Thành phần                            | Phiên bản (ADR-E0.5-01) | Vai trò                                                     |
| ------------------------------------- | ----------------------- | ----------------------------------------------------------- |
| Temporal server (all-in-one)          | `1.26.2`                | Front-end + History + Matching + Worker (4 ports)           |
| Temporal admin-tools (Helm-hook Jobs) | `1.26.2`                | Reconcile namespaces + task queues                          |
| Temporal UI                           | `2.5.0`                 | Read-only operator console (gp-platform ingress only)       |
| PostgreSQL (gp-data cluster)          | `16-alpine`             | Persistence cho Temporal history (db `genea_temporal`)      |
| NetworkPolicy                         | n/a                     | Default-deny + per-component allow                          |
| ServiceAccount                        | n/a                     | 4 SAs: temporal / temporal-ui / namespace-init / queue-init |
| PrometheusRule                        | n/a                     | 6 alerts (server-down, latency, failure rate, depth…)       |

> **Không cần** cài Temporal binary trực tiếp trên node — Helm
> chart tự pull Docker image khi `helm install`.

## 2. Cài đặt trên Kubernetes (production / staging)

### 2.1 Yêu cầu trước

- Kubernetes cluster `>=1.28.0` (theo `platform/helm/genealogy-platform/Chart.yaml`).
- Helm `>=3.14` (đã verify bằng `alpine/helm:3.16.3`).
- `kubectl` đã cấu hình trỏ vào cluster.
- **PostgreSQL instance** trong namespace `gp-data` (chart chỉ
  tham chiếu secret + DNS; provisioning DB là trách nhiệm của
  E2.1 baseline). DB cần thiết: `genea_temporal`.
- **External Secrets / Vault** đã cấu hình (chart mount secret
  `temporal-postgres-credentials` qua `secretKeyRef`).
- **Istio mTLS mesh** đã bật (E2.5) — Temporal không yêu cầu
  mesh nhưng NetworkPolicy mặc định deny-all egress nên Istio
  phải allow pod-to-pod trong cluster.
- 8 namespace platform (`gp-platform`, `gp-edge`, `gp-bff`,
  `gp-services`, `gp-workers`, `gp-data`, `gp-observability`,
  `gp-argocd`) — E2.1 cung cấp.

### 2.2 Kiểm tra prerequisites

```bash
# Cluster version
kubectl version --short
# Kỳ vọng: Client v1.30+, Server v1.28+

# Helm version
helm version --short
# Kỳ vọng: v3.14.0+

# Kiểm tra 8 namespace baseline đã có
kubectl get ns -l app.kubernetes.io/part-of=genealogy-platform
# Kỳ vọng: gp-platform, gp-edge, gp-bff, gp-services, gp-workers,
#          gp-data, gp-observability, gp-argocd

# Kiểm tra PostgreSQL trong gp-data đang chạy
kubectl -n gp-data get svc postgres
# Kỳ vọng: postgres ClusterIP 5432/TCP

# Kiểm tra secret đã được External Secrets tạo
kubectl -n gp-data get secret temporal-postgres-credentials
# Kỳ vọng: TYPE=Opaque, có 2 keys: username, password
```

**Nếu lệnh nào fail**, xem **§6 Troubleshooting** dưới.

### 2.3 Tạo database `genea_temporal`

Chart KHÔNG tạo database — bạn phải tạo thủ công (một lần per
cluster):

```bash
# Lấy password từ secret đã có
PG_PASS=$(kubectl -n gp-data get secret temporal-postgres-credentials \
  -o jsonpath='{.data.password}' | base64 -d)

# Tạo database + role (idempotent)
kubectl -n gp-data exec -i postgres-0 -- psql -U postgres <<EOF
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'temporal') THEN
    CREATE ROLE temporal LOGIN PASSWORD 'REPLACE_ME_VIA_SECRET';
  END IF;
END
\$\$;

SELECT 'CREATE DATABASE genea_temporal OWNER temporal'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'genea_temporal')\gexec

\c genea_temporal
GRANT ALL PRIVILEGES ON SCHEMA public TO temporal;
EOF
```

**Kỳ vọng**: `\dt` sau khi connect trả empty schema (Temporal
sẽ tự tạo tables khi boot lần đầu).

**Nếu DB đã tồn tại**, lệnh `CREATE DATABASE` được skip qua
`WHERE NOT EXISTS`.

### 2.4 Apply umbrella chart

E2.4 là một block trong umbrella chart `genealogy-platform` (cùng
chart với E2.1 baseline + E2.2 Kong + E2.3 Kafka + E2.4 Temporal +
E2.5 Istio + …). Không cần cài chart riêng.

```bash
cd /path/to/gp

# Render trước để xem resources sẽ apply (dry-run, an toàn)
helm template genealogy-platform \
  platform/helm/genealogy-platform \
  --values platform/helm/genealogy-platform/values.yaml \
  --values platform/helm/genealogy-platform/values-saas.yaml \
  | grep -E "^(---|kind:|  name: temporal)" | head -40
# Kỳ vọng: ~92 resources, bao gồm:
#   - StatefulSet/temporal
#   - Deployment/temporal-ui  (nếu values-ui.enabled=true)
#   - Service/temporal + Service/temporal-ui
#   - ServiceAccount/{temporal, temporal-ui, temporal-namespace-init, temporal-task-queue-init}
#   - ConfigMap/{genea-temporal-namespace-config, genea-temporal-search-attrs,
#                 genea-temporal-dynamic-config, genea-temporal-task-queue-config,
#                 temporal-init-scripts}
#   - Job/{temporal-namespace-init, temporal-task-queue-init}  (Helm hook)
#   - NetworkPolicy/{temporal-default-deny, temporal-allow, temporal-ui-allow}
#   - ConfigMap/temporal-contract-stub  (trong components/contract-stubs.yaml)

# Apply qua Argo CD (khuyến nghị cho production)
argocd app sync genealogy-platform-saas --revision HEAD
# Argo CD đọc values-saas.yaml qua GitOps repo (xem E2.9)

# Hoặc apply trực tiếp bằng helm (cho test/dev)
helm upgrade --install genealogy-platform \
  platform/helm/genealogy-platform \
  --namespace gp-platform \
  --values platform/helm/genealogy-platform/values.yaml \
  --values platform/helm/genealogy-platform/values-saas.yaml \
  --wait --timeout 5m
```

**Kỳ vọng**:

- Helm render ~92 resources.
- Helm-hook Jobs (`temporal-namespace-init`,
  `temporal-task-queue-init`) chạy TRƯỚC StatefulSet, tạo 8
  namespaces + 10 task queues, delete-self khi thành công.
- StatefulSet `temporal` Ready sau ~30-60s (chờ Postgres +
  schema bootstrap).

### 2.5 Verify cluster lên

```bash
# 1. Pod đã chạy
kubectl -n gp-data get pods -l app.kubernetes.io/component=temporal
# Kỳ vọng: temporal-0   1/1 Running  0/0

# 2. UI đã chạy (SaaS / on-prem)
kubectl -n gp-data get pods -l app.kubernetes.io/component=temporal-ui
# Kỳ vọng: temporal-ui-xxx   1/1 Running  0/0

# 3. gRPC port 7233 mở trong pod
kubectl -n gp-data exec temporal-0 -- sh -c "ss -lnt | grep -E '7233|7234|7235|7239'"
# Kỳ vọng: 4 dòng LISTEN

# 4. Helm-hook Jobs đã chạy thành công
kubectl -n gp-data get jobs -l app.kubernetes.io/component=temporal
# Kỳ vọng: COMPLETIONS=1/1, STATUS=Complete (jobs tự delete sau khi xong)

# 5. Namespaces đã được tạo bằng admin-tools
# (exec admin-tools image để query)
kubectl -n gp-data run temporal-tctl --rm -it --restart=Never \
  --image=temporalio/admin-tools:1.26.2 \
  -- tctl --address temporal.gp-data.svc.cluster.local:7233 namespace list
# Kỳ vọng: 8 dòng namespace — genea-default, genea-genealogy, genea-media,
#          genea-search, genea-interop, genea-notify, genea-reporting, genea-dna

# 6. Search attributes đã được register
kubectl -n gp-data run temporal-tctl --rm -it --restart=Never \
  --image=temporalio/admin-tools:1.26.2 \
  -- tctl --address temporal.gp-data.svc.cluster.local:7233 \
     search-attribute list
# Kỳ vọng: 9 fields — TenantId (CustomStringField), WorkflowType
#          (CustomKeywordField), TaskQueue, Attempt, AggregateType,
#          AggregateId, MediaAssetId, TransferJobId, ConsentId
```

**Nếu bất kỳ step nào fail**, xem **§6 Troubleshooting**.

### 2.6 Verify alert rules

```bash
# PrometheusRule đã được apply
kubectl -n gp-observability get prometheusrule \
  -l app.kubernetes.io/component=temporal
# Kỳ vọng: NAME: genea-temporal-rules, AGE: <mới>

# Reload Prometheus (nếu không tự pick up)
kubectl -n gp-observability rollout restart deploy prometheus

# Query Prometheus để kiểm tra alerts loaded
kubectl -n gp-observability port-forward svc/prometheus 9090:9090 &
curl -s http://localhost:9090/api/v1/rules | \
  jq '.data.groups[].rules[] | select(.name | contains("Temporal")) | .name'
# Kỳ vọng: 6 alert names —
#   TemporalServerDown, TemporalWorkflowStartLatencyHigh,
#   TemporalWorkflowFailureRateHigh, TemporalActivityFailureRateHigh,
#   TemporalTaskQueueDepthHigh, TemporalReconciliationFailed
```

## 3. Cài đặt trên kind cluster (local development)

Đã verify trong `pnpm smoke:temporal` (xem
`.kiro/specs/genealogy-platform/evidence/E2.4.md` §Live
verification). Cho multi-node kind + umbrella chart, xem
`docs/local-k8s-setup.md` Step 11 (sẽ được viết trong E2.4
follow-up khi E2.9 GitOps + E1.6 air-gapped mirror sẵn sàng).

### 3.1 kind cluster đơn giản (chỉ Temporal)

```bash
# Tạo kind cluster với 1 control-plane + 2 worker
cat <<EOF > /tmp/temporal-kind.yaml
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
  - role: worker
  - role: worker
EOF
kind create cluster --config /tmp/temporal-kind.yaml --name gp-e24

# Apply baseline + umbrella chart (xem docs/local-k8s-setup.md Step 5+)
# (E2.1 + E2.2 + E2.3 + E2.4 đều apply qua cùng umbrella chart)

helm upgrade --install genealogy-platform \
  platform/helm/genealogy-platform \
  --namespace gp-platform \
  --values platform/helm/genealogy-platform/values.yaml \
  --values platform/helm/genealogy-platform/values-dev.yaml \
  --wait --timeout 5m
```

### 3.2 Cleanup

```bash
# Xóa Helm release
helm uninstall genealogy-platform -n gp-platform

# Xóa kind cluster
kind delete cluster --name gp-e24

# Xóa PostgreSQL database (nếu muốn reset)
kubectl -n gp-data exec -i postgres-0 -- psql -U postgres \
  -c "DROP DATABASE IF EXISTS genea_temporal;"
```

## 4. Nâng cấp / Rollback

### 4.1 Nâng cấp version Temporal

Mỗi lần bump version (theo Renovate weekly PR):

```bash
# 1. Cập nhật ADR-E0.5-01 nếu là major bump
# 2. Cập nhật 3 chỗ trong repo:
#    - platform/helm/genealogy-platform/values.yaml (components.temporal.image.tag + adminToolsImage.tag + ui.image.tag)
#    - platform/local/profile.yaml (temporal.image + adminToolsImage)
#    - platform/local/docker-compose.yml (image:)
# 3. Cập nhật ADR-E0.5-01 nếu cần ADR mới
# 4. helm upgrade (chart re-reconcile namespaces + task queues)
helm upgrade genealogy-platform platform/helm/genealogy-platform \
  --namespace gp-platform \
  --reuse-values \
  --set components.temporal.image.tag=1.27.0 \
  --set components.temporal.adminToolsImage.tag=1.27.0 \
  --wait --timeout 5m
```

### 4.2 Rollback version Temporal

```bash
# Xem revision trước
helm history genealogy-platform -n gp-platform

# Rollback (giữ nguyên values)
helm rollback genealogy-platform <revision> -n gp-platform --wait
```

**Lưu ý**: rollback Helm KHÔNG xóa namespaces / task queues đã
tạo trên Temporal server. Workflow history vẫn còn trong
PostgreSQL. Workflows đang chạy sẽ tiếp tục với version mới (nếu
pod restart) hoặc version cũ (nếu pod vẫn dùng image cũ).

### 4.3 Rollback namespace config

Nếu một namespace mới tạo sai (ví dụ `retentionDays` quá lớn):

```bash
# 1. Sửa platform/temporal/namespace-config.yaml (giảm retentionDays)
# 2. helm upgrade chart → Helm-hook Job chạy lại, cập nhật namespace
helm upgrade genealogy-platform platform/helm/genealogy-platform \
  --namespace gp-platform --reuse-values --wait
# 3. Verify
kubectl -n gp-data run temporal-tctl --rm -it --restart=Never \
  --image=temporalio/admin-tools:1.26.2 \
  -- tctl --address temporal.gp-data.svc.cluster.local:7233 \
     namespace describe genea-media | grep -i retention
```

## 5. Backup / Restore

E2.4 không tự backup. Backup đến từ E14.1 (PostgreSQL PITR + namespace
export).

### 5.1 Backup workflow state

```bash
# Snapshot Postgres database
pg_dump -h <postgres-host> -U temporal genea_temporal | \
  gzip > genea_temporal-$(date +%Y%m%d).sql.gz

# Hoặc dùng PITR (E14.1)
# Restore = restore DB snapshot → chart tự reconcile lại namespaces
```

### 5.2 Restore trên cluster mới

```bash
# 1. Provision cluster mới + apply E2.1 baseline
# 2. Restore Postgres từ snapshot
pg_restore -h <new-pg-host> -U temporal -d genea_temporal <snapshot>

# 3. Apply umbrella chart (Helm-hook Jobs sẽ tạo lại 8 namespaces)
helm upgrade --install genealogy-platform platform/helm/genealogy-platform \
  --namespace gp-platform \
  --values platform/helm/genealogy-platform/values.yaml \
  --values platform/helm/genealogy-platform/values-saas.yaml \
  --wait
```

## 6. Troubleshooting matrix

| #   | Symptom                                                | Nguyên nhân khả dĩ                                                                   | Cách xử lý                                                                                                                                                                                                                                                |
| --- | ------------------------------------------------------ | ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | `helm install` fail với "parse error at line N"        | YAML syntax sai trong values hoặc templates                                          | `helm template ... --debug 2>&1 \| grep -A5 "parse error"` để xem line cụ thể. Sửa YAML rồi chạy lại.                                                                                                                                                     |
| 2   | Pod `temporal-0` CrashLoopBackOff với "DB unreachable" | Postgres secret `temporal-postgres-credentials` chưa có hoặc DNS sai                 | `kubectl -n gp-data get secret temporal-postgres-credentials -o yaml` (phải có key username + password); `kubectl -n gp-data get svc postgres` (DNS phải resolve); `kubectl -n gp-data exec temporal-0 -- nc -zv postgres.gp-data.svc.cluster.local 5432` |
| 3   | Pod `temporal-0` CrashLoopBackOff với "no space left"  | PVC cache không đủ (default 10Gi)                                                    | `kubectl -n gp-data get pvc -l app.kubernetes.io/component=temporal`; xem capacity. Bump `components.temporal.persistence.cacheSize` trong values.                                                                                                        |
| 4   | Helm-hook Job `temporal-namespace-init` fail           | Lỗi parse YAML trong namespace-config.yaml, hoặc admin-tools không reach được server | `kubectl -n gp-data logs job/temporal-namespace-init`; chạy `pnpm lint:temporal` để tìm lỗi YAML. Nếu YAML OK kiểm tra DNS: `kubectl -n gp-data exec job/temporal-namespace-init -- nslookup temporal.gp-data.svc.cluster.local`                          |
| 5   | `tctl namespace list` không thấy `genea-default`       | Helm-hook Job fail hoặc Job bị skip                                                  | Chạy lại `helm upgrade --reuse-values` (Job re-fire). Nếu vẫn fail xem #4.                                                                                                                                                                                |
| 6   | `tctl search-attribute list` thiếu fields              | `dynamic-config.yaml` chưa mount hoặc whitelist bị xóa                               | `kubectl -n gp-data exec temporal-0 -- cat /etc/temporal/config/dynamicconfig/config.yaml \| grep -A20 visibility`; kiểm tra 9 fields. Nếu thiếu → `pnpm lint:temporal` fail → sửa source → `helm upgrade`.                                               |
| 7   | UI không accessible                                    | NetworkPolicy chặn, hoặc Service chưa được tạo                                       | `kubectl -n gp-data get svc temporal-ui`; `kubectl -n gp-data get networkpolicy -l app.kubernetes.io/component=temporal-ui`; xem `templates/components/temporal/network-policies.yaml`.                                                                   |
| 8   | Prometheus không scrape Temporal                       | ServiceMonitor chưa có hoặc NetworkPolicy chặn egress                                | `kubectl -n gp-data get svc temporal -o yaml \| grep -A5 annotations` (cần `prometheus.io/scrape: "true"`); `kubectl -n gp-observability exec deploy/prometheus -- wget -qO- http://temporal.gp-data.svc.cluster.local:9090/metrics`                      |
| 9   | Workflow bị stuck "Workflow task timeout"              | Worker pool thiếu hoặc activity quá lâu                                              | `tctl workflow describe --workflow_id <id>` xem history; `tctl task-queue describe --task-queue <name>`; check worker replicas; tăng `system.activity.scheduleToCloseTimeout` trong `dynamic-config.yaml`.                                                |
| 10  | PII / DNA leak trong search attributes (audit alert)   | Worker SDK không pseudonymize trước khi insert                                       | **STOP** worker deployment. Sửa worker code qua platform `tenant-pseudo-id` library. Replay events sau khi fix. Audit xem `privacy-and-legal-gate.md` §11.                                                                                                |
| 11  | `pnpm smoke:temporal` fail trên macOS                  | Docker daemon không chạy, hoặc port 7233/8088 đã bị chiếm                            | `docker ps` (kiểm tra container khác); `lsof -i :7233` / `lsof -i :8088`. Cleanup: `docker rm -f gp-temporal-smoke gp-pg-smoke; docker network rm gp-smoke`                                                                                               |
| 12  | Temporal dev server không start trong smoke            | Image pull fail (network), hoặc admin-tools không tìm thấy `tctl`                    | `docker pull temporalio/auto-setup:1.26.2` test thủ công. Smoke probe dùng `tctl` qua `--entrypoint /bin/sh` (xem scripts/smoke-temporal.mjs comment).                                                                                                    |

## 7. ADR references

- **ADR-E0.5-01** — Pinned baseline versions (Temporal 1.26.x).
- **ADR-E0.5-07** — Temporal distribution (self-host mandatory; SaaS
  only with in-region + on-prem parity contract).
- `privacy-and-legal-gate.md` §14 — DNA namespace retention 365d.

## 8. Liên kết

- `runbook/temporal.md` — operator runbook (incident playbooks).
- `.kiro/specs/genealogy-platform/evidence/E2.4.md` — completion
  evidence + validation report.
- `docs/local-k8s-setup.md` — kind cluster setup (dùng chung với
  E2.1–E2.4).
- `docs/e23-kafka-apicurio-setup.md` — sibling E2.3 install doc.
