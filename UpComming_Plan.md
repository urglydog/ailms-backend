# Kế Hoạch Phát Triển Nâng Cao LMS (Premium Commercial Roadmap)

Tài liệu này định nghĩa chi tiết các Epic/User Story để nâng cấp hệ thống LMS lên tiêu chuẩn thương mại cao cấp. Các tính năng được chia theo Sprint dựa trên ma trận ưu tiên: **Hạ tầng chống chịu -> Dòng tiền -> Tính năng giữ chân/AI**.

Trước mỗi tính năng đều đi kèm với **Bối cảnh & Nỗi đau (Pain Point)** để đội ngũ kỹ thuật/vận hành luôn nắm rõ bài toán cốt lõi.

> **Lưu ý:** Các task đã hoàn thành (Redis Cache, Coupons, Bundles, Streak, AI Sentiment Filter, AI Study Plan, Knowledge Gaps, Instructor Dashboard) đã được dọn khỏi file này để giữ chỗ cho task mới — xem lại chi tiết triển khai/ngày hoàn thành trong lịch sử commit của từng repo (`be`/`fe`/`ai-worker`) hoặc trong `weekly_report.md` (tóm tắt theo tuần để demo).
>
> **Dọn lại 30/10/2026:** các mục mobile đánh dấu ✅ đã xong qua 3 lần rà soát (lần 2/3/4: port auth, học liệu, gradebook, quiz cá nhân, leaderboard, lộ trình AI, AI tutor explain, phát âm flashcard, chat discovery...) đã được gỡ khỏi file này — xem lịch sử commit repo `mobile` nếu cần chi tiết. Chỉ giữ lại các gap THẬT sự còn pending.

---

## BACKLOG / TASK MỚI

### Epic: Mobile Native App (React Native/Expo) — BẮT BUỘC cho khoá luận, deadline cuối 12/2026

**Bối cảnh & Nỗi đau:** Dự án là khoá luận tốt nghiệp, báo cáo dự kiến cuối tháng 12/2026 — app native là yêu cầu bắt buộc của đề tài, không phải quyết định theo nhu cầu người dùng. PWA Giai đoạn 1 (manifest + service worker no-op, đã deploy trên `fe`) bị bỏ ngang — test thật trên Safari iPhone cho thấy không tạo ra khác biệt đáng kể so với mở web thường (chỉ là icon màn hình chính), nên quyết định đi thẳng lên native thay vì tiếp tục đầu tư responsive/offline-first trên web.

**User Story:** Là học viên, tôi dùng 1 app native thật trên điện thoại để học (khoá học, flashcard/quiz, AI Tutor, lộ trình AI, thi cử có giám sát camera) — mượt và đúng chuẩn mobile, không bị giới hạn của trình duyệt (vd Safari iOS không hỗ trợ `getDisplayMedia`, hạn chế quyền camera...).

**Quyết định kỹ thuật đã chốt:**
- Stack: **React Native (Expo)**. Repo riêng: GitHub `urglydog/ailms-mobile`, clone về local `/var/lms/mobile` — không gộp monorepo với `fe`.
- Scope vai trò: **chỉ Học viên**. Giảng viên/Admin tiếp tục dùng web Next.js hiện tại, không port qua app.
- Test bằng **Expo Go + tunnel** (quét QR) là luồng chính suốt dự án — free, không cần Mac, không cần Apple Developer account. EAS Build (cần Apple Developer $99/năm cho iOS) là tuỳ chọn, chỉ cần nếu muốn file cài đặt độc lập hoặc submit store — không bắt buộc cho demo khoá luận.
- **Metro bundler (dev server) chạy trên máy cá nhân, KHÔNG chạy trên server Hetzner** — server đang căng RAM (free ~1.1GB, swap dùng 1.8GB lúc kiểm tra 06/10/2026), tránh ảnh hưởng `be`/`ai-worker` production.
- Offline Flashcard (từng định làm ở PWA Giai đoạn 2) dời sang sau khi có app native, dùng `expo-sqlite` thay IndexedDB.

**Giai đoạn (06/10 → cuối 12/2026, ~12 tuần):**
1. **Tuần 1 — Nền tảng:** ✅ đã xong (06/10/2026) — Expo Router project khởi tạo tại `/var/lms/mobile`, push lên `urglydog/ailms-mobile`; `src/lib/api/client.ts` port từ `fe/lib/api/client.ts` (cùng pattern auto-refresh JWT 401/403, đổi `localStorage` → `expo-secure-store`); cài `expo-secure-store`/`expo-auth-session`/`expo-camera`; permission camera/mic đã khai báo trong `app.json` cho proctoring sau này.
2. **Tuần 2-4 — Port luồng học tập lõi:** danh sách khoá học, chi tiết khoá học, enrollment/checkout (VNPay — kiểm tra lại flow redirect khi tới bước này), lesson player, Flashcard/Quiz UI, hồ sơ cá nhân cơ bản.
3. **Tuần 5-7 — Tính năng AI khác biệt hoá:** Socratic AI Tutor, AI Study Plan, bài thi + Proctoring dùng `expo-camera` (giải quyết luôn gốc rễ bug Safari iOS đứng hình/không ghi hình được, không cần vá tiếp bên web).
4. **Tuần 8-9 — Tính năng đặc thù native:** push notification (`expo-notifications`), file access tài nguyên tĩnh (`expo-document-picker`/`expo-file-system`), permission camera/mic đúng chuẩn OS.
5. **Tuần 10-11 — Offline Flashcard:** `expo-sqlite`, đồng bộ spaced repetition khi có mạng lại; phân loại API bắt buộc online (quiz có điểm, AI chat, hành động ảnh hưởng XP) vs được cache offline.
6. **Tuần 12 — Polish & chuẩn bị bảo vệ:** test thiết bị thật (Android + iOS, nhiều hãng/kích thước), đo hiệu năng cơ bản lấy số liệu cho báo cáo, 1 vòng polish UI/UX tối thiểu.

**Lưu ý rủi ro deadline:** nếu tới tuần 9-10 mà Giai đoạn 2-4 (port core + AI) chưa xong, cắt Giai đoạn 5 (offline flashcard) khỏi scope báo cáo trước — không cắt phần AI (giá trị cốt lõi khoá luận).

### Mobile — trạng thái (cập nhật 07/11/2026)

**Đã port từ web trong đợt 07/11/2026 (repo `mobile`, push thẳng `main`):** khiếu nại giao dịch (+ mở lại "chưa nhận được tiền"), Trung tâm trợ giúp + gửi ticket (FAQ sao từ `fe/messages/vi.json`), tin nhắn học viên↔giảng viên (+ nút "Nhắn tin giảng viên", badge chưa đọc ở TopNav), checkout chọn khuyến mãi thủ công (`couponId`+`manualCoupon`, danh sách `GET /coupons/available`) + hủy PayOS (`POST /payments/cancel`) + sửa poll giỏ hàng (BE thêm `orderGroupRef` vào `PaymentDto.Res`), giỏ hàng chọn khóa + gói combo (bundle) khớp giá như web, mã giới thiệu `?ref=`, chứng chỉ (sao chép link/chia sẻ/LinkedIn), trang Buổi live (công khai/khóa của tôi; xem live mở bằng trình duyệt), trang Thông tin & Chính sách + xác thực chứng chỉ, bộ lọc khóa học đầy đủ + kệ trang chủ + khối lồng tiếng AI + khóa học liên quan, bài thi (nháp đáp án, giờ còn lại theo `startedAt` BE, xác nhận trước khi nộp, đánh dấu câu, chặn Back, hết cache lượt cũ), lesson player hỗ trợ `?seek=` (Gap 3 cũ — XONG).

**Còn lại:**

| # | Gap | Trạng thái |
|---|---|---|
| 1 | Live session native (LiveKit) | **HOÃN** — cần EAS dev client; tạm mở phòng live bằng trình duyệt |
| 2 | Giám sát thi (proctoring) — chụp frame định kỳ gửi Gemini Vision | **CẦN TEST THIẾT BỊ THẬT** (rủi ro `takePictureAsync` xen giữa `recordAsync`) |
| 3 | Push notification (`expo-notifications`) | Chưa làm — Expo Go bỏ remote push; cần dev build |
| 4 | Offline flashcard (`expo-sqlite`) | Đã có lớp DB/sync cơ bản; cần rà soát/test thiết bị thật |
| 5 | Test thiết bị thật (Android + iOS) toàn bộ luồng mới ở trên | Chưa — code chỉ kiểm bằng `tsc` + lint trên server |

**Gap 1 — Live session mobile, lý do hoãn (12/10/2026):** Web dùng LiveKit (WebRTC) thật
(`@livekit/components-react`, video track camera/screen-share, chat + phụ đề dịch qua data
channel của LiveKit, không qua STOMP) — port đúng kỹ thuật sang mobile cần
`@livekit/react-native` + `@livekit/react-native-webrtc`, **bắt buộc build EAS dev client, KHÔNG
chạy được bằng Expo Go thuần** (gói native WebRTC không có trong Expo Go). Vì luồng test chính
của dự án là Expo Go + tunnel (xem Epic Mobile Native App ở trên), quyết định hoãn việc này lại,
không đổi sang EAS dev client cho riêng tính năng này. BE không cần đổi gì (`GET
/api/v1/live-sessions/{id}/view` đã có sẵn, trả `{status, viewerToken, serverUrl}`); mobile đã có
sẵn `liveView.ts` (stub) và `LiveBanner.tsx` (cố tình chưa có `onPress`) chờ đúng việc này nếu
sau này đổi quyết định.

**Gap 2 — Giám sát thi, rủi ro chưa xác minh:** `ProctoringCameraView` chụp still-frame định kỳ
(random 30-60s) gửi `/proctor-frame` cho Gemini Vision xác thực — nguồn vi phạm hình ảnh được
SERVER xác thực, giống cơ chế web dùng. **RỦI RO CHƯA XÁC MINH:** `takePictureAsync()` gọi xen
giữa lúc `recordAsync()` đang quay video liên tục CÓ THỂ làm dừng video đang quay (hành vi đã biết
của `expo-camera` ở nhiều phiên bản, không xác nhận được trong môi trường code vì không có thiết
bị thật) — PHẢI test thật trên điện thoại trước khi báo tính năng này ổn định; nếu video bị cắt
ngắn, cân nhắc bỏ quay liên tục, chỉ dựa vào chuỗi frame định kỳ.

**Quy ước xác nhận:** không tự ý chọn thứ tự làm tiếp — luôn hỏi ưu tiên trước khi bắt đầu gap mới trong bảng trên.

**Acceptance Criteria:**
- App chạy được qua Expo Go trên cả Android và iPhone thật, gọi đúng API `be` hiện có (không đổi BE).
- Học viên hoàn thành được 1 luồng đầy đủ: đăng nhập → xem khoá học → học bài → làm quiz/flashcard → (từ tuần 7) làm bài thi có giám sát camera.

---

## TASK CÒN DANG DỞ / CẦN THEO DÕI TIẾP

- **Nộp bài thi bị đứng hình trên Safari iOS** (`fe/app/(student)/exam/[quizId]/page.tsx`) — học viên phải tự bấm Dynamic Island tắt ghi hình rồi back lại mới nộp được bài, không tự động như desktop. Đã thử vá 1 lần (06/10/2026, dừng camera ngay trước khi đợi recorder.stop()) nhưng gây regression mất luôn phát hiện khuôn mặt — đã revert về bản cũ (commit `8bc7bab` trên `fe`). **Quyết định: KHÔNG vá tiếp trên web** — sẽ tự hết khi học viên chuyển sang làm bài thi trên app native (dùng `expo-camera` thật, không qua giới hạn WebKit `getUserMedia`/`MediaRecorder`), xem epic Mobile Native App ở trên, Tuần 5-7.

### Web — đã chốt xong (07/11/2026), chỉ còn việc nhỏ

Đã làm: quota Discovery chat; cờ `DEV_SEED_ENABLED`; gỡ VNPay (`V152`); Login/Register/Google/exam đọc lỗi qua `ApiError`; ~600 literal màu → token; toast góc phải trên, vuông, nền đặc; phòng thi ẩn Header/Footer, xin camera/màn hình đúng 1 lần, dọn camera khi nộp/rời trang, nộp bài nhanh (hết tự khoá XP 50s, AI giám sát chạy nền), reset state theo từng lượt thi, nhận diện khuôn mặt không còn đóng băng; thanh toán PayOS: hủy → tự về trang thanh toán, thay đơn PENDING cũ khi thanh toán lại, học viên **tự chọn khuyến mãi** (`GET /coupons/available`, `couponId`+`manualCoupon`), `POST /payments/cancel`.

**Còn lại (cần quyết định/không gấp):**
- Khuyến mãi công khai: coupon có mã chưa có cờ `is_public` → danh sách ở trang thanh toán chỉ hiện coupon tự động (autoApply). Hướng A: thêm cột `is_public` + Flyway.
- Dark theme thật: hiện khoá `color-scheme: only light` (chặn trình duyệt tự làm tối). Làm dark theme đầy đủ là việc lớn.
- Literal màu cyan/blue/purple/slate/indigo (~166 chỗ) chưa đổi (không có token tương đương).
- Exam: HTTP 500 khi nộp bài chưa tự kết thúc phiên (chỉ 404/410) — cố ý, tránh mất bài do lỗi tạm.
- Trang `/cart` hiển thị giá theo coupon tự động; trang thanh toán dùng lựa chọn của học viên → có thể lệch nếu bỏ chọn.
- Điểm các lượt thi thử 96–100 đều 0.00: chưa điều tra xem có phải lỗi chấm điểm hay chỉ do chọn sai.
- ai-worker `app/config.py:58` (giá trị tạm 0.7); `MaterialGenerationService` 2 catch `JsonProcessingException` im lặng; ai-worker `discovery.py` tự mở `httpx.AsyncClient`.
- Logo LinguaLearn cho kênh PayOS đã tạo ở `fe/public/brand/` (chưa commit).
- `mvn clean verify` xanh 07/11/2026 (457 test).

**Việc mobile đi kèm các thay đổi web:** checkout mobile cần nối cùng API chọn coupon thủ công (`couponId` + `manualCoupon: true`, `GET /coupons/available`) và `POST /payments/cancel` khi hủy PayOS; hiện vẫn chạy hành vi cũ (áp coupon tự động).

Tip bảo mật data: check request nào thường xuyên hoặc truy xuất lượng dữ liệu lớn => AI cảnh báo admin
