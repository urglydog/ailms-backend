# KẾ HOẠCH TRIỂN KHAI CHI TIẾT (FINAL PHASE)

> **Mục tiêu:** Hoàn thiện dứt điểm toàn bộ tính năng cốt lõi của LMS trong 24h tới.
> **Tiêu chí (Best Practice):** Sử dụng thư viện mã nguồn mở miễn phí, giải pháp Native không phụ thuộc bên thứ ba (không tốn phí API), thuật toán tự build để duy trì vĩnh viễn.

---

## TASK 1: Hoàn thiện màn hình Tiến độ học tập (Progress Tracking)
**Mức độ:** Trung bình | **Phạm vi:** Frontend
**Trạng thái:** Chưa thấy thay đổi trên giao diện học viên, thanh % đang lỗi ở 0%.

**Logic triển khai chi tiết:**
1. **Sửa lỗi Redirect:** Tại component `ProgressChart`, cập nhật sự kiện `onClick`: Tìm kiếm `lesson_id` cuối cùng mà học viên đang xem.
2. **Sửa lỗi hiển thị UI (Icon Khóa):** Truyền prop `isEnrolled={true}` vào thẻ `CourseCard`.
3. **Bổ sung Accordion Tracking Chương:** Gọi API lấy danh sách Chapter kèm mảng Lesson, tính toán `%` tiến độ và render lại thanh Progress Bar thu nhỏ. Kiểm tra lại công thức tính toán và re-render để hiển thị đúng thay vì 0%.

---

## TASK 2: Cấp quyền CRUD Học liệu cá nhân cho Học viên
**Mức độ:** Dễ | **Phạm vi:** Frontend
**Trạng thái:** Mới chỉ đổi được tên học liệu, chưa custom được câu hỏi và flashcard.

**Logic triển khai chi tiết:**
1. Áp dụng Component quản lý và chỉnh sửa chi tiết (thêm, sửa, xóa câu hỏi/flashcard) từ phía Giảng viên sang Học viên.
2. Bọc điều kiện Role cho phù hợp.

---

## TASK 3: Tối ưu luồng làm bài Quiz Public (Official)
**Mức độ:** Dễ | **Phạm vi:** Frontend
**Trạng thái:** Done

---

## TASK 4: Tái cấu trúc toàn diện Mindmap Editor (Giống Xmind)
**Mức độ:** Rất Khó | **Phạm vi:** Frontend (React Flow + Thuật toán Layout + CSS Custom)
**Trạng thái:** Yêu cầu làm lại hoàn toàn phía Giảng viên trước. Hỗ trợ Template, Auto-layout, Style Panel, và Phím tắt. Không tốn API Key (hoàn toàn dùng thư viện Frontend miễn phí).

**Logic triển khai chi tiết:**
1. **Quản lý Template & Style (Panel & Modal):**
   - Xây dựng Modal "Chọn Template" khi tạo mới hoặc Sinh AI (Basic Mindmap, Logic Chart, Org Chart...).
   - Xây dựng Sidebar Panel bên phải: chứa các tùy chọn Structure (Layout), Color Theme (Màu sắc), Text (Font, Style, Căn lề), Shape (Border, Fill).
2. **Auto Layout (Tự động căn chỉnh):**
   - Không cho phép kéo thả tự do phá vỡ cấu trúc.
   - Sử dụng thư viện `dagre` hoặc `d3-hierarchy` tích hợp cùng `React Flow` để tự động tính toán tọa độ (x, y) của các node mỗi khi có thay đổi (thêm/xóa/sửa node).
3. **Thao tác bằng Phím tắt (Keyboard Navigation):**
   - Bắt sự kiện `onKeyDown` trên React Flow:
   - `Enter`: Tạo một node anh em (sibling) dưới node hiện tại đang được chọn.
   - `Tab`: Tạo một node con (child) của node hiện tại.
   - `Delete` / `Backspace`: Xóa node đang được chọn (cùng toàn bộ node con của nó).
   - Tự động trigger hàm Auto Layout sau mỗi thao tác trên.
4. **Về vấn đề API Gemini:**
   - Việc tái tạo UI và tính năng của Xmind **HOÀN TOÀN KHÔNG** tốn tài nguyên API Key của Gemini. Mọi thuật toán tự động căn chỉnh (auto-layout), màu sắc, phím tắt đều được xử lý bằng Javascript tại Frontend máy khách. Gemini chỉ được gọi duy nhất 1 lần khi bạn bấm nút "Sinh Mindmap bằng AI" để trả về cấu trúc nội dung dạng Text/JSON.

---

## TASK 5: Hệ thống Spaced Repetition (SRS) Flashcard & WebSocket
**Mức độ:** Rất Khó | **Phạm vi:** Backend (Spring Boot) + Frontend
**Trạng thái:** UI mức độ chưa chuẩn Anki (<1m, <10m, 1d, 3d) và tính toán thời gian bị sai (tất cả đều nhảy về 9/12/2026).

**Logic triển khai chi tiết:**
1. Sửa lại UI các nút đánh giá chuẩn Anki: Again (<1m), Hard (<10m), Good (1d), Easy (3d).
2. Fix lại công thức tính toán `next_review_date` ở Backend để cộng đúng số ngày/phút vào thời gian hiện tại (`LocalDateTime.now()`) thay vì set cứng ngày.

---

## TASK 9: Fix cơ chế Refresh Token (Duy trì phiên đăng nhập)
**Mức độ:** Trung bình | **Phạm vi:** Frontend (Axios Interceptor) + Backend

**Logic triển khai chi tiết:**
1. Hiện tượng lỗi là do Access Token hết hạn (thường là 15-30 phút) nhưng Frontend không tự động gọi API `/refresh` để lấy Token mới, hoặc Refresh Token cũng bị hết hạn quá nhanh.
2. **Backend:** Cấu hình thời hạn của Refresh Token lên 7 ngày hoặc 30 ngày. Đảm bảo API `/api/v1/auth/refresh` hoạt động đúng, trả về Access Token mới.
3. **Frontend:** Thêm cấu hình trong Axios Interceptor (tại `api.ts` hoặc `fetcher`). Khi nhận mã lỗi `401 Unauthorized`, tự động lấy Refresh Token từ LocalStorage/Cookies gửi lên Backend lấy Access Token mới, lưu lại và *thử lại* (retry) request vừa bị lỗi.

---

## TASK 10: Quản lý thiết bị / Phiên làm việc đa nền tảng
**Mức độ:** Trung bình | **Phạm vi:** Backend

**Logic triển khai chi tiết:**
Quy định rạch ròi về chính sách phiên làm việc:
- Hỗ trợ lưu trữ cấu trúc `User_Sessions` trong Database hoặc Redis (Lưu `device_id`, `refresh_token`, `last_active`).
- Nếu muốn **giới hạn 1 thiết bị**: Khi đăng nhập ở thiết bị mới, Backend sẽ xóa/invalidate tất cả Refresh Token cũ của User đó. Các thiết bị cũ khi access token hết hạn sẽ không thể refresh và bị đá văng ra ngoài.
- Bổ sung màn hình "Quản lý thiết bị" ở Frontend cho phép người dùng xem các phiên đang đăng nhập và ấn "Đăng xuất khỏi thiết bị khác".

---

## TASK 11: Hoàn thiện Quản lý User (Admin, Instructor, Student)
**Mức độ:** Lớn | **Phạm vi:** Fullstack

**Logic triển khai chi tiết:**
1. **Phía Admin:** Bổ sung trang quản lý User: Đánh dấu Spam/Ham, khóa tài khoản, gửi link khôi phục mật khẩu, phê duyệt cấp lại mật khẩu.
2. **Phía Học viên (Student):** Bổ sung trang Cá nhân (Profile): Xem thông tin, đổi mật khẩu, yêu cầu gửi email khôi phục mật khẩu, cấu hình 2FA, nút Xóa tài khoản (xóa mềm).
3. **Phía Giảng viên (Instructor):** Bổ sung tab Quản lý học viên trong Workspace: Xem danh sách học viên trong khóa học của mình, tiến độ của từng người.
