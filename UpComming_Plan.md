# Kế hoạch phát triển LMS — chỉ giữ việc CHƯA XONG

> Dọn lại **15/11/2026**: toàn bộ việc đã xong đã được gỡ khỏi file này. Bản đầy đủ trước khi dọn: `docs/archive/UpComming_Plan_20261115_truoc_khi_don.md` (và lịch sử git). Chi tiết epic hoàn tiền/chi trả/quảng bá/công nợ + nhật ký: `docs/PAYMENT_PAYOUT_EPIC.md`. Quy tắc làm việc: `/var/lms/CLAUDE.md`.
>
> **Đã xong, không còn ở đây:** hạ tầng cache/coupon/bundle/streak, Epic hoàn tiền–chi trả–quảng bá–chia sẻ (giai đoạn A–G, đã deploy BE V154–V167 + FE), nhiều tài khoản nhận tiền, chi tự động PayOS (lõi), công nợ + trả nợ QR, chính sách hoàn tiền (mã lý do, minh chứng, khiếu nại), huy hiệu thông báo menu, và các lỗi streak/WebSocket/avatar/Discovery của giảng viên.

---

## 1. VIỆC ĐANG CHỜ BẠN (chặn tiến độ / cần thao tác)

| # | Việc | Ghi chú |
|---|---|---|
| 1 | **Sao lưu DB** | V163–V167 đã áp dụng khi CHƯA sao lưu. |
| 2 | **Gửi lại 2 lệnh PayOS thất bại để kiểm đường THÀNH CÔNG** (`LLR5-1` hoàn 10.000đ, `LLP1-1` rút 20.000đ bị ngân hàng báo gián đoạn 16/11). **BE đã deploy 16/11 (V168 áp dụng, khởi động sạch).** Để tách nguyên nhân: học viên đổi tài khoản nhận hoàn tiền sang ngân hàng khác (nút "Đổi tài khoản nhận hoàn tiền" ở Khiếu nại của tôi, cần FE bản 4870dd1 trên Vercel) rồi admin bấm Chi lại; với `LLP1-1` thì từ chối yêu cầu để hoàn số dư và giảng viên tạo yêu cầu mới với tài khoản khác. Hỏi PayOS vì sao lệnh treo ~15 giờ | Báo cáo: `docs/PAYOS_PAYOUT_VERIFICATION_20261116.md`. Hóa đơn bắt đầu sau khi đường thành công chạy thật. |
| 2b | ✅ **Phí kênh CHI PayOS đã chốt 17/11:** 2.950đ cố định/lệnh thành công, **nền tảng chịu hết** (không trừ giảng viên, không tính học viên). Đã thêm câu giải thích vào chính sách chia sẻ doanh thu, chính sách hoàn tiền và thẻ yêu cầu rút. **Còn chờ PayOS:** phí trừ vào ví chi hay quỹ phí riêng (ô "Còn lại" 97.050đ, "Cần thêm 902.950đ"), hết quỹ thì lệnh bị từ chối hay vẫn chi; sau khi biết thì sửa BE kiểm số dư cho đúng | Ảnh hưởng chứng từ/thống kê: thêm dòng "phí chi hộ PayOS" vào bảng kê nền tảng. |
| 3 | **Hỏi PayOS** (đang có email hỗ trợ): số dư tối thiểu 1.000.000đ là nạp một lần hay phải duy trì; số dư tài khoản chi có rút ngược được không; IP/hạn mức; lệnh chi có cần duyệt trên dashboard | Tài liệu API không nêu các điểm này. |
| 4 | **Trả cấu hình test về mặc định** sau khi thử xong: `PAYOUT_MIN_AMOUNT` (đang hạ xuống 20.000) → `100000`; `PAYOS_PAYOUT_MAX_AMOUNT`; `PAYOS_PAYOUT_ENABLED` (để 0 nếu chưa dùng thật) | Admin → Cài đặt. |
| 5 | **Duyệt hướng triển khai Hóa đơn & Thống kê** (mục 2.A) | Đã chốt toàn bộ câu hỏi 15/11 (mục 8 của `docs/INVOICE_AND_REPORTING_PLAN.md`); chỉ còn duyệt hướng triển khai ở mục 7 và chờ 2 lệnh PayOS xong. |
| 6 | **Quyết định chính sách**: (a) chặn gửi khiếu nại loại "đổi ý" khi ngoài chính sách (quá 7 ngày / đã học > 20%) hay chỉ cảnh báo; (b) bậc nợ 90 ngày có ẩn khóa khỏi tìm kiếm không (hiện chỉ chặn mua + gỡ khỏi kệ đề xuất) | Cả hai đang ở trạng thái "chỉ gắn nhãn/chặn một phần". |
| 7 | Nhập `REFUND_HOLIDAYS` (ngày lễ gia hạn hạn hoàn tiền) | Admin → Cài đặt. |
| 8 | **Đĩa server 38 GB chỉ còn ~3,7 GB (90%)**: dọn bản VS Code Server cũ (~3,3 GB), `/var/lms/fe_node_modules_old` + `fe_next_old` (~1,4 GB, tên "old"), volume Docker không dùng (3,4 GB — kiểm tên trước khi xoá) | Đĩa đầy đã làm hỏng build/backend một lần. |
| 9 | Hạ tầng: `maxmemory` Redis, bỏ `--reload` ở ai-api, giới hạn bộ nhớ container, giới hạn log `lms_backend`/`lms_caddy` | Từ đợt rà 11/11. |
| 11 | **Hiệu chỉnh đơn giá AI sau vài ngày chạy** (Admin → Cài đặt → Chi phí AI): đối chiếu tổng tiền trên trang AI Analytics với hóa đơn Google AI Studio/Groq của cùng kỳ rồi chỉnh đơn giá cho sát. Mặc định (0.30 / 2.50 / 0.15 USD mỗi triệu token, 0.04 USD/giờ âm thanh Groq, 25.500đ/USD) CHƯA đối chiếu với bảng giá thật của `gemini-3.5-flash` | Số liệu hiển thị là ước tính theo token. |
| 12 | **Xoay khóa Gemini** (log cũ của ai-worker từng in 8 ký tự đầu của khóa; nay chỉ còn 4 ký tự cuối) | Đã nằm ở mục 4, nhắc lại vì log cũ còn lưu. |
| 10 | Email giả của tài khoản demo (`@lms.local`) không nhận được thư xác nhận (BE gửi qua Gmail SMTP) | Muốn test rút tiền/liên kết tài khoản: đổi sang email thật hoặc xác nhận bằng SQL. |

---

## 2. TASK MỚI / ĐANG LÀM

### A. Hóa đơn / chứng từ & Thống kê – báo cáo tự động — ✅ H1–H4 + 3 ĐỢT CHỈNH ĐÃ DEPLOY (BE V172), CHỜ NGHIỆM THU CUỐI
**Bổ sung đợt 3 (21/11/2026, V173):** (a) bảng giá AI theo model trong `AiPricing` (3.6 Flash 0.75/3.75 đến hết 31/12/2026 rồi 1.50/7.50; 3.5 Flash 1.50/9.00; flash-lite 0.30/2.50) — đối chiếu với hóa đơn Google; đơn giá ở Cài đặt chỉ còn là dự phòng cho model chưa có trong bảng; (b) **đối soát hóa đơn**: bảng `ai_billing_entries` (admin nhập theo kỳ, không sửa/xóa, không chồng lấn; đã nhập sẵn 24/8–31/10 = 143.261đ và 1–21/11 = 220.739đ từ trang Usage của Google), trang AI Analytics hiện hóa đơn thực tế / đã quy được / chưa quy được; (c) ước tính lịch sử từ dữ liệu đã lưu (lồng tiếng, nhận dạng giọng nói, Gia sư AI) gắn `estimated=1` — chỉ phần nhìn thấy được (≈ 3% hóa đơn 24/8–31/10 và ≈ 1% tháng 11; phần còn lại là token suy nghĩ + lần chạy thử không để lại dấu vết); (d) Hỏi đáp giảng viên: tìm/lọc "chưa trả lời"/sắp xếp trong truy vấn (trước đây lọc sau phân trang nên sai trang), danh sách + thống kê học viên của giảng viên tính ở cơ sở dữ liệu. **Việc còn lại:** đo chi phí Gemini Live (phiên học trực tiếp) và Google Search grounding; `.env` của ai-worker đang đặt `GEMINI_MODEL=gemini-3.5-flash` (đắt gấp ~2,4 lần output so với 3.6 Flash khuyến mãi) — bạn cân nhắc đổi sang 3.6 Flash; từ 1/1/2027 giá 3.6 Flash tăng gấp đôi.

**Đợt chỉnh sau nghiệm thu 3 (21/11/2026):** BE V172 + FE + ai-worker đã deploy. (1) Trang Người dùng chỉ còn 2 nút lọc **Bị khóa / Bị gắn cờ spam** (bỏ Tất cả/Học viên/Giảng viên/Quản trị; xem người khác bằng ô tìm kiếm). (2) **Thông báo hệ thống:** bản đã thu hồi có **Khôi phục** (hiện lại cho người dùng) và **Xóa hẳn** (chỉ khi đã thu hồi). (3) **Hàng đợi AI:** làm rõ mục đích (việc lồng tiếng đang chờ/đang chạy/lỗi cần xử lý), mỗi dòng có khóa học, bài học, người yêu cầu, ngày tạo/hoàn tất; ẩn job lỗi đã có lần chạy thành công sau; **chặn "Thử lại" khi bài đó đã có bản thành công** (tránh tốn chi phí AI); bỏ dữ liệu thừa. (4) **AI Analytics làm lại theo chi phí thật:** mọi lệnh gọi Gemini/Groq (lồng tiếng dịch + nhận dạng, bóc băng, AI Tutor, tìm khóa học, trợ lý giảng viên, giám sát thi, nhúng dữ liệu) báo số token thật (cả token suy nghĩ) / giây âm thanh về BE (`POST /api/internal/ai-usage`, header `X-Internal-Token`), BE quy đổi bằng đơn giá cấu hình (nhóm **Chi phí AI** trong Cài đặt: 4 đơn giá + tỷ giá USD/VND) → thẻ chi phí quy ra đồng + so kỳ trước, biểu đồ cột xếp chồng theo ngày × tính năng, biểu đồ tròn theo tính năng, bảng người dùng có tìm kiếm/phân trang/khóa AI. **Dữ liệu cũ không khôi phục được:** trước 21/11/2026 chỉ ghi Flashcard/Mindmap/Quiz nên các kỳ cũ thấp hơn hóa đơn thật (trang có ghi chú khi khoảng chọn bắt đầu trước ngày này). Đây là **số ước tính theo token**, không khớp tuyệt đối hóa đơn nhà cung cấp. Đã kiểm: 1189 test BE đạt, V172 + `ddl-auto=validate` + các truy vấn mới chạy trên MySQL 8 tạm, 82 test worker đạt, một lệnh Gemini thật đã ghi đúng 1 dòng `ai_usage_logs` (token suy nghĩ + chi phí khớp công thức). DB đã sao lưu trước V172: `/home/dev-thien/backups/lms_db_pre_V172_20261120_1851.sql.gz`.
**Đợt chỉnh sau nghiệm thu 2 (20/11/2026):** chi tiết ở `docs/UI_ROUND2_PLAN_20261120.md` — V171 (mã truy cập chứng từ, thu hồi thông báo, lưu trữ gói khóa). PDF chứng từ thiết kế lại (bảng căn chỉnh, mã kiểm SHA-256, QR mở thẳng bản gốc qua đường công khai `/api/v1/public/invoices/{mã}` có giới hạn tần suất và công tắc tắt khẩn cấp); thông báo hệ thống có Sửa/Thu hồi; gói khóa học Xóa/Lưu trữ; học viên có lọc/tìm/phân trang giao dịch; giảng viên có lọc/phân trang sao kê, lịch sử rút, giao tiếp; admin: AI Analytics có bộ lọc, AI Queue gọn lại, cấu hình hệ thống chỉ còn thông số sửa được với điều khiển đúng kiểu và ma trận kiểm thử toàn bộ khóa.

**Đợt chỉnh sau nghiệm thu 1 (20/11/2026):** chi tiết ở `docs/UI_REDESIGN_PROPOSAL_20261120.md` — tra cứu-trước cho dữ liệu lớn, chứng từ gắn vào giao dịch/rút tiền/bảng kê, thống kê dạng biểu đồ, nút Chia sẻ thống nhất, cấu hình hệ thống theo nhóm, bỏ chữ "demo", phí PayOS là hằng số, bảng kê tháng bù các tháng đã qua.


Mọi khoản tiền (thanh toán, hoàn, chi trả, nợ) cần chứng từ bất biến có PDF + minh bạch chi phí; admin cần thống kê toàn nền tảng và xuất báo cáo tự động. Phạm vi, thiết kế dữ liệu, 4 giai đoạn (H1 gộp chứng từ → H2 quản trị → H3 thống kê → H4 báo cáo tự động) và quyết định đã chốt: **`docs/INVOICE_AND_REPORTING_PLAN.md`**. Bắt đầu sau khi hai lệnh PayOS LLR5-1/LLP1-1 xong và bạn duyệt hướng triển khai (mục 1 #5).

**Tiến độ H1 (20/11/2026):** BE lõi xong, `mvn clean verify` 766 test đạt, đã thử migration + truy vấn + backfill trên bản sao dữ liệu thật (9 chứng từ: 7 biên lai, 1 phiếu hoàn, 1 phiếu chi; bỏ `@example.com`/`DEMO-`; chạy lại không tạo trùng). Gồm V169 (`invoices`, `invoice_lines`, `invoice_sequences` + 3 khóa cấu hình `INVOICE_*`), gói `com.lms.invoice` (phát hành bằng job nền 2 phút, KHÔNG gắn vào luồng thanh toán/hoàn/rút/trả nợ), PDF OpenPDF, API `/api/v1/invoices`, `/api/v1/admin/invoices` (+ CSV). **Email (PR 2) đã code 20/11:** mỗi chứng từ MỚI (≤ 48 giờ) gửi kèm PDF; chứng từ phát bù cho giao dịch cũ không gửi mail. **Giao diện (PR 3) đã code, commit ở `fe` nhưng CHƯA push (chờ BE chạy V169):** `/invoices` (học viên), mục Chứng từ ở Thu nhập (giảng viên), `/admin/transactions/invoices` + menu (admin). **Còn lại:** test thật sau deploy (mua khóa → biên lai + email); phiếu xóa nợ, bảng kê tháng (STM/PLT) → H2/H3. **Trước khi deploy:** sao lưu DB (migration V169 chỉ thêm bảng/khóa cấu hình, không đụng dữ liệu cũ).

**H2–H4 (20/11/2026):** V170 (`report_snapshots` + khóa `REPORT_*`, `STATEMENT_EMAIL_INSTRUCTORS`, `PAYOS_PAYOUT_FEE`). Bảng kê thu nhập tháng giảng viên (`STM`, loại chứng từ mới, job mỗi giờ cho tháng vừa xong; email cho giảng viên chỉ khi bật `STATEMENT_EMAIL_INSTRUCTORS`). Thống kê tài chính admin `/admin/finance-reports`: doanh thu gộp/ròng, hoàn tiền, phần nền tảng/giảng viên, chi trả, chi phí PayOS (số lệnh × `PAYOS_PAYOUT_FEE`), nghĩa vụ/công nợ, đối soát PayOS với sổ cái; theo ngày/tuần/tháng, theo khóa/giảng viên; xuất CSV/XLSX (ghi dạng luồng, không thêm thư viện)/PDF. Báo cáo tự động ngày/tuần/tháng bật-tắt trong Cài đặt, người nhận cấu hình được (trống = mọi admin), job kiểm mỗi giờ + chống trùng `dedupe_key`, nút "Chạy ngay". **Bảng kê nền tảng (PLT) = báo cáo tháng ở trang này** (không tạo chứng từ riêng vì chứng từ cần một người nhận). **Chưa làm:** phiếu xóa nợ, chi phí PayOS lưu theo từng lệnh (hiện tính theo số lệnh × phí cấu hình), cơ chế phí cộng dồn chờ PayOS trả lời.

### B. Chi tự động PayOS — còn lại sau lõi
- Xác nhận thật (khi PayOS chi xong): hoàn tiền tự ghi "đã chuyển" + học viên nhận thông báo/email; rút tiền giảng viên tự ghi "đã chuyển".
- Thử các đường lỗi thật (tài khoản sai, vượt hạn mức, thiếu số dư) và ghi lại thông báo lỗi PayOS.
- SDK `payos-java 2.0.1`: hàm `estimateCredit` lỗi đóng gói (đã bỏ qua, chỉ kiểm số dư); xem xét nâng phiên bản SDK.
- Học viên bị ngân hàng từ chối tài khoản: chưa có màn hình tự cập nhật (admin phải liên hệ) và học viên chưa nhận thông báo khi chi thất bại.
- Tra cứu tên chủ tài khoản trước khi chi (tùy chọn): PayOS SDK không có; VietQR cần cấp quyền. Hiện chỉ cảnh báo lệch tên SAU khi chi.
- Hỏi PayOS xem lệnh chi có webhook không (hiện đối soát bằng hỏi lại mỗi 2 phút).

### C. Hoàn tiền / chi trả / công nợ — việc nhỏ còn lại
- Test tay (chưa ai chạy trọn): khiếu nại có tài khoản chuẩn → duyệt theo mã lý do; khiếu nại của giảng viên; tạo nợ (chế độ "giảng viên chịu toàn bộ" vượt số dư) → trả một phần qua QR → xóa nợ; huy hiệu menu admin.
- **B11:** 3 khiếu nại cũ `RESOLVED_REFUNDED` (giao dịch vẫn PAID, chưa thu hồi) cần admin xử lý tay từng ca (AI Agent & Unity đã xử lý bằng SQL khi test).
- Chữ giao diện mới của giai đoạn G (panel chính sách, công nợ, chi PayOS) mới chỉ tiếng Việt, chưa vào 4 file i18n.
- Mobile chưa cập nhật: nhiều tài khoản nhận tiền, form khiếu nại chuẩn, nhãn "chờ chuyển", thông báo mới (`PROMOTION_*`, `PAYOUT_*`, nhắc flashcard theo khóa, liên kết `/materials?courseId=`).
- Chia sẻ (D): D8 mobile; D6 đếm lượt xem, D7 link ngắn 🅿️ HOÃN (API công khai cần đủ 9 lớp bảo vệ).
- Quảng bá (E): boost tìm kiếm, quy kết theo `promotion_id`, đếm click, chiến dịch email 🅿️ HOÃN.
- Dữ liệu demo: đơn demo thiếu dòng sổ cái; trang legal còn ghi tay 37/97 (đã sửa phần mới, rà lại).
- Test FE (vitest) không chạy được trên server — chỉ CI chạy; test `payments/page.test.tsx` từng thiếu gói `motion`.

---

## 3. MOBILE — Epic Native App (React Native/Expo), BẮT BUỘC cho khoá luận, báo cáo cuối 12/2026

**Quyết định đã chốt:** Expo (repo `urglydog/ailms-mobile`, `/var/lms/mobile`, push thẳng `main`); chỉ vai trò Học viên; test bằng Expo Go + tunnel (không cần Apple Developer); Metro chạy trên máy cá nhân, KHÔNG chạy trên server; offline flashcard dùng `expo-sqlite`.

**Rủi ro deadline:** nếu trễ, cắt offline flashcard trước, không cắt phần AI.

| # | Gap còn lại | Trạng thái |
|---|---|---|
| 1 | Live session native (LiveKit) | **HOÃN** — cần EAS dev client (Expo Go không chạy WebRTC native); đang mở phòng live bằng trình duyệt. BE sẵn sàng (`GET /api/v1/live-sessions/{id}/view`) |
| 2 | Giám sát thi (proctoring) — chụp frame định kỳ gửi Gemini Vision | **CẦN TEST THIẾT BỊ THẬT** — rủi ro `takePictureAsync` xen giữa `recordAsync` làm dừng video; nếu video bị cắt thì bỏ quay liên tục, chỉ dùng chuỗi frame |
| 3 | Push notification (`expo-notifications`) | **HOÃN** — Expo Go không nhận remote push, chỉ có iPhone (dev build cần $99), BE chưa có bảng/API lưu push token; tạm dùng thông báo trong app tự làm mới 30 giây |
| 4 | Offline flashcard | Đã vá 09/11; **còn cần test thiết bị thật** (bật chế độ máy bay giữa chừng) |
| 5 | Test thiết bị thật (Android + iOS) cho toàn bộ luồng mới | Chưa — code chỉ kiểm bằng `tsc` + lint |
| 6 | Đa ngôn ngữ giao diện (vi/en/ja/zh) | Chưa — việc lớn |
| 7 | Chưa làm (ưu tiên thấp/khó): kéo-thả vị trí phụ đề, chất lượng video, biểu tượng "có tài liệu đính kèm" (BE chưa trả field), kệ khoá học theo danh mục ở trang chủ, xem live trong app | |

**Quy ước:** không tự chọn thứ tự — hỏi ưu tiên trước khi bắt đầu gap mới. **Tiêu chí nghiệm thu:** chạy qua Expo Go trên Android và iPhone thật, một luồng trọn vẹn: đăng nhập → xem khóa → học bài → quiz/flashcard → bài thi có giám sát camera.

---

## 4. LỖI / VẬN HÀNH ĐANG THEO DÕI

- **Nộp bài thi bị đứng hình trên Safari iOS** (`fe/app/(student)/exam/[quizId]/page.tsx`): đã thử vá 06/10/2026 gây regression mất nhận diện khuôn mặt → revert (commit `8bc7bab`). **Quyết định: KHÔNG vá tiếp trên web**, sẽ hết khi làm bài thi trên app native (`expo-camera`).
- **Supabase vector** (`esiyaricxvhucimtrdqn.supabase.co`) không phân giải tên: `embed_course` lỗi, ảnh hưởng đánh chỉ mục Discovery và RAG của AI Tutor (lồng tiếng không ảnh hưởng). Cần khôi phục project hoặc tạo mới rồi cập nhật `SUPABASE_VECTOR_URL/KEY` — **bạn đã nói sẽ xử lý sau**.
- **Khóa Gemini bị log dạng văn bản thường** trong URL (do `httpx` log INFO) ở ai-worker/ai-api: đặt log `httpx` lên WARNING hoặc gửi khóa qua header `x-goog-api-key`, rồi tạo khóa mới — **bạn đã nói sẽ xử lý sau**.
- Console có thể hiện một dòng 401 màu đỏ ở `ranking/me` khi token vừa hết hạn: đó là lần gọi đầu trước khi tự refresh (hành vi thiết kế), không phải lỗi quyền.
- `ai-worker/app/config.py:58` giá trị tạm 0.7; `MaterialGenerationService` có 2 `catch JsonProcessingException` im lặng; ai-worker `discovery.py` tự mở `httpx.AsyncClient` (nên dùng client chung).
- Điểm các lượt thi thử 96–100 đều 0.00: chưa điều tra xem lỗi chấm điểm hay chỉ do chọn sai.
- Exam: HTTP 500 khi nộp bài không tự kết thúc phiên (chỉ 404/410) — cố ý để tránh mất bài do lỗi tạm.

- **Vấn đề mới phát hiện khi làm đợt chỉnh 3 (21/11/2026), chưa sửa:**
  - ✅ **Đã sửa 21/11:** đăng nhập, AI Discovery, hỗ trợ và chứng từ công khai từng đọc IP từ phần tử ĐẦU của `X-Forwarded-For` (client tự giả được) — nay dùng chung `RateLimitFilter.clientIp` (IP do proxy nối vào cuối).
  - ✅ **Đã sửa 21/11:** danh sách thông báo giới hạn 200 mới nhất; thẻ số liệu Giám sát AI Queue lấy từ bảng job thật (`/api/v1/admin/dubbing-jobs/summary`) thay vì bộ nhớ tạm Celery; dọn đĩa (93% → 79%: xóa volume MySQL/Redis tạm do các lần kiểm thử + cache FE không dùng).
  - **Chưa tính vào chi phí AI:** (a) phiên học trực tiếp dùng Gemini Live API (`app/live/providers/gemini.py`, tính theo token âm thanh) chưa báo mức dùng; (b) AI Tutor bật Google Search grounding (miễn phí 5.000 truy vấn/tháng rồi 14 USD/1.000) chưa tính; (c) ghi nhận chi phí quá khứ chỉ làm được bằng ước tính từ dữ liệu đã lưu hoặc đối chiếu bảng điều khiển Google/Groq.
  - ✅ **Đã sửa 21/11:** Q&A giảng viên (tìm/lọc/sắp xếp ở BE) và danh sách + thống kê học viên của giảng viên (tính ở BE).
  - Danh sách thông báo của người dùng chưa giới hạn số lượng khi tải.
  - Chi phí AI chưa chính xác tuyệt đối: nhúng dữ liệu ước lượng token bằng độ dài/4 (đánh dấu `estimated`); Edge-TTS miễn phí nên không tính; Groq tính theo giờ âm thanh (làm tròn tối thiểu theo chính sách nhà cung cấp không áp dụng); lệnh gọi Gemini thất bại giữa chừng không có `usageMetadata` nên không ghi.
  - Các đơn giá AI sửa được trong Cài đặt (khác nguyên tắc "giá trị do bên thứ ba quy định không cho sửa") — giữ vì đây là hiệu chỉnh ước tính; nếu muốn cố định thì chuyển thành hằng số trong mã.

- **Tối ưu chi phí AI (21/11/2026, đo thật trên job lồng tiếng #14/#15 và học liệu):** lồng tiếng 10 phút còn ≈ 1.350đ (trước đợt chỉnh ≈ 54.000đ/bài) nhờ gộp lô + `DUBBING_THINKING_LEVEL=minimal` + giới hạn độ dài theo tốc độ đọc đã đo của giọng (lần đầu một giọng mới đọc lại ~65% câu vì chưa có số liệu → nên "làm ấm" giọng mới bằng một đoạn đọc thử ngắn). Học liệu: mức suy nghĩ `low` (MATERIAL/QUIZ_EXPLAIN) và `minimal` (tiêu đề, kiểm duyệt) bằng biến `MATERIAL_THINKING_LEVEL`, `QUIZ_EXPLAIN_THINKING_LEVEL`, `AUX_THINKING_LEVEL` (rỗng = tắt); mindmap 633đ → 165đ, quiz ≈ 740đ → 330đ, flashcard ≈ 700đ → 375–500đ, nhanh gấp 3. **Chưa làm:** (a) điểm nghẽn thời gian lồng tiếng là bước ghép âm thanh (pydub từng câu, ~0,33s/câu, 53% thời gian) → viết lại thành một lệnh FFmpeg, cần bạn của chủ dự án xem lại; (b) `AZURE_TTS` chưa được ghi chi phí; (c) Gia sư hỏi đáp, Discovery, trợ lý giảng viên, giám sát thi, kế hoạch học vẫn dùng suy nghĩ mặc định — đo chất lượng rồi mới hạ; (d) hạ vòng rút gọn 2 → 1; (e) tăng `TTS_CONCURRENCY` 8 → 16 nếu gói Azure cho phép; (f) đổi sang Gemini 3.6 Flash rồi đo lại.
- ✅ **Đã sửa 21/11:** `quantity_level` (Ít ≈10 / Vừa ≈20 / Nhiều ≈30 câu hoặc thẻ; phạm vi hẹp tính theo tỷ lệ số bài, sàn 5 câu/10 thẻ), `difficulty_level` (Dễ/Trung bình/Khó theo thang Bloom) và mức chi tiết mindmap (≈15/≈35/≈60 nút) nay đi vào prompt (`ai-worker/app/services/material_options.py`; BE gửi thêm `courseLessonCount`). Đo thật: quiz 10/25/5 câu, flashcard 12/20/25/10 thẻ, mindmap 15/39/62 nút theo từng mức. Chi phí tăng theo số lượng (quiz Nhiều/Khó ≈ 1.400đ so với ≈ 330đ trước). **Lỗi/khoảng trống mới phát hiện còn lại:** Quiz theo phạm vi Chương tự dịch các bài chưa có bản dịch của ngôn ngữ đó (đã gặp: 181 câu, ≈ 1.150đ, ghi vào DUBBING_TRANSLATE) — không phải lỗi nhưng là chi phí ngầm nên hiển thị cho người dùng. Tác vụ định kỳ "xóa thông báo đã đọc quá 90 ngày" của ai-worker chưa được lập trình (`NotImplementedError` mỗi giờ).

- ✅ **Đã sửa 21/11 (điểm yếu bảo mật):** khóa Gemini từng nằm trong URL (`?key=`) nên `httpx` in đủ khóa ra log của worker/api trong mỗi lệnh gọi. Nay khóa đi trong header `x-goog-api-key` (3 chỗ: generate, embed, batch embed) và `ai-worker/app/log_redaction.py` che mọi `key=`/chuỗi dạng `AIza…`/`AQ.…` ở log `httpx`/`httpcore`; có test chống tái phát. Đã xoay khóa (khóa mới trong `.env`), ba container AI được tạo lại nên log cũ chứa khóa cũ đã mất. **Việc còn lại của chủ dự án:** xóa khóa cũ trên AI Studio sau khi test lồng tiếng thật ổn. Model đã chuyển sang `gemini-3.6-flash` (giá khuyến mãi 0,75/3,75 USD đến hết 31/12/2026, sau đó 1,50/7,50 — xem lại model từ tháng 12); `TTS_CONCURRENCY` mặc định 8 → 16 (theo dõi lỗi 429).

- **Đo lồng tiếng trên 3.6 Flash (job #16, lect 4, 15 phút, ja-JP, 21/11/2026):** 205 s; Gemini ≈ 734đ (≈ 490đ/10 phút, 0 token suy nghĩ), Azure ước tính ≈ 1.800đ, tổng ≈ 1.700đ/10 phút và ≈ 137 s/10 phút (job #15 trên 3.5: ≈ 1.350đ, 94 s — nhưng bản dịch #15 bị nén quá ngắn, chỉ 0,13 ký tự Nhật/ký tự Anh). **Phát hiện:** (1) bước đọc bị nghẽn bởi việc **đo độ dài từng file MP3 bằng pydub/FFmpeg** (đo thật: ≈ 6,9 file/s trên 2 vCPU, không đổi giữa 8 và 16 luồng) nên `TTS_CONCURRENCY=16` chưa giúp gì; cách sửa: tính độ dài từ kích thước file (MP3 CBR 32 kbps ⇒ giây = byte/4000) hoặc đổi đầu ra Azure sang WAV (đọc header) — cùng hướng với đề xuất của bạn; bước ghép cũng giải mã lại từng file. (2) 3.6 Flash dịch **dài và sát nghĩa hơn** 3.5 Flash dưới cùng giới hạn ký tự (A/B 162 câu: tổng ký tự 2.391–2.865 so với 1.888–2.196; vượt giới hạn 50–59% so với 26–41%; vượt 1,3× 30–42% so với 8–22%) nên nhiều câu phải rút gọn/đọc lại/tăng tốc hơn (job #16: 90/259 câu vẫn R > 1,3, 47 câu đọc lại); 3.5 hay bỏ ý (vd "việc hành chính" → "tổng quan"). Cân nhắc: siết prompt "tuyệt đối không vượt max_chars" cho 3.6, hoặc chấp nhận. (3) Không có lỗi 429, log không còn khóa với khóa mới và 16 luồng.
- **Modal/BGM:** chủ dự án đã thêm `BGM_SEPARATION=modal`, `MODAL_TOKEN_ID`, `MODAL_TOKEN_SECRET` vào `.env` ai-worker và container đã được tạo lại để nạp. **Chưa có tác dụng**: code tách nhạc (`app/services/background_music.py`, `modal_apps/demucs_separator.py`) chưa được push lên GitHub và thư viện `modal` chưa có trong image; khi bạn push code, cần build lại image (`docker compose up -d --build ai-worker`) rồi deploy hàm `lms-demucs` bằng token.

- ✅ **Mức suy nghĩ theo tính năng (21/11/2026, ai-worker):** tất cả lệnh Gemini nay đã được rà. Đặt thấp: lồng tiếng `DUBBING_THINKING_LEVEL=minimal` (đã có sẵn trong `.env`); học liệu `MATERIAL_THINKING_LEVEL=low`; giải thích đáp án quiz `low`; tiêu đề hội thoại và kiểm duyệt đánh giá `AUX_THINKING_LEVEL=minimal`; Discovery (cả 2 bước) `DISCOVERY_THINKING_LEVEL=minimal`; trợ lý giảng viên `INSTRUCTOR_AI_THINKING_LEVEL=minimal`; kế hoạch học `STUDY_PLAN_THINKING_LEVEL=low`; giám sát thi: rủi ro `PROCTORING_RISK_THINKING_LEVEL=minimal`, khung hình `PROCTORING_FRAME_THINKING_LEVEL=low`. **Giữ mặc định:** Gia sư AI hỏi đáp (2 lệnh có/không có ngữ cảnh). Đo thật: `minimal` luôn 0 token suy nghĩ; `low` thất thường (0–600). Mỗi lệnh Discovery/giảng viên/rủi ro giảm 75–90% chi phí. Mỗi biến rỗng = về mặc định của model. **Chưa kiểm:** phân tích khung hình bằng ảnh thật (giữ `low` cho thận trọng); lập kế hoạch học chưa đo.

### Web — việc nhỏ cần quyết định / không gấp
- Khuyến mãi công khai: coupon có mã chưa có cờ `is_public` → danh sách ở trang thanh toán chỉ hiện coupon tự động. Hướng đề xuất: thêm cột `is_public` + Flyway.
- Trang `/cart` hiển thị giá theo coupon tự động nhưng trang thanh toán dùng lựa chọn của học viên → có thể lệch nếu bỏ chọn.
- Dark theme thật (hiện khóa `color-scheme: only light`) — việc lớn; ~166 chỗ màu cyan/blue/purple/slate/indigo chưa có token.
- Logo LinguaLearn cho kênh PayOS ở `fe/public/brand/` (chưa commit).

---

## 5. QUY TRÌNH & GHI CHÚ

- **Làm việc trên nhánh `feat/additional-features`** (BE/FE/ai-worker); BE chạy thật từ `/var/lms/be` (bind-mount + devtools) nên code dở KHÔNG để ở đó — dùng **một** worktree dùng lại (`/home/dev-thien/work/be-work`), xoá `target/` sau mỗi lần verify, kiểm `df -h /` trước khi build lớn. Trước khi push BE: `mvn clean verify` đầy đủ. Có migration → sao lưu DB trước khi restart. FE không push trước khi BE tương ứng đã chạy.
- **API công khai** phải đủ 9 lớp bảo vệ (danh sách trắng đầu vào, không ghép chuỗi vào SQL/Redis, phản hồi cố định 204, giới hạn bộ nhớ, rate limit từ chối-khi-lỗi, công tắc tắt khẩn cấp, handler nhỏ, test âm tính, rà Jackson/Tomcat).
- Ý tưởng bảo mật dữ liệu: nhận diện request thường xuyên hoặc truy xuất lượng dữ liệu lớn → AI cảnh báo admin.
