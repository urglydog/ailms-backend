# Kế Hoạch Triển Khai Nâng Cao (Advanced Implementation Plan)

Dựa trên yêu cầu đánh giá kiến trúc và kiểm soát chất lượng mã nguồn (Code Quality Audit), kế hoạch triển khai đã được nâng cấp với các tiêu chuẩn khắt khe nhằm loại bỏ rủi ro hồi quy (regression risks), các lỗ hổng kiến trúc (architectural blind spots) và đảm bảo tính ổn định ở cấp độ Production.

Dưới đây là Kế hoạch Triển khai chi tiết cho 5 vấn đề, **chưa thực hiện code**:

---

## 1. Lỗi Student View & Badge Counter ("Kho Học Liệu Official")

**Verified Root Cause Strategy:**
- Hệ thống đếm thông báo (badge) hiện tại nhận số lượng raw từ WebSocket/API `useNotification()` mà không đối chiếu với bối cảnh (context) hiện tại (ví dụ: `courseId` hoặc `lessonId`).
- Phân tích payload: Cần kiểm tra xem payload trả về của Notification có chứa metadata như `lessonId` hay không để client lọc chính xác.

**Edge Cases & Guardrails:**
- **Lỗi đồng bộ state:** Nếu notification được đánh dấu là "đã đọc" (read) trên một tab, tab khác phải cập nhật realtime. 
- Học liệu bị ẩn/chưa tới giờ mở (scheduled): Không được tính vào badge cho sinh viên dù đã có notification.

**Step-by-Step Technical Execution Steps:**
1. Cập nhật type/payload của Notification Backend để đảm bảo trả về `courseId` và `lessonId` (nếu liên kết với lesson).
2. Refactor `MaterialManager.tsx`: Lọc mảng `notifications` không chỉ dựa trên `type === 'NEW_OFFICIAL_MATERIAL'` mà còn phải khớp với `courseId` và `lessonId` của View hiện tại.
3. Đối chiếu danh sách `filteredOfficialMaterials` đang hiển thị để loại trừ các học liệu đang ở trạng thái khóa (chưa mở).

---

## 2. Lỗi Thuật toán SRS (Spaced Repetition System) ở Flashcards

**Verified Root Cause Strategy (Lịch sử Git):**
- **KHÔNG viết lại thuật toán.** Do SRS trước đây đã hoạt động bình thường, đây chắc chắn là một lỗi hồi quy (regression). 
- Phải thực hiện kiểm tra `git log -S "nextReviewAt"` hoặc `git blame` trên service xử lý SRS của Backend và các hàm tiện ích xử lý thời gian (Date utils). Mục tiêu là tìm ra commit đã làm sai lệch múi giờ (UTC vs Local) hoặc đơn vị thời gian (nhầm lẫn giữa seconds và milliseconds).

**Edge Cases & Guardrails:**
- Dữ liệu lịch sử: Các Flashcard cũ có thể đang lưu time-stamp sai định dạng do lỗi hồi quy trước đó. Cần có cơ chế fallback hoặc migration.
- Client Timezone Shift: So sánh `nextReviewAt` <= `now()` ở phía Client dễ bị sai nếu trình duyệt ở múi giờ khác. Việc xác định `isDue` phải do Backend tính toán và khóa cứng (lock) kết quả, Client chỉ hiển thị.

**Step-by-Step Technical Execution Steps:**
1. Chạy lệnh Git để truy vết sự thay đổi của logic cộng ngày (interval) và việc parse ngày tháng.
2. Sửa lỗi tại điểm hồi quy: Đảm bảo thời gian tính toán tiếp theo (`nextReviewAt`) tuân thủ chuẩn ISO-8601 (UTC time).
3. Đảm bảo cờ `isDue` trả về từ Backend phản ánh chính xác trạng thái dựa trên thời gian thực tế của server, tránh phụ thuộc vào múi giờ của client ở component `FlashcardViewer`.

---

## 3. Lỗi Trạng thái Official & Logic Drag-and-Drop (Dependency Rule)

**Verified Root Cause Strategy:**
- Logic chuyển trạng thái Official/Draft không thể là một thao tác toggle tuyến tính 1-1. Học liệu và Lesson là mối quan hệ Nhiều-Nhiều (Many-to-Many).
- Khi gỡ học liệu khỏi một Lesson, không được tự động chuyển về "Unofficial" trừ khi đó là Lesson cuối cùng chứa nó.

**Edge Cases & Guardrails:**
- Batch Drag-and-Drop: Phải xử lý trường hợp kéo thả nhiều item cùng lúc nếu hệ thống hỗ trợ.
- Xóa nhầm do legacy UI: Các nút Delete/Official cũ có thể gây conflict event. Cần gỡ bỏ hoàn toàn.

**Step-by-Step Technical Execution Steps:**
1. Xóa bỏ hoàn toàn các nút Standalone "Official/Delete" ở Header UI (double-click legacy).
2. Tích hợp event `onDrop` tại khu vực Material Workspace: Khi kéo thả vào Lesson, hiển thị Modal xác nhận đánh dấu Official.
3. Sửa backend logic (unassign material): Khi gỡ học liệu khỏi Lesson, đếm số liên kết còn lại (`remainingLessonsCount`). **Chỉ** chuyển trạng thái sang `Draft` (Unofficial) khi số liên kết bằng 0.

---

## 4. Lỗi Context Menu, Phím tắt (Keyboard Scope) & Giao diện Tree UI

**Verified Root Cause Strategy:**
- Phím tắt toàn cục (Global Keyboard Listeners) như `Delete`, `Ctrl+C`, `Ctrl+V` gây ra rủi ro side-effect khổng lồ (ví dụ: người dùng bấm Delete khi đang gõ text tìm kiếm khiến học liệu bị xóa nhầm).
- Input nhập ID tĩnh (Raw ID prompt) không bảo mật và gây lỗi UX nặng.

**Edge Cases & Guardrails:**
- Scope Isolation: Lắng nghe phím tắt phải được khóa chặt (guarded) bởi Active Element (ví dụ: bỏ qua nếu focus đang nằm trong `input`, `textarea`, hoặc `contenteditable`).
- Validation Tree: Không cho phép move thư mục cha vào trong thư mục con của chính nó (ngăn chặn Circular Dependency).

**Step-by-Step Technical Execution Steps:**
1. **Scope Phím Tắt:** Trong `useEffect` xử lý `keydown` của thư mục, thêm `Guard Clause`: kiểm tra `document.activeElement.tagName`. Nếu thuộc các thẻ nhập liệu, `return` lập tức. Khóa phím tắt chỉ hoạt động trên các Material Card đang được select/focus.
2. **Refactor UI Chuyển Thư Mục:** Thay thế `window.prompt` bằng một UI Modal Portal.
3. Tích hợp một component **Searchable Tree-Select Dropdown** bên trong Modal để render danh sách thư mục trực quan. Xử lý logic lọc đệ quy để chặn việc di chuyển thư mục vào chính nó.

---

## 5. Tối ưu Hiệu suất Layout (Grid vs List Optimization)

**Verified Root Cause Strategy:**
- Bố cục Grid hiện tại (Unstructured Grid) không phù hợp cho số lượng lớn học liệu vì thiếu cột thông tin ngữ cảnh. Cần đổi sang List View.
- Rủi ro hiệu suất: Render hàng trăm hàng List có thể làm lag UI.

**Edge Cases & Guardrails:**
- Hỗ trợ màn hình nhỏ: List view cần phải cuộn ngang hoặc tự ẩn bớt các cột ít quan trọng (responsive) trên Mobile/Tablet.
- Performance: Cần Virtualization hoặc Pagination.

**Step-by-Step Technical Execution Steps:**
1. Chuyển đổi mặc định UI sang Dạng Danh sách (List View) với cấu trúc phân tầng rõ ràng (Tên, Loại, Trạng thái, Cập nhật). Thêm View Toggle (Grid/List).
2. Bổ sung các Filter/Sort bar chuyên dụng.
3. Áp dụng Virtualized List (thông qua `@tanstack/react-virtual` hoặc lazy-rendering pagination) để giới hạn số lượng DOM nodes khi Instructor load thư mục Workspace chứa trên 50+ học liệu.

---

> [!IMPORTANT]
> **User Review Required**
> Kế hoạch đã được đánh giá lại và bao phủ toàn bộ các tiêu chuẩn khắt khe về kiến trúc (Architectural constraints) cũng như quản lý lỗi hồi quy. Xin vui lòng phê duyệt (Approve) Kế hoạch Cập nhật này để tôi tiến hành bước tiếp theo: **Truy vết lỗi bằng Git và bắt đầu triển khai mã nguồn**.
