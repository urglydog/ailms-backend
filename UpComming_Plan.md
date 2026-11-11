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
| 3 | Push notification (`expo-notifications`) | **HOÃN (09/11/2026)** — Expo Go không nhận remote push (xác minh docs Expo) + chỉ có iPhone (dev build cần Apple Developer $99) + BE chưa có bảng/API lưu push token. Tạm dùng thông báo trong app tự làm mới 30s |
| 4 | Offline flashcard (`expo-sqlite`) | Đã rà soát + vá 09/11/2026 (mở được khi mất mạng nhờ cache học liệu, không ghi đè tiến độ chưa đồng bộ, đồng bộ lúc mở app/foreground). **Còn cần test thiết bị thật** (bật chế độ máy bay giữa chừng) |
| 5 | Test thiết bị thật (Android + iOS) toàn bộ luồng mới ở trên | Chưa — code chỉ kiểm bằng `tsc` + lint trên server |

**Đợt đối chiếu web ↔ mobile (09/11/2026):** ĐÃ làm — phụ đề phủ lên video + cài đặt phụ đề (cỡ/màu/nền/vị trí/viền; không có kéo-thả vị trí như web, không hiện ở fullscreen native), Transcript tô sáng theo thời gian thật, trang "Khoá học của tôi" (thống kê, Tiếp tục học, tìm kiếm/lọc/sắp xếp, lưới/danh sách, gợi ý), danh sách khoá học lưới/danh sách + chip bộ lọc, hồ sơ công khai (tab Chứng chỉ mặc định, thống kê, chia sẻ, lọc chứng chỉ). CHƯA làm — đa ngôn ngữ giao diện (vi/en/ja/zh, việc lớn: phải rà toàn bộ chuỗi chữ mobile); phụ đề kéo-thả vị trí; xem live trong app (Gap 1).

**Đợt đối chiếu web ↔ mobile lần 2 (09/11/2026, từng điều khiển):** ĐÃ làm thêm — điều khiển player (lùi/tua 5s, tốc độ phát, nút Gốc/Dịch, tự chuyển bài), công tắc "Nghe bản gốc", nút "Lồng tiếng", toàn màn hình tự vẽ giữ phụ đề, danh sách khoá học cuộn tải thêm + đếm kết quả + trạng thái trống, "Khám phá theo chủ đề", yêu thích có tìm kiếm/thêm giỏ/đăng ký, hiện/ẩn mật khẩu, xác nhận đăng xuất. CHƯA làm (đã khảo sát, ưu tiên thấp/khó): chất lượng video (web chỉ có ở nguồn YouTube), phím tắt (không áp dụng mobile), kéo-thả vị trí phụ đề, biểu tượng "có tài liệu đính kèm" ở danh sách bài (BE chưa trả field), kệ khoá học theo từng danh mục ở trang chủ, đa ngôn ngữ giao diện.

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

### Epic: Hoàn tiền – Chi trả giảng viên – Quảng bá – Chia sẻ khóa học (bắt đầu 11/11/2026)

**Bối cảnh & Nỗi đau:** mọi tiền thu về 1 tài khoản PayOS của nền tảng nhưng chưa có đường trả cho giảng viên; hoàn tiền chỉ là cờ trong `payment_disputes` (không đụng `Payment`, không thu hồi ghi danh, giảng viên không biết, doanh thu bị thổi phồng, có thể hoàn 2 lần); chia sẻ khóa học mới có `?ref=` thô (không SEO, không tracking, không chặn tự mua); chưa có quảng bá nào để biện minh tỷ lệ organic 63% của nền tảng.

**Chi tiết + bảng theo dõi từng hạng mục:** `be/docs/PAYMENT_PAYOUT_EPIC.md` (file này chỉ giữ tóm tắt; sửa trạng thái ở đó thì cập nhật dòng tương ứng ở đây). Báo cáo rà soát gốc: `be/PAYMENT_REFUND_PAYOUT_REVIEW_20261111.md`.

**Quyết định đã chốt (11/11/2026):** nền tảng thu hết → chi trả sau; tỷ lệ 37/63 + 97/3 giữ mặc định nhưng thành cấu hình admin (đổi chỉ áp dụng đơn mới); hạn hoàn tiền 7 ngày (tự gia hạn qua T7/CN); giữ tiền = hạn hoàn + 3 ngày; ngưỡng rút 100.000đ; chi trả đợt ngày 5 & 20, thủ công đợt đầu; rút phải gửi yêu cầu + xác nhận email; hoàn tiền thì thu hồi ghi danh (mềm) và loại khỏi doanh thu; giảng viên phản hồi (48h), admin quyết định cuối; giảng viên bị trừ đúng phần đã nhận (3 chế độ trách nhiệm, xem file chi tiết).

**Quy trình phát triển (bắt buộc):** backend bind-mount thư mục thật + devtools + `restart: unless-stopped` → code dở KHÔNG để trong `/var/lms/be`. Code ở nhánh `feat/payout-epic` (worktree riêng), merge vào `feat/additional-features` khi giai đoạn xong + `mvn clean verify` xanh + chủ dự án đã sao lưu DB. FE không push trước khi BE giai đoạn tương ứng đã deploy.

| Giai đoạn | Nội dung | Trạng thái |
|---|---|---|
| 0 | Rà soát, duyệt kế hoạch, ghi tài liệu | ✅ XONG 11/11/2026 |
| A | Cấu hình tỷ lệ, `platform_rate`, sổ cái, audit, chặn giảng viên tự mua | ✅ XONG + ĐÃ DEPLOY BE 11/11/2026 (538 test xanh; V154–V158 chạy thành công trên DB thật, dữ liệu cũ nguyên vẹn, sổ cái khớp 1.514/1.514 đơn). FE đã push, xem trên Vercel. 🔧 còn: trang legal ghi tay 37/97, đơn demo thiếu sổ cái, test FE payments chưa chạy được (thiếu gói `motion`) |
| B | Hoàn tiền (REFUNDED, thu hồi ghi danh, RefundService, DisputeService v2, giảng viên phản hồi, UI, pháp lý) | ✅ code + test xong 11/11/2026 (BE, FE, pháp lý, mobile). ⛔ chưa deploy — chờ duyệt; B11 (3 khiếu nại cũ) cần admin xử lý tay |
| C | Tài khoản nhận tiền, yêu cầu rút, trang giảng viên/admin, email, đối soát | 🔄 ĐANG LÀM — BE xong + verify xanh 11/11/2026 (649 test); FE (C7, C8) xong ở local (chưa push). C2 ⛔ chờ chủ dự án cấu hình khóa mã hóa; C9 tự động PayOS 🅿️ HOÃN |
| D | Chia sẻ khóa học (SEO/OG, hộp chia sẻ, thống kê theo đơn mua) | ✅ BE + FE XONG 11/11/2026 (FE commit local chưa push). D5 QR ⛔ chờ chọn thư viện; D6 đếm lượt xem, D7 link ngắn 🅿️ HOÃN; D8 mobile ⏳ |
| E | Quảng bá nền tảng (đăng ký/duyệt, kệ nổi bật, thống kê ước tính) | ✅ BE + FE XONG 11/11/2026 (665 test xanh). Boost tìm kiếm, quy kết theo `promotion_id`, đếm click, chiến dịch email 🅿️ HOÃN |
| F | Mobile, tài liệu `claude/*.md`, kiểm thử đầu-cuối | ✅ XONG phần tự động 11/11/2026. ⛔ Deploy B–E chờ chủ dự án duyệt (BE nhánh `feat/payout-epic`, FE commit local) |

**Việc chờ chủ dự án:** sao lưu DB trước mỗi deploy có migration · biến môi trường khóa mã hóa số tài khoản · chọn thư viện QR · kiểm tra PayOS có chi hộ · nhập ngày lễ · quyết định hạ tầng (maxmemory Redis, bỏ `--reload` ở ai-api, giới hạn bộ nhớ container).

**Quy ước API công khai:** được phép nhưng phải đủ 9 lớp bảo vệ (danh sách trắng đầu vào, không ghép chuỗi vào SQL/Redis, phản hồi cố định 204, giới hạn bộ nhớ, rate limit từ chối-khi-lỗi, công tắc tắt khẩn cấp, handler nhỏ, test âm tính, rà Jackson/Tomcat) — xem file chi tiết.

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

Tip bảo mật data: check request nào thường xuyên hoặc truy xuất lượng dữ liệu lớn => AI cảnh báo admin
