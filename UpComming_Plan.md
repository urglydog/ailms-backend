# Kế Hoạch Phát Triển Nâng Cao LMS (Premium Commercial Roadmap)

Tài liệu này định nghĩa chi tiết các Epic/User Story để nâng cấp hệ thống LMS lên tiêu chuẩn thương mại cao cấp. Các tính năng được chia theo Sprint dựa trên ma trận ưu tiên: **Hạ tầng chống chịu -> Dòng tiền -> Tính năng giữ chân/AI**.

Trước mỗi tính năng đều đi kèm với **Bối cảnh & Nỗi đau (Pain Point)** để đội ngũ kỹ thuật/vận hành luôn nắm rõ bài toán cốt lõi.

> **Lưu ý:** Các task đã hoàn thành (Redis Cache, Coupons, Bundles, Streak, AI Sentiment Filter, AI Study Plan, Knowledge Gaps, Instructor Dashboard) đã được dọn khỏi file này để giữ chỗ cho task mới — xem lại chi tiết triển khai/ngày hoàn thành trong lịch sử commit của từng repo (`be`/`fe`/`ai-worker`) hoặc trong `weekly_report.md` (tóm tắt theo tuần để demo).

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

### Mobile — Rà soát sâu lần 2 (07/10/2026, đối chiếu TOÀN BỘ component web, không chỉ file API)

**Bài học:** lần khảo sát đầu (bên dưới) chỉ đọc file `lib/api/*.ts`, bỏ sót các màn web gộp NHIỀU nguồn dữ liệu trong 1 trang (vd Materials Workspace thật ra có 3 nguồn: cá nhân + official + tài nguyên tĩnh, không phải 1). Từ giờ: port bất kỳ màn nào PHẢI đọc hết component/hook web thật trước khi code mobile, không suy luận từ tên file — xem [[feedback_mobile_port_survey_first]] trong memory.

**Lỗ hổng thật tìm thấy (rà lại 14 màn "đã xong") — TẤT CẢ đã fix xong, xem bảng "Chưa port" ở dưới (mục 8 là mục còn lại duy nhất):** Học liệu AI thiếu tài nguyên tĩnh, lesson player thiếu cấu trúc tab, course detail thiếu banner Live/Bundle, gradebook thiếu KnowledgeGapsWidget, hồ sơ thiếu toggle riêng tư/đổi mật khẩu — chi tiết triển khai xem lịch sử commit repo `mobile`.

### Mobile — Khảo sát chi tiết màn hình còn thiếu (07/10/2026, đối chiếu từng route với `fe`)

**Đã port xong, chạy được (verify qua web `expo start --web`, chưa test thiết bị thật):** đăng nhập (email/password + Google OAuth), đăng ký, quên mật khẩu, danh sách/chi tiết khoá học, khoá học của tôi, lesson player, làm bài thi (exam, chỉ OFFICIAL_EXAM không giám sát camera), lịch sử làm bài, gradebook, chứng chỉ, wishlist, thông báo, hồ sơ, checkout PayOS (1 khoá), Học liệu AI (Flashcard/Mindmap/Quiz).

**Bug đã biết — rà lại 08/10/2026, cả 3 đã xong:**
1. ✅ **Nhầm luồng Quiz** — đã fix xong 07/10 (code `material/[id].tsx` đã phân nhánh đúng theo `quizType`, xác nhận lại đúng khi rà soát 08/10).
2. ✅ **Mindmap hiện quá nhỏ** — đã fix xong (`mermaidHtml.ts` đã áp đủ 3 patch CSS + `min-width: 700px` cho phép cuộn ngang, xác nhận lại đúng khi rà soát 08/10).
3. ✅ **Thiếu nút "←" quay lại thủ công** — chỉ còn thiếu ở `exam/[quizId]/index.tsx`, đã thêm cho màn chặn "yêu cầu giám sát" (an toàn, chưa vào bài thi); CỐ Ý không thêm cho màn đang làm bài có tính giờ, tránh bấm nhầm mất lượt làm bài (repo `mobile` commit `aba3ed5`).

**Chưa port — liệt kê theo mức ưu tiên đề xuất (CẦN CONFIRM thứ tự trước khi làm tiếp):**

> Các mục đã hoàn thành (bug Quiz, tài nguyên tĩnh khoá học, cấu trúc tab lesson player, banner
> Live/Bundle ở course detail, KnowledgeGapsWidget, toggle riêng tư/hồ sơ công khai, giỏ hàng,
> lịch sử thanh toán, ranking/leaderboard, dubbing/transcript, giới hạn 1 phiên xem đồng thời)
> đã dọn khỏi bảng này — xem lịch sử commit repo `mobile` nếu cần chi tiết.

| # | Tính năng | Vì sao quan trọng | Phụ thuộc BE |
|---|---|---|---|
| 8 | Live session (màn riêng, không chỉ banner) — **HOÃN** (12/10/2026, xem lý do bên dưới) | Học trực tiếp | `live.ts`: `/api/v1/live-sessions/**` |

**Mục 8 — Live session mobile, lý do hoãn (12/10/2026):** Web dùng LiveKit (WebRTC) thật
(`@livekit/components-react`, video track camera/screen-share, chat + phụ đề dịch qua data
channel của LiveKit, không qua STOMP) — port đúng kỹ thuật sang mobile cần
`@livekit/react-native` + `@livekit/react-native-webrtc`, **bắt buộc build EAS dev client, KHÔNG
chạy được bằng Expo Go thuần** (gói native WebRTC không có trong Expo Go). Vì luồng test chính
của dự án là Expo Go + tunnel (xem Epic Mobile Native App ở trên), quyết định hoãn việc này lại,
không đổi sang EAS dev client cho riêng tính năng này. BE không cần đổi gì (`GET
/api/v1/live-sessions/{id}/view` đã có sẵn, trả `{status, viewerToken, serverUrl}`); mobile đã có
sẵn `liveView.ts` (stub) và `LiveBanner.tsx` (cố tình chưa có `onPress`) chờ đúng việc này nếu
sau này đổi quyết định.

**Quy ước xác nhận:** không tự ý chọn thứ tự làm tiếp nữa (đã có 2 lần đặt sai phạm vi trong buổi — nối "Học liệu AI" vào trang chưa mua, và nối LECTURE_QUIZ vào màn thi chính thức) — luôn hỏi ưu tiên trước khi bắt đầu mục mới trong bảng trên.

**Đã xong (12/10/2026, repo `mobile` commit `1273ea0`):** port trang Cộng đồng/bảng xếp hạng đầy
đủ (top 30, streak cá nhân, hạng của bạn, bấm 1 hạng → hồ sơ công khai mở sẵn tab Chứng chỉ) —
song song với tính năng tương đương bên web (`fe` commit `b1a6551`/`ef1ae0a`). Không port animation
stagger CSS vì mobile chưa dùng `react-native-reanimated` ở đâu khác. Fix "ẩn chatbot AI lúc thi"
bên web KHÔNG áp dụng cho mobile — mobile chưa có chatbot AI Discovery nào để sửa.

### Mobile — Rà soát toàn diện lần 3 (12/10/2026, theo yêu cầu đối chiếu từng domain: auth, học liệu, bảng điểm, hỏi đáp, giám sát thi, cộng đồng, hồ sơ/thiết bị — KHÔNG đụng domain video/stream/dubbing, thuộc phạm vi người khác)

**Kết quả: đa số domain đã đạt parity đầy đủ với web, chỉ có 2 gap thật + 1 gap cosmetic:**

- ✅ **Auth/tài khoản** (đăng ký+OTP, đăng nhập email/Google, quên mật khẩu, danh sách phiên đăng
  nhập + đăng xuất thiết bị khác) — parity đầy đủ, cùng endpoint BE. Lưu ý phụ: bản thân web
  **không có** cơ chế "ban"/giới hạn số thiết bị tự động — chỉ Redis lưu session để hiển thị +
  đăng xuất tất cả thiết bị khác thủ công (`AuthService.generateTokens`/`logoutAllDevices`,
  be/src/main/java/com/lms/auth/service/AuthService.java), không có auto-evict/giới hạn số lượng.
  Nếu sau này cần giới hạn thật, phải xây ở BE trước, không phải việc của mobile.
- ✅ **Học liệu Workspace (3 nguồn: cá nhân/official/tài nguyên tĩnh)**, **SRS flashcard** (mobile
  còn làm tốt hơn — có SM-2 local thật cho offline, `src/lib/db/flashcardDb.ts`, trong khi web chỉ
  hiện gợi ý UI, tính thật ở BE), **Hỏi đáp** (`QnaTab.tsx` dùng STOMP WebSocket thật, không phải
  stub), **Tài nguyên** (`ResourcesTab.tsx`), **Bảng điểm + KnowledgeGapsWidget** — parity đầy đủ.
- ⚠️ **Gradebook — gap cosmetic nhỏ:** thiếu phân trang lịch sử quiz + badge "quiz đã bị xoá" so
  với web (`CourseGradebookTab.tsx`). Không chặn luồng, ưu tiên thấp.
- ✅ **Quiz cá nhân — đã port soạn/sửa câu hỏi** (12/10/2026, repo `mobile` commit `5dfcc52`):
  `components/material/QuizPersonalEditor.tsx` (thêm/sửa/xoá câu hỏi, dùng `Modal` built-in RN).
  `material/[id].tsx` thêm tab "Làm bài"/"Soạn câu hỏi", CHỈ hiện khi `!material.isOfficial`.
  Field `isOfficial` được BE bổ sung vào `MaterialDetailRes` (commit `b3b9fd9` trên `be`, **CHƯA
  PUSH** — chờ `mvn clean verify`, môi trường code không có Maven để tự chạy) vì trước đây response
  tính `isOfficial` nội bộ nhưng không trả ra; BE vẫn tự chặn server-side nếu cố sửa học liệu
  Official (`AccessDeniedDomainException`, đã verify sẵn có) — field mới chỉ để FE ẩn/hiện đúng UI.
- ⚠️ **Giám sát thi — đã vá phần chụp frame, CẦN TEST THIẾT BỊ THẬT trước khi coi là xong**
  (12/10/2026, repo `mobile` commit `5dfcc52`): `ProctoringCameraView` giờ chụp still-frame định kỳ
  (random 30-60s) gửi `/proctor-frame` cho Gemini Vision xác thực — nguồn vi phạm hình ảnh được
  SERVER xác thực, giống cơ chế web dùng (trước đây mobile chỉ có `TAB_SWITCH` hoạt động thật,
  `NO_FACE`/`MULTIPLE_FACES`/`HEAD_TURNED`/`GAZE_AWAY` là code chết vì `expo-camera` managed
  workflow không hỗ trợ face detection on-device). **RỦI RO CHƯA XÁC MINH:** `takePictureAsync()`
  gọi xen giữa lúc `recordAsync()` đang quay video liên tục CÓ THỂ làm dừng video đang quay (hành
  vi đã biết của `expo-camera` ở nhiều phiên bản, không xác nhận được trong môi trường code vì
  không có thiết bị thật) — PHẢI test thật trên điện thoại trước khi báo tính năng này ổn định; nếu
  video bị cắt ngắn, cân nhắc bỏ quay liên tục, chỉ dựa vào chuỗi frame định kỳ. Fix kèm theo:
  `exam/[quizId]/index.tsx` trước đây bỏ qua response của `recordViolation` hoàn toàn khiến
  auto-submit khi vượt số lần vi phạm không bao giờ chạy dù BE đã đếm đúng — đã xử lý
  `shouldAutoSubmit` cho cả 2 nguồn vi phạm.

**Acceptance Criteria:**
- App chạy được qua Expo Go trên cả Android và iPhone thật, gọi đúng API `be` hiện có (không đổi BE).
- Học viên hoàn thành được 1 luồng đầy đủ: đăng nhập → xem khoá học → học bài → làm quiz/flashcard → (từ tuần 7) làm bài thi có giám sát camera.

---

## TASK CÒN DANG DỞ / CẦN THEO DÕI TIẾP

- **Nộp bài thi bị đứng hình trên Safari iOS** (`fe/app/(student)/exam/[quizId]/page.tsx`) — học viên phải tự bấm Dynamic Island tắt ghi hình rồi back lại mới nộp được bài, không tự động như desktop. Đã thử vá 1 lần (06/10/2026, dừng camera ngay trước khi đợi recorder.stop()) nhưng gây regression mất luôn phát hiện khuôn mặt — đã revert về bản cũ (commit `8bc7bab` trên `fe`). **Quyết định: KHÔNG vá tiếp trên web** — sẽ tự hết khi học viên chuyển sang làm bài thi trên app native (dùng `expo-camera` thật, không qua giới hạn WebKit `getUserMedia`/`MediaRecorder`), xem epic Mobile Native App ở trên, Tuần 5-7.

---

Tip bảo mật data: check request nào thường xuyên hoặc truy xuất lượng dữ liệu lớn => AI cảnh báo admin
