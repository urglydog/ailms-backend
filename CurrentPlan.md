# 📋 Phân Tích UpComming_Plan & Current Plan (v2)

> Phân tích dựa trên hiện trạng code thực tế + feedback chi tiết từ người dùng.  
> Cập nhật lần cuối: 14/09/2026  
> *(Phần thay đổi mô hình YouTube đã được tách riêng thành văn bản Đề Xuất trình ban quản trị).*

---

## TASK 1: Tiến Độ Học Tập (Progress Tracking)

**Hiện trạng:** "chưa thật sự thấy effect để test"

**Đề xuất:**
- Kiểm tra endpoint progress đã trả đúng data chưa
- Nếu chưa có giao diện → build trang `/my-progress` với biểu đồ tiến độ
- **Ưu tiên:** THẤP — để sau khi hoàn thiện các task cốt lõi

---

## TASK 2: CRUD Học Liệu Cá Nhân (6 sub-task)

### 2A. Flashcard — Kho Cá Nhân (Browse tab)

| Tính năng | Hiện trạng | Cần làm |
|---|---|---|
| Edit card | ✅ `PATCH /api/v1/flashcards/{id}` | Verify instant update |
| Thêm card | ✅ `POST /api/v1/flashcards/deck/{generationId}` | OK |
| **Xóa card** | ❌ Chưa có | BE: `DELETE /api/v1/flashcards/{id}` + FE: nút xóa |
| **Export Anki/Quizlet** | ❌ | FE-only: xuất `.txt` tab-separated (Anki/Quizlet đều import được) |

### 2B. Flashcard — Kho Official

| Tính năng | Cần làm |
|---|---|
| **SRS cho Official** | Entity `FlashcardReview` đã sẵn sàng. FE: dùng FlashcardStudyMode nhưng ẩn edit/add/delete |
| **Export** | Cùng logic export như 2A |

### 2C. Mindmap — Kho Cá Nhân
- Xoay 4 hướng, tải SVG, Copy code: ✅ đã có
- **Tooltip nút Copy:** hover → "Copy mã Mermaid (paste vào nền tảng hỗ trợ Mermaid.js)"
- **Xóa mindmap:** đã có `useDeleteMaterial` hook → thêm nút xóa

### 2D. Mindmap — Kho Official + Tab Xem Tĩnh Instructor
- `CourseMaterialsManager.tsx` tab VIEW: thêm `readOnly={true}` (1 dòng code)
- Student Official: verify `readOnly` sau build mới

### 2E. Trắc Nghiệm — Kho Cá Nhân
- Xóa/sửa câu hỏi cho student: cần endpoint mới với ownership check

### 2F. Phía Giảng Viên

| Vấn đề | Đề xuất |
|---|---|
| Ngôn ngữ mặc định trống | FE set default = `'vi'` hoặc pick đầu tiên từ `availableLanguages` |
| Không thoát được màn hình tạo | Thêm nút "✕ Đóng" + fix breadcrumb navigation |
| Thêm câu hỏi quiz mới | BE: `POST /instructor/quizzes/{quizId}/questions` |
| Flashcard CRUD giảng viên | Thêm/sửa/xóa card với instructor ownership check |
| **Tạo học liệu thủ công (Mới)** | Thêm nút "Tạo Thủ Công (Không dùng AI)" để khởi tạo Quiz/Flashcard/Mindmap trống hoàn toàn, cho phép giảng viên tự gõ 100% thay vì bắt buộc dùng AI. Giao diện tái sử dụng form Edit hiện tại. |
| **Checklist duyệt khóa học tự động** | Trước khi chuyển khóa học sang `PENDING_REVIEW`, FE/BE tự động check: tối thiểu X phút video/bài học, có thumbnail, và có ít nhất 1 bài tập (tạo bằng AI hoặc thủ công). Chặn gửi duyệt ngay tại client nếu chưa đạt. |
| **Trạng thái `NEEDS_REVISION` & Re-submit** | Bổ sung trạng thái `NEEDS_REVISION` kèm Feedback Note từ Admin. Giảng viên sửa xong sẽ có nút "Gửi duyệt lại" (Re-submit) để về lại `PENDING_REVIEW`. Khi khóa học ở trạng thái `PENDING_REVIEW` hoặc `APPROVED`, khóa các thông tin nhạy cảm (như thay đổi toàn bộ video) để tránh "treo đầu dê bán thịt chó". |

### 2G. Cấu Trúc Khóa Học (Curriculum Structure) - Chuẩn Udemy
- Tổ chức học liệu theo mô hình hình cây: **Chương (Section) $\rightarrow$ Bài học (Lecture) $\rightarrow$ Tài nguyên đính kèm (Resources)**.
- Cho phép đính kèm trực tiếp (pin/attach) Quiz, Flashcard, Mindmap từ Kho Official vào từng Lecture cụ thể, xuất hiện ở tab Resources bên cạnh video bài giảng.
- **Phân định rõ 2 loại Quiz:**
  - **Lecture Quiz (Quick Check):** Gắn trong tab Resources cuối bài học. Chỉ từ 1-3 câu, KHÔNG cần Camera, KHÔNG cần đếm ngược, làm xong biết đáp án ngay để củng cố kiến thức.
  - **Official Exam / Test:** Áp dụng toàn bộ luồng kiểm tra nghiêm ngặt ở Task 3 (Camera, Timer, ma trận, 2 chốt nộp bài).

---

## TASK 3: Tối Ưu Luồng Làm Bài Quiz Official

### 3A. Layout Sidebar (đã sửa, cần verify)
Thứ tự: Camera → Đếm ngược → Ma trận câu hỏi → Nút Nộp bài

### 3B. Ma Trận 2 Chiều — Cải Tiến
Ô vuông chia 70% trên (số câu) + 30% dưới (✓ xanh đã làm / ✗ đỏ chưa làm)

### 3C. Ma Trận Trong Lịch Sử
Thêm grid navigation vào `history/page.tsx` để nhảy nhanh đến câu bất kỳ

### 3D. Quy Trình Nộp Bài 2 Chốt Chặn ⭐

**Chốt 1 — Nhấn "Nộp bài":**
- Kiểm tra số câu chưa trả lời.
- Nếu CÓ câu chưa làm → hiện cảnh báo: **"Bạn còn [X] câu chưa hoàn thành, bạn có chắc chắn muốn nộp?"** thay vì chặn cứng (`disabled`).
- Ma trận 2 chiều ở sidebar vẫn phát huy tác dụng: user nhìn vào ma trận thấy câu nào đỏ (chưa làm) → nhấn vào → nhảy thẳng đến câu đó.
- Vẫn **cho phép nộp** nếu user xác nhận muốn bỏ qua các câu trống (câu trống = 0 điểm).

**Chốt 2 — Màn hình Review Confirm:**
- Render danh sách TỪNG CÂU, mỗi dòng hiện chính xác chuỗi:
  - `Câu 1: ✅ Đã ghi nhận câu trả lời`
  - `Câu 2: ❌ Chưa ghi nhận câu trả lời` (đối với câu bị bỏ trống)
- Phải scroll xuống cuối mới thấy 2 nút:
  - **[Quay lại bài thi]** — đóng, quay lại làm bài
  - **[Xác nhận nộp bài]** — gọi API submit thật

### 3E. Câu Hỏi Chọn Nhiều Đáp Án (Multi-Select) ⭐

**Schema thay đổi:**
- `QuizQuestion` thêm: `questionType` (`SINGLE_CHOICE` / `MULTI_CHOICE`)
- `QuizAnswer`: hỗ trợ lưu nhiều `selectedOption` (bảng trung gian hoặc JSON array)
- Migration SQL cần thiết

**Quy tắc tính điểm Multi-Choice:**
- Phải chọn **ĐÚNG HẾT** tất cả đáp án được set sẵn → mới được điểm câu đó
- Chọn thiếu 1 đáp án đúng → **0 điểm**
- Chọn dư 1 đáp án sai → **0 điểm**

**Công thức tính điểm chuyên nghiệp:**
- Single-choice: 1.0 điểm gốc/câu
- Multi-choice: 1.5 điểm gốc/câu (khó hơn → trọng số cao hơn)
- Tổng điểm gốc = (số câu single × 1.0) + (số câu multi × 1.5)
- Điểm thang 10 = (điểm đạt được / Tổng điểm gốc) × 10

**FE thay đổi:**
- `radio` → `checkbox` khi `questionType === 'MULTI_CHOICE'`
- `answers` state: `Record<id, optionId>` → `Record<id, optionId | optionId[]>`
- Instructor: form thêm câu hỏi mới có toggle "Cho chọn nhiều đáp án" + tick nhiều ô đúng

---

## TASK 10: Quản Lý Thiết Bị / Phiên Đăng Nhập

### 10C. Quản Lý Đa Thiết Bị — Mô Hình Google ⭐

**Nguyên tắc:**
- **Cho phép đăng nhập trên nhiều thiết bị** cùng lúc.
- **KHÔNG cưỡng chế logout** thiết bị cũ.

**Luồng Phát Hiện & Chống Share Tài Khoản:**
1. **Cảnh báo thiết bị mới:** Hệ thống gửi **email cảnh báo** đến email user khi phát hiện thiết bị mới.
2. **Quản lý thiết bị:** User có thể vào **Trang quản lý thiết bị** (trong Settings cá nhân) để xem thiết bị đang kết nối và nhấn **"Ban thiết bị"** nếu cần.
3. **Chặn phát video đồng thời (Concurrent Stream Restriction):** 
   - **Cơ chế kỹ thuật:** FE video player định kỳ 15-30 giây gửi một request nhẹ (Heartbeat ping qua API `POST /api/v1/sessions/heartbeat` kèm `session_id` và `course_id`). Backend lưu heartbeat vào Redis kèm TTL. Nếu phát hiện một `session_id` khác cùng user đang phát video, Backend trả về cờ yêu cầu dừng stream ở thiết bị (session) cũ kèm thông báo *"Tài khoản của bạn đang phát video trên thiết bị khác"*.

---

## TASK 11: Quản Lý User

### 11A. Admin ⭐

**Bỏ cơ chế khóa AI thủ công → Thay bằng auto rate-limit:**
1. Thiết lập limit call AI/ngày cho mỗi user.
2. Quá ngưỡng 80% $\rightarrow$ cảnh báo, 100% $\rightarrow$ chặn auto. Cố tình spam vượt ngưỡng $\rightarrow$ tạm khóa AI 24h.

**Dashboard Admin (Data-driven):**
- Xây dựng dashboard dạng biểu đồ trực quan (như chứng khoán).
- Sử dụng **Recharts** (dạng Sparkline, Trend indicator, Bar chart).
- KHÔNG hiển thị giao diện theo kiểu nút bấm và chữ rời rạc "loãng" màn hình.

### 11B. Giảng Viên (Instructor Onboarding)
- **Profile Khởi tạo Giảng viên:** Để lọc spam tốt hơn việc chỉ dựa vào 2FA, yêu cầu user điền một Profile ngắn gọn (Headline/Tiêu đề chuyên môn, Bio, Thông tin thanh toán) trước khi được vào giao diện tạo khóa học lần đầu.

### 11C. Student/User Cá Nhân
- Bổ sung chức năng gửi yêu cầu Khôi phục Mật khẩu qua Email.
- Thêm bảo mật 2 bước bằng ứng dụng Authenticator (2FA).

---

## 🎯 Đề Xuất Thứ Tự Ưu Tiên Chuẩn

| # | Task | Lý do | Ước lượng |
|---|---|---|---|
| 1 | **2F — Fix UX giảng viên** (Ngôn ngữ mặc định, thêm câu hỏi, trạng thái duyệt) | Bug UX trực tiếp & Core flow | 4-5h |
| 2 | **3D — 2 chốt chặn nộp bài** | Academic integrity | 3-4h |
| 3 | **2A+2B — Flashcard xóa + Export + SRS Official** | Hoàn thiện trải nghiệm học | 4-5h |
| 4 | **3B+3C — Ma trận 2 chiều + History** | UX bài thi nhiều câu | 3-4h |
| 5 | **2D — Mindmap readOnly** | 1 dòng code | 15 phút |
| 6 | **2G — Curriculum Structure (Mới)** | Task kiến trúc nặng, phân tách 2 loại Quiz | 8-10h |
| 7 | **3E — Multi-choice + tính điểm mới** | Schema lớn, cần plan kỹ | 8-10h |
| 8 | **11B — Instructor Onboarding (Mới)** | Lọc spam, hồ sơ chuyên nghiệp | 3-4h |
| 9 | **11A — Auto rate-limit AI + Dashboard** | Bỏ thủ công, tự động hóa | 8-10h |
| 10| **11C — 2FA + Quên mật khẩu** | Nền tảng bảo mật | 6-8h |
| 11| **10C — Concurrent Stream (Heartbeat Redis)** | Infrastructure nâng cao | 8-10h |
