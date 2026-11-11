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
| ID | Hạng mục | Trạng thái |
|---|---|---|
| B1 | V155 soft-revoke ghi danh + `@SQLRestriction`; kích hoạt lại khi mua lại | ⏳ CHƯA LÀM |
| B2 | V156 `payment_refunds` + cột mới của `payment_disputes` / `dispute_attachments` | ⏳ CHƯA LÀM |
| B3 | `RefundService.approve` (REFUNDED, thu hồi ghi danh, chứng chỉ REVOKED, ẩn review, sổ cái REVERSAL, cache) | ⏳ CHƯA LÀM |
| B4 | `DisputeService` v2: chặn trùng, category, eligibility, hạn 48h cho giảng viên, validate status | ⏳ CHƯA LÀM |
| B5 | API giảng viên: danh sách/chi tiết/phản hồi khiếu nại (ẩn thông tin ngân hàng học viên) | ⏳ CHƯA LÀM |
| B6 | Đánh dấu đã chuyển tiền hoàn (`markTransferred`) + email học viên/giảng viên (escape HTML) | ⏳ CHƯA LÀM |
| B7 | Job quá hạn phản hồi → NO_RESPONSE + nhắc admin | ⏳ CHƯA LÀM |
| B8 | FE giảng viên: tab "Yêu cầu hoàn tiền" | ⏳ CHƯA LÀM |
| B9 | FE admin: trang dispute nâng cấp (chế độ trách nhiệm + xem trước số tiền + mã chuyển khoản) | ⏳ CHƯA LÀM |
| B10 | FE học viên: category, trạng thái từng bước, thấy `adminNote`, REFUNDED | ⏳ CHƯA LÀM |
| B11 | Báo cáo các dispute `RESOLVED_REFUNDED` cũ để admin xử lý tay | ⏳ CHƯA LÀM |
| B12 | Viết lại `/legal/refund`, `/legal/revenue-share`, `/legal/terms` (mục giảng viên) | ⏳ CHƯA LÀM |

### Giai đoạn C — Tài khoản nhận tiền & chi trả
| ID | Hạng mục | Trạng thái |
|---|---|---|
| C1 | V157 (phần còn lại): `instructor_payout_accounts`, `instructor_payouts` | ⏳ CHƯA LÀM |
| C2 | Mã hóa số tài khoản AES-GCM + `BankDirectory` | ⛔ BỊ CHẶN — chờ bạn thêm biến môi trường khóa mã hóa |
| C3 | Liên kết tài khoản: xác nhận email (token hash, GETDEL, TTL 30'), khớp tên, khóa 3 ngày khi đổi | ⏳ CHƯA LÀM |
| C4 | `PayoutService`: yêu cầu rút, idempotency, xác nhận email, máy trạng thái | ⏳ CHƯA LÀM |
| C5 | API admin chi trả: duyệt, xuất CSV, đã chi, từ chối; email các mốc | ⏳ CHƯA LÀM |
| C6 | Đối soát hàng đêm + trang Đối soát | ⏳ CHƯA LÀM |
| C7 | FE giảng viên "Thu nhập" (số dư, tài khoản, rút tiền, lịch sử, sao kê) | ⏳ CHƯA LÀM |
| C8 | FE admin "Chi trả" + huy hiệu + nhật ký tài chính | ⏳ CHƯA LÀM |
| C9 | Tự động chi qua PayOS `payouts()` | 🅿️ HOÃN — cần bạn kiểm tra kênh PayOS có bật chi hộ; đã thiết kế `PayoutExecutor` để nối sau |

### Giai đoạn D — Chia sẻ khóa học
| ID | Hạng mục | Trạng thái |
|---|---|---|
| D1 | SEO/OG: `generateMetadata`, `metadataBase`, `sitemap.ts`, `robots.ts` | ⏳ CHƯA LÀM |
| D2 | `lib/referral.ts`: hết hạn 30 ngày, đọc định dạng cũ; `buildShareUrl` | ⏳ CHƯA LÀM |
| D3 | `ShareCourseDialog` (copy, `navigator.share` có fallback, Facebook/X/LinkedIn/Telegram/email) + điểm đặt | ⏳ CHƯA LÀM |
| D4 | Thống kê chia sẻ theo đơn mua (số đơn, doanh thu, chuyển đổi theo link) | ⏳ CHƯA LÀM |
| D5 | Mã QR | ⛔ BỊ CHẶN — chờ bạn chọn có thêm thư viện `qrcode.react` không |
| D6 | `POST /referrals/visit` (đếm lượt mở link) | 🅿️ HOÃN — API công khai ghi dữ liệu, chỉ làm với đủ lớp bảo vệ, sau khi A–C xong |
| D7 | Link ngắn `/r/{code}` | 🅿️ HOÃN |
| D8 | Mobile: hết hạn mã giới thiệu + chia sẻ native | ⏳ CHƯA LÀM |

### Giai đoạn E — Quảng bá của nền tảng
| ID | Hạng mục | Trạng thái |
|---|---|---|
| E1 | V160 `course_promotions` (+ `promotion_daily_stats`) | ⏳ CHƯA LÀM |
| E2 | Giảng viên đăng ký, admin duyệt (vị trí, thời gian, ưu tiên) | ⏳ CHƯA LÀM |
| E3 | Kệ "Khóa học nổi bật" qua tham số mới của `GET /api/v1/courses` (không thêm endpoint công khai), nhãn "Được đề xuất", boost tìm kiếm + xóa cache | ⏳ CHƯA LÀM |
| E4 | Thống kê quảng bá theo đơn mua (`payments.promotion_id`) cho giảng viên/admin | ⏳ CHƯA LÀM |
| E5 | `POST /promotions/{id}/click` + đếm hiển thị | 🅿️ HOÃN — như D6 |
| E6 | Chiến dịch email/thông báo + `marketing_opt_out` + link hủy | 🅿️ HOÃN — làm sau cùng, có thể tách PR |

### Giai đoạn F — Hoàn thiện
| ID | Hạng mục | Trạng thái |
|---|---|---|
| F1 | Mobile: dispute (category, trạng thái mới, `adminNote`), payments (REFUNDED) | ⏳ CHƯA LÀM |
| F2 | Cập nhật `claude/*.md` (core-features, database, architecture) | ⏳ CHƯA LÀM |
| F3 | Ngày lễ nhập vào `REFUND_HOLIDAYS` | ⛔ BỊ CHẶN — admin nhập khi vận hành |
| F4 | Kiểm thử đầu-cuối toàn bộ (mục "Kiểm chứng") | ⏳ CHƯA LÀM |

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
Chỉ INSTRUCTOR đã có xác minh. Tài khoản: mã hóa AES-GCM, hiển thị che, tên chủ khớp tên đã xác minh, xác nhận qua email, đổi tài khoản khóa rút 3 ngày. Yêu cầu rút: ≥ 100.000đ ≤ khả dụng, 1 yêu cầu mở/lần, `Idempotency-Key`, xác nhận email (30'), xếp lịch chi ngày 5/20, admin duyệt → chi (bắt buộc mã giao dịch) / từ chối (bắt buộc lý do) → số dư hoàn lại. Đợt đầu chi thủ công.

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
2. Biến môi trường khóa mã hóa số tài khoản + HMAC (C2).
3. Chọn thư viện QR (D5).
4. Kiểm tra kênh PayOS có bật chi hộ (C9).
5. Nhập ngày lễ vào `REFUND_HOLIDAYS` (F3).
6. Quyết định hạ tầng: `maxmemory` Redis, bỏ `--reload` ở ai-api, giới hạn bộ nhớ container, lớp lọc bot phía trước.
