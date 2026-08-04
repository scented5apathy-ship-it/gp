# Scale and SLO — Discovery deliverable

> Companion to E0.3 of `tasks.md`. Anchors all numerical capacity, performance, durability, availability and residency promises that downstream epics will engineer against. Treat every value with a `DRAFT` marker as an input to E0.5 ADR closure; none of them is a binding SLA until product, security, privacy and operations have signed off in E0.6.

## 1. Mục đích và phạm vi

- Tổng hợp các con số scale (tenant, person/tree, media, DNA kit, request rate, concurrent job) cho ba mốc **Y1 / Y3 / Y5**.
- Cung cấp mô hình workload (read / write / search / async / event) và ba bộ synthetic dataset **10K / 100K / 1M person** để k6/Gatling, Testcontainers và bench harness tái sử dụng.
- Cố định SLO ban đầu (latency, availability, error budget), RPO/RTO, retention theo plan và data residency theo jurisdiction.
- Khóa các biến số còn mở thành đầu vào cho **E0.5 ADR** và **E0.6 ownership catalog**; không đặt thêm công nghệ mới ngoài stack đã chốt trong `design.md` §1.

## 2. Scale model theo mốc

Con số dưới đây mô tả **steady-state active** (không phải peak burst). Peak được cộng thêm hệ số ở §4. Mọi hàng mang nhãn `DRAFT` cần được E0.5 ADR khóa trước khi đưa vào Helm values mặc định.

### 2.1 Active tenant / user

| Hạng mục | Y1 (DRAFT) | Y3 (DRAFT) | Y5 (DRAFT) | Ghi chú |
|---|---|---|---|---|
| Active tenant (SaaS) | 1 000 | 5 000 | 20 000 | Bao gồm free + paid; on-premise tính riêng ở §2.6 |
| Paying tenant | 150 | 800 | 3 500 | Ước lượng conversion 15% |
| Enterprise tenant (on-premise) | 5 | 25 | 100 | Mỗi tenant có thể có dataset rất lớn |
| DAU user / tenant trung bình | 3 | 5 | 8 | Editor + viewer tính chung |
| Peak DAU / tenant (broadcast/webinar) | 200 | 500 | 1 500 | Dùng để tính burst search |

### 2.2 Active tree / person

| Hạng mục | Y1 (DRAFT) | Y3 (DRAFT) | Y5 (DRAFT) | Ghi chú |
|---|---|---|---|---|
| Tree / tenant trung bình | 3 | 5 | 8 | Family + research + partner |
| Tree lớn nhất (p99) | 10 000 person | 50 000 person | 250 000 person | Phục vụ benchmark tree-view |
| Total person toàn hệ (SaaS) | 30 M | 200 M | 1 B | Bao gồm cả LIVING bị redact |
| Total person active query / ngày | 1 M | 10 M | 60 M | Cache + OpenSearch |
| Person ingestion / ngày (write) | 50 K | 300 K | 1,5 M | Manual + GEDCOM import |

### 2.3 Media / object storage

| Hạng mục | Y1 (DRAFT) | Y3 (DRAFT) | Y5 (DRAFT) | Ghi chú |
|---|---|---|---|---|
| Object lưu trữ (image, doc, audio) | 20 M | 150 M | 800 M | Bản gốc + thumbnail + preview |
| Dung lượng raw (S3/MinIO) | 5 TB | 50 TB | 250 TB | Sau envelope encryption |
| Media / person trung bình | 0,7 | 1,2 | 1,5 | Không tính DNA |
| Media upload / giờ (peak) | 10 K | 40 K | 120 K | Có malware scan + preview job |
| Preview / PDF render / giờ (peak) | 2 000 | 8 000 | 25 000 | Gotenberg + media worker |

### 2.4 DNA kit

| Hạng mục | Y1 (DRAFT) | Y3 (DRAFT) | Y5 (DRAFT) | Ghi chú |
|---|---|---|---|---|
| DNA kit / tenant lớn | 50 | 500 | 5 000 | Kit có thể nhiều người |
| Tổng DNA sample lưu trữ | 100 K | 1 M | 10 M | Chỉ lưu derived + raw encrypted |
| Match job / ngày | 500 | 5 000 | 50 000 | Chạy Temporal activity |
| Match latency ceiling (p95) | 60 s | 30 s | 15 s | Không tính intake queue |

### 2.5 Request rate và concurrency

| Hạng mục | Y1 (DRAFT) | Y3 (DRAFT) | Y5 (DRAFT) | Ghi chú |
|---|---|---|---|---|
| Read RPS (BFF + service) | 5 K | 25 K | 80 K | BFF gộp UI request |
| Write RPS (BFF + service) | 300 | 1 500 | 5 000 | Bao gồm proposal/merge |
| Search RPS | 200 | 1 000 | 4 000 | p95 < 1 s theo NFR2 |
| Ghép nối event (Kafka) | 1 K msg/s | 5 K msg/s | 15 K msg/s | Partition per aggregate |
| Concurrent Temporal workflow | 200 | 1 000 | 4 000 | Worker pool scale theo queue |
| Concurrent render job | 80 | 300 | 1 000 | Gotenberg + media worker |
| Concurrent DNA match job | 20 | 100 | 500 | Resource-heavy, fenced pool |

### 2.6 On-premise enterprise

- Mỗi enterprise tenant vận hành stack riêng; target phần cứng tham chiếu (DRAFT) **64 vCPU / 256 GB RAM / 10 TB NVMe / 1 Gbps** cho cụm Kubernetes + PostgreSQL + Kafka + MinIO + object storage.
- Một enterprise tenant lớn có thể đạt **1–5 M person** và **20 TB media**; capacity planning tuân theo công thức §5 nhưng nhân hệ số headroom 1,5×.
- SLO on-premise mặc định bằng SaaS **trừ khi** khách hàng ký enterprise addendum; RPO/RTO có thể nâng lên 5 phút / 1 giờ với chi phí bổ sung.

## 3. Synthetic dataset

Mỗi bộ dataset phải đặt dưới `.kiro/specs/genealogy-platform/bench/datasets/<size>/` (E0.3 chỉ đặt spec, file seed sẽ tạo ở E1.2 / E1.3). Mọi dữ liệu đều **synthetic — không chứa PII/DNA thật**; deterministic seed (reproducible build theo NFR7).

### 3.1 Cấu trúc dataset `S`

| Bảng / aggregate | 10K | 100K | 1M | Quan hệ |
|---|---|---|---|---|
| Tenant | 1 | 5 | 20 | Owner |
| Tree | 10 | 200 | 4 000 | tenant |
| Person | 10 000 | 100 000 | 1 000 000 | tree |
| Relationship | 25 000 | 300 000 | 3 500 000 | person × person |
| Citation | 30 000 | 350 000 | 4 000 000 | person / relationship |
| Media | 7 000 | 120 000 | 1 200 000 | person / tree |
| Claim / proposal | 1 500 | 20 000 | 250 000 | person |
| Audit event | 100 000 | 1 M | 10 M | toàn hệ |
| DNA match record | 200 | 4 000 | 40 000 | person |

### 3.2 Phân bố thuộc tính

- **Living status:** 92% `LIVING`, 5% `PRESUMED_LIVING`, 3% `DECEASED` (cỡ 10K); phân bố này dùng để benchmark ABAC redaction.
- **Visibility:** 60% PRIVATE, 25% UNLISTED, 15% PUBLIC; gắn với 20% UNLISTED có token ngắn hạn.
- **Citation density:** trung bình 3 citation / person (p99 = 40); dùng để benchmark search facet.
- **Branch depth:** trung bình 5 đời, p99 = 12 (long-lived lineage).
- **Media:** 60% image, 25% document, 10% audio, 5% video preview; mỗi media có 3 derivative (thumb, preview, raw).
- **GEDCOM bundle:** 10% tree có 1 bundle import 5–25 MB; dùng để benchmark parser.

### 3.3 Synthetic generation rules

- Tên, địa danh, ngày tháng lấy từ bộ **faker seed** theo locale `vi-VN`, `en-US`, `fr-FR`; không import từ nguồn thật.
- DNA raw bytes là chuỗi hex random cố định trong seed; không mô phỏng marker y khoa.
- Deterministic seed theo công thức `seed = sha256(size + locale)`; golden output phải khớp byte-for-byte.
- Mỗi dataset kèm `manifest.json` ghi `tenant/person/media/audit` checksum, license marker và cảnh báo `SYNTHETIC_ONLY`.

### 3.4 Workload model

| Workload class | Mix Y1 | Hành vi | SLI gắn với |
|---|---|---|---|
| Browse tree (read) | 55% | Read-heavy, cacheable, neighborhood fetch | p95 latency, cache hit |
| Search (read) | 15% | Filter + faceted, có suggest | p95 search latency |
| Detail / person (read) | 10% | Includes redaction + ABAC | p95 latency, redaction cost |
| Write proposal/merge | 10% | Idempotent, optimistic concurrency | p95 write latency, conflict rate |
| Media upload | 6% | Multipart + scan + preview | Throughput, scan success |
| Async job (publish, DNA match) | 4% | Temporal workflow | Workflow success rate, heartbeat |

## 4. Peak burst và headroom

- Peak multiplier mặc định `burst = 3.0×` (NFR2 band) cho SaaS; on-premise `burst = 2.0×`.
- Headroom tối thiểu cho resource sizing (DRAFT — E0.5 ADR sẽ chốt):
  - CPU/Mem utilization p95 ≤ 65% (cluster), ≤ 70% (node).
  - PostgreSQL connection pool ≤ 75% capacity.
  - Kafka consumer lag ≤ 30 s cho critical topic, ≤ 5 phút cho async topic.
  - Object storage IOPS không vượt 70% provisioned.
- Backpressure: Kong giới hạn per-tenant RPS bằng plan quota; vượt ngưỡng trả 429 + `Retry-After` theo RFC 9457.
- Autoscale rule (Helm/KEDA stub): tăng replica khi metric vượt 70% trong 5 phút; giảm sau 15 phút dưới 40% (chống flap).

## 5. SLO ban đầu

Con số dưới đây là **SLO target ban đầu** (chưa phải SLA ký với khách). Mọi SLO phải đo được bằng SLI từ telemetry, có dashboard Grafana, alert Prometheus và runbook liên kết — yêu cầu của NFR5.

### 5.1 API latency

| Endpoint class | SLI | SLO target (DRAFT) | Burn-rate alert |
|---|---|---|---|
| Read phổ biến (GET tree, person, search) | p95 latency | < 300 ms (NFR2) | 2× budget / 1 h, 5× budget / 5 min |
| Write phổ biến (POST/PUT person, claim) | p95 latency | < 600 ms (NFR2) | 2× budget / 1 h, 5× budget / 5 min |
| Search nâng cao | p95 latency | < 1 000 ms (NFR2) | 2× budget / 30 min |
| Tree view initial render | p75 TTI | < 2 500 ms (NFR2) | 2× budget / 1 h |
| Public share first paint | p75 LCP | < 2 000 ms | 2× budget / 1 h |
| BFF aggregate (multi-call) | p95 | < 800 ms | 2× budget / 1 h |

### 5.2 Availability

| Service class | SLO target (DRAFT) | Error budget tháng |
|---|---|---|
| SaaS production (toàn hệ) | 99,9 % (NFR3) | 43,2 phút / tháng |
| API read path | 99,95 % | 21,6 phút / tháng |
| Search (best-effort) | 99,5 % | 3 giờ / tháng |
| Async job (publish, DNA match) | 99,0 % | 7 giờ 18 phút / tháng |
| Media pipeline | 99,0 % | 7 giờ 18 phút / tháng |
| On-premise base | 99,5 % (cluster HA) | 3 giờ 36 phút / tháng |

`error budget` sẽ là tín hiệu freeze release khi vượt (NFR7 + design §16.5 — ADR chốt ở E0.5).

### 5.3 RPO / RTO

| Tầng | RPO (DRAFT) | RTO (DRAFT) | Cơ chế |
|---|---|---|---|
| PostgreSQL (tenant data) | ≤ 5 phút (PITR + WAL ship) | ≤ 4 giờ (NFR3) | Backup mã hóa + PITR; enterprise có thể nâng 1 phút |
| Object storage (media) | ≤ 15 phút (cross-region replication) | ≤ 4 giờ | Versioning + lifecycle; on-premise replicate sang site thứ hai |
| Kafka | ≤ 1 phút (mirror + retention) | ≤ 1 giờ | MirrorMaker 2 / cluster link |
| Temporal namespace | ≤ 1 phút (DB snapshot) | ≤ 1 giờ | Namespace per env + backup |
| Keycloak realm | ≤ 15 phút (DB backup) | ≤ 2 giờ | Export/import runbook |
| Vault / KMS | Không mất (HSM hoặc unseal quorum) | Không downtime | Shamir + replication |
| Audit log | Không mất (append-only WORM) | Không downtime | Kafka + MinIO WORM bucket |

### 5.4 Error budget guardrails

- Burn-rate alert dựng theo Google SRE workbook: 1 h / 6 h / 24 h / 3 ngày window.
- Khi burn-rate 14,4× trong 1 h → page on-call; 6× trong 6 h → ticket + freeze release; 3× trong 24 h → review SLO.
- Privacy/DNA finding (severity 1) đóng băng budget ngay lập tức, bất kể burn-rate (NFR1, NFR7).

## 6. Retention theo plan

| Plan | Hot storage | Warm storage | Cold / archive | Hard delete |
|---|---|---|---|---|
| Free | 90 ngày | 1 năm | 2 năm | Sau 3 năm không hoạt động + 30 ngày grace |
| Pro | 1 năm | 3 năm | 5 năm | Sau 5 năm không hoạt động + 60 ngày grace |
| Enterprise | 3 năm | 7 năm | 10 năm (tùy hợp đồng) | Theo hợp đồng + legal hold |
| Audit log | 1 năm hot | 5 năm warm | 7 năm cold (immutable) | Chỉ khi hết legal hold |
| DNA raw | Mã hóa envelope; KHÔNG cold | 7 năm (consent kéo dài) | Không archive | Khi consent thu hồi + retention |
| Media | 1 năm hot | 5 năm warm | Theo plan | Cùng Person xóa mềm |

- Mọi retention policy chạy qua Temporal scheduled workflow; mỗi lần chạy phải tạo `deletion_evidence` không chứa nội dung đã xóa (requirements §6.5).
- Backups giữ tối thiểu 30 ngày local + 90 ngày offsite; mã hóa envelope với key từ Vault/KMS.
- Legal hold vô hiệu hóa retention workflow cho tới khi được gỡ; auditor phải xuất được evidence chain-of-custody.

## 7. Data residency

| Region | Plan | Residency yêu cầu | Giải thích |
|---|---|---|---|
| EU (Frankfurt, Paris) | Pro / Enterprise | Dữ liệu PII/DNA không rời EU | Keycloak, Postgres, MinIO, Kafka đặt trong region EU |
| US (Virginia, Oregon) | Pro / Enterprise | Dữ liệu PII/DNA không rời US | Tương tự EU |
| APAC (Singapore, Tokyo) | Pro / Enterprise | Residency theo jurisdiction KL/PIPL/JP | Đặt region theo tenant onboarding |
| On-premise | Enterprise | Không rời site khách hàng | Toàn bộ stack chạy nội bộ |
| Cross-region fallback | N/A | Disaster recovery DR site **cùng jurisdiction** | Không replicate PII/DNA ra ngoài region chính |

- Tenant khi tạo phải chọn `data_residency = {EU, US, APAC, ON_PREMISE}`; trường này không thể đổi sau đó (chỉ có thể migrate qua export/import bundle có audit).
- Backup offsite phải cùng jurisdiction; DR test hàng quý.
- Flagsmith feature flag `residency.eu_only`, `residency.apac_only` bật/tắt capability per region (theo E0.4).
- Cloudflare / CDN chỉ cache asset tĩnh không chứa PII; metadata tuân theo §6.6 của requirements.

## 8. Capacity model (công thức)

Cho mỗi service, capacity Y-năm suy ra từ workload §3.4 × peak §4:

```
read_rps_p95 = read_rps_yN × burst × (1 + cache_miss_penalty)
write_rps_p95 = write_rps_yN × burst × (1 + retry_factor)
worker_count = ceil(workflow_p95_load / worker_throughput × 1.2 headroom)
storage_growth = sum(object_per_person × person_active_yN) × (1 + replica_overhead)
```

- `cache_miss_penalty = 1.15` (Redis/Valkey hit ratio 85%).
- `retry_factor = 1.4` (idempotent retry với jitter).
- `worker_throughput` đo bằng Testcontainers benchmark trong E1.2 / E1.3.
- `replica_overhead = 1.3` (S3/MinIO replicate 3 site cho HA).

Mỗi service phải ship bảng capacity trong `runbook/<service>.md` (E0.6 ownership).

## 9. Benchmark plan

| Test | Mục tiêu | Tool | Threshold |
|---|---|---|---|
| API read p95 | NFR2 | k6 | < 300 ms @ 5K RPS |
| API write p95 | NFR2 | k6 | < 600 ms @ 300 RPS |
| Tree view TTI | NFR2 | Playwright + synthetic 10K | < 2 500 ms p75 |
| Search p95 | NFR2 | k6 + OpenSearch stub | < 1 000 ms @ 200 RPS |
| GEDCOM import | E2.3 | Gatling + 25 MB bundle | < 60 s end-to-end |
| Publish tree | E2.4 | Playwright + tree 10K | < 90 s end-to-end |
| DNA match | E2.5 | Temporal test env | < 60 s p95 Y1, 15 s p95 Y5 |
| Tenant isolation | E3.4 | Negative test suite | 0 escape |
| Restore drill | NFR3 | Quarterly | RPO/RTO < target |

Mọi benchmark chạy trên infrastructure gần production (node type, network, storage) và trên dataset §3. Kết quả phải được lưu tại `.kiro/specs/genealogy-platform/evidence/benchmark/`.

## 10. Capacity decision table (DRAFT)

| Resource | Y1 | Y3 | Y5 | Lý do |
|---|---|---|---|---|
| BFF replica | 6 | 24 | 72 | 5K RPS / replica tham chiếu |
| Domain service replica | 4 / service | 12 / service | 32 / service | Stateless, autoscale |
| PostgreSQL instance | 1 primary + 2 read replica (8 vCPU/32 GB) | 1 primary + 4 read replica (16 vCPU/64 GB) | 1 primary + 6 read replica (32 vCPU/128 GB) + Citus shard | RPS + storage |
| Kafka broker | 3 broker KRaft 100 GB | 6 broker KRaft 500 GB | 12 broker KRaft 2 TB | 15K msg/s + retention |
| MinIO / S3 | 4 node 10 TB NVMe | 12 node 10 TB NVMe | 24 node 10 TB NVMe | Replication 3 |
| Redis/Valkey | 3 node 8 GB | 6 node 32 GB | 12 node 64 GB | Session + cache |
| Temporal worker | 8 | 24 | 64 | Workflow concurrency |
| Gotenberg + media worker | 6 | 16 | 40 | Render job |
| Keycloak | 3 node RAFT 8 vCPU | 6 node 16 vCPU | 12 node 32 vCPU | Realm + cache |
| OpenFGA | 1 cluster 3 node | 3 cluster 3 node | 9 cluster 3 node | Tuple scale |

Con số trên dùng để dựng Helm values mặc định; E0.5 ADR sẽ chốt sizing tool cụ thể (KEDA / HPA / cluster-autoscaler).

## 11. Open questions cho E0.5 ADR

Mỗi mục dưới đây là input bắt buộc cho E0.5; nếu thiếu, E0.3 chỉ đạt PARTIAL.

1. Chốt `burst multiplier` (đề xuất 3.0× SaaS, 2.0× on-premise) và headroom utilization band.
2. Chốt cache hit ratio mục tiêu (đề xuất 85%) và chiến lược cache invalidation.
3. Chốt RPO/RTO từng tầng (§5.3) sau khi đối chiếu DR drill mock.
4. Chốt retention per plan (§6) và khả năng export bundle cho tenant chuyển region.
5. Chốt data residency quy tắc cross-region cho backup/DR (§7).
6. Chốt error budget burn-rate alert threshold theo SRE workbook.
7. Chốt cluster topology cho on-premise enterprise (single-cluster vs multi-cluster).
8. Chốt capacity tool: HPA + KEDA hay cluster-autoscaler; chốt Postgres operator (Zalando / CloudNativePG).
9. Chốt chi phí vận hành / tenant / tháng (TCO) cho SaaS Pro / Enterprise.
10. Chốt golden signal coverage: tỉ lệ service có RED + USE dashboard trước release.

## 12. Liên kết downstream

- **E0.4** tham chiếu §7 để chốt cờ residency.
- **E0.5** tham chiếu §11 để chốt ADR.
- **E0.6** tham chiếu §10 để đưa capacity vào ownership catalog và RACI.
- **E1.2 / E1.3** đặt seed synthetic dataset §3 và benchmark tool §9.
- **E2.2 / E2.3** tham chiếu §5.1 để dựng SLO dashboard và alert.
- **E3 / E4 / E5** tham chiếu §5 để chọn async channel / caching strategy.
- **E10.4** tham chiếu §5.3 RPO/RTO để dựng DR runbook.
- **E11** tham chiếu §6 để dựng retention workflow.
