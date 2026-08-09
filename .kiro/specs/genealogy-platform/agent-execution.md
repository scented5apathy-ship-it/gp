# AI Agent Execution Contract

## 1. Mục đích

Tài liệu này là hợp đồng thực thi bắt buộc cho mọi AI Agent nhận task từ `tasks.md`. Agent phải triển khai đúng phạm vi, dùng các quyết định trong spec, cung cấp bằng chứng kiểm thử và chỉ đánh dấu task hoàn thành khi toàn bộ Definition of Done đạt.

## 2. Prompt chuẩn để giao một task

Thay `<TASK_ID>` bằng mã task, ví dụ `E1.3`, rồi gửi nguyên prompt sau cho Agent:

```text
Bạn là AI Software Engineering Agent chịu trách nhiệm triển khai task <TASK_ID> của Genealogy Platform.

Nguồn sự thật bắt buộc, đọc theo thứ tự:
1. .kiro/specs/genealogy-platform/requirements.md
2. .kiro/specs/genealogy-platform/design.md
3. .kiro/specs/genealogy-platform/tasks.md
4. .kiro/specs/genealogy-platform/agent-execution.md
5. Các ADR, AGENTS.md và tài liệu dự án có liên quan nếu tồn tại

Nhiệm vụ:
- Tìm chính xác task <TASK_ID> trong tasks.md và chỉ triển khai phạm vi của task đó.
- Kiểm tra dependency, ADR và prerequisite của task trước khi sửa code/config.
- Khảo sát codebase và tuân theo cấu trúc, convention, thư viện, security policy và toolchain hiện có.
- Ưu tiên cấu hình/tích hợp nền tảng đã chọn; không tự xây lại capability của Kong, Keycloak, OpenFGA, Temporal, Strimzi/Apicurio, Istio, Vault/KMS, S3/MinIO, Valkey, Flagsmith, Argo hoặc Grafana stack.
- Không tự chọn công nghệ còn được đánh dấu cần ADR hoặc cần quyết định của chủ dự án. Nếu gặp quyết định chưa chốt, dừng ở trạng thái BLOCKED, ghi rõ câu hỏi và không đánh dấu task hoàn thành.
- Triển khai production-grade: secure-by-default, tenant-safe, privacy-first, observable, testable, backward-compatible và phù hợp SaaS/on-premise.
- Không mở rộng ngoài phạm vi nếu không cần thiết để task hoạt động đúng.

Quy trình bắt buộc:
1. Đọc và tóm tắt mục tiêu, phạm vi, dependency, requirements và Definition of Done của <TASK_ID>.
2. Kiểm tra trạng thái dependency trong tasks.md và xác minh artifact thực tế; không chỉ tin checkbox.
3. Lập kế hoạch thay đổi theo file/component/contract/migration/test trước khi triển khai.
4. Triển khai theo contract-first; migration phải backward-compatible và event/API/schema phải qua compatibility checks.
5. Viết hoặc cập nhật unit, integration, contract, security, accessibility hoặc E2E tests phù hợp với task.
6. Chạy formatter, lint, typecheck, build, test và security checks được dự án định nghĩa.
7. Review diff để phát hiện secret, PII/DNA leak, tenant bypass, unsafe default, coupling xuyên service và capability bị tự xây trùng nền tảng.
8. Cập nhật tài liệu/config/runbook liên quan nếu task yêu cầu.
9. Tạo Completion Evidence theo mẫu trong agent-execution.md.
10. Chỉ đổi checkbox `- [ ] <TASK_ID>` thành `- [x] <TASK_ID>` khi mọi điều kiện hoàn thành đều đạt. Không đánh dấu epic cha hoàn thành nếu còn subtask hoặc acceptance chưa đạt.

Quy tắc trạng thái:
- DONE: triển khai đầy đủ, validation pass, có bằng chứng; được phép đánh dấu [x].
- BLOCKED: thiếu ADR/quyết định/credential/environment/dependency; giữ [ ], không workaround bằng giả định.
- PARTIAL: mới hoàn thành một phần hoặc validation chưa pass; giữ [ ], ghi phần còn thiếu.
- FAILED: implementation hoặc validation thất bại; giữ [ ], ghi lỗi và cách tái hiện.

Kết quả cuối phải báo cáo:
- Trạng thái: DONE/BLOCKED/PARTIAL/FAILED.
- Task ID và phạm vi đã thực hiện.
- Files/config/contracts/migrations đã thay đổi.
- Quyết định và giả định đã sử dụng.
- Lệnh validation cùng kết quả.
- Security/privacy/tenant-isolation checks.
- Completion Evidence location.
- Checkbox đã cập nhật hay chưa và lý do.

Không commit, push, tạo PR hoặc thay đổi task khác nếu không được yêu cầu rõ ràng.
```

## 3. Prompt ngắn dùng hằng ngày

Khi Agent đã được cấu hình luôn đọc spec, có thể dùng:

```text
Triển khai task <TASK_ID> theo .kiro/specs/genealogy-platform/agent-execution.md.
Đọc đầy đủ requirements.md, design.md và tasks.md trước khi làm.
Chỉ đánh dấu [x] khi dependency, acceptance, test, security/privacy checks và Completion Evidence đều đạt; nếu thiếu quyết định hoặc chưa hoàn tất, giữ [ ] và báo BLOCKED/PARTIAL.
```

## 4. Quy trình Agent phải thực hiện

### 4.1 Intake

Agent phải trích xuất và ghi nhận:

- Task ID và epic cha.
- Mục tiêu nghiệp vụ/kỹ thuật.
- Requirements `R/NFR` được tham chiếu.
- Dependency và trạng thái thực tế.
- Platform/tool liên quan.
- Artifact cần tạo hoặc sửa.
- Test và acceptance áp dụng.
- ADR hoặc quyết định còn thiếu.

### 4.2 Khảo sát

Trước khi sửa file, Agent phải:

- Đọc file cấu hình/build/dependency hiện có.
- Tìm implementation/test tương tự và tái sử dụng convention.
- Xác nhận thư viện đã tồn tại trước khi import.
- Xác nhận ownership boundary và contract giữa các service.
- Kiểm tra working tree để không ghi đè thay đổi ngoài task.

### 4.3 Kế hoạch

Kế hoạch phải chia thành các bước có thể kiểm chứng:

1. Contract/schema/config thay đổi.
2. Domain/application/platform integration.
3. Persistence/migration nếu có.
4. Authorization, privacy và tenant isolation.
5. Observability và failure handling.
6. Tests và validation.
7. Documentation/runbook và task evidence.

### 4.4 Triển khai

Agent phải tuân thủ:

- Contract-first với OpenAPI, Protobuf và Apicurio schemas.
- Flyway expand-contract; không migration phá hủy dữ liệu trong cùng release.
- jOOQ query luôn có tenant boundary khi dữ liệu tenant-scoped.
- OpenFGA chỉ quyết định quan hệ; ABAC vẫn kiểm living, DNA, consent và contextual deny.
- Temporal workflow deterministic; activity idempotent, có timeout, retry và heartbeat phù hợp.
- Kong chỉ xử lý edge/runtime policies; domain authorization ở ứng dụng.
- Istio xử lý workload identity/mTLS; không cấu hình retry chồng chéo gây amplification.
- Vault/KMS quản lý secret/key; không ghi secret vào code, Git, log, event hoặc workflow payload.
- Kafka consumer idempotent; event không chứa dữ liệu nhạy cảm không cần thiết.
- OpenFeature có safe fallback; feature flag không được bypass security/consent.
- SaaS và on-premise dùng cùng domain code, khác qua adapter/configuration.

### 4.5 Validation

Agent phải tìm và chạy lệnh thật của repository, tối thiểu gồm các nhóm áp dụng:

- Formatter/format check.
- Lint/static analysis.
- Typecheck/compile/build.
- Unit tests.
- Integration/Testcontainers tests.
- OpenAPI/Protobuf/Apicurio compatibility tests.
- Migration tests từ phiên bản được hỗ trợ.
- Tenant isolation và authorization negative tests.
- Security checks phù hợp: Semgrep, Trivy, Gitleaks, ZAP, Checkov.
- Accessibility/Playwright nếu thay đổi UI.
- Helm/Kubernetes/config validation nếu thay đổi platform.

Không được báo pass nếu lệnh chưa chạy. Nếu môi trường không cho chạy, trạng thái tối đa là `PARTIAL` hoặc `BLOCKED`.

## 5. Completion Evidence

Mỗi task hoàn thành phải có một file bằng chứng tại:

```text
.kiro/specs/genealogy-platform/evidence/<TASK_ID>.md
```

Ví dụ `E1.3` dùng `.kiro/specs/genealogy-platform/evidence/E1.3.md`.

Nội dung chuẩn:

```markdown
# Completion Evidence — <TASK_ID>

- Status: DONE
- Completed at: <RFC-3339 timestamp>
- Scope: <mô tả ngắn>
- Requirements: <R/NFR references>

## Changes

- <file/component/contract/migration và lý do>

## Decisions

- <ADR hoặc quyết định đã tuân theo>
- <giả định đã được phê duyệt; ghi None nếu không có>

## Validation

| Command/check | Result | Evidence              |
| ------------- | ------ | --------------------- |
| `<command>`   | PASS   | <summary/report path> |

## Security and privacy

- Tenant isolation: PASS/N/A — <bằng chứng>
- Authorization/ABAC: PASS/N/A — <bằng chứng>
- Secret/PII/DNA leakage: PASS — <bằng chứng>
- Dependency/config scan: PASS/N/A — <bằng chứng>

## Acceptance criteria

- [x] <criterion 1>
- [x] <criterion 2>

## Residual risks

- None
```

Nếu còn residual risk ảnh hưởng Definition of Done, status không được là `DONE`.

## 6. Quy tắc đánh dấu task

### 6.1 Được đổi sang `[x]` khi

- Dependency hoàn tất và artifact tồn tại.
- Toàn bộ nội dung task đã triển khai.
- Acceptance/DoD của subtask và epic áp dụng đều đạt.
- Tests/validation bắt buộc đã chạy và pass.
- Không còn lỗi critical/high hoặc blocker chưa giải quyết.
- Evidence file tồn tại với `Status: DONE`.
- Diff đã được self-review và chỉ chứa thay đổi thuộc phạm vi.

### 6.2 Phải giữ `[ ]` khi

- Thiếu ADR hoặc quyết định người dùng.
- Chỉ scaffold/mock nhưng task yêu cầu implementation hoàn chỉnh.
- Test bị skip, không chạy được hoặc thất bại.
- Chưa kiểm tenant/privacy/security.
- Còn manual step bắt buộc chưa xác nhận.
- Evidence thiếu hoặc ghi `PARTIAL/BLOCKED/FAILED`.

### 6.3 Cách cập nhật

Agent chỉ thay đúng dòng task:

```markdown
- [ ] E4.3 Date/calendar/place model
```

thành:

```markdown
- [x] E4.3 Date/calendar/place model
```

Không sửa mô tả task để làm giảm phạm vi hoặc hợp thức hóa implementation thiếu. Nếu phát hiện spec sai, Agent phải đề xuất thay đổi riêng và giữ task chưa hoàn thành.

### 6.4 Đánh dấu epic

Epic hiện được xem hoàn tất khi:

- Tất cả subtask trong epic là `[x]`.
- Phần `Kiểm thử/DoD` của epic đạt.
- Có evidence cho từng subtask.
- Có thể thêm dòng `**Epic status: DONE**` dưới tiêu đề epic; không thay thế checkbox subtask.

### 6.5 PARTIAL → DONE continuation protocol

Task đã commit ở trạng thái **PARTIAL** (Status: PARTIAL trong
`evidence/<TASK_ID>.md`) phải được đóng bằng một commit **riêng biệt**
theo continuation protocol này. Agent nhận yêu cầu "hoàn thiện
task" hoặc "PARTIAL → DONE" phải:

#### 6.5.1 Đọc evidence file của commit PARTIAL trước khi viết code

`evidence/<TASK_ID>.md` của lần commit PARTIAL đã liệt kê **đầy đủ
residual gaps** dưới dạng bảng có cột `Owner epic`. Agent phải đọc
file đó và **không tự phát hiện lại** các gap đã được sub-agent
review xác nhận.

#### 6.5.2 Liệt kê gap cụ thể trong prompt

Prompt giao continuation PHẢI:

1. **Dẫn link `evidence/<TASK_ID>.md`** của commit PARTIAL.
2. **Liệt kê rõ từng gap cụ thể** (đánh số 1, 2, 3, …) thay vì dùng
   cụm từ mơ hồ như "hoàn thiện", "đóng các gap còn lại",
   "DONE E3.1".
3. **Ghi rõ scope guard** — agent-execution.md §4.4 cấm mở rộng ngoài
   các gap đã liệt kê; nếu phát hiện gap mới phải đề xuất ADR / task
   riêng.
4. **Chỉ rõ yêu cầu cứng** cho gap đặc thù (ví dụ: "cấm tự xây SPI
   per ADR-E0.5-05"; "federated `groups` KHÔNG được force-sync trực
   tiếp vào tenant_groups user attribute"; "helm chart render phải
   pass thực sự, không được skip silently khi `helm` không có trên
   PATH").

#### 6.5.3 Anti-pattern

Các prompt dưới đây bị cấm vì không đủ rõ để agent PARTIAL → DONE
một cách an toàn:

- ❌ "Hoàn thiện E3.1" — agent tự quyết ưu tiên, có thể bỏ sót gap.
- ❌ "Đóng các gap còn lại" — không liệt kê → bỏ sót.
- ❌ "DONE E3.1" — yêu cầu status mà không nói rõ phải đóng gap nào.
- ❌ "Triển khai tiếp E3.1" — không có scope guard.
- ❌ "Hoàn thành E3.1 theo spec" — quá rộng, có thể vi phạm §4.4.

#### 6.5.4 Template prompt khuyến nghị

```text
Triển khai task <TASK_ID> theo .kiro/specs/genealogy-platform/agent-execution.md,
mở rộng commit scaffold PARTIAL <COMMIT_SHA> thành DONE.

Nguồn sự thật:
1. .kiro/specs/genealogy-platform/evidence/<TASK_ID>.md
   ← danh sách N residual gap cần close trong epic này
2. .kiro/specs/genealogy-platform/{requirements,design,tasks,
   architecture-decisions}.md (các ADR liên quan)

Yêu cầu cứng (KHÔNG được giữ [ ] cho đến khi mọi mục đạt):
1. <Gap #1 — mô tả + bằng chứng đạt>
2. <Gap #2 — mô tả + bằng chứng đạt>
…
N. <Gap #N — mô tả + bằng chứng đạt>

Quy trình:
1. Đọc evidence/<TASK_ID>.md trước (đã liệt kê residual gaps).
2. Viết TODO list cho N mục, gán thứ tự dependency.
3. Triển khai theo contract-first + test-first.
4. Chạy validation thực sự (không skip silently khi toolchain thiếu).
5. Self-review secret/PII/DNA leak + tenant bypass + cross-service coupling.
6. Cập nhật evidence/<TASK_ID>.md: Status DONE + timestamp mới +
   checklist N mục ✓.
7. Commit với message `<TASK_ID>: hoàn thiện … (DONE)`.
8. Chỉ flip checkbox `[ ] <TASK_ID>` → `[x]` khi toàn bộ N mục ✓
   + tests pass + evidence DONE.

Báo cáo cuối:
- Status: DONE / BLOCKED / PARTIAL / FAILED
- N mục residual gap: đã close mục nào + bằng chứng
- Lệnh validation + kết quả
- Checkbox đã cập nhật hay chưa + lý do
```

#### 6.5.5 Quy tắc commit

- Commit PARTIAL → DONE **KHÔNG ĐƯỢC** squash vào commit PARTIAL
  gốc. Hai commit tách biệt giúp `git log` truy vết được lịch sử.
  Nếu cần thiết khi review, dùng `git rebase -i` để gộp nhưng
  KHÔNG thay đổi nội dung evidence của commit trước.
- Commit message phải có prefix `<TASK_ID>:` (ví dụ
  `E3.1: hoàn thiện Keycloak OIDC runtime + integration tests (DONE)`).
- Không tạo commit mới nếu validation chưa pass thực sự (nếu
  `helm` không có trên PATH, smoke render phải exit với
  `BLOCKED` chứ không `PASS`).

## 7. Prompt review độc lập

Sau khi Agent triển khai, nên giao Agent khác review bằng prompt:

```text
Review độc lập task <TASK_ID> theo requirements.md, design.md, tasks.md và agent-execution.md.
Không sửa checkbox trước khi review xong.
Kiểm tra scope, correctness, architecture boundaries, use of approved platforms, backward compatibility, tenant isolation, authorization, privacy/DNA, tests và Completion Evidence.
Chạy lại validation quan trọng.
Nếu đạt, xác nhận DONE; nếu không đạt, đổi evidence status thành PARTIAL/FAILED, giữ hoặc trả checkbox về [ ] và liệt kê finding theo severity với file:line.
```

## 8. Ví dụ giao task

```text
Triển khai task E2.2 theo .kiro/specs/genealogy-platform/agent-execution.md.
Không xây gateway service. Cấu hình Kong Gateway runtime bằng declarative config/GitOps cho route, auth validation, CORS, request-size, rate limit và correlation ID; domain authorization phải giữ ở service.
Chạy config validation, smoke tests và security negative tests. Tạo evidence/E2.2.md và chỉ đánh dấu E2.2 [x] nếu toàn bộ DoD đạt.
```
