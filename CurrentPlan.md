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
- **Xóa mindmap:** ✅ Đã hoàn thành (Có modal xác nhận)

### 2D. Mindmap — Kho Official + Tab Xem Tĩnh Instructor
- `CourseMaterialsManager.tsx` tab VIEW: thêm `readOnly={true}` (1 dòng code)
- Student Official: verify `readOnly` sau build mới

### 2E. Trắc Nghiệm — Kho Cá Nhân
- Xóa/sửa câu hỏi cho student: cần endpoint mới với ownership check

### 2F. Phía Giảng Viên [PASSED]
- Đã hoàn thành các UX cơ bản cho giảng viên (Ngôn ngữ, Tạo thủ công, Xóa học liệu, Chỉnh sửa Node Mindmap).

### 2G. Cấu Trúc Khóa Học (Curriculum Structure) - Chuẩn Udemy
- Tổ chức học liệu theo mô hình hình cây: **Chương (Section) $\rightarrow$ Bài học (Lecture) $\rightarrow$ Tài nguyên đính kèm (Resources)**.
- Cho phép đính kèm trực tiếp (pin/attach) Quiz, Flashcard, Mindmap từ Kho Official vào từng Lecture cụ thể, xuất hiện ở tab Resources bên cạnh video bài giảng.
- **Phân định rõ 2 loại Quiz:**
  - **Lecture Quiz (Quick Check):** Gắn trong tab Resources cuối bài học. Chỉ từ 1-3 câu, KHÔNG cần Camera, KHÔNG cần đếm ngược, làm xong biết đáp án ngay để củng cố kiến thức.
  - **Official Exam / Test:** Áp dụng toàn bộ luồng kiểm tra nghiêm ngặt ở Task 3 (Camera, Timer, ma trận, 2 chốt nộp bài).

---

## TASK 3: Tối Ưu Luồng Làm Bài Quiz Official (Theo chuẩn mô tả)

### 3A & 3B. Layout Sidebar, Phân Trang & Ma Trận 2 Chiều Cố Định
- **Layout Cố Định (Sticky):** Các thành phần bên cột phải (đồng hồ đếm ngược, camera giám sát, nút nộp bài, ma trận 2 chiều) **tuyệt đối không bị xê dịch** khi người dùng cuộn màn hình để làm các câu hỏi khác. 
- **Vị trí Ma Trận:** Ma trận 2 chiều phải nằm **BÊN DƯỚI** nút nộp bài, hoặc ở vị trí `absolute` góc phải phía dưới cùng (nơi còn trống).
- **Phân trang:** Hiển thị tối đa 5 câu hỏi/trang, sử dụng nút mũi tên `< >` để chuyển đổi qua lại giữa các trang.
- **Quy tắc Ma Trận:**
  - Ô vuông chia tỉ lệ: 70% phía trên hiển thị số thứ tự câu hỏi, 30% phía dưới hiển thị dấu tích nền xanh (đã làm) và dấu `x` nền đỏ (chưa làm).
  - Khi nhấn vào câu nào trên ma trận, màn hình sẽ tự động forward về đúng câu đó (bất kể đang ở trang nào).

### 3C. Tích hợp Ma Trận vào Lịch Sử Làm Bài
- Màn hình lịch sử / xem chi tiết bài làm hiện đang dồn toàn bộ đáp án vào một trang cuộn dọc rất dài (có thể lên tới hàng trăm câu).
- **Yêu cầu:** Bắt buộc bổ sung Ma Trận 2 chiều vào màn hình Lịch sử này để học viên có thể click vào ma trận và nhảy ngay đến câu hỏi cần xem, thay vì phải cuộn dọc liên tục.

### 3D. Quy Trình Nộp Bài 2 Chốt Chặn
- **Chốt 1 (Nút Nộp bài ngoài bài thi):** Nếu phát hiện có câu hỏi chưa làm, hệ thống KHÔNG cần đếm chính xác số câu, mà chỉ hiện thông báo nhắc nhở chung: *"Vui lòng hoàn thành toàn bộ câu hỏi trước khi nộp"* (Mục đích: Gọi nhớ người dùng tự nhìn vào ma trận để xem câu nào còn thiếu).
- **Chốt 2 (Màn hình Review Confirm):**
  - Hiển thị danh sách TỪNG CÂU HỎI dọc từ trên xuống dưới.
  - Mỗi câu hỏi chỉ hiển thị dòng chữ: `✅ Đã ghi nhận câu trả lời` HOẶC `❌ Chưa ghi nhận câu trả lời` (đối với câu bị bỏ trống).
  - KHÔNG sử dụng ma trận hay dạng rút gọn ở bước này.
  - Người dùng **bắt buộc phải cuộn xuống tận cùng** của danh sách này mới thấy được 2 nút: **[Xác nhận nộp bài]** và **[Quay lại bài thi]**.

### 3E. Câu Hỏi Chọn Nhiều Đáp Án (Multi-choice)
- **Giao diện:** Chuyển từ thẻ radio (chỉ chọn 1) sang dạng ô **Checkbox** để học viên có thể check chọn nhiều đáp án.
- **Nhắc nhở:** Tự động bổ sung ghi chú dòng chữ `(Chọn nhiều đáp án)` dưới tiêu đề của các câu hỏi thuộc loại này.
- **Logic Tính Điểm Mới:** Đảm bảo đúng chuẩn: Sinh viên phải chọn **ĐÚNG VÀ ĐỦ TẤT CẢ** các đáp án đúng được set sẵn mới được tính điểm câu đó. Chọn thiếu hoặc dư đáp án sai đều bị 0 điểm.

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
| 1 | **2F — Fix UX giảng viên** | Đã hoàn thành (PASSED) | 0h |
| 2 | **3D — 2 chốt chặn nộp bài** | Academic integrity | 3-4h |
| 3 | **2A+2B — Flashcard xóa + Export + SRS Official** | Hoàn thiện trải nghiệm học | 4-5h |
| 4 | **3B+3C — Ma trận 2 chiều + History** | UX bài thi nhiều câu | 3-4h |
| 5 | **2D — Mindmap readOnly** | Đã hoàn thành (PASSED) | 0h |
| 6 | **2G — Curriculum Structure (Mới)** | Task kiến trúc nặng, phân tách 2 loại Quiz | 8-10h |
| 7 | **3E — Multi-choice + tính điểm mới** | Schema lớn, cần plan kỹ | 8-10h |
| 8 | **11B — Instructor Onboarding (Mới)** | Lọc spam, hồ sơ chuyên nghiệp | 3-4h |
| 9 | **11A — Auto rate-limit AI + Dashboard** | Bỏ thủ công, tự động hóa | 8-10h |
| 10| **11C — 2FA + Quên mật khẩu** | Nền tảng bảo mật | 6-8h |
| 11| **10C — Concurrent Stream (Heartbeat Redis)** | Infrastructure nâng cao | 8-10h |
