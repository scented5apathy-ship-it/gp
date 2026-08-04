# Genealogy Platform — Requirements

## 1. Tầm nhìn

Xây dựng nền tảng quản lý gia phả toàn cầu, chuyên nghiệp và hiện đại, phục vụ đồng thời:

- SaaS đa tenant cho cá nhân, gia đình, dòng họ và tổ chức.
- Enterprise on-premise với cùng mô hình chức năng và gói triển khai độc lập.
- Web responsive/PWA, ưu tiên riêng tư, cộng tác có kiểm soát và khả năng truy nguyên dữ liệu.

Hệ thống phải biểu diễn được quan hệ gia đình phức tạp, lưu giữ bằng chứng lịch sử, media và dữ liệu DNA nhạy cảm; hỗ trợ nhiều ngôn ngữ, lịch, múi giờ và địa danh lịch sử.

## 2. Thuật ngữ

- **Tenant**: không gian khách hàng độc lập về dữ liệu, cấu hình, thành viên và hạn mức.
- **Genealogy/Tree**: một gia phả thuộc tenant.
- **Person**: cá nhân lịch sử hoặc hiện tại trong gia phả; không nhất thiết có tài khoản.
- **User**: chủ thể đăng nhập qua OIDC.
- **Claim**: một khẳng định về cá nhân, quan hệ hoặc sự kiện.
- **Citation/Source**: trích dẫn và nguồn chứng minh claim.
- **Living person**: người còn sống hoặc được suy luận là có khả năng còn sống.
- **DNA kit**: bộ dữ liệu xét nghiệm DNA được quản lý theo consent riêng.

## 3. Vai trò và nguyên tắc quyền

Vai trò tenant mặc định: `OWNER`, `ADMIN`, `MEMBER`, `AUDITOR`, `BILLING_ADMIN`.

Vai trò theo gia phả mặc định: `TREE_ADMIN`, `EDITOR`, `CONTRIBUTOR`, `VIEWER`, `GUEST`.

- Quyền áp dụng theo tenant, gia phả, nhánh, hồ sơ và loại tài nguyên.
- Một người dùng có thể giữ nhiều vai trò; quyền cấm tường minh thắng quyền cho phép.
- Hồ sơ người còn sống, DNA và tài liệu nhạy cảm có policy riêng.
- Mọi kiểm tra quyền phải được thực thi phía server; UI chỉ phản ánh quyền đã cấp.

## 4. Yêu cầu chức năng

### R1. Tenant, tổ chức và vòng đời thuê bao

**User story:** Là chủ tổ chức, tôi muốn tạo và quản trị không gian độc lập để quản lý nhiều gia phả an toàn.

**Acceptance criteria**

1. WHEN một tenant được tạo THEN hệ thống SHALL khởi tạo owner, cấu hình locale, timezone, chính sách riêng tư và hạn mức mặc định.
2. WHEN người quản trị mời người dùng THEN hệ thống SHALL hỗ trợ email/link có hạn dùng, vai trò, phạm vi và thu hồi lời mời.
3. WHEN tenant bị đình chỉ hoặc yêu cầu xóa THEN hệ thống SHALL chặn truy cập, áp dụng retention, export và quy trình xóa có audit.
4. WHERE triển khai SaaS THEN hệ thống SHALL hỗ trợ plan, quota, usage metering và billing integration qua adapter.
5. WHERE triển khai on-premise THEN hệ thống SHALL hoạt động không phụ thuộc dịch vụ cloud độc quyền và hỗ trợ license/config ngoại tuyến.

### R2. Xác thực và danh tính

1. WHEN người dùng đăng nhập THEN hệ thống SHALL dùng OIDC Authorization Code + PKCE.
2. IF tenant yêu cầu MFA hoặc enterprise federation THEN hệ thống SHALL thực thi qua OIDC provider.
3. WHEN phiên, thiết bị hoặc quyền thay đổi THEN hệ thống SHALL cho phép thu hồi phiên và ghi audit.
4. WHEN user liên kết với Person THEN hệ thống SHALL yêu cầu xác minh và không tự động công khai hồ sơ Person.
5. Hệ thống SHALL hỗ trợ account recovery, email verification và SSO theo domain tenant.

### R3. Quản lý gia phả

1. Người có quyền SHALL tạo, sao chép, lưu trữ, phục hồi, chuyển quyền và xóa gia phả.
2. Mỗi gia phả SHALL có metadata, slug, ảnh đại diện, locale, timezone, lịch mặc định, quy ước tên và chính sách cộng tác.
3. Mỗi gia phả SHALL có một trong ba visibility: `PRIVATE`, `UNLISTED`, `PUBLIC`.
4. `PRIVATE` SHALL chỉ cho người được mời truy cập.
5. `UNLISTED` SHALL chỉ truy cập bằng link/mã có thể hết hạn hoặc thu hồi và SHALL không được index.
6. `PUBLIC` SHALL có trang công khai có thể tìm kiếm nhưng SHALL che dữ liệu người sống và dữ liệu nhạy cảm theo policy.
7. Hệ thống SHALL cho phép cấu hình sửa trực tiếp, duyệt thay đổi hoặc mô hình kết hợp theo vai trò/phạm vi.

### R4. Hồ sơ cá nhân

1. Hệ thống SHALL quản lý nhiều tên, tên bản địa, bí danh, giới tính mô tả, đại từ, trạng thái sống, nghề nghiệp, tiểu sử, danh hiệu, định danh ngoài và ghi chú.
2. Trường ngày SHALL hỗ trợ chính xác, ước lượng, khoảng, trước/sau, không rõ và nhiều hệ lịch.
3. Địa điểm SHALL hỗ trợ tọa độ, cấp hành chính, tên theo thời kỳ và liên kết địa danh chuẩn hóa.
4. Hồ sơ SHALL có privacy level theo trường và policy riêng cho người sống/trẻ vị thành niên.
5. Hệ thống SHALL phát hiện trùng lặp, hỗ trợ so sánh/merge có preview, bảo toàn nguồn và có thể hoàn tác.
6. Mọi thay đổi SHALL có version, actor, timestamp, lý do và diff.

### R5. Quan hệ và mô hình phả hệ

1. Hệ thống SHALL hỗ trợ cha/mẹ-con sinh học, nhận nuôi, nuôi dưỡng, giám hộ, cha/mẹ kế, phối ngẫu, bạn đời và quan hệ tùy chỉnh.
2. Quan hệ SHALL có thời gian hiệu lực, trạng thái, mức độ chắc chắn, ghi chú, nguồn và privacy.
3. Hệ thống SHALL hỗ trợ nhiều phối ngẫu, gia đình tái hôn, phụ huynh không xác định và quan hệ tranh chấp.
4. WHEN tạo hoặc sửa quan hệ THEN hệ thống SHALL phát hiện chu trình bất hợp lệ, tự-quan hệ và cảnh báo mâu thuẫn thời gian.
5. Claim chưa chắc chắn SHALL cùng tồn tại với giả thuyết khác và không bị coi là dữ kiện xác nhận.

### R6. Trực quan hóa cây và điều hướng

1. Hệ thống SHALL cung cấp pedigree, descendant, fan chart, hourglass và family view.
2. Người dùng SHALL pan, zoom, collapse branch, chọn root, lọc quan hệ và số thế hệ.
3. Cây lớn SHALL tải tăng dần, dùng projection phía server và không tải toàn bộ graph vào client.
4. Hệ thống SHALL có minimap, breadcrumbs, tìm nhanh, chế độ in và export ảnh/PDF.
5. Giao diện SHALL usable bằng bàn phím, screen reader và màn hình di động theo WCAG 2.2 AA.

### R7. Sự kiện, dòng thời gian và bản đồ

1. Hệ thống SHALL quản lý sự kiện cá nhân, gia đình và gia phả: sinh, mất, kết hôn, ly hôn, cư trú, di cư, quân ngũ, học vấn, tôn giáo và loại tùy chỉnh.
2. Sự kiện SHALL liên kết nhiều người với vai trò khác nhau, ngày, nơi, nguồn, media và privacy.
3. Hệ thống SHALL hiển thị timeline cá nhân/gia đình và bản đồ hành trình theo thời gian.
4. Hệ thống SHALL hỗ trợ ngày lặp cho giỗ, sinh nhật, lễ tưởng niệm và nhắc việc theo timezone.
5. Lịch SHALL hỗ trợ chuyển đổi và hiển thị nhiều hệ lịch mà không làm mất giá trị gốc.

### R8. Nguồn, trích dẫn và nghiên cứu

1. Hệ thống SHALL quản lý repository, source, citation, transcript, page/locator, URL, quality và attachment.
2. Claim quan trọng SHALL liên kết được nhiều citation với đánh giá độ tin cậy.
3. Hệ thống SHALL cung cấp research log, task, giả thuyết, conflict và trạng thái chứng minh.
4. Người dùng SHALL xem provenance từ dữ kiện đến citation, source và file gốc.
5. Import SHALL giữ nguyên nguồn gốc và phân biệt dữ liệu nhập với dữ liệu đã xác minh.

### R9. Media và tài liệu

1. Hệ thống SHALL hỗ trợ ảnh, audio, video, PDF và tài liệu; metadata, album, tag, caption, ngày, địa điểm và người liên quan.
2. Upload SHALL dùng URL ký tạm thời; file SHALL được kiểm loại/MIME, checksum, quota và quét malware trước khi phát hành.
3. Worker SHALL tạo thumbnail, preview, OCR và transcoding theo loại file.
4. Hệ thống SHALL hỗ trợ crop/focal point, xoay, sắp xếp, phiên bản và phát hiện file trùng.
5. Download/public delivery SHALL tuân thủ authorization, visibility, watermark và signed URL có hạn dùng.
6. Xóa SHALL áp dụng soft-delete, retention và dọn object có kiểm soát.

### R10. Cộng tác và kiểm duyệt

1. Contributor SHALL gửi change proposal gồm diff, nguồn, lý do và phạm vi.
2. Editor SHALL approve, reject, yêu cầu chỉnh sửa hoặc merge một phần.
3. Hệ thống SHALL phát hiện xung đột optimistic concurrency và cung cấp dữ liệu so sánh.
4. Tenant/tree admin SHALL cấu hình direct edit hoặc approval theo vai trò, nhánh và loại dữ liệu.
5. Hệ thống SHALL có comment, mention, watch, assignment, notification và activity feed.
6. Thay đổi đã duyệt SHALL truy nguyên được proposal và reviewer.

### R11. Tìm kiếm và khám phá

1. Hệ thống SHALL tìm kiếm person, event, place, source và media có accent-insensitive, fuzzy và alias matching.
2. Kết quả SHALL luôn được lọc theo tenant, authorization và privacy trước khi trả về.
3. Hệ thống SHALL cung cấp facet theo gia phả, thời gian, địa điểm, trạng thái sống và loại tài nguyên.
4. Public discovery SHALL chỉ index dữ liệu `PUBLIC` đã được redaction.
5. Hệ thống SHALL hỗ trợ saved search và cảnh báo khi có kết quả mới.

### R12. Import, export và tương tác dữ liệu

1. Hệ thống SHALL import/export GEDCOM 7 và hỗ trợ mapping cho dữ liệu phổ biến từ GEDCOM 5.5.1.
2. Import SHALL chạy bất đồng bộ, có validate, dry-run, preview, duplicate matching, progress và báo cáo lỗi theo dòng.
3. Export SHALL hỗ trợ toàn bộ hoặc theo nhánh, privacy redaction, media bundle và manifest checksum.
4. Hệ thống SHALL export CSV/JSON/PDF cho phạm vi phù hợp.
5. Public API SHALL version hóa, dùng OAuth2 scopes, rate limit, idempotency key và OpenAPI.
6. Webhook SHALL có chữ ký, retry, dead-letter và replay có audit.

### R13. DNA và consent

1. DNA SHALL là module opt-in, mặc định tắt và có authorization tách biệt với quyền xem cây.
2. Hệ thống SHALL quản lý kit metadata, provider, loại xét nghiệm, người sở hữu, người quản lý và file raw được mã hóa.
3. BEFORE upload, matching, sharing, research use hoặc export THEN hệ thống SHALL ghi consent cụ thể, informed, versioned và có thể thu hồi.
4. WHEN consent bị thu hồi THEN hệ thống SHALL dừng xử lý tương ứng, thu hồi chia sẻ và áp dụng retention/delete policy.
5. Hệ thống SHALL hỗ trợ match, shared segment, relationship estimate, notes và trạng thái xác nhận mà không công khai raw DNA.
6. DNA access, download, match và consent change SHALL luôn được audit bất biến.
7. Dữ liệu DNA SHALL không xuất hiện trong public search, public tree hoặc export mặc định.
8. Việc sử dụng DNA cho trẻ vị thành niên hoặc người không đủ năng lực SHALL yêu cầu guardian workflow và policy theo jurisdiction.

### R14. Thông báo và truyền thông

1. Hệ thống SHALL hỗ trợ in-app và email; adapter có thể mở rộng cho push/SMS.
2. Người dùng SHALL cấu hình kênh, loại sự kiện, digest, quiet hours và locale.
3. Notification SHALL không làm lộ dữ liệu nhạy cảm ngoài phạm vi quyền hiện tại.
4. Template SHALL version hóa, đa ngôn ngữ và tùy biến thương hiệu tenant.

### R15. Báo cáo, thống kê và xuất bản

1. Hệ thống SHALL cung cấp dashboard chất lượng dữ liệu, completeness, conflict, orphan và duplicate.
2. Hệ thống SHALL tạo báo cáo dòng họ, sổ gia phả, timeline, danh sách ngày kỷ niệm và thống kê nhân khẩu.
3. Report SHALL hỗ trợ template, preview, PDF/print và redaction theo policy.
4. Analytics sản phẩm SHALL không thu thập DNA/raw sensitive content và SHALL tôn trọng consent/opt-out.

### R16. Quản trị, audit và hỗ trợ

1. Admin SHALL quản lý tenant, feature flag, quota, job, webhook, integration và trạng thái dịch vụ theo phạm vi được cấp.
2. Audit log SHALL append-only, chống sửa đổi, tìm kiếm được và export theo retention policy.
3. Support access SHALL cần phê duyệt, giới hạn thời gian/phạm vi, banner rõ ràng và audit đầy đủ.
4. Impersonation SHALL bị tắt mặc định và không bao giờ vượt qua quyền DNA/secret export.
5. Hệ thống SHALL có data subject request: access, correction, portability, restriction và deletion.

### R17. PWA và trải nghiệm ngoại tuyến

1. PWA SHALL cài đặt được, responsive và có app shell cache an toàn.
2. Offline mode SHALL chỉ lưu dữ liệu tối thiểu người dùng đã chọn và SHALL mã hóa dữ liệu nhạy cảm tại client khi khả thi.
3. Mutation offline SHALL dùng queue, idempotency và conflict resolution; DNA/raw media SHALL không cache offline mặc định.
4. Hệ thống SHALL hiển thị rõ trạng thái đồng bộ, lỗi và dữ liệu chưa gửi.

### R18. Quốc tế hóa và khả năng tiếp cận

1. UI, email, report và dữ liệu phân loại SHALL hỗ trợ i18n, pluralization, RTL và locale fallback.
2. Tên người và địa danh SHALL giữ nguyên script gốc, transliteration và alternate forms.
3. Hệ thống SHALL lưu thời điểm bằng UTC, timezone IANA và dữ liệu lịch gốc đủ để tái hiển thị chính xác.
4. Luồng chính SHALL đạt WCAG 2.2 AA và hỗ trợ reduced motion, contrast và keyboard navigation.

## 5. Yêu cầu phi chức năng

### NFR1. Bảo mật và riêng tư

- Áp dụng OWASP ASVS Level 2, OWASP API Security Top 10, least privilege và defense in depth.
- TLS cho mọi kết nối; mã hóa at-rest; envelope encryption và key rotation cho DNA/file nhạy cảm.
- Secret không nằm trong source/image; hỗ trợ external secret manager.
- Có CSP, CSRF defense, secure cookies, input validation, output encoding, rate limit và abuse protection.
- Tenant isolation được kiểm thử tự động; không tin `tenant_id` do client tự khai báo.

### NFR2. Hiệu năng mục tiêu ban đầu

- API đọc phổ biến: p95 dưới 300 ms, không tính download file và job dài.
- API ghi phổ biến: p95 dưới 600 ms.
- Tìm kiếm: p95 dưới 1 giây trong phạm vi tenant thông thường.
- Tree view SHALL tương tác lần đầu trong 2,5 giây ở p75 trên mạng di động tốt và tải tăng dần tới ít nhất 10.000 person/tree.
- Mục tiêu phải được xác nhận bằng benchmark trước production; không coi là cam kết SLA mặc định.

### NFR3. Sẵn sàng và phục hồi

- SaaS production mục tiêu 99,9% theo tháng, loại trừ bảo trì công bố.
- Dùng health/readiness probe, graceful shutdown, timeout, retry có jitter, circuit breaker và bulkhead.
- Backup mã hóa, PITR cho PostgreSQL; object versioning tùy môi trường.
- Mục tiêu ban đầu: RPO ≤ 15 phút, RTO ≤ 4 giờ; enterprise có thể cấu hình cao hơn.
- Disaster recovery SHALL được diễn tập định kỳ.

### NFR4. Khả năng mở rộng và nhất quán

- Service SHALL stateless khi có thể và scale ngang.
- Giao dịch mạnh chỉ trong boundary một service; liên service dùng event, saga và eventual consistency.
- Event publication SHALL dùng transactional outbox; consumer SHALL idempotent.
- Không service nào được đọc/ghi trực tiếp database ownership của service khác.

### NFR5. Quan sát và vận hành

- OpenTelemetry cho trace, metric và structured log; correlation/trace ID xuyên BFF, gRPC và Kafka.
- Không log token, raw DNA, nội dung file, PII nhạy cảm hoặc secret.
- Có SLI/SLO, dashboard, alert, runbook và audit cho thao tác vận hành.

### NFR6. Khả chuyển và on-premise

- Workload đóng gói OCI image, triển khai Kubernetes bằng Helm.
- Storage, email, OIDC, billing, malware scan và KMS SHALL qua adapter/configuration.
- On-premise SHALL hỗ trợ PostgreSQL, Kafka, S3-compatible storage và OIDC provider do khách hàng quản lý.
- Upgrade SHALL có migration versioned, compatibility matrix, preflight và rollback plan.

### NFR7. Chất lượng

- Unit, integration với Testcontainers, gRPC/API contract, migration, security, accessibility và Playwright E2E.
- Consumer-driven contract cho tương tác service; schema/event compatibility được kiểm tra CI.
- Không phát hành khi có lỗi critical/high chưa được chấp thuận ngoại lệ.

### NFR8. Tận dụng nền tảng tiêu chuẩn

- Hệ thống SHALL ưu tiên sản phẩm chuẩn, đã được kiểm chứng và có thể self-host cho các capability phổ biến; chỉ tự xây phần tạo khác biệt nghiệp vụ gia phả.
- API routing, identity, workflow, authorization graph, event platform, schema registry, object storage, secret/KMS, service mesh, GitOps, feature flag, observability và media processing SHALL dùng nền tảng đã được phê duyệt trong Design.
- Mọi nền tảng SHALL có owner, version policy, Helm/config-as-code, backup/restore, monitoring, security hardening, upgrade/rollback và runbook.
- Tích hợp SHALL qua contract/adapter để tránh rò rỉ API vendor vào domain và duy trì parity SaaS/on-premise.
- Không tự xây lại capability đã có trong nền tảng, trừ khi ADR chứng minh thiếu chức năng, rủi ro, chi phí hoặc yêu cầu pháp lý.
- Việc thêm nền tảng mới SHALL có ADR đánh giá license, tổng chi phí sở hữu, lock-in, data residency, high availability, khả năng air-gap và kỹ năng vận hành.

## 6. Quy tắc dữ liệu cốt lõi

1. ID nội bộ SHALL là UUID/ULID không mang ý nghĩa nghiệp vụ.
2. Mọi aggregate tenant-scoped SHALL chứa tenant identity từ trusted context.
3. Person khác User; xóa User không mặc nhiên xóa Person lịch sử.
4. Dữ kiện quan trọng SHALL biểu diễn được certainty, provenance và temporal validity.
5. Hard delete chỉ chạy sau retention/legal hold và phải tạo deletion evidence không chứa nội dung đã xóa.
6. Public projection SHALL tách khỏi bản ghi private và được tái tạo khi policy thay đổi.

## 7. Ngoài phạm vi bản đầu

- Tự vận hành phòng xét nghiệm hoặc diễn giải y khoa từ DNA.
- Mạng xã hội công khai, quảng cáo hành vi hoặc bán dữ liệu.
- Chỉnh sửa cây đồng thời kiểu CRDT hoàn toàn thời gian thực.
- Cam kết tuân thủ pháp lý cho mọi quốc gia nếu chưa có đánh giá pháp lý theo jurisdiction.

## 8. Chỉ số thành công

- Tỷ lệ hoàn tất tạo tenant → gia phả → person đầu tiên.
- Thời gian tới cây đầu tiên và tỷ lệ import GEDCOM thành công.
- Tỷ lệ proposal được xử lý, duplicate được giải quyết và hồ sơ có citation.
- Tỷ lệ lỗi đồng bộ, job thất bại, vi phạm isolation bằng 0 và sự cố privacy bằng 0.
- Core Web Vitals, accessibility pass rate, API SLO và restore drill success.

## 9. Giả định cần xác nhận sau discovery

- Nhà cung cấp cloud chính, vùng dữ liệu và yêu cầu data residency.
- Mô hình giá, plan, quota và billing provider.
- OIDC distribution mặc định cho SaaS và bản quyền phù hợp cho on-premise.
- Jurisdiction phát hành đầu tiên và đánh giá pháp lý đối với DNA.
- Quy mô mục tiêu: tenant, person/tree, media, request rate và số kết nối đồng thời.
