Dưới đây là **toàn bộ nội dung tích hợp (All-in-one Master Task)** được chuẩn hóa cấu trúc chặt chẽ, gắn kèm các rào cản kỹ thuật để gửi thẳng một lần duy nhất cho Antigravity thực thi mà không lo bị hallucination hay sót việc:

```markdown
# MASTER TASK: CHUẨN HÓA TOÀN DIỆN LUỒNG KIỂM DUYỆT KHÓA HỌC & PROFILE ONBOARDING (CHUẨN UDEMY)

Yêu cầu thực thi trọn gói quy trình duyệt khóa học và hồ sơ giảng viên dựa trên kiến trúc Unified Account đã hoàn thành.

---

### CÁC NGUYÊN TẮC BẤT DI BẤT DỊCH (STRICT GUARDRAILS)
1. TUYỆT ĐỐI KHÔNG can thiệp, chỉnh sửa hay refactor các module học liệu hiện tại (Quiz, Flashcard, Mindmap, Material Manager, Video Player)[cite: 1, 2].
2. TUYỆT ĐỐI KHÔNG sửa các file migration cũ (từ V1 đến V110) để tránh lỗi Checksum Mismatch[cite: 1, 2]. Mọi thay đổi DB bắt buộc phải nằm trong file mới `V111__add_user_profile_fields.sql`[cite: 1].
3. Giữ nguyên enum trạng thái khóa học hiện có của Backend: `DRAFT`, `PENDING`, `PUBLISHED`, `REJECTED`[cite: 1]. Không tự chế thêm enum mới làm lệch parsing JSON.

---

### PHẦN 1: DATABASE MIGRATION (FLYWAY V111)
- Tạo file mới: `backend/.../resources/db/migration/V111__add_user_profile_fields.sql`.
- Dùng Procedure chuẩn MySQL để kiểm tra metadata trước khi thêm cột, tránh lỗi cú pháp `IF NOT EXISTS` trên các bản MySQL thấp:
  ```sql
  DELIMITER $$   CREATE PROCEDURE AddProfileFieldsIfNotExists()   BEGIN       IF NOT EXISTS (           SELECT * FROM INFORMATION_SCHEMA.COLUMNS            WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'headline'       ) THEN           ALTER TABLE users ADD COLUMN headline VARCHAR(255) NULL;       END IF;        IF NOT EXISTS (           SELECT * FROM INFORMATION_SCHEMA.COLUMNS            WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'bio'       ) THEN           ALTER TABLE users ADD COLUMN bio TEXT NULL;       END IF;   END $$
  DELIMITER ;

  CALL AddProfileFieldsIfNotExists();
  DROP PROCEDURE AddProfileFieldsIfNotExists;

```

*(Lưu ý: Cột `reject_reason` trong bảng `courses` đã có sẵn từ V1, không cần thêm cột này vào migration)*.

* Cập nhật ánh xạ 2 trường `headline` và `bio` vào Entity `User.java`, `UserDto.java`, `UserProfileDto.java`.



---

### PHẦN 2: PROFILE ONBOARDING TẠI CHỖ (IN-PLACE MODAL)

* **Backend (`CourseService.java`):**
* Tại API tạo khóa học (`POST /api/v1/courses`):
* Kiểm tra điều kiện hồ sơ giảng viên (chặn cả `null` lẫn chuỗi trắng):
```java
if (user.getHeadline() == null || user.getHeadline().trim().isEmpty() 
    || user.getBio() == null || user.getBio().trim().length() < 20) {
    throw new BusinessException("PROFILE_INCOMPLETE", "Vui lòng hoàn thiện chức danh và tiểu sử giảng viên (tối thiểu 20 ký tự).");
}

```




* **Frontend (`/instructor/courses/page.tsx`):**
* Khi người dùng click nút `+ Tạo khóa học mới`:
* Nếu API ném lỗi `PROFILE_INCOMPLETE` (hoặc state người dùng chưa đủ headline/bio):
* Mở ngay **Modal Onboarding hoàn thiện hồ sơ tại chỗ (In-place Modal)**:
* Ô 1: Chức danh nghề nghiệp (`headline` - text input).
* Ô 2: Tiểu sử ngắn (`bio` - textarea, tối thiểu 20 ký tự).


* Người dùng điền xong bấm `Lưu & Tiếp tục`:
1. Gọi API cập nhật profile (`PUT /api/v1/profile` hoặc tương đương).
2. Tự động trigger tạo khóa học mới (trạng thái `DRAFT`) mà KHÔNG chuyển trang `/profile` làm đứt gãy luồng trải nghiệm.









---

### PHẦN 3: CHUẨN HÓA AUTOMATED CHECKLIST (ĐIỀU KIỆN GỬI DUYỆT)

* **Chuẩn hóa hàm `computeMissingConditions` (Backend & Frontend `SubmitChecklist.tsx`):**
* **Điều kiện 1:** Khóa học có Tiêu đề, Mô tả và đã tải lên Ảnh bìa (Thumbnail).
* **Điều kiện 2:** Có tối thiểu **1 Chương (Chapter)**.
* **Điều kiện 3:** Có tối thiểu **1 Bài học (Lesson) có Video ở trạng thái sẵn sàng (`status = 'READY'`)** (Chuẩn hóa từ 3 video về tối thiểu 1 video).
* **Điều kiện 4 (Khuyến nghị):** Có đính kèm Quiz/Flashcard/Mindmap (Chỉ mang tính chất gợi ý hiển thị tick xanh; TUYỆT ĐỐI KHÔNG dùng làm điều kiện chặn nút gửi duyệt).


* **Trạng thái nút "Gửi Admin Duyệt":**
* Chỉ kích hoạt (enabled) khi thỏa mãn đủ 3 điều kiện bắt buộc trên. Khi bấm, gọi API chuyển `course.status` từ `DRAFT` sang `PENDING`.



---

### PHẦN 4: ADMIN PHÊ DUYỆT / TỪ CHỐI & BẢO VỆ TÍNH TOÀN VẸN

* **Admin Moderation Detail (`/admin/moderation/[id]/page.tsx`):**
* Nút xanh: **"Phê duyệt"** -> Gọi API chuyển status sang `PUBLISHED`.
* Nút đỏ: **"Từ chối"** -> Mở Modal bắt buộc Admin nhập lý do (`rejectionReason`). Gọi API: `POST /api/v1/admin/courses/{id}/reject` kèm body `{ reason: "..." }`.


* **Backend bảo vệ nội dung (`CurriculumService` / `ChapterService` / `LessonService`):**
* Cập nhật `course.status = 'REJECTED'`, lưu nội dung vào cột `reject_reason`.
* **Khóa cứng (Block):** Chặn các API sửa/thêm/xóa bài học, chương, video khi `course.status == PENDING` hoặc `PUBLISHED` (ngăn tráo video khi đang duyệt hoặc đã lên sóng).
* **Mở quyền sửa:** Cho phép Giảng viên cập nhật bài học/chương khi `course.status == DRAFT` HOẶC `REJECTED`.


* **Phía Giảng viên (`CourseBuilderForm.tsx`):**
* Nếu khóa học đang ở trạng thái `REJECTED`:
* Hiển thị Alert Banner màu đỏ nổi bật ở đầu trang: *"Khóa học cần chỉnh sửa theo yêu cầu của Admin: [Nội dung rejectReason]"*.
* Mở quyền sửa bài học/chương.
* Đổi tên nút hành động thành: **"Gửi duyệt lại" (Re-submit)** để chuyển ngược về `PENDING`.


* Nếu khóa học đang ở trạng thái `PENDING`:
* Chuyển toàn bộ các form nhập liệu, danh sách bài học sang chế độ **Chỉ đọc (Read-only / Disabled)**. Vô hiệu hóa nút gửi duyệt.





---

### PHẦN 5: KIỂM THỬ XÁC NHẬN

* Đảm bảo Backend Maven compile pass và Frontend Next.js build không lỗi lint/types.
* Xác nhận chạy thông suốt luồng:
1. Tạo User mới -> Bấm tạo khóa học hiện Modal Onboarding -> Nhập thông tin -> Khởi tạo Course DRAFT.
2. Thêm 1 Chapter + 1 Lesson Video READY -> Checklist đủ điều kiện -> Bấm gửi duyệt sang `PENDING` -> Form chuyển sang Read-only.
3. Admin từ chối kèm lý do -> Khóa học chuyển sang `REJECTED` -> Giảng viên thấy Banner đỏ kèm lý do -> Sửa bài học và bấm "Gửi duyệt lại".
4. Admin bấm "Phê duyệt" -> Khóa học chuyển sang `PUBLISHED` và xuất hiện ngoài trang chủ.



```

```