# Kế Hoạch Các Hạng Mục Còn Lại (Cập nhật 22/09/2026)

Nội dung cũ của file này (sửa lỗi Material Workspace, redesign UI Login/Kho học liệu...) đã hoàn tất toàn bộ trong các đợt việc trước — xoá bỏ để tránh nhầm với việc đang làm. File này thay bằng danh sách các hạng mục **còn lại thật sự**, gồm 2 nhóm:

- Nhóm A: các hạng mục đã cố tình dời lại khi làm Task 1/4/11 (nguồn gốc từ `CurrentPlan.md` cũ — đã xoá file đó, toàn bộ nội dung còn giá trị được gộp vào đây) vì quy mô lớn hơn "làm nhanh dứt điểm".
- Nhóm B: 2 hạng mục mới bạn vừa nêu (Học liệu chung toàn khoá + Import/Export tương thích Anki/Quizlet) — khảo sát code cho thấy **một phần đã có sẵn**, xem chi tiết bên dưới.

Mỗi mục có: hiện trạng thật (đã kiểm tra code, không phỏng đoán), phân tích khả thi, phương pháp đề xuất, và test case để coi là "xong".

---

## Nhóm A — Hạng mục đã dời lại từ Task 1/4/11

### A1. Certificate PDF khi học viên đạt 100% tiến độ

**Hiện trạng**: chưa có gì. Không có thư viện sinh PDF nào trong `pom.xml` (không OpenPDF/iText/PDFBox), không có entity `Certificate`, `Enrollment` chỉ có `completedAt`.

**Phân tích khả thi**: khả thi, rủi ro thấp nếu chọn đúng cách làm — rủi ro nằm ở việc có thêm bảng mới hay không, không nằm ở việc sinh PDF (Java có nhiều lib PDF ổn định).

**Đề xuất**:
- Không tạo bảng `Certificate` riêng (tránh 1 migration + 1 entity chỉ để lưu thứ suy ra được). Thêm 1 cột `certificate_code` (UUID, nullable) vào `enrollments` — sinh ra lúc `completedAt` được set lần đầu.
- Thêm 1 dependency PDF (đề xuất **OpenPDF** — license MIT, nhẹ, đủ dùng cho 1 trang chứng chỉ, cùng nhóm rủi ro thấp như OpenCSV vừa thêm).
- Endpoint `GET /api/v1/enrollments/{courseId}/certificate` — kiểm tra `completedAt != null`, sinh PDF **on-the-fly** (không lưu file lên B2), trả về `application/pdf` trực tiếp. Không cần thêm storage, không cần job nền.
- FE: nút "Tải chứng chỉ" chỉ hiện khi `progressPct >= 100` (đã có sẵn field này ở `CourseCard`/trang tiến độ).
- Bổ sung từ `CurrentPlan.md` cũ: khi `progressPct` vừa chạm 100% trong lúc học viên đang ở màn hình học, hiện 1 popup chúc mừng nhẹ (kèm icon cúp) mời bấm tải chứng chỉ ngay, thay vì phải tự tìm nút — tận dụng đúng chỗ progress bar vừa thêm ở `app/(learn)/layout.tsx` (Task 1 đợt trước) để biết khi nào % vừa đổi sang 100.

**Test case (pass khi)**:
1. Enrollment có `progressPct < 100` → gọi endpoint trả 422, không sinh PDF.
2. Enrollment vừa chạm 100% → `certificate_code` được set đúng 1 lần, gọi lại nhiều lần không đổi mã.
3. File PDF tải về mở được, có tên học viên, tên khoá học, ngày hoàn thành, mã xác thực.
4. `mvn compile` sạch, health-check sau khi thêm cột migration mới.

---

### A2. Gộp điểm Quiz / học liệu tĩnh vào công thức % tiến độ

**Hiện trạng**: `progressPct` tính thuần theo % bài học có video đã xem đủ ngưỡng (BR-PROGRESS-02). `quizScore` đã có trường riêng trên `EnrolledCourse` nhưng **không** cộng vào `progressPct`.

**Phân tích khả thi**: đây là **thay đổi business rule**, không phải bug fix hay bổ sung UI — đổi công thức sẽ làm % của toàn bộ enrollment cũ thay đổi giá trị ngay lập tức (không cần user làm gì thêm), ảnh hưởng ngược tới dữ liệu lịch sử. Theo đúng CLAUDE.md mục 1 ("Thay đổi logic lớn — BẮT BUỘC confirm"), mục này **cần chốt rõ business rule mới trước khi code**, không nằm trong nhóm "làm nhanh".

**Câu hỏi cần bạn chốt trước khi làm** (chưa code gì ở đây):
- Trọng số: video chiếm bao nhiêu %, Quiz chiếm bao nhiêu %? (vd 70/30?)
- Học liệu tĩnh (Task nhóm B bên dưới) có tính vào % không, hay chỉ để tham khảo?
- Enrollment cũ đã có `completedAt` theo công thức cũ — có cần tính toán lại hàng loạt (batch job) hay giữ nguyên "đã hoàn thành thì thôi"?

**3 chi tiết bổ sung từ `CurrentPlan.md` cũ (chưa được liệt kê trước đó), gộp vào đây vì cùng thuộc "đổi công thức tính %":**
1. **Tick/bỏ tick thủ công**: học viên tự bấm đánh dấu hoàn thành 1 bài học bằng tay (không chỉ tự động qua % xem video) — cần 1 cột trạng thái override trên `LessonProgress` (vd `isManuallyCompleted`) tách biệt khỏi phần trăm xem thật, để không phá vỡ BR-PROGRESS-01 (không tính tua nhanh) khi tính lượt xem thật.
2. **Học liệu tĩnh tự tick khi xem/tải**: nếu học liệu tĩnh (Nhóm B1) được tính vào %, cần bắt sự kiện "đã tải/đã xem" ở `CourseResourcesTab`/`StudentResourceController` — hiện endpoint tải file chỉ trả `fileUrl`, chưa ghi nhận sự kiện này.
3. **Cân đối lại mẫu số khi giảng viên xoá 1 Quiz/mục bắt buộc**: hệ thống đã dùng soft-delete (`isDeleted`) cho `MaterialGeneration`/`Quiz` nên về nguyên tắc mẫu số (tổng số mục bắt buộc) đã tự động loại các mục đã xoá nếu công thức tính % luôn lọc theo `isDeleted = false` tại thời điểm tính — **cần viết test riêng xác nhận đúng hành vi này** trước khi coi là an toàn, vì đây là đúng rủi ro mà `CurrentPlan.md` cũ cảnh báo (giảng viên xoá quiz làm tụt tiến độ học viên đã nộp bài).

**Test case (khi đã chốt công thức)**:
- Phần công thức mới: viết sau khi có business rule cụ thể (trọng số) — chưa thể viết trước.
- Phần tick thủ công: học viên bấm tick tay 1 bài chưa xem video → `progressPct` tăng đúng 1 đơn vị/tổng số bài; bấm bỏ tick → giảm lại đúng.
- Phần cân đối mẫu số: tạo 1 enrollment đã hoàn thành X/10 mục, giảng viên xoá 1 Quiz (soft-delete) → tổng mẫu số phải còn 9, % phải tăng lên tương ứng (không được giữ nguyên X/10 cũ gây sai số), học viên KHÔNG bị tụt % vì mục đã xoá không còn tính là "chưa hoàn thành".

---

### A3. ✅ ĐÃ XONG — Gỡ trang `/progress` cũ

Phát hiện lúc làm: vẫn còn 1 link thật trỏ tới trang này ở dropdown Header ("Báo cáo tiến độ") — không mồ côi như dự đoán ban đầu. Đã hỏi và bạn chọn xoá cả hai. Đã xoá route `app/(student)/progress/page.tsx` và mục "Báo cáo tiến độ" khỏi `Header.tsx`. Verify: `eslint` + `tsc --noEmit` sạch, `GET /progress` trả 404 đúng như kỳ vọng, trang chủ vẫn 200.

---

### A4. Mermaid Live Preview + Template nâng cao (Fishbone / Tree-card / Matrix)

**Ghi chú từ bạn (giảng viên, đã tự thử trước đó)**: đã cố hiện thực fishbone, treecard, matrix nhưng hầu như đều lỗi do giới hạn thư viện; nguyên nhân một phần do dùng auto-format ở tab hiển thị tĩnh nên không mô phỏng đúng 100% mọi thao tác realtime.

**Phân tích khả thi (đã kiểm tra code + thư viện đang dùng — `mermaid@11.4.1`)**:
- `MindmapEditor.tsx` hiện tại là editor kéo-thả (React Flow) xuất ra cú pháp Mermaid `mindmap`/`flowchart`, `initialTemplate` chỉ là hướng layout (`LR`/`TD`), **không phải** một bộ "loại biểu đồ" khác nhau.
- Mermaid.js (mọi bản, kể cả 11.4.1) **có hỗ trợ native**: `mindmap`, `flowchart`. **Không có** loại diagram chuẩn nào tên "fishbone/Ishikawa" hay "matrix diagram" trong core Mermaid — đây không phải giới hạn cấu hình, mà là thư viện không định nghĩa cú pháp cho 2 loại này. Muốn có thật sự cần tự vẽ SVG/D3 riêng, không phải "sửa thêm" trên Mermaid.
- → Xác nhận đúng những gì bạn đã gặp: đây là **giới hạn thật của thư viện**, không phải lỗi cách làm của bạn.

**Đề xuất chia làm 3 phần riêng, độ khả thi khác nhau**:

| Phần | Khả thi | Đề xuất |
|---|---|---|
| a) Live Preview split-screen (gõ code Mermaid trái, xem hình phải, realtime) | **Khả thi, rủi ro thấp** | Thêm 1 tab "Soạn Code" cạnh "Chỉnh Sửa"/"Mã Mermaid" đã có. Textarea bên trái, debounce ~400ms gọi `mermaid.render()` (đã có sẵn trong bundle, không cần cài thêm gì) render bên phải. Lỗi cú pháp hiện thông báo đỏ ngay tại chỗ thay vì phải bấm Lưu mới biết sai. |
| b) Template "Tree-card" (sơ đồ cây phân nhánh) | **Khả thi** | Thực chất trùng với diagram `mindmap` Mermaid đã hỗ trợ — không cần renderer mới, chỉ cần 1 nút "Chèn khung Tree-card mẫu" sinh sẵn code `mindmap` mẫu để sửa tiếp. |
| c) Fishbone / Matrix | **Không khả thi trong khuôn khổ Mermaid** | Muốn có thật sự phải đổi hẳn cơ chế vẽ (SVG tự viết hoặc lib khác như Markmap/D3) — đây là 1 hạng mục lớn, tách riêng hoàn toàn, cần đánh giá lại từ đầu, không nằm trong phạm vi "làm nhanh dứt điểm" của đợt này. |

**Test case cho phần (a) — phần duy nhất đề xuất làm trong đợt tới**:
1. Gõ code Mermaid hợp lệ → hình bên phải cập nhật trong < 1s, không cần bấm nút nào.
2. Gõ code sai cú pháp → hiện thông báo lỗi đỏ tại chỗ, KHÔNG crash trắng màn hình, KHÔNG giữ lại hình cũ gây hiểu nhầm.
3. Bấm "Lưu" từ tab này → dùng đúng `onSave` đã có, không tạo luồng lưu riêng.
4. `npx eslint` + `tsc --noEmit` sạch.

---

### A5. Export PDF cheatsheet / đề trắng (kèm đáp án trang cuối)

**Hiện trạng**: chưa có.

**Đề xuất**: nên làm **sau** A1 vì dùng chung hạ tầng PDF (cùng 1 dependency OpenPDF, tránh thêm 1 lib PDF thứ 2). Sinh PDF từ danh sách `QuizQuestion` hiện có (không cần dữ liệu mới) — layout: câu hỏi + 4 lựa chọn, đáp án đúng in ở trang cuối riêng.

**Test case**: PDF sinh ra đúng số câu, đáp án ở cuối khớp với đáp án đúng trong DB, không lộ đáp án ở phần đề.

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
| **A4a — Mermaid Live Preview** | Vừa | Chưa làm — khả thi rõ ràng, không đụng backend |
| **A1 — Certificate PDF** | Vừa | Chưa làm — cần thêm 1 dependency + 1 cột migration |
| **A5 — Export PDF cheatsheet** | Nhỏ (nếu làm sau A1) | Chưa làm — phụ thuộc lib PDF của A1 |
| **A2 — Gộp % Quiz vào tiến độ** | Lớn, cần chốt business rule trước | Chưa làm — đổi logic ảnh hưởng dữ liệu cũ, KHÔNG nên làm vội |
| A4b/c — Template Tree-card / Fishbone-Matrix | Tree-card nhỏ, Fishbone/Matrix không khả thi với Mermaid | Fishbone/Matrix cần đánh giá lại từ đầu bằng lib khác nếu vẫn muốn làm |
| A6 — Redis token-bucket | — | Không cần làm trừ khi có vấn đề thật |
| **A7 — Onboarding Wizard (Task 11B)** | — | **Bỏ qua**, giữ nguyên chỉ đạo trước đó, không đưa vào lịch làm |

Bạn muốn bắt đầu từ mục nào?
