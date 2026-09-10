# KẾ HOẠCH TRIỂN KHAI CHI TIẾT (FINAL PHASE)

> **Mục tiêu:** Hoàn thiện dứt điểm toàn bộ tính năng cốt lõi của LMS trong 24h tới.
> **Tiêu chí (Best Practice):** Sử dụng thư viện mã nguồn mở miễn phí, giải pháp Native không phụ thuộc bên thứ ba (không tốn phí API), thuật toán tự build để duy trì vĩnh viễn.

---

## TASK 1: Hoàn thiện màn hình Tiến độ học tập (Progress Tracking)
**Mức độ:** Trung bình | **Phạm vi:** Frontend

**Logic triển khai chi tiết:**
1. **Sửa lỗi Redirect:**
   - Tại component `ProgressChart`, cập nhật sự kiện `onClick`: Tìm kiếm `lesson_id` cuối cùng mà học viên đang xem (lấy từ API `/api/v1/progress/last-accessed`).
   - Nếu có, `router.push('/learn/[lessonId]')`. Nếu chưa học bài nào, `router.push('/courses/[slug]')`.
2. **Sửa lỗi hiển thị UI (Icon Khóa):**
   - Truyền prop `isEnrolled={true}` vào thẻ `CourseCard` khi nó được gọi từ trang `/progress` để tắt vĩnh viễn cái ổ khóa.
3. **Bổ sung Accordion Tracking Chương:**
   - Gọi API lấy danh sách Chapter kèm mảng Lesson.
   - Viết hàm `reduce` so khớp danh sách Lesson ID với mảng `completedLessons` để tính: `(số bài hoàn thành / tổng số bài trong chương) * 100`.
   - Render dưới dạng thanh Progress Bar thu nhỏ cho từng chương.

---

## TASK 2: Cấp quyền CRUD Học liệu cá nhân cho Học viên
**Mức độ:** Dễ | **Phạm vi:** Frontend

**Logic triển khai chi tiết (Best Practice):**
Không cần sinh ra route mới. Tái sử dụng component `CourseMaterialsManager.tsx` nhưng bọc nó bằng điều kiện Role:
1. **Kiểm tra JWT Role:** Nếu `role === 'STUDENT'`, kích hoạt `StudentMode`.
2. **Cấp quyền (RBAC):**
   - **Xóa:** Gọi `DELETE /api/v1/materials/{id}`. (Có popup xác nhận).
   - **Sửa (Chỉ cho Flashcard):** Cho phép sửa chuỗi text mặt trước/sau. Gọi `PATCH /api/v1/materials/{id}`.
   - **Nút "Sinh AI":** Giữ nguyên tính năng sinh học liệu bằng AI nhưng áp dụng cơ chế Giới hạn số lần (Quota) theo đúng Business Rule. Khi hết lượt, nút sẽ bị mờ đi kèm thông báo báo hết lượt sinh trong ngày. Đồng thời cung cấp thêm các form để học viên có thể tự thêm Flashcard/Quiz bằng tay.

---

## TASK 3: Tối ưu luồng làm bài Quiz Public (Official)
**Mức độ:** Dễ | **Phạm vi:** Frontend

**Logic triển khai chi tiết:**
1. **Định danh Official:** Trong list bài tập, ưu tiên sort các bài có cờ `is_official = true` lên đầu bảng. Thêm Badge `.badge-active` màu xanh navy (chuẩn UI mới) ghi chữ "Thi Chính Thức".
2. **Phòng chống gian lận:** 
   - Nếu Quiz có cờ `strict_mode = true`, gọi hàm `document.addEventListener('visibilitychange')` trong `useEffect` lúc làm bài. 
   - Nếu học viên chuyển Tab, cảnh báo Toast (Sonner). Quá 3 lần → Tự động gọi API Submit nộp bài ngay lập tức 0 điểm. (Thuần Frontend, không tốn tài nguyên Server).

---

## TASK 4: Kéo thả Mindmap (Drag & Drop)
**Mức độ:** Khó | **Phạm vi:** Frontend (Thư viện Open Source)

**Logic triển khai chi tiết (Miễn phí & Vĩnh viễn):**
Tuyệt đối KHÔNG dùng các thư viện trả phí như GoJS hay các cổng kéo thả bên thứ 3.
1. **Sử dụng React Flow (`@xyflow/react`):** Thư viện chuẩn MIT, cực kỳ mạnh mẽ, miễn phí 100% của Vercel ecosystem.
2. **Luồng chuyển đổi:**
   - Dữ liệu gốc trong DB lưu là **Mermaid Syntax** (để render nhẹ nhàng cho học viên).
   - Khi Giảng viên ấn "Sửa trực quan", viết một hàm `parseMermaidToReactFlow(text)` chuyển Markdown thành mảng `Nodes[]` và `Edges[]`.
   - React Flow sẽ hiển thị khung kéo thả. Giảng viên kéo node, thay tên.
   - Nhấn "Lưu": Gọi hàm `stringifyReactFlowToMermaid(nodes, edges)` xuất ngược ra mã Mermaid và gọi API PATCH đẩy lên Database.
3. **Ưu điểm:** Học viên vẫn dùng thư viện Mermaid siêu nhẹ, Giảng viên có đồ họa xịn.

---

## TASK 5: Hệ thống Spaced Repetition (SRS) Flashcard & WebSocket
**Mức độ:** Rất Khó | **Phạm vi:** Backend (Spring Boot) + Frontend

**Logic triển khai chi tiết (Tự build 100%, không phụ thuộc):**
Đây là giải pháp vĩnh viễn, mô phỏng thuật toán SuperMemo-2 kinh điển của Anki.

1. **Thuật toán SRS (Backend - Java):**
   - Thêm 3 trường vào bảng `flashcard_progress`: `easiness_factor` (float, default 2.5), `interval` (int, số ngày), `next_review_date` (timestamp).
   - Khi học viên đánh giá thẻ (1-3 điểm):
     * **Khó (1):** interval = 1 ngày. easiness_factor -= 0.15.
     * **TB (2):** interval = (interval cũ) * 1.5. easiness_factor không đổi.
     * **Dễ (3):** interval = (interval cũ) * easiness_factor. easiness_factor += 0.1.
   - Cộng `interval` vào ngày hiện tại để ra `next_review_date`.

2. **Cronjob nhắc nhở (Backend - Spring Boot):**
   - Dùng `@Scheduled(cron = "0 0 8 * * ?")` (Chạy mỗi 8h sáng).
   - Truy vấn SQL: Lấy tất cả user có thẻ `next_review_date <= NOW()`.
   - Lưu vào bảng `notifications`.

3. **WebSocket (Realtime):**
   - Sử dụng `SockJS` + `STOMP` (Có sẵn trong thư viện Spring WebSocket, miễn phí 100%).
   - Frontend dùng `stompjs` subscribe vào topic `/user/queue/notifications`. Khi có thông báo ôn tập, chuông đỏ tự động nảy số (không cần Reload).

---
*Ngày mai chúng ta sẽ code lần lượt từ Task 1 đến Task 5.*
