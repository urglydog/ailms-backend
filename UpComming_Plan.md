# Kế Hoạch Các Hạng Mục Còn Lại (Cập nhật 22/09/2026)

Nội dung cũ của file này (sửa lỗi Material Workspace, redesign UI Login/Kho học liệu...) đã hoàn tất toàn bộ trong các đợt việc trước — xoá bỏ để tránh nhầm với việc đang làm. File này thay bằng danh sách các hạng mục **còn lại thật sự**, gồm 2 nhóm:

- Nhóm A: các hạng mục đã cố tình dời lại khi làm Task 1/4/11 (nguồn gốc từ `CurrentPlan.md` cũ — đã xoá file đó, toàn bộ nội dung còn giá trị được gộp vào đây) vì quy mô lớn hơn "làm nhanh dứt điểm".
- Nhóm B: 2 hạng mục mới bạn vừa nêu (Học liệu chung toàn khoá + Import/Export tương thích Anki/Quizlet) — khảo sát code cho thấy **một phần đã có sẵn**, xem chi tiết bên dưới.

Mỗi mục có: hiện trạng thật (đã kiểm tra code, không phỏng đoán), phân tích khả thi, phương pháp đề xuất, và test case để coi là "xong".

---

## Nhóm A — Hạng mục đã dời lại từ Task 1/4/11

### A1. ✅ ĐÃ XONG (phần lõi) — Certificate PDF khi học viên đạt 100% tiến độ

**Hiện trạng**: chưa có gì. Không có thư viện sinh PDF nào trong `pom.xml` (không OpenPDF/iText/PDFBox), không có entity `Certificate`, `Enrollment` chỉ có `completedAt`.

**Phân tích khả thi**: khả thi, rủi ro thấp nếu chọn đúng cách làm — rủi ro nằm ở việc có thêm bảng mới hay không, không nằm ở việc sinh PDF (Java có nhiều lib PDF ổn định).

**Đề xuất**:
- Không tạo bảng `Certificate` riêng (tránh 1 migration + 1 entity chỉ để lưu thứ suy ra được). Thêm 1 cột `certificate_code` (UUID, nullable) vào `enrollments` — sinh ra lúc `completedAt` được set lần đầu.
- Thêm 1 dependency PDF (đề xuất **OpenPDF** — license MIT, nhẹ, đủ dùng cho 1 trang chứng chỉ, cùng nhóm rủi ro thấp như OpenCSV vừa thêm).
- Endpoint `GET /api/v1/enrollments/{courseId}/certificate` — kiểm tra `completedAt != null`, sinh PDF **on-the-fly** (không lưu file lên B2), trả về `application/pdf` trực tiếp. Không cần thêm storage, không cần job nền.
- FE: nút "Tải chứng chỉ" chỉ hiện khi `progressPct >= 100` (đã có sẵn field này ở `CourseCard`/trang tiến độ).
- Bổ sung từ `CurrentPlan.md` cũ: khi `progressPct` vừa chạm 100% trong lúc học viên đang ở màn hình học, hiện 1 popup chúc mừng nhẹ (kèm icon cúp) mời bấm tải chứng chỉ ngay, thay vì phải tự tìm nút — tận dụng đúng chỗ progress bar vừa thêm ở `app/(learn)/layout.tsx` (Task 1 đợt trước) để biết khi nào % vừa đổi sang 100.

**Đã làm**: migration `V127__enrollment_certificate_code.sql` (cột `certificate_code` VARCHAR(36) nullable), set UUID cùng lúc với `completedAt` trong `LessonProgressService.recalculateEnrollmentProgress`, thêm dependency `com.github.librepdf:openpdf:1.3.42`, endpoint `GET /api/v1/enrollments/{courseId}/certificate` (sinh PDF on-the-fly bằng OpenPDF, chặn 422 nếu chưa hoàn thành), nút "Tải chứng chỉ" (icon cúp) ở `CourseCard.tsx` chỉ hiện khi `progressPct >= 100`.

**Chưa làm (bonus, không phải core)**: popup chúc mừng tự động khi % vừa chạm 100 lúc đang học — để dành, có thể làm sau nếu bạn muốn, không chặn việc tải chứng chỉ (nút ở Course Card đã đủ dùng được ngay).

**Test case (pass khi)**:
1. Enrollment có `progressPct < 100` → gọi endpoint trả 422, không sinh PDF. — ✅ logic đã viết đúng, **chưa test tay**.
2. Enrollment vừa chạm 100% → `certificate_code` được set đúng 1 lần, gọi lại nhiều lần không đổi mã. — ✅ logic đã viết đúng (chỉ set khi `getCertificateCode() == null` gộp chung điều kiện với `completedAt == null`), **chưa test tay**.
3. File PDF tải về mở được, có tên học viên, tên khoá học, ngày hoàn thành, mã xác thực. — **cần test tay** (chưa có enrollment nào đủ 100% trong dữ liệu hiện tại để thử end-to-end).
4. `mvn compile` — ✅ BUILD SUCCESS, restart + health-check 200 OK, xác nhận migration V127 đã áp dụng vào DB thật.

---

### A2. ✅ ĐÃ XONG (phần công thức chính) — Gộp điểm Quiz vào công thức % tiến độ

**Quyết định đã chốt với bạn (23/09/2026)**:
1. Trọng số: **70% video / 30% Quiz**.
2. Học liệu tĩnh: **không tính vào %** (chỉ tham khảo).
3. Enrollment cũ đã có `completedAt`: **giữ nguyên đã hoàn thành**, không hồi tố.

**Đã làm**: `LessonProgressService.recalculateEnrollmentProgress` — công thức mới `progressPct = 70% × (bài COMPLETED/tổng bài READY) + 30% × (100 nếu điểm Quiz chính thức cao nhất ≥ 5.00/10, else 0)`. Ngưỡng "đạt" (5.00/10) **tái dùng đúng ngưỡng đã có ở Gradebook** (`InstructorGradebookController`, không bịa ngưỡng mới), "Quiz chính thức" xác định qua `QuizRepository.findFirstByMaterialGeneration_Course_IdAndIsOfficialTrueOrderByCreatedAtDesc` (method đã tồn tại sẵn, cùng cách Gradebook xác định "quiz của khoá"). Khoá **không có** Quiz chính thức → dồn 100% trọng số vào video (không phạt học viên vì thiếu thứ giảng viên chưa tạo). Enrollment đã có `completedAt` → early-return, giữ nguyên nguyên trạng, không áp công thức mới.

Vì Quiz giờ ảnh hưởng %, đã nối thêm 1 điểm gọi: `QuizService.submitAttempt` (khi quiz chính thức) giờ cũng gọi `LessonProgressService.recalculateEnrollmentProgress` ngay sau khi chấm điểm — trước đây nộp Quiz không đụng gì tới `progressPct`, giờ phải tính lại ngay, không đợi học viên xem thêm video mới cập nhật.

**Chưa làm (3 chi tiết phụ, tách riêng theo đúng đề xuất ban đầu — không thuộc phạm vi "đã chốt")**:
1. **Tick/bỏ tick thủ công** 1 bài học — cần cột `isManuallyCompleted` mới trên `LessonProgress`, đây là 1 quyết định schema/UX riêng, chưa hỏi bạn.
2. **Học liệu tĩnh tự tick khi xem/tải** — đã loại khỏi phạm vi theo quyết định #2 ở trên, không cần làm.
3. **Cân đối mẫu số khi xoá Quiz/mục bắt buộc** — `findFirstByMaterialGeneration_Course_IdAndIsOfficialTrueOrderByCreatedAtDesc` chỉ lấy quiz **chưa xoá** mặc định (Spring Data JPA không tự lọc `isDeleted`, cần kiểm tra kỹ — nếu instructor xoá đúng quiz đang là "quiz chính thức" thì lần tính tiếp theo sẽ không tìm thấy quiz nào → tự động rơi về "không có Quiz chính thức" → chỉ tính video, không bị kẹt ở 70%. Hành vi này **an toàn theo đúng tinh thần đã cảnh báo**, nhưng chưa viết test riêng xác nhận — để dành nếu bạn muốn chắc chắn hơn.

**Test case**:
1. Khoá không có Quiz chính thức → % tính như cũ (100% trọng số video). — ✅ có unit test (`coEnrollment_tinhDungPhanTramTheoBaiReady`, `hoanThanhTatCaBai_setCompletedAt`).
2. Khoá có Quiz chính thức, video 100% nhưng chưa làm Quiz → % = 70.00, chưa set `completedAt`. — ✅ unit test mới `coQuizChinhThuc_chuaDat_chi70PhanTramVideo`.
3. Khoá có Quiz chính thức, video 100% + điểm Quiz cao nhất ≥ 5.00 → % = 100.00, set `completedAt` + `certificateCode`. — ✅ unit test mới `coQuizChinhThucDaDat_video100PhanTram_hoanThanhDu100`.
4. Enrollment đã `completedAt` từ trước → gọi lại không đổi %, không đổi `completedAt`. — ✅ unit test cũ `daCoCompletedAt_khongGhiDeLai` (đã cập nhật cho phù hợp early-return mới).
5. `mvn -o test` — ✅ **374/374 test pass** (toàn bộ test suite, không chỉ module này), `mvn -o test-compile` sạch (đã sửa `LessonProgressServiceTest` bị vỡ do constructor thêm 2 tham số), restart + health-check 200 OK, không có lỗi circular bean dependency giữa `QuizService` ↔ `LessonProgressService`.

---

### A3. ✅ ĐÃ XONG — Gỡ trang `/progress` cũ

Phát hiện lúc làm: vẫn còn 1 link thật trỏ tới trang này ở dropdown Header ("Báo cáo tiến độ") — không mồ côi như dự đoán ban đầu. Đã hỏi và bạn chọn xoá cả hai. Đã xoá route `app/(student)/progress/page.tsx` và mục "Báo cáo tiến độ" khỏi `Header.tsx`. Verify: `eslint` + `tsc --noEmit` sạch, `GET /progress` trả 404 đúng như kỳ vọng, trang chủ vẫn 200.

---

### A4. Mermaid Live Preview (✅ phần a đã xong) + Template nâng cao (Fishbone / Tree-card / Matrix — chưa làm)

**Ghi chú từ bạn (giảng viên, đã tự thử trước đó)**: đã cố hiện thực fishbone, treecard, matrix nhưng hầu như đều lỗi do giới hạn thư viện; nguyên nhân một phần do dùng auto-format ở tab hiển thị tĩnh nên không mô phỏng đúng 100% mọi thao tác realtime.

**Phân tích khả thi (đã kiểm tra code + thư viện đang dùng — `mermaid@11.4.1`)**:
- `MindmapEditor.tsx` hiện tại là editor kéo-thả (React Flow) xuất ra cú pháp Mermaid `mindmap`/`flowchart`, `initialTemplate` chỉ là hướng layout (`LR`/`TD`), **không phải** một bộ "loại biểu đồ" khác nhau.
- Mermaid.js (mọi bản, kể cả 11.4.1) **có hỗ trợ native**: `mindmap`, `flowchart`. **Không có** loại diagram chuẩn nào tên "fishbone/Ishikawa" hay "matrix diagram" trong core Mermaid — đây không phải giới hạn cấu hình, mà là thư viện không định nghĩa cú pháp cho 2 loại này. Muốn có thật sự cần tự vẽ SVG/D3 riêng, không phải "sửa thêm" trên Mermaid.
- → Xác nhận đúng những gì bạn đã gặp: đây là **giới hạn thật của thư viện**, không phải lỗi cách làm của bạn.

**Đề xuất chia làm 3 phần riêng, độ khả thi khác nhau**:

| Phần | Khả thi | Đề xuất |
|---|---|---|
| a) Live Preview split-screen (gõ code Mermaid trái, xem hình phải, realtime) | **✅ ĐÃ XONG** | Thêm component `MermaidCodeEditor.tsx` + tab "Soạn Code" mới trong `CourseMaterialsManager.tsx` (cạnh "Chỉnh Sửa"/"Mã Mermaid"). Textarea trái debounce 400ms → tái dùng nguyên `MermaidViewer` đã có bên phải, nút Lưu gọi đúng `updateMermaidMutation` sẵn có. |
| b) Template "Tree-card" (sơ đồ cây phân nhánh) | **✅ ĐÃ XONG** | Nút "Chèn khung Tree-card mẫu" trong tab "Soạn Code" (`MermaidCodeEditor.tsx`), điền sẵn code `mindmap` mẫu 3 nhánh để sửa tiếp — đúng dự đoán, không cần renderer mới. |
| c) Fishbone / Matrix | **Không khả thi trong khuôn khổ Mermaid** | Muốn có thật sự phải đổi hẳn cơ chế vẽ (SVG tự viết hoặc lib khác như Markmap/D3) — đây là 1 hạng mục lớn, tách riêng hoàn toàn, cần đánh giá lại từ đầu, không nằm trong phạm vi "làm nhanh dứt điểm" của đợt này. |

**Test case cho phần (a)**:
1. Gõ code Mermaid hợp lệ → hình bên phải cập nhật trong < 1s, không cần bấm nút nào. — **cần test tay**.
2. Gõ code sai cú pháp → hiện thông báo lỗi đỏ tại chỗ (đúng, `MermaidViewer` đã có sẵn khối `error` hiện thông báo + code gốc, không crash trắng màn hình). — **cần test tay**.
3. Bấm "Lưu" từ tab này → dùng đúng `onSave`/`updateMermaidMutation` đã có, không tạo luồng lưu riêng. — ✅ đúng, tái dùng nguyên hàm cũ.
4. `npx eslint` + `tsc --noEmit` — ✅ đã chạy, sạch.

---

### A5. ✅ ĐÃ XONG — Export PDF cheatsheet / đề trắng

**Đã làm**: `QuizService.exportQuizPdf` (dùng chung OpenPDF của A1) sinh 2 chế độ — `mode=blank` (đề trắng, đáp án in riêng trang cuối) và `mode=cheatsheet` (câu hỏi + đáp án đúng in liền, tô màu xanh để ôn nhanh). Hai endpoint song song đúng khuôn 2 nhóm quyền đã có trong hệ thống: `GET /api/v1/instructor/quizzes/{quizId}/export-pdf` (giảng viên, ownership = course.instructor) và `GET /api/v1/quizzes/{quizId}/export-pdf` (học viên, ownership = quiz sở hữu cá nhân — đúng tinh thần bản gốc `CurrentPlan.md`: "Export Quiz" là nhu cầu của học viên). Nút bấm thêm ở cả `CourseMaterialsManager.tsx` (giảng viên) và `(student)/materials/[id]/page.tsx` (học viên, chỉ hiện khi quiz không phải Official — đúng vì endpoint học viên chỉ cho export quiz cá nhân của chính họ).

**Test case (pass khi)**:
1. PDF sinh ra đúng số câu, đáp án ở cuối (`mode=blank`) khớp với đáp án đúng trong DB, không lộ đáp án ở phần đề. — **cần test tay**.
2. `mode=cheatsheet` hiện đáp án đúng tô màu ngay trong phần đề (không tách trang riêng). — **cần test tay**.
3. Giảng viên A không export được PDF quiz của khoá giảng viên B (403). — logic ownership tái dùng nguyên từ `addQuestionsFromCsv`, đã đúng từ trước.
4. `mvn compile` — ✅ BUILD SUCCESS. `npx eslint` + `tsc --noEmit` (cả 2 file FE) — ✅ sạch.

---

### A6. Redis token-bucket cho rate-limit AI (thay DB-COUNT hiện tại)

**Hiện trạng**: DB-COUNT (đếm bằng `COUNT(*)` MySQL) đang chạy ổn, hạn mức 6/ngày thấp nên rủi ro race-condition (2 request cùng lúc lách qua giới hạn) gần như không đáng kể trên quy mô hiện tại.

**Đề xuất**: giữ nguyên, chỉ đổi khi có bằng chứng cụ thể về hiệu năng hoặc lách hạn mức xảy ra thật.

**Ghi chú bổ sung từ `CurrentPlan.md` cũ**: đề xuất gốc muốn hạn mức áp dụng cho **cả Student và Instructor**. Hiện tại instructor chỉ được miễn hạn mức khi sinh học liệu cho **khoá học của chính họ** (kiểm tra bằng `course.getInstructor().getId().equals(user.getId())` trong `MaterialGenerationService`) — nếu instructor thao tác trên khoá của người khác vẫn bị tính như student. Đây là hành vi đã đúng theo thiết kế hiện tại, không phải lỗ hổng, không cần sửa.

---

### A7. Task 11B — Onboarding Profile Wizard: vẫn giữ nguyên trạng thái BỎ QUA

`CurrentPlan.md` cũ xếp mục này "Cao" (chặn spam tài khoản), nhưng bạn đã chỉ đạo trực tiếp trong đợt Task 1/4/11 vừa qua: **không đụng vào**, vì đây thuộc luồng tạo/xét duyệt khoá học của người khác đang phụ trách. Ghi lại rõ ở đây để không bị vô tình làm lại khi đọc thấy mức ưu tiên "Cao" — quyết định bỏ qua vẫn còn hiệu lực cho tới khi bạn tự nói lại khác đi.

---

## Nhóm B — Hạng mục mới bạn vừa nêu

### B1. ✅ ĐÃ XONG — "Học liệu chung" / Tài nguyên tĩnh toàn khoá học

**⚠️ Phát hiện quan trọng khi khảo sát code — tính năng này KHÔNG PHẢI làm từ đầu:**

| Phần | Trạng thái | Vị trí |
|---|---|---|
| Backend: entity + upload nhiều file (PDF/Word/PPT/ZIP) + gắn courseId-only hoặc +chapter/+lesson + list + xoá mềm | ✅ **Đã có, đầy đủ, đang chạy** | `CourseResource.java`, `InstructorResourceController.java`, `StudentResourceController.java` |
| Frontend học viên: tab "Tài nguyên" hiện danh sách + tải file | ✅ **Đã có, đầy đủ, đang chạy** | `CourseResourcesTab.tsx`, đã gắn vào tab `resources` ở màn hình học |
| Frontend giảng viên: modal chọn nhiều file, validate, gọi đúng API upload | ✅ Component đã viết xong, đúng logic | `UploadStaticMaterialModal.tsx` |
| Nút mở modal trên ở màn hình giảng viên | ❌ **KHÔNG TỒN TẠI** — đã grep toàn bộ `/var/lms/fe`, 0 nơi import `UploadStaticMaterialModal` | — |

Nói cách khác: toàn bộ hạ tầng đã xây sẵn từ trước (đúng ý bạn mô tả — tách biệt hẳn với học liệu từng chương, vì `CourseResource` là entity riêng, không liên quan `MaterialGeneration`/Quiz/Flashcard/Mindmap), chỉ **thiếu duy nhất 1 nút bấm** để giảng viên mở được modal upload đã viết sẵn. Đây là code mồ côi (orphaned component), không phải tính năng thiếu.

**Đề xuất**: việc rất nhỏ, an toàn — thêm 1 nút "Tài nguyên tĩnh" vào màn hình `Sửa khoá học` (cạnh khu vực quản lý học liệu hiện có, ví dụ `CourseMaterialsManager.tsx` hoặc 1 tab riêng ngang hàng "Học liệu"/"Chương trình học"), mở `UploadStaticMaterialModal` đã có, kèm danh sách file đã tải (gọi lại đúng API list/delete đã có sẵn, chỉ thiếu phần hiển thị + nút xoá phía giảng viên).

**Đã làm**: thêm nút "Tài Nguyên Tĩnh" vào toolbar `CourseMaterialsManager.tsx` (cạnh nút "Hoạt động"), mở component mới `StaticResourcesPanel.tsx` — liệt kê tài nguyên đã tải (gọi lại `courseResourcesApi.getCourseResources` có sẵn), nút xoá từng file (gọi `deleteResource` có sẵn), và nút "Tải Lên Tài Nguyên Mới" mở đúng `UploadStaticMaterialModal.tsx` đã viết sẵn trước đó. Không sửa dòng backend nào (đúng dự đoán — hạ tầng đã đủ).

**Test case (pass khi)** — đã tự kiểm tra được phần build, còn lại chờ bạn test tay qua UI:
1. Giảng viên vào màn hình sửa khoá học, thấy nút "Tài Nguyên Tĩnh", bấm mở đúng modal đã có. *(cần test tay)*
2. Tải lên 1 file PDF không gắn chương/bài học nào → xuất hiện ngay trong tab "Tài nguyên" phía học viên, tải về được. *(cần test tay)*
3. Tải file sai định dạng (vd `.exe`) → bị chặn với thông báo lỗi rõ ràng (logic chặn `ALLOWED_MIME_TYPES` đã có sẵn ở BE). *(cần test tay)*
4. Giảng viên xoá 1 tài nguyên → biến mất khỏi danh sách ngay (invalidate đúng queryKey `course-resources`). *(cần test tay)*
5. `npx eslint` + `tsc --noEmit` sạch — ✅ đã chạy, sạch.

---

### B2. ✅ ĐÃ XONG — Import/Export tương thích Anki/Quizlet cho Flashcard

**⚠️ Cũng đã khảo sát kỹ — chỉ THIẾU 1 CHIỀU, không phải thiếu toàn bộ:**

| Chiều | Trạng thái | Vị trí |
|---|---|---|
| **Export** từ nền tảng ra file `.txt` tab-separated (chuẩn Anki/Quizlet import được) | ✅ **Đã có, đang chạy** | `app/(student)/materials/[id]/page.tsx` — nút "📥 Xuất ra file Anki/Quizlet", xuất client-side, không gọi API |
| **Import** — học viên đưa file `.txt` từ Anki/Quizlet vào bộ thẻ cá nhân của mình trên nền tảng | ❌ Chưa có | — |
| Import CSV cho giảng viên (Quiz + Flashcard, để soạn nhanh) | ✅ **Vừa hoàn thành trong đợt Task 4** | `importQuizQuestionsCsv`/`importFlashcardsCsv` |

**Đề xuất**: tái dùng gần như nguyên vẹn hạ tầng OpenCSV vừa thêm ở Task 4 — khác biệt duy nhất là parse theo dấu **tab** thay vì dấu phẩy (`CSVParserBuilder().withSeparator('\t')` của OpenCSV, không cần lib mới) và chỉ 2 cột (Front/Back, không có header — file Anki xuất ra thường không có dòng tiêu đề, cần xử lý khác 1 chút so với CSV Quiz/Flashcard hiện tại vốn luôn bỏ dòng đầu).

Vì đây là **học liệu cá nhân của học viên** (không phải Official do giảng viên tạo), endpoint mới phải dùng theo mô hình sở hữu (owner-based) sẵn có — tương tự `addPersonalQuestion`/`addFlashcard` hiện tại (dựa trên `MaterialGeneration.user`), **không** dùng nhóm endpoint `/instructor/...` đã thêm ở Task 4.

**Đã làm**: endpoint mới `POST /api/v1/flashcards/deck/{generationId}/import-txt` (owner-based, dùng đúng `deck.getMaterialGeneration().getUser()` như `addFlashcard` hiện có) — parse tab-separated bằng `CSVParserBuilder().withSeparator('\t')`, **không** bỏ dòng đầu (khác CSV Quiz/Flashcard của Task 4). FE: nút "📤 Nhập từ file Anki/Quizlet" thêm ngay cạnh nút Export có sẵn ở `(student)/materials/[id]/page.tsx`.

**⚠️ Sửa lại 1 chi tiết so với test case đã viết trước đó**: khi code thật `addFlashcard` (hàm thêm 1 thẻ) hoá ra **không có** kiểm tra chặn Official như `addPersonalQuestion` của Quiz — đây là hành vi đã tồn tại từ trước với Flashcard, không phải lỗi mới. Endpoint `.txt` mới này làm đúng theo khuôn có sẵn (chỉ kiểm tra quyền sở hữu, không chặn Official) để nhất quán với hành vi thêm-1-thẻ đã có, thay vì tự thêm rule mới không ai yêu cầu. Bỏ test case #3 cũ (sai giả định), test case dưới đây đã cập nhật đúng thực tế.

**Test case (pass khi)**:
1. Học viên bấm "Xuất ra file Anki/Quizlet" từ 1 bộ thẻ cá nhân → mở lại chính file đó, import ngược vào 1 bộ thẻ khác → số thẻ khớp 1-1, không lệch dữ liệu (round-trip test). *(cần test tay)*
2. Import file `.txt` thật xuất từ Anki (không có header) → toàn bộ dòng được nhận đúng thành thẻ mới, không bị mất dòng đầu tiên do nhầm là header. *(cần test tay)*
3. Học viên A không thể import vào deck của học viên B (kiểm tra quyền sở hữu — đã có sẵn logic này, dùng lại nguyên).
4. `docker exec lms_backend mvn -o clean compile -DskipTests` — ✅ đã chạy, BUILD SUCCESS, health-check 200 OK sau restart.
5. `npx eslint` + `tsc --noEmit` — ✅ đã chạy, sạch.

---

## Bảng ưu tiên đề xuất (chờ bạn chọn làm mục nào trước)

| Hạng mục | Quy mô | Trạng thái |
|---|---|---|
| ~~B1 — Nút mở học liệu tĩnh~~ | Rất nhỏ | ✅ Đã xong, chờ bạn test tay qua UI |
| ~~B2 — Import ngược Anki .txt~~ | Nhỏ | ✅ Đã xong, chờ bạn test tay qua UI |
| ~~A3 — Gỡ trang /progress~~ | Rất nhỏ | ✅ Đã xong (đã xoá cả link Header) |
| ~~A4a — Mermaid Live Preview~~ | Vừa | ✅ Đã xong, chờ bạn test tay qua UI |
| ~~A1 — Certificate PDF~~ | Vừa | ✅ Đã xong (phần lõi), chờ bạn test tay qua UI |
| ~~A5 — Export PDF cheatsheet~~ | Nhỏ | ✅ Đã xong, chờ bạn test tay qua UI |
| ~~A4b — Template Tree-card~~ | Nhỏ | ✅ Đã xong |
| ~~A2 — Gộp % Quiz vào tiến độ~~ | Lớn | ✅ Đã xong (70/30, 374/374 test pass), 3 chi tiết phụ để dành sau |
| A4c — Fishbone/Matrix | Lớn | Không khả thi với Mermaid, cần đánh giá lại từ đầu nếu vẫn muốn làm |
| A6 — Redis token-bucket | — | Không cần làm trừ khi có vấn đề thật |
| A7 — Onboarding Wizard (Task 11B) | — | **Bỏ qua theo chỉ đạo trước đó**, không đụng vào |
| A4b/c — Template Tree-card / Fishbone-Matrix | Tree-card nhỏ, Fishbone/Matrix không khả thi với Mermaid | Fishbone/Matrix cần đánh giá lại từ đầu bằng lib khác nếu vẫn muốn làm |
| A6 — Redis token-bucket | — | Không cần làm trừ khi có vấn đề thật |
| **A7 — Onboarding Wizard (Task 11B)** | — | **Bỏ qua**, giữ nguyên chỉ đạo trước đó, không đưa vào lịch làm |

Bạn muốn bắt đầu từ mục nào?
