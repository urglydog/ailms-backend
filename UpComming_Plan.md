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

### Mobile — Khảo sát chi tiết màn hình còn thiếu (07/10/2026, đối chiếu từng route với `fe`)

**Đã port xong, chạy được (verify qua web `expo start --web`, chưa test thiết bị thật):** đăng nhập (email/password + Google OAuth), đăng ký, quên mật khẩu, danh sách/chi tiết khoá học, khoá học của tôi, lesson player, làm bài thi (exam, chỉ OFFICIAL_EXAM không giám sát camera), lịch sử làm bài, gradebook, chứng chỉ, wishlist, thông báo, hồ sơ, checkout PayOS (1 khoá), Học liệu AI (Flashcard/Mindmap/Quiz).

**Bug đang biết, cần sửa trước khi coi Học liệu AI là xong:**
1. **Nhầm luồng Quiz** — Học liệu AI loại QUIZ có 2 kiểu `LECTURE_QUIZ` (ôn tập thường, không giới hạn lượt/không tính giờ) và `OFFICIAL_EXAM` (thi thật, có giờ/giám sát) — BE phân biệt rõ qua `quizType`, nhưng mobile đang nối CẢ HAI vào chung màn `exam/[quizId]` (màn thi chính thức). Cần: đọc `quizType` từ `materialsApi.getDetail`, nếu `LECTURE_QUIZ` thì render quiz ngay tại chỗ (không timer/không proctoring, port rút gọn từ `fe/components/materials/QuizViewer.tsx`), chỉ `OFFICIAL_EXAM` mới điều hướng sang `/exam/{quizId}`.
2. **Mindmap hiện quá nhỏ** — thiếu áp 3 patch CSS mà bản web làm trên SVG (`width/height=100%`, bỏ `max-width` mermaid tự thêm, `preserveAspectRatio="xMidYMid meet"`) + chưa bật pinch-zoom cho WebView (`minimumZoomScale`/`maximumZoomScale`) vì mobile không có chuột cuộn như web.
3. Thêm nút "←" quay lại thủ công ở các màn `Stack.Screen` (phòng trường hợp vào thẳng URL không qua điều hướng trong app — ưu tiên thấp, không phải bug chặn release).

**Chưa port — liệt kê theo mức ưu tiên đề xuất (CẦN CONFIRM thứ tự trước khi làm tiếp):**

| # | Tính năng | Vì sao quan trọng | Phụ thuộc BE |
|---|---|---|---|
| 1 | Hoàn thiện bug Quiz (LECTURE_QUIZ) ở trên | Đang có tính năng làm SAI, ưu tiên trên cả tính năng mới | `/api/v1/quizzes/**` (đã có) |
| 2 | AI Tutor chat (trong lesson player) | Giá trị cốt lõi khoá luận, đã có trong plan gốc Tuần 5-7 | `tutor.ts`: `/api/v1/courses/{id}/tutor/ask`, `/sessions` |
| 3 | AI Study Plan | Cùng nhóm AI differentiator, cũng Tuần 5-7 gốc | `/api/v1/student/courses/{id}/study-plan` (+generate/reschedule) |
| 4 | Giỏ hàng + checkout nhiều khoá (`cart`, `checkout/cart`) | Mở rộng ngoài đặc tả gốc trên web — mobile hiện chỉ mua được 1 khoá/lần | `cart.ts`, `payments.ts` (`createBatch` — BE đã có sẵn) |
| 5 | Lịch sử thanh toán (`payments`) | Học viên không xem lại được đã mua gì qua mobile | `payments.ts`: `GET /payments/mine` (đã dùng 1 phần cho checkout, cần màn list riêng) |
| 6 | Tài nguyên khoá học (`CourseResourcesTab`) | Tải file đính kèm bài giảng | `/api/v1/student/courses/{id}/resources` |
| 7 | Live session | Học trực tiếp — chưa rõ mobile có cần ngay không (camera/stream phức tạp) | `live.ts`: `/api/v1/live-sessions/**` |
| 8 | Ranking/leaderboard, banner thông báo hệ thống | Tính năng phụ, không chặn luồng học | `ranking.ts`, `systemAnnouncements.ts` |
| 9 | Q&A/nhắn tin khoá học | Cần khảo sát thêm — `communication.ts` lẫn cả endpoint GV, phải tách đúng phần học viên trước khi port | TBD |

**Quy ước xác nhận:** không tự ý chọn thứ tự làm tiếp nữa (đã có 2 lần đặt sai phạm vi trong buổi — nối "Học liệu AI" vào trang chưa mua, và nối LECTURE_QUIZ vào màn thi chính thức) — luôn hỏi ưu tiên trước khi bắt đầu mục mới trong bảng trên.

**Acceptance Criteria:**
- App chạy được qua Expo Go trên cả Android và iPhone thật, gọi đúng API `be` hiện có (không đổi BE).
- Học viên hoàn thành được 1 luồng đầy đủ: đăng nhập → xem khoá học → học bài → làm quiz/flashcard → (từ tuần 7) làm bài thi có giám sát camera.

---

## TASK CÒN DANG DỞ / CẦN THEO DÕI TIẾP

- **Nộp bài thi bị đứng hình trên Safari iOS** (`fe/app/(student)/exam/[quizId]/page.tsx`) — học viên phải tự bấm Dynamic Island tắt ghi hình rồi back lại mới nộp được bài, không tự động như desktop. Đã thử vá 1 lần (06/10/2026, dừng camera ngay trước khi đợi recorder.stop()) nhưng gây regression mất luôn phát hiện khuôn mặt — đã revert về bản cũ (commit `8bc7bab` trên `fe`). **Quyết định: KHÔNG vá tiếp trên web** — sẽ tự hết khi học viên chuyển sang làm bài thi trên app native (dùng `expo-camera` thật, không qua giới hạn WebKit `getUserMedia`/`MediaRecorder`), xem epic Mobile Native App ở trên, Tuần 5-7.

---

Tip bảo mật data: check request nào thường xuyên hoặc truy xuất lượng dữ liệu lớn => AI cảnh báo admin
