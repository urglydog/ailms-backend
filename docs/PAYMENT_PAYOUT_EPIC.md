# Epic: Hoàn tiền – Chi trả giảng viên – Quảng bá – Chia sẻ khóa học

Tạo 11/11/2026. File này là bản **chi tiết + bảng theo dõi tiến độ** của Epic; bản tóm tắt nằm ở `UpComming_Plan.md` (mục cùng tên). Hai file phải luôn khớp: sửa trạng thái ở đây thì cập nhật dòng tương ứng ở `UpComming_Plan.md`.

Báo cáo rà soát gốc (lỗ hổng B1–B7): `PAYMENT_REFUND_PAYOUT_REVIEW_20261111.md`.

## Ký hiệu trạng thái
`✅ XONG` · `🔄 ĐANG LÀM` · `⏳ CHƯA LÀM` · `🅿️ HOÃN` (cố ý để sau, kèm lý do) · `🔧 CẦN CẢI THIỆN` (đã chạy nhưng còn nợ) · `⛔ BỊ CHẶN` (chờ quyết định/cấu hình của chủ dự án)

## Lịch sử thay đổi kế hoạch
| Ngày | Thay đổi |
|---|---|
| 11/11/2026 | Tạo kế hoạch. Tỷ lệ chia mặc định giữ 37/63 (organic) và 97/3 (referral) nhưng chuyển thành cấu hình admin. Hạn hoàn 7 ngày (gia hạn qua T7/CN), giữ tiền = hạn + 3 ngày, ngưỡng rút 100.000đ, chi trả đợt ngày 5 & 20. Giảng viên phản hồi – admin quyết định cuối. |
| 11/11/2026 | Quy trình phát triển: backend bind-mount thư mục thật + devtools + `restart: unless-stopped` → code dở KHÔNG được để trong `/var/lms/be`. Code ở nhánh riêng `feat/payout-epic` (worktree riêng), chỉ merge vào `feat/additional-features` khi giai đoạn xong + verify + bạn đã sao lưu DB. |
| 11/11/2026 | Giai đoạn A đã deploy BE. Bổ sung: FE chỉ deploy/test trên Vercel, không chạy trên server. |
| 11/11/2026 | Giai đoạn A: `refunded_at` + `REFUNDED` dời sang B; thêm `GET /instructor/revenue-share` (có đăng nhập) thay cho `GET /public/policy` (đã bỏ). Không có endpoint công khai mới nào ở giai đoạn A. |
| 11/11/2026 | API công khai: được phép nhưng phải đủ lớp bảo vệ (mục "Bảo mật API công khai"). Làm ở giai đoạn D/E, sau khi A–C xong; có công tắc tắt khẩn cấp. |

## Nguyên tắc xuyên suốt
- Không đụng `.env`, `docker-compose.yml`, cấu hình Redis/firewall (việc hạ tầng do chủ dự án quyết).
- Migration Flyway chỉ **thêm** cột/bảng, không xóa dữ liệu; viết kiểu chạy lại không nhân đôi.
- Tiền: `BigDecimal`, VND làm tròn `HALF_UP` scale 0.
- Mọi thay đổi trạng thái tiền ghi `finance_audit_logs`; sổ cái chỉ thêm dòng, không sửa/xóa.
- Server chỉ ~3,8GB RAM: không thêm container; job nhẹ, chạy theo lô; chạy test với `-Xmx512m`.
- Mỗi giai đoạn: `mvn clean verify` (đầy đủ) trước khi merge; FE `npm run lint` + `npx tsc --noEmit`; đủ `@Mock` cho mọi `@InjectMocks` bị ảnh hưởng.

## Bảng theo dõi

### Giai đoạn 0 — Tài liệu & quy trình
| ID | Hạng mục | Trạng thái |
|---|---|---|
| 0.1 | Rà soát lỗ hổng hoàn tiền/chi trả, báo cáo B1–B7 | ✅ XONG 11/11/2026 |
| 0.2 | Kế hoạch được duyệt; ghi vào repo (file này + `UpComming_Plan.md`) | ✅ XONG 11/11/2026 |
| 0.3 | Worktree + nhánh `feat/payout-epic` | ✅ XONG 11/11/2026 |

### Giai đoạn A — Cấu hình, tỷ lệ chia, sổ cái, audit, chặn tự mua
Trạng thái chung: **✅ ĐÃ DEPLOY BE 11/11/2026 (21:18 giờ VN).** `mvn clean verify` xanh (538 test); Flyway áp dụng V154–V158 thành công trên DB thật; đối chiếu sau migration: mọi bảng cũ giữ nguyên số dòng, sổ cái 1.514 dòng = 1.514 đơn PAID có thu nhập (tổng 190.853.630đ khớp tuyệt đối), 0 đơn PAID thiếu `platform_rate`. Bản sao lưu DB trước migration: `~/backups/lms_db_pre_epicA_20261111_141518.sql.gz` (cùng bản sao lưu của chủ dự án). FE push sau BE; FE chỉ chạy trên Vercel.

Số hiệu migration thực tế (khác bảng dự kiến ban đầu): V154 `platform_rate` · V155 `finance_audit_logs` · V156 seed `system_configs` · V157 `instructor_ledger_entries` · V158 backfill EARNING. Các giai đoạn sau đánh số tiếp từ V159.

| ID | Hạng mục | Trạng thái |
|---|---|---|
| A1 | V154: `payments.platform_rate` (backfill = `platform_fee/amount`). `refunded_at` + `PaymentStatus.REFUNDED` dời sang Giai đoạn B (tránh cột chưa dùng) | ✅ XONG 11/11/2026 |
| A2 | V155 `finance_audit_logs` + `FinanceAuditService` (IP lấy theo `RateLimitFilter.clientIp`, entity `@Immutable`) | ✅ XONG 11/11/2026 |
| A3 | V156: seed 9 khóa `system_configs` (mặc định = hành vi cũ 37/63, 97/3) | ✅ XONG 11/11/2026 |
| A4 | `SystemConfigService`: getter có kiểu, cache 60s, validate khóa tài chính (từ chối cả lô nếu 1 giá trị sai), audit khi đổi; lỗi DB → dùng cache cũ/mặc định | ✅ XONG 11/11/2026 |
| A5 | `PaymentService.applyOutcome`: tỷ lệ từ config, ghi `platform_rate`, làm tròn VND scale 0 | ✅ XONG 11/11/2026 |
| A6 | V157 + `LedgerService.recordEarning` (cùng transaction với PAID, idempotent). Số dư/pending/available/frozen làm ở Giai đoạn C | ✅ XONG 11/11/2026 |
| A7 | V158 backfill EARNING cho đơn PAID cũ (idempotent) | ✅ XONG 11/11/2026 |
| A8 | Chặn giảng viên tự mua khóa của mình (`createPayment`, `createBatchPayment` kể cả trong bundle, `CartService.addItem`) — mã lỗi `BR-PAY-06` | ✅ XONG 11/11/2026 |
| A9 | Xóa 2 dòng yml chết; sửa javadoc `Payment`; `DemoDataLoader` ghi `platform_rate` | 🔧 CẦN CẢI THIỆN — đơn demo do `DemoDataLoader` tạo trực tiếp KHÔNG có dòng sổ cái (đối soát C6 sẽ phát hiện/sửa) |
| A10 | FE: bỏ hard-code ở trang admin payments (`platformRate`) và `CourseReferralSection` (đọc `GET /instructor/revenue-share`, endpoint cần đăng nhập, không công khai) | 🔧 CẦN CẢI THIỆN — trang `/legal/revenue-share` vẫn ghi tay 37%/97%; sẽ viết lại ở B12. Commit FE `63263c8` đã push sau khi BE deploy (11/11/2026); chờ chủ dự án xem trên Vercel |
| A11 | FE: nhóm "Tài chính" trong Cấu hình hệ thống (input đúng kiểu từng khóa, đủ 4 ngôn ngữ) | ✅ XONG 11/11/2026 |
| A12 | `GET /api/v1/instructor/revenue-share` (INSTRUCTOR/ADMIN, chỉ đọc) | ✅ XONG 11/11/2026 |
| A13 | Test: `FinanceConfigKeysTest`, `SystemConfigServiceTest`, `LedgerServiceTest`, thêm vào `PaymentServiceTest`/`CartServiceTest` | ✅ XONG 11/11/2026 |
| A14 | Test FE `payments/page.test.tsx` | ⛔ BỊ CHẶN — môi trường thiếu gói `motion` trong `node_modules` (lỗi có từ trước, `tsc` cũng báo); cần `npm ci` rồi chạy lại |

### Giai đoạn B — Hoàn tiền & khiếu nại
Số hiệu migration thực tế: V159 `refund_core` (payments.refunded_at, enrollments.revoked_*, bảng `payment_refunds`) · V160 `dispute_v2` (category/eligibility/instructor_*, `dispute_attachments.side`). Đã kiểm trên MySQL 8 tạm: chỉ thêm cột/bảng, dữ liệu cũ nguyên vẹn, DB chặn hoàn trùng (`uk_pr_payment`), Hibernate `validate` qua, thu hồi mềm/kích hoạt lại chạy đúng với Hibernate+MySQL thật.

| ID | Hạng mục | Trạng thái |
|---|---|---|
| B1 | V159 soft-revoke ghi danh + `@SQLRestriction("revoked_at IS NULL")`; mua lại → `EnrollmentRepository.reactivate` (giữ progress/completedAt, chứng chỉ ACTIVE lại) | ✅ BE XONG 11/11/2026 (verify xanh 585 test) |
| B2 | V159/V160: `payment_refunds`, cột mới `payment_disputes`/`dispute_attachments` | ✅ BE XONG |
| B3 | `RefundService.approve` (REFUNDED, thu hồi ghi danh+chứng chỉ, ẩn review, sổ cái REVERSAL, xóa cache, audit, báo giảng viên, cảnh báo ≥3 lần hoàn/90 ngày); 3 chế độ trách nhiệm | ✅ BE XONG |
| B4 | `DisputeService` v2: 1 khiếu nại/giao dịch, category (mặc định OTHER cho client cũ), eligibility theo `RefundPolicyService` (hạn 7 ngày + gia hạn T7/CN/lễ, tiến độ ≤ 20%), hạn 48h cho giảng viên, validate status resolve | ✅ BE XONG |
| B5 | API giảng viên `/api/v1/instructor/disputes` (list/get/respond, ẩn thông tin cá nhân học viên) | ✅ BE XONG |
| B6 | `markTransferred` + email học viên (đã escape HTML) + thông báo | ✅ BE XONG |
| B7 | `DisputeDeadlineJob` (hằng giờ) → NO_RESPONSE + nhắc admin | ✅ BE XONG |
| B8 | FE giảng viên: tab "Yêu cầu hoàn tiền" | ⏳ CHƯA LÀM |
| B9 | FE admin: trang dispute nâng cấp (chế độ trách nhiệm + xem trước số tiền + mã chuyển khoản) | ⏳ CHƯA LÀM |
| B10 | FE học viên: category, trạng thái từng bước, thấy `adminNote`, REFUNDED | ⏳ CHƯA LÀM |
| B11 | Báo cáo 3 khiếu nại `RESOLVED_REFUNDED` cũ để admin xử lý tay (giao dịch vẫn PAID, chưa thu hồi) | ⏳ CHƯA LÀM — chờ chủ dự án duyệt từng ca |
| B12 | Viết lại `/legal/refund`, `/legal/revenue-share`, `/legal/terms` | ⏳ CHƯA LÀM |
| B13 | Mobile: category, trạng thái mới, `adminNote`, REFUNDED (BE tương thích ngược nên app cũ vẫn chạy) | ⏳ CHƯA LÀM |

### Giai đoạn C — Tài khoản nhận tiền & chi trả
Số hiệu migration thực tế: V161 (`instructor_payout_accounts`, `instructor_payouts`). Đã kiểm trên MySQL 8 tạm: chỉ thêm bảng; Hibernate `validate` qua; các truy vấn số dư (khả dụng/đang giữ/đóng băng) và đối soát chạy đúng trên MySQL thật. `mvn clean verify` xanh 649 test (11/11/2026), nhánh `feat/payout-epic`.

**Bật tính năng (chủ dự án làm, tôi không sửa `.env`/compose):** (1) tạo khóa `openssl rand -base64 32`, SAO LƯU khóa ở nơi an toàn (mất khóa = mất số tài khoản đã lưu); (2) thêm `LMS_PAYOUT_ENCRYPTION_KEY=<khóa>` vào `be/.env`; (3) thêm dòng `LMS_PAYOUT_ENCRYPTION_KEY: ${LMS_PAYOUT_ENCRYPTION_KEY:-}` vào mục `environment` của service `backend` trong `docker-compose.yml` (service này không có `env_file` nên biến chỉ nằm trong `.env` sẽ KHÔNG vào container); (4) khởi động lại backend. Thiếu khóa: backend vẫn chạy bình thường, chỉ API tài khoản nhận tiền/rút tiền báo lỗi rõ ràng và trang Thu nhập hiện cảnh báo.

| ID | Hạng mục | Trạng thái |
|---|---|---|
| C1 | V161 `instructor_payout_accounts` + `instructor_payouts` | ✅ BE XONG 11/11/2026 |
| C2 | Mã hóa số tài khoản AES-256-GCM (`PayoutCrypto`), HMAC dò trùng, danh mục ngân hàng (`BankDirectory`) | ✅ XONG + ĐÃ CẤU HÌNH KHÓA, đã liên kết tài khoản thật ở môi trường chạy (14/11/2026). 🔧 BIN ngân hàng cần đối chiếu danh sách VietQR trước khi dùng cho chi trả tự động |
| C3 | Liên kết tài khoản: chỉ giảng viên đã xác minh, email xác nhận (token băm SHA-256 trong Redis, GETDEL, TTL 30'), khớp tên chủ tài khoản, cảnh báo trùng số tài khoản. ~~Khóa rút 3 ngày khi ĐỔI~~ — BỎ ngày 14/11/2026 (xem C10) | ✅ BE XONG |
| C4 | `PayoutService`: yêu cầu rút (Idempotency-Key, khóa dòng giảng viên, giữ chỗ sổ cái, email xác nhận bắt buộc), xác nhận email, hủy, máy trạng thái PENDING_EMAIL→REQUESTED→APPROVED→PAID / REJECTED / CANCELLED / FAILED | ✅ BE XONG |
| C5 | API admin `/api/v1/admin/payouts` (duyệt, đã chi + mã giao dịch, từ chối, thất bại, xem số tài khoản đầy đủ có nhật ký, xuất CSV chống công thức Excel) + 6 email các mốc | ✅ BE XONG |
| C6 | Việc nền: hủy yêu cầu quá 30' chưa xác nhận (5 phút/lần); đối soát đêm ghi bù dòng EARNING còn thiếu (≤200 đơn/lần) và báo admin | ✅ BE XONG. 🔧 chưa có trang "Đối soát" riêng cho admin |
| C7 | FE giảng viên "Thu nhập" `/instructor/earnings` (số dư, tài khoản nhận tiền, yêu cầu rút có Idempotency-Key, lịch sử, sao kê) + trang xác nhận `/instructor/earnings/confirm` (cố ý cần bấm nút, tránh trình quét email ăn token) | ✅ FE XONG 11/11/2026 (commit local, chưa push chờ BE deploy). 🔧 chuỗi chữ viết cứng tiếng Việt (chưa i18n 4 ngôn ngữ) |
| C8 | FE admin "Chi trả" `/admin/transactions/payouts` (duyệt, đã chuyển + mã giao dịch, từ chối, thất bại, xem số TK, xuất CSV) | ✅ FE XONG 11/11/2026. 🔧 còn: huy hiệu số yêu cầu chờ ở sidebar, trang xem nhật ký tài chính |
| C10 | **Nhiều tài khoản nhận tiền** (V163: `is_default`, `replaces_account_id`; trạng thái `REMOVED`): tối đa 5 tài khoản ACTIVE, 1 mặc định; thêm mới cần email; sửa ngân hàng/số cần email (bản chờ thay thế bản cũ, bản cũ vẫn dùng tới khi xác nhận), sửa riêng tên chủ thì áp dụng ngay; đổi mặc định/gỡ không cần email; gỡ bị chặn khi có yêu cầu rút mở dùng tài khoản đó; BỎ khóa rút 3 ngày (quyết định 14/11/2026). API: `GET/PUT/DELETE /instructor/payout-accounts`, `POST …/{id}/default` | ✅ BE + FE XONG + ĐÃ DEPLOY 14/11/2026 (678 test xanh) |
| C11 | Rút tiền chọn tài khoản (`accountId` tùy chọn, mặc định = tài khoản mặc định); `PUT /instructor/payouts/{id}/account` đổi tài khoản của yêu cầu CHƯA duyệt (PENDING_EMAIL/REQUESTED) — dùng khi ngân hàng bảo trì/nhập sai; yêu cầu chụp lại (snapshot) tài khoản lúc tạo nên đổi mặc định không ảnh hưởng yêu cầu đang chờ; thất bại do admin đánh dấu → tiền trả về số dư + thông báo gợi ý đổi tài khoản | ✅ XONG 14/11/2026 |
| C12 | FE `/instructor/earnings`: danh sách tài khoản dạng thẻ nổi (sửa/thêm/gỡ/đặt mặc định NGAY TRÊN TRANG, không popup), ô "Nhận về tài khoản" khi rút, "Đổi tài khoản nhận" trong lịch sử rút. Chữ cảnh báo lệch tên đổi thành "tên tài khoản" (so với `users.full_name`, không phải tên CCCD) | ✅ XONG 14/11/2026 |
| C9 | Tự động chi qua PayOS `payouts()` | 🅿️ HOÃN — cần bạn kiểm tra kênh PayOS có bật chi hộ; đã tách logic nên nối thêm `PayoutExecutor` được |

### Giai đoạn D — Chia sẻ khóa học
Không thêm API công khai nào. Số liệu chia sẻ chỉ đo **đơn mua** (đã đăng nhập). `mvn clean verify` xanh 665 test (11/11/2026, gồm cả Giai đoạn E).

| ID | Hạng mục | Trạng thái |
|---|---|---|
| D1 | SEO/OG: `generateMetadata` (OpenGraph/Twitter/canonical) cho trang khóa học, `metadataBase` (lấy từ biến Vercel tự cấp, không cần env mới), `app/sitemap.ts`, `app/robots.ts` | ✅ FE XONG 11/11/2026 (commit local, chưa push) |
| D2 | `lib/referral.ts` lưu kèm thời điểm, hết hạn 30 ngày, đọc được định dạng cũ, last-click; có test (`referral.test.ts`) | ✅ FE XONG |
| D3 | `ShareCourseDialog` (sao chép, Web Share API có dò hỗ trợ, Facebook/X/LinkedIn/Telegram/Email, link gắn `ref`+kênh) + nút ở panel cài đặt khóa và trang `/instructor/share` | ✅ FE XONG. 🔧 chưa có Zalo |
| D4 | Thống kê theo đơn mua: `GET /api/v1/instructor/share/courses` (khóa + mã giới thiệu + đơn/thu nhập theo nguồn, cần đăng nhập) + trang `/instructor/share` | ✅ BE + FE XONG. 🔧 chưa có tỷ lệ chuyển đổi (cần đo lượt xem, D6) |
| D5 | Mã QR trong hộp chia sẻ (`qrcode.react` 4.2.0, ISC, không phụ thuộc gói khác; vẽ SVG trên trình duyệt) | ✅ FE XONG 12/11/2026 |
| D6 | `POST /referrals/visit` (đếm lượt mở link) | 🅿️ HOÃN — API công khai ghi dữ liệu, chỉ làm với đủ 9 lớp bảo vệ |
| D7 | Link ngắn `/r/{code}` | 🅿️ HOÃN |
| D8 | Mobile: hết hạn mã giới thiệu + chia sẻ native | ⏳ CHƯA LÀM |

### Giai đoạn E — Quảng bá của nền tảng
Số hiệu migration thực tế: V162 (`course_promotions`). Đã kiểm trên MySQL 8 tạm: Hibernate `validate` qua; truy vấn kệ nổi bật chạy đúng (đúng thứ tự ưu tiên, loại khóa nháp/riêng tư/đợt hết hạn, theo danh mục); thống kê loại đơn REFUNDED. Kệ dùng `GET /api/v1/courses/featured` — nằm sẵn dưới `PUBLIC_GET_ENDPOINTS` (`/api/v1/courses/**`) nên KHÔNG sửa `SecurityConfig`; chỉ đọc, đầu vào kiểm chặt (enum, slug theo mẫu, size kẹp 1–24), cache 15 phút.

| ID | Hạng mục | Trạng thái |
|---|---|---|
| E1 | V162 `course_promotions` | ✅ BE XONG 11/11/2026 |
| E2 | Giảng viên đăng ký/hủy, admin duyệt (khung thời gian ≤ 90 ngày; ĐÃ BỎ ô độ ưu tiên 14/11/2026 — xem E7)/từ chối/gỡ sớm có lý do bắt buộc (E8); điều kiện: khóa đã xuất bản + công khai + có ảnh bìa + ≥ 3 bài | ✅ BE + FE XONG (`/instructor/share`, `/admin/promotions`) |
| E3 | Kệ "Được đề xuất" trang chủ (nhãn rõ ràng) qua `GET /courses/featured` | ✅ BE + FE XONG. Kệ đầu trang danh mục (`CATEGORY_FEATURED`) hiện ở `/courses?category=<slug>` từ 14/11/2026 (`CategoryFeaturedShelf`; chỉ hiện khi lọc 1 danh mục và không gõ từ khóa) |
| E3b | Boost thứ hạng tìm kiếm + nhãn "Được đề xuất" trên thẻ khóa | 🅿️ HOÃN — phải sửa 3 truy vấn tìm kiếm phức tạp và cache 15 phút; rủi ro cao so với lợi ích |
| E4 | Thống kê hiệu quả: đơn/doanh thu khi chạy so với khoảng bằng nhau ngay trước đó (ước tính) | ✅ BE + FE XONG. Quy kết chính xác theo `payments.promotion_id` 🅿️ HOÃN (cần sửa luồng tạo đơn) |
| E5 | `POST /promotions/{id}/click` + đếm hiển thị | 🅿️ HOÃN — như D6 |
| E6 | Chiến dịch email/thông báo + `marketing_opt_out` + link hủy | 🅿️ HOÃN — làm sau cùng, có thể tách PR |
| E7 | Bỏ độ ưu tiên chấm tay (nguy cơ thiên vị): kệ lấy tối đa 100 khóa đang chạy, xáo theo GIỜ hiện tại (hạt giống = giờ + vị trí) rồi cắt theo `size`; `priority` bỏ khỏi API/FE, cột DB giữ nguyên (không migration) | ✅ XONG 14/11/2026 |
| E8 | Gỡ quảng bá: admin "Gỡ quảng bá" BẮT BUỘC nêu lý do → giảng viên nhận thông báo `PROMOTION_ENDED`; giảng viên gỡ đợt đang chạy → admin nhận `PROMOTION_WITHDRAWN`; cả hai ghi audit; khóa bị ẩn/gỡ xuất bản tự biến khỏi kệ (truy vấn đã lọc) | ✅ XONG 14/11/2026 |
| E9 | Thông báo `PROMOTION_SUBMITTED` cho giảng viên khi gửi đăng ký (trước đây chỉ admin được báo); định dạng ngày thông báo thống nhất `HH:mm dd/MM/yyyy` | ✅ XONG 14/11/2026 |
| E10 | FE `/instructor/share`: tìm kiếm (bỏ dấu), lọc trạng thái, phân trang 5 khóa/trang, khối danh sách chiều cao tối thiểu cố định; ô chọn khóa quảng bá có tìm nhanh (`SearchableSelect`) | ✅ XONG 14/11/2026 |

### Giai đoạn F — Hoàn thiện
| ID | Hạng mục | Trạng thái |
|---|---|---|
| F1 | Mobile: loại vấn đề, thông báo hoàn tiền/chuyển khoản, `REFUNDED`, mỗi giao dịch 1 khiếu nại (push `main` 8d044dc, tsc/lint sạch ở file đã sửa; các lỗi tsc/lint còn lại có từ trước) | ✅ XONG 11/11/2026 |
| F2 | Cập nhật `claude/*.md` | ✅ XONG |
| F3 | Ngày lễ nhập vào `REFUND_HOLIDAYS` | ⛔ BỊ CHẶN — admin nhập khi vận hành |
| F4 | Kiểm thử đầu-cuối: `mvn clean verify` 665 test; áp V1→V162 trên MySQL 8 tạm; Hibernate `validate`; JPQL chạy thật; **khởi động đủ bean toàn ứng dụng (48s) + gọi thử API mới** (công khai chỉ đọc, còn lại 401 khi không token, đầu vào độc 400) | ✅ XONG phần tự động. 🔧 chưa thử luồng thật trên Vercel/PayOS (cần deploy) |
| F5 | **Deploy**: BE nhánh `feat/payout-epic` (B–E) đã merge vào `feat/additional-features` và chạy thật; FE đã push | ✅ XONG 14/11/2026 (BE restart 2 lần: V162 trước đó, V163 lúc 10:38 14/11; chưa sao lưu DB trước V163 — chủ dự án sẽ sao lưu sau) |

## Bảo mật API công khai
Chỉ làm khi đủ **tất cả** lớp sau (áp dụng cho D6, E5 và mọi API công khai thêm sau này):
1. Chỉ nhận dữ liệu có cấu trúc, kiểm theo danh sách trắng (mã `[a-z0-9]{6,20}`, kênh trong danh sách cố định, khóa người xem 32 ký tự hex); trường lạ bị từ chối; body ≤ 1KB.
2. Không ghép dữ liệu người dùng vào SQL/Redis; khóa Redis chỉ dựng từ phần đã kiểm tra; không ghi nguyên văn đầu vào vào log.
3. Phản hồi cố định `204`, không phản chiếu đầu vào.
4. Bộ nhớ/DB không tăng vô hạn: chỉ đếm cho khóa học có thật × kênh hợp lệ; khóa Redis có TTL; thống kê gộp theo ngày.
5. Giới hạn tần suất theo IP + tổng toàn cục, **từ chối khi lỗi** (dự phòng đếm trong bộ nhớ khi Redis hỏng).
6. Công tắc tắt khẩn cấp `TRACKING_ENABLED` trong `system_configs`.
7. Handler nhỏ, không phụ thuộc service nhạy cảm (payments/users).
8. Test âm tính bắt buộc: SQL injection, JSON dị dạng, body lớn, Unicode lạ, gọi dồn dập.
9. Xác nhận Jackson không bật polymorphic default typing; Spring Boot/Tomcat còn trong bản vá.
Rủi ro còn lại: quá tải do bot ở mức mạng (cần lớp lọc phía trước — quyết định hạ tầng của chủ dự án); số liệu lượt xem có thể bị làm sai (không dùng để tính tiền).

## Thiết kế chi tiết

### Tỷ lệ chia
Mặc định 37/63 (organic) và 97/3 (referral), đọc từ `system_configs` (`REVENUE_ORGANIC_INSTRUCTOR_PCT`, `REVENUE_REFERRAL_INSTRUCTOR_PCT`). Mỗi đơn chốt cứng `platform_fee`, `instructor_earning`, `platform_rate` lúc PAID → đổi cấu hình chỉ áp dụng đơn mới, không ảnh hưởng báo cáo cũ. Muốn 70/30 + 90/10 chỉ sửa 2 số trong admin.

### Trách nhiệm tài chính khi hoàn tiền
Học viên luôn nhận lại 100% `payment.amount`. Admin chọn 1 trong 3 chế độ (hệ thống tính sẵn số tiền):
| Chế độ | Khi dùng | Giảng viên bị trừ | Nền tảng |
|---|---|---|---|
| `STANDARD` (mặc định) | đổi ý trong hạn; lỗi hệ thống/trùng thanh toán/không ghi danh | = `instructor_earning` | trả phần còn lại, chịu phí cổng |
| `PLATFORM_COMPENSATES` | học viên đã dùng được nhưng lỗi do nền tảng | 0 | chịu 100% |
| `INSTRUCTOR_FAULT` | mô tả sai nghiêm trọng/vi phạm (bắt buộc lý do) | = `amount` | 0 |
Ví dụ 1.000.000đ organic 37/63: STANDARD → trừ giảng viên 370.000; PLATFORM_COMPENSATES → 0 (nền tảng lỗ 370.000 + phí cổng); INSTRUCTOR_FAULT → 1.000.000. Đã chi rồi thì số dư giảng viên âm, trừ vào lần chi sau.

### Hoàn tiền (RefundService.approve — 1 giao dịch DB, idempotent)
Khóa payment + giảng viên → `payment.status=REFUNDED`/`refunded_at` (mọi query `status=PAID` tự loại) → thu hồi ghi danh mềm (`revoked_at`, `@SQLRestriction`) → chứng chỉ `REVOKED` → ẩn review + tính lại điểm → sổ cái `REFUND_REVERSAL` → `payment_refunds` (PENDING_TRANSFER) → xóa cache khóa học → thông báo + email + audit. Mua lại: kích hoạt lại dòng ghi danh cũ, giữ `completedAt` (không cộng lại XP). Mỗi (học viên, khóa) chỉ hoàn 1 lần; cảnh báo admin khi ≥3 lần hoàn/90 ngày.

### Điều kiện hoàn (eligibility)
Trong hạn (`paid_at + 7 ngày`, rơi T7/CN dời tới hết thứ Hai, cộng `REFUND_HOLIDAYS`) và tiến độ ≤ 20% → AUTO_ELIGIBLE. Lý do TECHNICAL_ERROR/DOUBLE_CHARGE/NOT_ENROLLED → EXCEPTION (bỏ qua hạn). Còn lại OUT_OF_POLICY. Admin quyết định cuối trong mọi trường hợp; giảng viên có 48h phản hồi.

### Sổ cái & số dư
`pending` (EARNING chưa đến `available_at`) · `available` (dòng `available_at ≤ now`) · `frozen` (earning của giao dịch đang có dispute mở) · `paidOut`. `available_at = paid_at + hạn hoàn + 3 ngày` tính lúc PAID.

### Chi trả
Chỉ INSTRUCTOR đã có xác minh. Tài khoản: mã hóa AES-GCM, hiển thị che, tên chủ khớp tên đã xác minh, xác nhận qua email, ~~đổi tài khoản khóa rút 3 ngày~~ (đã bỏ 14/11/2026, xem C10). Yêu cầu rút: ≥ 100.000đ ≤ khả dụng, 1 yêu cầu mở/lần, `Idempotency-Key`, xác nhận email (30'), xếp lịch chi ngày 5/20, admin duyệt → chi (bắt buộc mã giao dịch) / từ chối (bắt buộc lý do) → số dư hoàn lại. Đợt đầu chi thủ công.

### Quảng bá & chia sẻ
Xem bảng D/E. Kệ nổi bật đi qua tham số mới của endpoint danh sách công khai sẵn có; boost tìm kiếm phải xóa cache `publicCourseSearch` (TTL 15').

## Kiểm chứng đầu-cuối (F4)
1. Đổi tỷ lệ trong cấu hình → đơn mới theo tỷ lệ mới, đơn cũ không đổi.
2. Mua organic 1.000.000đ → EARNING 370.000 đang giữ; giảng viên tự mua → bị từ chối.
3. Khiếu nại → giảng viên được báo + phản hồi → admin hoàn STANDARD → REFUNDED, mất quyền học, chứng chỉ REVOKED, doanh thu giảm đúng ở dashboard giảng viên/admin/AI, REVERSAL −370.000, email, audit; khiếu nại lần 2 bị chặn; mua lại không cộng lại XP.
4. Liên kết ngân hàng → rút 100.000đ (double-click không tạo 2 yêu cầu) → xác nhận email → duyệt → đã chi → số dư giảm; từ chối → hoàn lại; dưới ngưỡng/đổi tài khoản <3 ngày bị chặn.
5. Hoàn sau khi đã chi → số dư âm, chặn rút, bù trừ ở khoản kế.
6. Chia sẻ: link `?ref=` → mua → 97/3 và hiện trong thống kê; mã quá 30 ngày không áp dụng.
7. Quảng bá: đăng ký → duyệt → lên kệ có nhãn, đứng đầu tìm kiếm; hết hạn thì biến mất.
8. `mvn clean verify`, `npm run lint`, `npx tsc --noEmit`, vitest; thử tay trên Chrome desktop + Safari iOS + Android Chrome.

## Việc chờ chủ dự án
1. Sao lưu DB (`mysqldump`) trước khi deploy bất kỳ giai đoạn nào có migration.
2. ~~Biến môi trường khóa mã hóa số tài khoản + HMAC (C2).~~ ĐÃ XONG.
3. Chọn thư viện QR (D5).
4. Kiểm tra kênh PayOS có bật chi hộ (C9).
5. Nhập ngày lễ vào `REFUND_HOLIDAYS` (F3).
6. Quyết định hạ tầng: `maxmemory` Redis, bỏ `--reload` ở ai-api, giới hạn bộ nhớ container, lớp lọc bot phía trước.

## Nhật ký 14/11/2026 (quyết định + việc đã làm)
- **Quyết định:** tối đa 5 tài khoản nhận tiền; thêm mới cần xác nhận email; sửa không khóa (đổi ngân hàng/số vẫn cần email, đổi tên chủ thì không); tài khoản được chọn (mặc định) dùng để nhận tiền; chi trả vẫn bất đồng bộ (theo đợt, 7–14 ngày) nên đổi tài khoản đúng lúc chi không gây nhầm nhờ snapshot; quảng bá bỏ ưu tiên chấm tay, xoay vòng công bằng theo giờ, có cơ chế gỡ vì vi phạm hoặc giảng viên tự gỡ.
- **Lỗi đã sửa cùng đợt:** `/streak/me` 500 do retry khóa lạc quan nằm trong cùng transaction (nay mỗi lần thử 1 transaction riêng); WebSocket thông báo dùng token hết hạn nên lặp "Expired JWT" mỗi 3 giây (nay lấy token mới mỗi lần nối lại); nhắc flashcard trỏ `/materials` thiếu `courseId` + thông báo realtime `SRS_REMINDER` mất nội dung/liên kết (nay theo từng khóa, có tên khóa, tối đa 3 khóa/người/ngày); avatar học viên không hiện do `next/image` chặn domain lạ (nay dùng `<img>` thô ở Header/AccountMenu/ProfileBanner); điều kiện gửi duyệt "chưa xác minh định danh" nay có nút "Làm ngay" tới `/teaching/onboarding`.
- **Lưu ý vận hành:** sao lưu DB sau V163 (chủ dự án làm); email chạy qua Gmail SMTP (`be/.env` đè mặc định Mailpit) nên tài khoản email giả không nhận được thư xác nhận — demo dùng cách bật tài khoản bằng SQL hoặc đổi sang email thật.

## Giai đoạn G — Chi tự động PayOS, công nợ & chính sách (14/11/2026)
Quyết định chủ dự án: chi tự động qua PayOS (admin bấm từng lệnh); tài khoản học viên khai CÓ CẤU TRÚC + xác nhận chính chủ, người khai chịu trách nhiệm về độ chính xác (không tra cứu tên chủ tài khoản); "giảng viên chịu toàn bộ" = 100% giá trị đơn (không công thức phí thêm) nhưng phải có mã lý do + minh chứng + giảng viên được phản hồi trước + quyền khiếu nại; số dư âm là CÔNG NỢ: bù trừ từ doanh thu mới, trả chủ động (một phần được) qua QR PayOS, bậc thang 30/90 ngày, không khóa tài khoản/không gắn cờ spam; thêm chính sách vào điều khoản.

| ID | Hạng mục | Trạng thái |
|---|---|---|
| G1 | Lõi chi tự động PayOS (V164 `payos_payout_orders`): cổng PayOS có công tắc `PAYOS_PAYOUT_ENABLED` (mặc định TẮT) + trần `PAYOS_PAYOUT_MAX_AMOUNT`, kiểm số dư tài khoản chi, idempotency theo `reference_id`, lỗi không tự đổi trạng thái tiền, đối soát nền 2 phút, báo admin khi lỗi/lệch tên; nút "Chi qua PayOS" ở trang Chi trả | ✅ XONG + ĐÃ DEPLOY BE 14/11/2026. ⛔ chưa chạy thật: chờ PayOS xử lý kênh chi hộ (lỗi liên kết ví Baokim phía PayOS), có thể cần khóa API riêng (`PAYOS_PAYOUT_*`) |
| G2 | Học viên khai tài khoản nhận hoàn tiền có cấu trúc (V165: `refund_bank_code/number/holder/confirmed`): chọn ngân hàng, nhập số 2 lần, tên chủ, tick chính chủ; BE kiểm ngân hàng/số/tên/xác nhận; client cũ (chữ tự do) vẫn chạy, chỉ chuyển tay | ✅ BE + FE XONG 14/11/2026 (chưa deploy) |
| G3 | Chi tự động hoàn tiền ở trang khiếu nại admin: nút "Chi qua PayOS" (ưu tiên tài khoản có cấu trúc đã xác nhận; chữ tự do chỉ chấp nhận khi đọc RÕ RÀNG), hiện trạng thái lệnh/lệch tên | ✅ BE + FE XONG (chưa deploy) |
| G4 | Sửa nhãn trạng thái hoàn tiền: học viên thấy "Đã duyệt · chờ chuyển khoản" cho tới khi admin thật sự chuyển (trước đây hiện "Đã hoàn tiền" ngay khi duyệt gây hiểu lầm); thông báo "Khiếu nại đã được xử lý" ghi rõ sẽ chuyển khoản sau | ✅ XONG (chưa deploy) |
| G5 | Công nợ (V166): nợ = số dư khả dụng ÂM; `instructor_debt_state` (debt_since, bậc), `instructor_debt_payments` (orderCode ≥ 1e12 không trùng đơn mua); job đêm đồng bộ + thông báo lên bậc; API `GET /instructor/finance/debt` | ✅ XONG (chưa deploy) |
| G6 | Trả nợ qua QR PayOS, trả một phần được (tối thiểu `DEBT_MIN_REPAY`, hoặc trả hết phần còn lại): webhook dispatch theo dải orderCode, idempotent (UNIQUE `debt_payment_id` ở sổ cái), đối soát nền 5 phút phòng webhook không tới, thông báo "còn nợ X" | ✅ BE + FE (`DebtCard` ở Thu nhập) XONG (chưa deploy) |
| G7 | Bậc thang nợ (`DebtGuard`): ≥ `DEBT_RESTRICT_DAYS`(30) không xuất bản khóa mới (hiện trong checklist gửi duyệt, mã DEBT-01) + không đăng ký quảng bá; ≥ `DEBT_SUSPEND_DAYS`(90) chặn MUA khóa của giảng viên đó (đơn lẻ/giỏ/bundle) + khỏi kệ "Được đề xuất"; có khiếu nại đang chờ → tạm dừng chế tài | ✅ XONG (chưa deploy). 🔧 bậc 90 ngày CHƯA ẩn khỏi trang tìm kiếm/danh sách (phải sửa các truy vấn tìm kiếm có cache) — chỉ chặn mua + gỡ khỏi kệ đề xuất |
| G8 | Admin xóa nợ có lý do bắt buộc (ledger ADJUSTMENT, audit, báo giảng viên) + danh sách người nợ ở trang Chi trả | ✅ XONG (chưa deploy) |
| G9 | Chính sách hoàn tiền (V167): mã lý do chuẩn (`RefundReasonCode`, mỗi mã đi đúng 1 chế độ), "giảng viên chịu toàn bộ" cần mã + ghi chú ≥10 ký tự + minh chứng ≥20 ký tự + giảng viên đã phản hồi hoặc hết hạn 48h; giảng viên thấy số bị trừ + lý do + minh chứng | ✅ XONG (chưa deploy) |
| G10 | Khiếu nại của giảng viên (1 lần, trong `DISPUTE_APPEAL_DAYS`=7): admin giữ nguyên hoặc giảm về chế độ Chuẩn (hoàn lại phần phạt vượt mức vào sổ cái); làm ngay trong trang, không popup | ✅ XONG (chưa deploy) |
| G11 | Cảnh báo admin khi khóa có tỷ lệ hoàn tiền cao 30 ngày (≥`REFUND_FLAG_MIN_COUNT`, ≥`REFUND_FLAG_RATE_PCT`%), chỉ cảnh báo, không tự trừ/gỡ | ✅ XONG (chưa deploy) |
| G12 | Điều khoản: viết lại `/legal/refund` (tài khoản nhận hoàn tiền, trạng thái), `/legal/revenue-share` (tiêu chuẩn quy trách nhiệm, công nợ, rút tiền), thêm mục 3A ở `/legal/terms` | ✅ XONG (chưa push FE) |

**Còn nợ/giới hạn của giai đoạn G:** chữ giao diện mới (panel chính sách, công nợ) chỉ tiếng Việt (chưa vào 4 file i18n); chưa có tra cứu tên chủ tài khoản (PayOS SDK không có hàm này; VietQR cần cấp quyền) — hệ thống chỉ so tên PayOS trả về SAU khi chi và báo admin nếu lệch; mobile chưa cập nhật; bậc 90 ngày chưa ẩn khỏi tìm kiếm; học viên bị từ chối chuyển khoản (số sai) chưa có màn hình tự cập nhật tài khoản (admin liên hệ).
**Deploy:** BE nhánh `feat/additional-features` đã có code + migration V165–V167 nhưng CHƯA restart; FE commit local CHƯA push (FE không push trước khi BE deploy). Sao lưu DB trước khi restart (3 migration, đều thêm cột/bảng, không sửa dữ liệu cũ).
