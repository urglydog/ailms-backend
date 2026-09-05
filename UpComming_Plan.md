# UPCOMING PLAN — KẾ HOẠCH CẢI TIẾN LMS

> **Quy tắc bắt buộc:**
> - Tuân thủ tuyệt đối Skill-set, Business Rule (BR) hiện tại của hệ thống.
> - Không ảnh hưởng chức năng đang hoạt động ổn định.
> - Không tự ý thay đổi nội dung file `.env` / biến môi trường.
> - Không tự ý commit, push bất kỳ thay đổi nào khi chưa được xác nhận.
> - Mỗi task phải đợi lệnh **CONFIRM** từ người dùng trước khi bắt đầu triển khai.

---

## TASK 1 — Phát âm Flashcard đúng ngôn ngữ hoặc loại bỏ hoàn toàn

**Vấn đề:**
Nút phát âm (Text-to-Speech) trong thẻ Flashcard phía học viên đang dùng cứng `lang='en-US'` nên khi thẻ có nội dung tiếng Việt hoặc tiếng Nhật (như ảnh ví dụ) phát âm sẽ sai hoàn toàn.

**Mức độ khả thi:** 🟢 Cao

**Đề xuất:** Ưu tiên cách A (sửa đúng ngôn ngữ) thay vì xóa bỏ — tính năng TTS giá trị với người học ngôn ngữ.

**Cách thực hiện:**
- **Phương án A (Khuyến nghị):** Đọc trường `language` từ metadata của `FlashcardDeck` (đã lưu trong entity) và truyền vào `SpeechSynthesisUtterance.lang` khi gọi Web Speech API. Không cần gọi thêm bất kỳ AI API key nào — dùng hoàn toàn Web API của trình duyệt.
  - Map: `vi` → `vi-VN`, `en` → `en-US`, `ja` → `ja-JP`, `ko` → `ko-KR`, `zh` → `zh-CN`, …
  - Fallback khi ngôn ngữ không xác định: ẩn nút phát âm thay vì phát sai.
- **Phương án B (Đơn giản, an toàn):** Ẩn hẳn nút phát âm ở phía học viên. Mindmap đã OK theo yêu cầu của bạn, nên chỉ cần tắt TTS cho Flashcard.

**Phạm vi thay đổi:**
- Frontend only: component Flashcard của học viên (`/app/(student)/materials/[id]/page.tsx` hoặc component con).
- Không đụng backend, không đụng API key.

**Trạng thái:** ✅ Hoàn thành

---

## TASK 2 — Ngôn ngữ phản hồi của AI Gia sư khi giải thích đáp án sai Quiz

**Vấn đề:**
AI Gia sư hiện đang giải thích đáp án sai bằng tiếng Việt mặc dù bộ Quiz có thể được tạo từ nội dung tiếng Nhật hoặc ngôn ngữ khác. Câu hỏi: nên fix cứng một ngôn ngữ hay theo ngôn ngữ của bộ Quiz?

**Mức độ khả thi:** 🟢 Cao

**Phân tích & Đề xuất:**

| Phương án | Ưu điểm | Nhược điểm |
|---|---|---|
| **A. Fix cứng tiếng Anh** | Nhất quán, không tốn thêm context token, Gemini giỏi tiếng Anh nhất | Học viên phải đọc tiếng Anh — có thể gây rào cản |
| **B. Theo ngôn ngữ của bộ Quiz** | Trải nghiệm tự nhiên nhất | Tốn ~100–300 token bổ sung/lần gọi vì phải thêm ngôn ngữ vào system prompt; Gemini đôi khi không nhất quán với ngôn ngữ ít phổ biến |
| **C. Theo ngôn ngữ lồng tiếng khóa học** | Đồng nhất với trải nghiệm học | Cần query thêm bảng audio_tracks |

**Khuyến nghị của tôi:** **Phương án B** — Truyền trường `language` từ entity `Quiz` / `MaterialGeneration` vào system prompt của AI Gia sư với chỉ dẫn: *"Respond exclusively in [language]."* Chi phí token tăng không đáng kể (< 5 token cho dòng lệnh ngôn ngữ), nhưng trải nghiệm tốt hơn nhiều. Đây không phải gọi AI mới mà chỉ điều chỉnh prompt trong lần gọi đã có sẵn.

**Phạm vi thay đổi:**
- Backend: `QuizService.explainWrongAnswer(...)` — bổ sung `language` vào prompt gửi Gemini.
- Không thay đổi schema DB, không thay đổi entity, không thay đổi endpoint.

**Trạng thái:** ✅ Hoàn thành

---

## TASK 3 — Sửa màn hình Tiến độ học tập: redirect sai + thiếu tracking + thiếu tab

**Vấn đề (theo mô tả):**
1. Nhấn vào thanh tiến độ → redirect sang trang chi tiết khóa học (đúng), nhưng các khóa học bên dưới hiển thị icon khóa (sai — học viên đã mua).
2. Không có tab hay nút nào để xem chi tiết tracking tiến độ (số bài hoàn thành, % theo chương...).
3. Chưa rõ tiến độ % tính dựa trên số liệu gì.

**Mức độ khả thi:** 🟡 Trung bình (cần làm rõ logic backend trước)

**Làm rõ logic tính tiến độ (BR-PROGRESS-01):**
- Backend có bảng `lesson_progresses` — mỗi row ghi nhận học viên đã xem >= 90% bài học đó (BR-PROGRESS-01: threshold 90%).
- `% tiến độ = số bài đã hoàn thành / tổng số bài của khóa * 100`.
- Hiện API `/api/v1/progress` trả về `completedLessons` và `totalLessons` — frontend cần dùng đúng 2 trường này.

**Cách thực hiện:**
1. **Fix redirect:** Khi nhấn vào thanh tiến độ → chuyển đến `/learn/[lessonId]` (bài học cuối cùng đang học) thay vì trang giới thiệu khóa học. Nếu chưa học bài nào thì mới redirect vào trang chi tiết khóa.
2. **Fix icon khóa:** Kiểm tra lại component `CourseCard` — khi render trong trang `/progress` phải biết học viên đã enroll → không hiện icon khóa. Truyền prop `isEnrolled={true}` hoặc dùng route segment khác.
3. **Bổ sung tab/section tracking:** Thêm accordion hoặc section "Chi tiết tiến độ" mở ra danh sách từng chương + số bài hoàn thành / tổng số bài trong chương đó.
4. **Hiển thị số liệu rõ ràng:** `X/Y bài hoàn thành · Z% · Bài gần nhất: [tên bài]`.

**Phạm vi thay đổi:** Frontend (`/app/(student)/progress/page.tsx`), không đụng backend.

**Trạng thái:** ⬜ Chưa tiến hành

---

## TASK 4 — Hệ thống SRS Flashcard + Thông báo ôn tập (Chuông Socket)

**Vấn đề:**
1. Học viên đánh dấu mức độ khó cho từng thẻ (Dễ / Trung bình / Khó) — nhưng chức năng này hiện chỉ lưu giá trị, chưa có hệ thống SRS nào lên lịch ôn tập dựa vào đó.
2. Icon chuông thông báo trên Header đang dùng dummy data (hardcode), chưa kết nối WebSocket thật.

**Mức độ khả thi:** 🟡 Trung bình (SRS cần thêm backend scheduler)

**Cách thực hiện — SRS (Spaced Repetition System):**
- Áp dụng thuật toán **SM-2** (đơn giản, phổ biến):
  - Dễ → interval nhân 2.5
  - Trung bình → interval giữ nguyên
  - Khó → reset về 1 ngày
- Lưu `next_review_at` vào bảng `flashcard_progresses` (thêm cột hoặc bảng mới nếu chưa có).
- Backend cần một **Scheduled Job** (Spring `@Scheduled`) chạy mỗi ngày, quét các thẻ đến hạn ôn và tạo Notification cho học viên.

**Cách thực hiện — Thông báo WebSocket:**
- Backend đã có STOMP/WebSocket cấu hình. Cần:
  1. Tạo endpoint STOMP `/topic/notifications/{userId}`.
  2. Gửi push notification qua STOMP khi scheduler phát hiện thẻ đến hạn.
  3. Frontend: thay dummy data trong `NotificationProvider` bằng STOMP subscription thật.

**Lưu ý BR:** Không thay đổi BR-CARD-01 hiện tại. Thêm bảng DB phải đi kèm Flyway migration mới.

**Trạng thái:** ⬜ Chưa tiến hành

---

## TASK 5 — Quyền CRUD học liệu cá nhân của học viên & Kéo thả Mindmap cho giảng viên

**Vấn đề & Đề xuất:**
Có 2 luồng cần xem xét riêng:

### 5A — Học viên CRUD học liệu cá nhân (Flashcard & Quiz)

**Mức độ khả thi:** 🟢 Cao (chỉ frontend + API đã có sẵn)

**Khuyến nghị:** **Nên làm** — mục tiêu học viên tự cá nhân hóa tài liệu mà không tốn AI API key.

**Giới hạn quyền học viên (stricter than instructor):**
| Loại | Cho phép | Không cho phép |
|---|---|---|
| Flashcard | Sửa nội dung card (mặt trước/sau), thêm ghi chú cá nhân, xóa card | Tạo deck mới bằng AI, đổi ngôn ngữ |
| Quiz | Xem lại đáp án, xem giải thích, xóa khỏi kho cá nhân | Sửa câu hỏi/đáp án (tránh gian lận), tạo quiz mới bằng AI |
| Mindmap | Xem, zoom, pan | Sửa code Mermaid (xem 5B bên dưới) |

**Phạm vi thay đổi:** Frontend `MaterialManager.tsx` (tab Personal) + gọi các endpoint PATCH/DELETE đã có sẵn trong backend.

### 5B — Kéo thả Mindmap cho giảng viên (Drag & Drop → UML sync)

**Mức độ khả thi:** 🟡 Trung bình-Khó (cần thay thư viện Mermaid)

**Đề xuất thư viện thay thế Mermaid:**
- **React Flow** (`reactflow`) — render node-edge, hỗ trợ drag & drop native, có thể export lại thành Mermaid text.
- Hoặc **GoJS** (thương mại, chất lượng cao hơn nhưng có phí).

**Cơ chế đồng bộ 2 chiều:**
1. Khi giảng viên kéo node → React Flow cập nhật state graph.
2. Một hàm `graphToMermaid(nodes, edges)` chuyển đổi state → chuỗi Mermaid text.
3. Textarea UML bên cạnh cập nhật realtime.
4. Ngược lại: khi sửa text UML → parse lại → cập nhật React Flow canvas.

**Học viên:** Giữ nguyên chỉ xem (Mermaid render only). Không cho sửa Mermaid code.

**Trạng thái:** ⬜ Chưa tiến hành

---

## TASK 6 — Cải tiến giao diện Kho Học Liệu cá nhân học viên (sort, filter, đổi tên)

**Vấn đề:**
- Học liệu hiển thị dạng list thẳng theo thứ tự tạo, không phân nhóm theo type.
- Không có filter.
- Học liệu mặc định tên "Học liệu không tên" — xấu và không có nghĩa.

**Mức độ khả thi:** 🟢 Cao

**Cách thực hiện:**
1. **Group by type:** Hiển thị 3 section riêng: 🧠 Mindmap · 🃏 Flashcard · 📝 Quiz — mỗi section có số lượng và có thể thu gọn.
2. **Filter bar:** Thêm pill/chip filter: `Tất cả | Mindmap | Flashcard | Quiz | Official | Cá nhân`.
3. **Sort:** Dropdown `Mới nhất | Cũ nhất | Tên A–Z`.
4. **Inline rename:** Click vào tên học liệu → input inline edit → Enter để lưu. Gọi API PATCH `/api/v1/materials/{id}` (cập nhật trường `title`). Tên mặc định khi tạo nên được đặt dựa trên tên khóa học + loại học liệu + số thứ tự (ví dụ: *"Unity - Mindmap #1"*) thay vì "Học liệu không tên".

**Phạm vi thay đổi:**
- Frontend: `MaterialManager.tsx`.
- Backend: kiểm tra xem endpoint PATCH title đã có chưa; nếu chưa thêm action nhỏ vào `MaterialGenerationController`.
- Đổi tên mặc định: sửa logic tạo tên trong `MaterialGenerationService`.

**Trạng thái:** ⬜ Chưa tiến hành

---

## TASK 7 — Gom 3 nút sinh học liệu thành 1 nút + popup thông minh

**Vấn đề:**
Có 3 nút riêng biệt (+ Sinh Mindmap, + Sinh Flashcard, + Sinh Quiz Thi Cử) và trong popup lại còn có thẻ `<select>` để chọn lại loại — dư thừa, UI rối.

**Mức độ khả thi:** 🟢 Cao (pure frontend refactor)

**Cách thực hiện:**
1. Thay 3 nút bằng **1 nút** duy nhất: `🤖 Sinh Học Liệu AI`.
2. Popup mở ra với:
   - **Bước 1:** Chọn loại (3 card lớn có icon: Mindmap / Flashcard / Quiz) — thay thế select dropdown hiện tại.
   - **Bước 2:** Sau khi chọn loại, popup cập nhật nội dung (options tương ứng với loại đó) — ngôn ngữ, phạm vi, cấu hình quiz…
3. Có nút **Quay lại** để đổi loại trong cùng popup.

**Phạm vi thay đổi:** Frontend only — component popup sinh học liệu trong màn hình instructor materials.

**Trạng thái:** ⬜ Chưa tiến hành

---

## TASK 8 — Điều hướng (Navigation) trong Kho Học Liệu & Đề thi + fix nút 404

**Vấn đề:**
1. Đang xem chi tiết một học liệu, muốn quay lại list phải nhấn nút "← Quay lại danh sách" nhỏ, không trực quan.
2. Click vào chữ "Kho Học Liệu & Đề Thi" trên sidebar không về lại màn hình danh sách.
3. Nút "Quản lý Học liệu & Quiz" trên màn hình Bảng điểm lớp học → 404 Not Found.
4. Mỗi màn hình có nút thoát nhỏ li ti riêng lẻ gây phân tán.

**Mức độ khả thi:** 🟢 Cao

**Cách thực hiện:**
1. **Breadcrumb navigation:** Thêm breadcrumb chuẩn ở đầu mỗi màn hình con: `Kho Học Liệu & Đề Thi > [Tên học liệu]` — click vào phần nào sẽ về đúng màn hình đó. Loại bỏ các nút "Quay lại danh sách" nhỏ li ti.
2. **Sidebar link:** Đảm bảo click "Kho Học Liệu & Đề Thi" trên sidebar luôn reset state về màn hình danh sách (không giữ lại state chi tiết cũ).
3. **Fix nút 404 Bảng điểm:** Nút "Quản lý Học liệu & Quiz" đang link sai route. Sửa href thành `/instructor/materials?courseId={courseId}` thay vì route không tồn tại hiện tại.
4. **Tab ngang (Tổng quan / Khóa học / …):** Mỗi tab khi click phải reset về trạng thái gốc của chức năng đó — không giữ state của màn hình con đang mở.

**Phạm vi thay đổi:** Frontend — sidebar navigation, layout instructor, GradebookPage.

**Trạng thái:** 🔄 Đang thực hiện

---

## TASK 9 — Fix Quiz: giới hạn lần làm bài + đồng hồ đếm ngược + màn hình kết quả

**Vấn đề (chi tiết từ mô tả):**
1. **Max attempts không được enforce:** Giảng viên set 1 lần nhưng học viên đã làm 2 lần vẫn cho làm tiếp.
2. **Không có đồng hồ đếm ngược:** Set 3 phút nhưng màn hình làm bài không hiển thị timer.
3. **Toast "nộp bài" không phù hợp:** Hiện toast xanh lá "Đã nộp bài - bạn đạt 0 điểm" — không nên dùng toast cho thông tin quan trọng này.
4. **Màn hình kết quả quá sơ sài:** Chỉ hiện `Hoàn thành bài thi! 0/10 Điểm` và số câu đúng. Cần thêm chi tiết.
5. **Nút "Quay lại Tiến độ học tập"** không có tác dụng rõ ràng.

**Mức độ khả thi:**
- Fix max attempts: 🟢 Cao — lỗi logic backend.
- Đồng hồ đếm ngược: 🟢 Cao — thuần frontend.
- Màn hình kết quả: 🟢 Cao — thuần frontend.

**Cách thực hiện:**

### 9.1 Fix max attempts (Backend)
- Kiểm tra lại `QuizService.startAttempt()`: truy vấn `quizAttemptRepository.countByUser_EmailAndQuiz_Id(...)` — đảm bảo đang đếm đúng theo `quizId` (không phải courseId cũ).
- Thêm log để xác nhận `attemptCount >= maxAttempts` được check trước khi tạo attempt mới.

### 9.2 Đồng hồ đếm ngược (Frontend)
- Khi `startAttempt` trả về `durationMinutes`, bắt đầu countdown `useEffect` + `setInterval`.
- Hiển thị timer cố định ở góc trên bên phải màn hình làm bài: `⏱ 02:47`.
- Khi hết giờ → tự động gọi `submitQuiz` và chuyển sang màn hình kết quả.

### 9.3 Màn hình kết quả (Frontend)
- **Xóa toast** "Đã nộp bài" — thông tin này phải hiển thị trực tiếp trên màn hình kết quả, không dùng toast.
- **Màn hình kết quả nâng cấp:**
  ```
  ✅ Đã nộp bài thành công
  Điểm của bạn: 7/10
  ──────────────────────
  ✅ Số câu đúng:  7/10
  ❌ Số câu sai:   3/10
  ⏱ Thời gian làm bài: 2 phút 13 giây
  📅 Thời gian nộp: 05/09/2026 16:19
  ──────────────────────
  [Xem lại đáp án] [Về trang học]
  ```
- Nút "Quay lại Tiến độ học tập" → đổi thành "Về trang học" redirect đến `/learn/[lastLessonId]` hoặc `/my-courses` nếu không có lastLesson.

**Phạm vi thay đổi:**
- Backend: `QuizService.startAttempt` (check max attempts).
- Frontend: `exam/[quizId]/page.tsx` (timer + result screen + toast removal).

**Trạng thái:** 🔄 Đang thực hiện

---

## Bảng Tóm Tắt & Thứ Tự Ưu Tiên

| # | Task | Khả thi | Phạm vi | Ưu tiên | Trạng thái |
|---|---|---|---|---|---|
| 1 | TTS Flashcard đúng ngôn ngữ | 🟢 Cao | FE only | 🔴 Cao | ✅ Hoàn thành |
| 2 | AI Gia sư trả lời theo ngôn ngữ Quiz | 🟢 Cao | BE (prompt) | 🔴 Cao | ✅ Hoàn thành |
| 3 | Tiến độ học tập: fix redirect + tracking | 🟡 TB | FE | 🟠 TB | ⬜ Chưa |
| 4 | SRS Flashcard + WebSocket notification | 🟡 TB | BE + FE | 🟡 Dài hạn | ⬜ Chưa |
| 5A | Học viên CRUD học liệu cá nhân | 🟢 Cao | FE | 🟠 TB | ⬜ Chưa |
| 5B | Kéo thả Mindmap giảng viên (React Flow) | 🟡 Khó | FE (lib mới) | 🟡 Dài hạn | ⬜ Chưa |
| 6 | Sort/filter/rename kho học liệu | 🟢 Cao | FE (+BE nhỏ) | 🟠 TB | ⬜ Chưa |
| 7 | Gom 3 nút sinh học liệu thành 1 | 🟢 Cao | FE only | 🟠 TB | ⬜ Chưa |
| 8 | Navigation breadcrumb + fix 404 gradebook | 🟢 Cao | FE | 🔴 Cao | ✅ Hoàn thành |
| 9 | Fix Quiz: attempts + timer + result screen | 🟢 Cao | FE + BE | 🔴 Cao | ✅ Hoàn thành |

---

*Cập nhật lần cuối: 05/09/2026 — Chờ CONFIRM từng task trước khi triển khai.*
