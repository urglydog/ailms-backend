# CHIA VIỆC THEO TÍNH NĂNG — AI-Powered LMS

Bổ sung cho `doc/DEVELOPMENT_PLAN.md`. Tài liệu đó chia theo **tầng** (1 người BE, 1 người FE)
cho mỗi giai đoạn — cách này khiến người làm FE phải chờ và đọc code người làm BE mới ráp được.
File này chia lại theo **tính năng dọc (vertical feature)**: mỗi tính năng có 1 người ôm trọn từ
entity/API (BE) đến màn hình (FE), tự quyết định hợp đồng dữ liệu của chính mình.

**Từ nay, mục "Chia việc" trong `DEVELOPMENT_PLAN.md` đã lỗi thời — dùng file này thay thế.**

---

## Nguyên tắc chia

1. **Một tính năng = một người, trọn vẹn BE + FE.** Không còn khái niệm "người BE" / "người FE" cố định.
2. **Không 2 người cùng sửa 1 file.** Khi 2 tính năng trong cùng giai đoạn chạm chung 1 entity (ví dụ
   cả hai đều đụng `Course`), tách riêng **controller** theo mục đích: một cái cho ghi (CRUD nội bộ của
   chủ sở hữu), một cái cho đọc công khai (`*PublicController` hoặc endpoint GET riêng). Việc này được
   ghi rõ trong mục "Ranh giới file" của từng tính năng bên dưới.
3. **Hợp đồng trước, code sau.** Nếu 2 tính năng phụ thuộc nhau (ví dụ tính năng B cần đọc field mà
   tính năng A ghi), chốt tên field + kiểu dữ liệu trước khi cả hai bắt đầu code song song.
4. **Tái sử dụng khung Giai đoạn 0.** Entity, repository, và nhiều component FE (mock) đã có sẵn từ
   Giai đoạn 0 — việc của từng tính năng là viết Service/Controller/DTO thật ở BE và nối API thật
   thay cho mock ở FE, không phải dựng lại từ đầu.
5. **Trước khi code:** mở `lms-usecase-map` lấy đúng UC/entity/BR, rồi đọc skill chuyên sâu được nêu.
   **Sau khi code:** chạy `lms-verify`, đối chiếu "Hoàn thành khi" của tính năng.

---

## Giai đoạn 0 & 1 — đã xong, không chia lại

- **Giai đoạn 0** (khung sườn): xong, xem `project/be/RUNNING.md`.
- **Giai đoạn 1** (UC01–08, 40, 41): xong — `auth` module BE đầy đủ (JWT, OTP, RBAC), FE có
  đăng ký/đăng nhập/profile/admin-users/instructor-dashboard-stub.

---

## Giai đoạn 2 — Khóa học & Kiểm duyệt

**UC:** 09, 10, 23, 31–33, 36, 42–44 · **Entity có sẵn từ GĐ0:** `Category`, `Course`, `Chapter`,
`Lesson`, `CourseReview` · **BR:** BR-COURSE-01…05, BR-ROLE-01, BR-ROLE-03

> Đã kiểm tra git: `CourseController` hiện tại chỉ là stub tạm (docstring tự ghi "TẠM THỜI... dummy
> data"), chưa có `CourseService`/DTO thật. Cả 2 tính năng dưới đây bắt đầu từ số 0.

### F2.1 — Tạo & Kiểm duyệt khóa học (vòng đời trọn vẹn)

Gộp phía Giảng viên và phía Admin vào **một người** vì cả hai cùng thao túng máy trạng thái
`Course.status` (`DRAFT→PENDING→PUBLISHED/REJECTED→ARCHIVED`) — tách hai phía cho hai người dễ lệch
điều kiện chuyển trạng thái.

- **UC:** 31 (CRUD khóa học), 32 (CRUD chương), 33 (CRUD bài học + đánh dấu Preview), 36 (gửi duyệt),
  42 (Admin duyệt/từ chối), 43 (Admin CRUD danh mục)
- **BE — file mới:** `catalog.controller.CourseController` (ghi: create/update/submit/archive),
  `ChapterController`, `LessonController` (CRUD metadata, chưa xử lý video — đó là GĐ4),
  `CategoryController` (chỉ Admin), `CourseService`/`ChapterService`/`LessonService`, DTO tương ứng.
- **FE:** `app/instructor/courses/` — danh sách khóa học của Giảng viên, editor (tiêu đề/mô tả/
  thumbnail/danh mục/giá), quản lý chương-bài (kéo-thả `displayOrder`), nút "Gửi duyệt" hiện
  checklist điều kiện chưa đạt; `app/admin/courses/` — hàng đợi duyệt, dialog từ chối (bắt buộc lý do);
  `app/admin/categories/`.
- **BR bắt buộc đúng:** `canSubmitForReview()` cần đủ tiêu đề+mô tả+thumbnail+≥1 chương+≥3 bài
  `READY` (BR-COURSE-01) · từ chối cần lý do ≥20 ký tự (BR-COURSE-04) · tối đa 5 lần gửi lại
  (BR-COURSE-04) · xóa khóa có học viên = archive mềm, không `DELETE` (BR-COURSE-03) · không xóa
  Category còn khóa tham chiếu (BR-COURSE-05) · ownership Giảng viên kiểm ở service, không chỉ
  `@PreAuthorize` (BR-ROLE-01) · tối đa 2 bài Preview/khóa (BR-ENROLL-02, chỉ đặt cờ ở đây, guard
  truy cập thật làm ở GĐ3.
- **Ranh giới file:** endpoint GET công khai (danh sách/chi tiết khóa học cho Guest/Student) **không**
  nằm trong `CourseController` này — thuộc F2.2. `CourseController` ở đây chỉ có endpoint ghi + GET
  riêng cho Giảng viên xem khóa của chính mình.
- **Hoàn thành khi:** Giảng viên tạo đủ điều kiện → gửi duyệt → Admin duyệt → khóa chuyển
  `PUBLISHED`; Admin từ chối thiếu lý do bị chặn; gửi duyệt lần thứ 6 bị chặn; xóa khóa có học viên
  ra `ARCHIVED` chứ không mất dữ liệu.

### F2.2 — Khám phá công khai & Đánh giá

- **UC:** 09 (tìm/lọc), 10 (chi tiết), 23 (đánh giá), 44 (Admin ẩn đánh giá vi phạm)
- **BE — file mới:** `catalog.controller.CoursePublicController` (GET tìm kiếm có phân trang + lọc
  category/level/price/keyword, GET chi tiết theo slug — **route riêng, không đụng
  `CourseController` của F2.1**), `enrollment.controller.CourseReviewController`
  (`CourseReviewService`, tạo đánh giá + Admin ẩn).
- **FE:** thay `lib/mock/courses.ts` bằng `lib/api/courses.ts` gọi API thật ở `app/(public)/courses`
  và `app/(public)/courses/[slug]` (2 trang này đã dựng UI từ GĐ0 — `CourseCard.tsx`,
  `CourseFilters.tsx`, `ChapterAccordion.tsx` có sẵn, chỉ đổi nguồn dữ liệu); thêm form đánh giá +
  danh sách review trên trang chi tiết; `app/admin/reviews/` để ẩn review vi phạm.
- **BR bắt buộc đúng:** chỉ khóa `PUBLISHED` hiện công khai (BR-ROLE-03) · mỗi học viên đánh giá 1
  khóa 1 lần (UNIQUE `user_id,course_id`, đã có ở entity từ GĐ0).
- **Phụ thuộc F2.1:** chỉ đọc `Course.status`, không ghi. Có thể bắt đầu song song ngay từ đầu giai
  đoạn, dùng dữ liệu Flyway-seed hoặc tự tạo khóa test qua Postman gọi thẳng repository nếu F2.1
  chưa xong API tạo khóa.
- **Hoàn thành khi:** Guest tìm/lọc/xem chi tiết được khóa `PUBLISHED`; khóa `DRAFT`/`PENDING` không
  hiện ở danh sách công khai; học viên đánh giá xong không đánh giá lại được lần 2; Admin ẩn review
  thì review biến mất khỏi trang chi tiết.

**2 người song song ngay từ ngày 1** — không ai chờ ai vì F2.2 chỉ đọc, không ghi vào state của F2.1.

---

## Giai đoạn 3 — Ghi danh & Thanh toán

**UC:** 11–15, 48 · **Entity:** `Enrollment`, `Payment` · **BR:** BR-ENROLL-01…03, BR-PAY-01…06

### F3.1 — Học thử & Ghi danh miễn phí + Guard truy cập nội dung

- **UC:** 11 (Preview), 12 (ghi danh miễn phí)
- **BE:** `enrollment.controller.EnrollmentController` (POST ghi danh miễn phí),
  `EnrollmentService`, và quan trọng nhất: **middleware/annotation kiểm sở hữu** dùng chung cho mọi
  endpoint nội dung khóa học (`@RequireEnrollment` hoặc filter tương tự) — tính năng này **là hạ tầng
  dùng chung cho toàn bộ hệ thống về sau** (GĐ5–8 đều cần).
- **FE:** guard ở `app/(student)/learn/[lessonId]` — chặn AI Tutor/Quiz/Flashcards nếu chỉ là
  Preview; nút "Đăng ký học ngay" cho khóa miễn phí ở trang chi tiết (đã có UI ở F2.2, nối API thật).
- **BR bắt buộc đúng:** ghi danh chỉ với khóa `PUBLISHED` (BR-ENROLL-01) · UNIQUE
  `(user_id, course_id)` · **không có endpoint hủy ghi danh** · Preview xem được video nhưng chặn
  AI Tutor/Quiz/Flashcards, tối đa 2 bài/khóa (BR-ENROLL-02) · khóa `ARCHIVED` vẫn cho học viên đã sở
  hữu truy cập đầy đủ (BR-ENROLL-03).
- **Hoàn thành khi:** ghi danh khóa miễn phí xong truy cập được toàn bộ nội dung; xem bài Preview khi
  chưa sở hữu bị chặn Tutor/Quiz; khóa chuyển Archived không mất quyền truy cập của học viên cũ.

### F3.2 — Thanh toán & Đối soát

- **UC:** 13 (khởi tạo đơn), 14 (thanh toán qua cổng), 15 (lịch sử), 48 (đối soát Admin)
- **BE:** `payment.controller.PaymentController` (tạo đơn, callback IPN 3 cổng, lịch sử),
  `PaymentService`, job `@Scheduled` chuyển `PENDING`→`EXPIRED` sau 15 phút.
- **FE:** luồng checkout ở trang chi tiết khóa (nút "Mua khóa học" đã có UI từ F2.2, nối thật),
  `app/(student)/payments/` lịch sử giao dịch, `app/admin/payments/` đối soát.
- **BR bắt buộc đúng — dễ sai nhất cả dự án:** `amount` lấy từ **giá server**, tuyệt đối không nhận
  từ client (BR-PAY-02) · callback xác thực HMAC/checksum trước khi xử lý · **idempotent theo
  `txn_ref`** — callback lặp lại không tạo `Enrollment` thứ 2 (BR-PAY-03) · `platformFee`/
  `instructorEarning` = 30/70%, chốt cứng lúc `PAID` (BR-PAY-05) · không có refund (BR-PAY-04).
- **Phụ thuộc F3.1:** dùng chung `EnrollmentService.createFromPayment(...)` — chốt chữ ký hàm này
  trước khi F3.2 bắt đầu code, vì thanh toán thành công phải gọi vào đúng hàm ghi danh của F3.1
  (không viết logic ghi danh riêng ở đây, tránh trùng 2 đường tạo `Enrollment`).
- **Hoàn thành khi:** mua khóa Sandbox → `PAID` → có `Enrollment` + thông báo; gửi callback giả lập
  2 lần chỉ tạo 1 `Enrollment`; sửa giá ở client không đổi được số tiền thật; đơn bỏ dở 15 phút tự
  `EXPIRED`.

**Thứ tự:** F3.1 làm trước (dù chỉ vài giờ) vì F3.2 gọi thẳng vào `EnrollmentService` của nó — chốt
chữ ký hàm ngay khi F3.1 bắt đầu, rồi 2 người có thể chạy song song phần còn lại.

---

## Giai đoạn 4 — Nạp video & Lưu trữ

**UC:** 34, 35 · **Entity:** `LessonDocument`; hoàn thiện `Lesson.videoSource/videoUrl/…`
**BR:** BR-CHUNK-01, BR-UPLOAD-01, BR-DUB-11

Phase nhỏ, tách theo **loại file** vì validate hoàn toàn khác nhau (video cần `ffprobe`, tài liệu cần
kiểm magic number) — không có phụ thuộc chéo, chạy song song tự nhiên.

### F4.1 — Nạp video bài giảng

- **UC:** 34 · **BE:** endpoint upload multipart/presigned trong `LessonController` (đã có từ F2.1,
  thêm method), tích hợp B2 client, `ffprobe` lấy `durationSec`, kiểm nguồn YouTube còn công khai.
- **FE:** uploader có thanh tiến độ trong editor bài học (F2.1 UI), chọn MP4 hoặc dán URL YouTube.
- **BR:** MP4 ≤ 2GB, thời lượng 1–180 phút (BR-CHUNK-01); nguồn bị gỡ sau khi có audio → chuyển
  `UNAVAILABLE`, giữ audio, báo Giảng viên (BR-DUB-11 — phần detect thuộc GĐ5, ở đây chỉ chuẩn bị
  field).
- **Hoàn thành khi:** nạp được cả MP4 và YouTube, `durationSec` đúng, `Lesson.status` → `READY`.

### F4.2 — Tài liệu đính kèm

- **UC:** 35 · **BE:** `LessonDocumentController`+`Service`, kiểm magic number (không tin đuôi file),
  giới hạn 50MB/file, 5 file/bài. · **FE:** quản lý danh sách tài liệu trong editor bài học.
- **Hoàn thành khi:** đổi tên `.exe` thành `.pdf` bị chặn; vượt 5 file bị chặn kèm thông báo rõ.

---

## Giai đoạn 5 — Pipeline lồng tiếng AI ⭐

**UC:** 18–20, 37, 45, 47 · **Entity:** `AiJob`, `AiJobChunk`, `Transcript`, `TranscriptSegment`,
`AudioTrack`, `AudioChunk`, `VoiceMapping` · **BR:** BR-CHUNK-02…05, BR-DUB-01…11, BR-STORAGE-01

> ⚠️ **Giai đoạn này KHÔNG chia theo feature-dọc BE+FE được** — kiến trúc là 1 pipeline bất đồng bộ
> xuyên 2 service (`be` sinh job → `ai-worker` xử lý → `be` forward realtime), không phải sản phẩm
> độc lập. Chia theo **service sở hữu**, giống mô hình cũ nhưng rõ ranh giới hơn. Phần FE của giai
> đoạn này rất nhẹ — `DubbingActivatePanel`/`PipelineProgress`/`LanguageSwitcher` **đã dựng xong UI ở
> Giai đoạn 0**, chỉ cần nối API thật.

### F5.1 — Producer & Cấu hình (BE, có 1 chút FE)

- **UC:** 18 (yêu cầu lồng tiếng), 37 (pre-warm), 47 (Voice Mapping)
- **BE:** `dubbing.controller.DubbingController` (5 bước tiền điều kiện → tạo `AiJob` → Redis
  `SETNX` lock → LPUSH), `VoiceMappingController` (Admin), hạn ngạch Redis counter.
- **FE:** nối `DubbingActivatePanel` + `LanguageSwitcher` (đã có từ GĐ0) vào API thật;
  `app/admin/voice-mappings/`.
- **BR:** dedupe 2 lớp — UNIQUE `active_flag` ở DB + Redis lock TTL 30 phút (BR-DUB-05) · hạn ngạch
  15/30 job/ngày (BR-DUB-06) · chặn ngôn ngữ trùng gốc (BR-DUB-09) · chỉ ngôn ngữ `is_active`
  (BR-DUB-07).

### F5.2 — Pipeline Execution (AI Worker, Python)

- **UC:** 19 · **AI Worker:** `app/tasks/dubbing.py` — chunk 10 phút → `groq_asr.py` → Gemini dịch 3
  bước → Adaptive Speech Rate → `edge_tts.py` → FFmpeg concat. Port thuật toán từ
  `ai-worker/core/` theo bảng dùng/bỏ ở mục 1 của `DEVELOPMENT_PLAN.md`.
- **BR:** phân đoạn cố định 10 phút (BR-CHUNK-02) · `R≤1.0` giữ tốc độ+padding,
  `1.0<R≤1.3` chỉnh rate, `R>1.3` bắt buộc re-summarize (BR-DUB-03) · retry ≤3 lần, `SKIPPED` không
  retry (BR-CHUNK-04, BR-DUB-10) · dọn file trung gian ≤24h (BR-STORAGE-01).
- **Hoàn thành khi:** video 15 phút ra `final.mp3` khớp mốc thời gian; ép lỗi 1 chunk thấy retry rồi
  giữ audio gốc đúng đoạn đó.

### F5.3 — Realtime & Giám sát

- **UC:** 20 (theo dõi realtime), 45 (Admin giám sát queue)
- **BE:** subscribe Redis Pub/Sub `lms:dubbing:progress` → forward WebSocket; `AiJobController` cho
  Admin (danh sách, retry job lỗi).
- **FE:** nối `PipelineProgress` (đã có UI từ GĐ0) vào WebSocket thật; `app/admin/jobs/`.
- **BR:** chunk đầu xong bắn ngay, `TrackStatus=PARTIAL` (BR-CHUNK-03).
- **Hoàn thành khi:** bấm kích hoạt thấy progress bar chạy thật theo từng chunk; học viên thứ 2 chọn
  cùng ngôn ngữ trong lúc đang xử lý chỉ subscribe, không tạo job mới.

**Gợi ý người:** F5.2 cần người rành Python/thuật toán nhất — ưu tiên xếp riêng, không ghép việc
khác. F5.1 và F5.3 có thể gộp làm 1 nếu chỉ có 2 người ở giai đoạn này.

---

## Giai đoạn 6 — Dual Player & Tiến độ học tập

**UC:** 16, 17, 21, 22 · **Entity:** `LessonProgress` · **BR:** BR-SYNC-01, BR-PROGRESS-01…04

Cả 2 tính năng đều vertical đầy đủ — không phụ thuộc nhau, chạy song song hoàn toàn.

### F6.1 — Dual Player thật (nối dữ liệu)

- **UC:** 16, 17 · Logic đồng bộ `useDualPlayerSync` (BR-SYNC-01: vòng kiểm 250ms, 4 sự kiện,
  YouTube buffering) **đã viết xong ở Giai đoạn 0** — việc của tính năng này là:
- **BE:** endpoint trả `AudioTrack` thật (kể cả trạng thái `PARTIAL` + danh sách `AudioChunk`).
- **FE:** nối `DualPlayer.tsx` (đã có ở GĐ0) vào API thật thay `lesson.activeTrack = null` mock;
  xử lý nguồn YouTube qua IFrame API (`YT_STATE` đã định nghĩa sẵn trong hook).
- **Hoàn thành khi:** xem 15 phút không lệch tiếng; đổi tốc độ audio khớp theo; bài `PARTIAL` vẫn
  xem được qua playlist chunk.

### F6.2 — Ghi nhận & Báo cáo tiến độ

- **UC:** 21, 22 · **BE:** `LessonProgressController`, `recalculateProgress()` trên `Enrollment`,
  ghi mỗi 15s + tại pause/seek/unload. · **FE:** gửi tiến độ định kỳ từ Dual Player, trang báo cáo
  cá nhân `app/(student)/progress/`.
- **BR:** `watchedSec` không cộng tua nhanh, `isCompleted` một chiều khi ≥90% (BR-PROGRESS-01) ·
  điểm Quiz trong báo cáo = MAX trên mọi bộ (BR-PROGRESS-04, phần Quiz thật làm ở GĐ7, để `null` an
  toàn ở đây).
- **Hoàn thành khi:** tua tới không làm hoàn thành oan; reload về đúng vị trí cũ; % tiến độ khóa học
  cập nhật đúng khi hoàn thành thêm 1 bài.

---

## Giai đoạn 7 — Học liệu AI (Creator Agent)

**UC:** 24–29 · **Entity:** `MaterialGeneration`, `Mindmap`, `FlashcardDeck`, `Flashcard`,
`FlashcardReview`, `Quiz`, `QuizQuestion`, `QuizOption`, `QuizAttempt`, `QuizAnswer`
**BR:** BR-MAT-01…08, BR-QUIZ-01…02, BR-CARD-01

### F7.1 — Hạ tầng sinh học liệu + Mindmap (làm trước, người khác phụ thuộc vào đây)

- **UC:** 24 (form yêu cầu), 25 (Creator Agent thực thi), 26 (quản lý phiên bản), 27 (xem Mindmap)
- **BE:** `MaterialGenerationController`+`Service` (hạn ngạch 6/ngày dùng chung 3 loại, giới hạn 10
  bộ/khóa, trả `202 Accepted`), AI Worker `app/tasks/material.py` phần khung (nhận request, gọi
  Gemini, validate JSON/Mermaid, retry ≤2 lần) + `MindmapService` cụ thể.
- **FE:** form tạo học liệu (loại/ngôn ngữ/phạm vi/số lượng/độ khó), danh sách phiên bản đã tạo,
  màn xem Mindmap (Mermaid.js, zoom).
- **BR:** cấp khóa học không phải cấp bài (BR-MAT-01) · `versionNo` tăng dần không ghi đè (BR-MAT-07)
  · validate LLM retry ≤2, sai thì `FAILED` không chặn pipeline lồng tiếng (BR-MAT-06).
- **Đây là hạ tầng dùng chung** cho F7.2/F7.3: `MaterialGenerationController`, luồng validate-retry,
  và UI form/danh sách phiên bản. **Nên hoàn thành khung trước 2–3 ngày** rồi 2 người kia mới thêm
  `FlashcardService`/`QuizService` vào khung đó.

### F7.2 — Flashcards & SM-2

- **UC:** 28 · **BE:** `FlashcardService` sinh thẻ, `FlashcardReviewController` (SM-2: EF khởi tạo
  2.5, sàn 1.3, lịch 1→6 ngày→theo EF). · **FE:** màn lật thẻ, đánh giá Dễ/TB/Khó.
- ⚠️ `TODO(doc)`: công thức `I(n)` và ánh xạ điểm `q` của SM-2 bị mất khi convert KLTN — đối chiếu
  bản Word trước khi code, không tự đoán số.
- **Hoàn thành khi:** ôn thẻ xong thấy lịch ôn tiếp theo giãn đúng theo SM-2; trả lời sai reset về
  1 ngày.

### F7.3 — Quiz

- **UC:** 29 · **BE:** `QuizService` sinh câu hỏi (đúng 4 phương án, **không** `explanation`/
  `timestampSec` — BR-QUIZ-01), `QuizAttemptController` (không giới hạn số lần làm). · **FE:** màn
  làm bài, hiện đáp án đúng/sai **chỉ sau khi nộp** (BR-QUIZ-02).
- **Hoàn thành khi:** làm lại nhiều lần đều lưu lịch sử; điểm về đúng báo cáo F6.2 (`MAX` trên mọi
  bộ).

**3 người lý tưởng cho giai đoạn này** (F7.1 trước, F7.2+F7.3 song song sau). Nếu chỉ có 2 người:
1 người làm F7.1, người còn lại làm cả F7.2 và F7.3 tuần tự.

---

## Giai đoạn 8 — Socratic Tutor & Course Discovery

**UC:** 30, 49 · **Entity:** `ChatSession`, `ChatMessage` · **BR:** BR-TUTOR-01…04, BR-DISCOVERY-01…02

2 agent độc lập hoàn toàn — không phụ thuộc nhau, chạy song song từ đầu.

### F8.1 — Socratic AI Tutor

- **UC:** 30 · **AI Worker:** `app/api/tutor.py` (đã có stub từ GĐ0) — RAG trên Supabase Vector,
  tối đa 5 đoạn transcript. · **BE:** lưu `ChatSession`/`ChatMessage`, forward qua `INTERNAL_BE_URL`.
  · **FE:** panel chat trượt trong trang học bài, mốc thời gian trong câu trả lời là link nhấp
  được → `seek` Dual Player.
- **BR — luận điểm cốt lõi của đề tài:** không đưa đáp án trực tiếp, chỉ 1–2 câu hỏi gợi mở
  (BR-TUTOR-01) · **bắt buộc** ≥1 mốc thời gian trong mọi câu trả lời về kiến thức bài giảng
  (BR-TUTOR-02) · 30 tin/học viên/ngày (BR-TUTOR-04).
- Việc phát sinh: job đánh index embedding `TranscriptSegment` lên Supabase Vector — móc vào cuối
  pipeline GĐ5 (chỉnh nhỏ ở F5.2, không phải việc mới của F8.1).
- **Hoàn thành khi:** hỏi "giải hộ bài này" không ra đáp án, có mốc thời gian nhấp seek đúng giây.

### F8.2 — Course Discovery Chat

- **UC:** 49 · **AI Worker:** `app/api/discovery.py` (đã có stub) — Gemini Function Calling →
  `search_courses(...)`. **Stateless, không dùng `ChatSession`** (BR-DISCOVERY-01). · **FE:** khung
  chat ở trang chủ/trang tìm kiếm, hiện kết quả dạng `CourseCard` (tái dùng component từ GĐ0).
- **BR:** Guest 15 tin/IP/giờ, Student 30 tin/ngày (dùng chung counter BR-TUTOR-04) · chỉ trả lời
  chủ đề tìm khóa học (BR-DISCOVERY-02).
- **Hoàn thành khi:** hỏi tự nhiên ra đúng thẻ khóa học khớp bộ lọc; hỏi ngoài chủ đề bị từ chối
  lịch sự; vượt hạn ngạch trả `429`.

---

## Giai đoạn 9 — Thống kê & Giám sát

**UC:** 38, 39, 46 · **BR:** BR-PAY-05 (hiển thị), BR-DUB-08 (báo cáo audio ít dùng)

### F9.1 — Thống kê & Quản lý học viên (phía Giảng viên)

- **UC:** 38 (doanh thu), 39 (học viên & tiến độ) · **BE:** API tổng hợp doanh thu gộp/phí nền
  tảng/thực nhận (BR-PAY-05, đã có sẵn 3 cột trong `Payment` từ GĐ0), danh sách học viên + tiến độ
  theo khóa. · **FE:** dashboard Giảng viên (biểu đồ doanh thu, bảng học viên).

### F9.2 — Giám sát hệ thống & Job định kỳ (phía Admin)

- **UC:** 46 · **BE:** API giám sát CPU/RAM, hoàn thiện 4 job Celery beat còn để `NotImplementedError`
  từ GĐ0 (`cleanup_temp_files` đã làm ở F5.2, `cleanup_old_notifications`, `remind_flashcard_reviews`,
  `report_unused_audio` — BR-DUB-08, chỉ báo cáo, **không tự xóa**). · **FE:** dashboard Admin giám
  sát tài nguyên + hàng đợi.

Phase nhỏ, có thể gộp cả 2 cho 1 người nếu team đang bận việc khác.

---

## Giai đoạn 10 — Kiểm thử, thực nghiệm & Triển khai

**Không chia theo tính năng** — đây là việc cross-cutting, áp dụng nguyên tắc **ai xây tính năng nào
thì người đó viết test cho tính năng đó** (unit test tầng service, ưu tiên: dedupe job F5.1, idempotent
IPN F3.2, tính `R` F5.2, SM-2 F7.2, tính tiến độ F6.2).

Việc chung không thuộc riêng ai — phân theo năng lực lúc đó:
- Integration test BE↔Celery↔AI API (Testcontainers).
- Thực nghiệm đối chứng Groq vs WhisperX local (§6.3.2 KLTN) — nên là người đã làm F5.2.
- Functional test theo bảng 49 UC, điền bảng Quality Metrics §NFR.
- Hướng dẫn cài đặt + quy trình nghiệp vụ (§5.4 KLTN).

---

## Tổng hợp — số tính năng mỗi giai đoạn

| GĐ | Tên | Số tính năng | Ghi chú |
| --- | --- | --- | --- |
| 2 | Khóa học & Kiểm duyệt | 2 | Song song hoàn toàn |
| 3 | Ghi danh & Thanh toán | 2 | F3.1 chốt hợp đồng trước |
| 4 | Nạp video & Lưu trữ | 2 | Song song hoàn toàn |
| 5 | Pipeline lồng tiếng AI | 3 | Chia theo service, không phải theo BE/FE |
| 6 | Dual Player & Tiến độ | 2 | Song song hoàn toàn |
| 7 | Học liệu AI | 3 | F7.1 làm trước, chặn F7.2/F7.3 vài ngày |
| 8 | Tutor & Discovery | 2 | Song song hoàn toàn |
| 9 | Thống kê & Giám sát | 2 | Có thể gộp làm 1 nếu ít người |
| 10 | Kiểm thử & Triển khai | — | Cross-cutting, ai xây thì người đó test |

**Team 2 người:** ghép 2 tính năng/giai đoạn cho 2 người, trừ GĐ5/7 chọn người hợp kỹ năng nhất cho
phần AI Worker (F5.2, F7.1) trước.
**Team 3 người:** vừa khít hầu hết giai đoạn, dư người ở GĐ2/3/4/6/8/9 thì lấy dư sức đó bắt đầu sớm
tính năng của giai đoạn kế — miễn không phá quy tắc *"giai đoạn sau phụ thuộc entity giai đoạn trước"*
ở đầu `DEVELOPMENT_PLAN.md`.
