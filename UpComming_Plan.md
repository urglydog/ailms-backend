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
| 10 | Email giả của tài khoản demo (`@lms.local`) không nhận được thư xác nhận (BE gửi qua Gmail SMTP) | Muốn test rút tiền/liên kết tài khoản: đổi sang email thật hoặc xác nhận bằng SQL. |

---

## 2. TASK MỚI / ĐANG LÀM

### A. Hóa đơn / chứng từ & Thống kê – báo cáo tự động — 🚧 H1 (lõi BE) ĐÃ CODE 20/11, CHỜ SAO LƯU DB + DEPLOY
Mọi khoản tiền (thanh toán, hoàn, chi trả, nợ) cần chứng từ bất biến có PDF + minh bạch chi phí; admin cần thống kê toàn nền tảng và xuất báo cáo tự động. Phạm vi, thiết kế dữ liệu, 4 giai đoạn (H1 gộp chứng từ → H2 quản trị → H3 thống kê → H4 báo cáo tự động) và quyết định đã chốt: **`docs/INVOICE_AND_REPORTING_PLAN.md`**. Bắt đầu sau khi hai lệnh PayOS LLR5-1/LLP1-1 xong và bạn duyệt hướng triển khai (mục 1 #5).

**Tiến độ H1 (20/11/2026):** BE lõi xong, `mvn clean verify` 766 test đạt, đã thử migration + truy vấn + backfill trên bản sao dữ liệu thật (9 chứng từ: 7 biên lai, 1 phiếu hoàn, 1 phiếu chi; bỏ `@example.com`/`DEMO-`; chạy lại không tạo trùng). Gồm V169 (`invoices`, `invoice_lines`, `invoice_sequences` + 3 khóa cấu hình `INVOICE_*`), gói `com.lms.invoice` (phát hành bằng job nền 2 phút, KHÔNG gắn vào luồng thanh toán/hoàn/rút/trả nợ), PDF OpenPDF, API `/api/v1/invoices`, `/api/v1/admin/invoices` (+ CSV). **Còn lại:** PR 2 email xác nhận thanh toán kèm PDF; PR 3 giao diện (học viên/giảng viên/admin + nav + i18n); phiếu xóa nợ, bảng kê tháng (STM/PLT) → H2/H3. **Trước khi deploy:** sao lưu DB (migration V169 chỉ thêm bảng/khóa cấu hình, không đụng dữ liệu cũ).

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
