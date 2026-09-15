# Đề Xuất Chuyển Đổi Mô Hình YouTube Cho LMS
*(Thay đổi phương thức cấp quyền Giảng Viên và quy trình duyệt nội dung)*

## 1. Tóm Tắt Mục Tiêu
- **Trước đây:** Học viên phải nộp hồ sơ minh chứng (bằng cấp, chứng chỉ) $\rightarrow$ Admin duyệt thủ công $\rightarrow$ Được cấp quyền Giảng Viên (Role INSTRUCTOR) $\rightarrow$ Mới được truy cập trang quản lý và tạo khóa học.
- **Mô hình mới (YouTube-like):** Nền tảng mở. Mọi người dùng đã xác thực tài khoản đều có thể vào trang quản lý và tạo nội dung. Việc kiểm duyệt sẽ chuyển từ **kiểm duyệt người dùng** sang **kiểm duyệt nội dung** (duyệt từng khóa học/video).
- **Nguyên tắc bất di bất dịch:**
  - **GIỮ NGUYÊN** toàn bộ giao diện (UI) và logic nghiệp vụ hiện tại của Workspace Giảng viên (Instructor Workspace).
  - **GIỮ NGUYÊN** hệ thống Role hiện tại (`STUDENT`, `INSTRUCTOR`, `ADMIN`).
  - **CHỈ THAY ĐỔI** cách người dùng tiếp cận màn hình đó và quy trình đăng nội dung.

---

## 2. Chi Tiết Thay Đổi Về Hệ Thống CSDL (Entities)

### 2.1. Lược Bỏ (Remove)
- **`InstructorRequest` Entity:** Bỏ hoàn toàn bảng này trong Database. Không còn khái niệm "Yêu cầu nâng cấp tài khoản".

### 2.2. Thêm Mới & Cập Nhật (Add & Update)
- **`User` Entity:**
  - Role `INSTRUCTOR` vẫn được giữ. Tuy nhiên, nó mang ý nghĩa là "Nhà sáng tạo nội dung đã được xác nhận" (đã có ít nhất 1 khóa học được duyệt) thay vì là "Giáo viên được cấp phép".
  - Bổ sung (nếu chưa có) các trường kiểm tra bảo mật bắt buộc trước khi cho tạo nội dung: `is_email_verified`, `is_2fa_enabled`.
- **`Course` Entity (Khóa học):**
  - Hiện tại khóa học có thể đang public ngay khi tạo. Cần bổ sung cột `course_status` (Enum: `DRAFT`, `PENDING_REVIEW`, `APPROVED`, `REJECTED`).
  - Khóa học mới tạo sẽ ở trạng thái `DRAFT` (bản nháp). Khi giảng viên upload đủ video và bấm "Xuất bản", trạng thái chuyển sang `PENDING_REVIEW`.
- **`ContentReport` Entity (Hệ thống Báo cáo Vi phạm):**
  - Entity mới hoàn toàn để thay thế cho khâu kiểm duyệt đầu vào. Người dùng (Học viên) có thể report các nội dung xấu.
  - Các trường: `id`, `reporter_id` (User), `target_type` (Enum: `COURSE`, `VIDEO`, `REVIEW`), `target_id`, `reason_category`, `description`, `status` (`PENDING`, `RESOLVED`, `DISMISSED`).

---

## 3. Chi Tiết Thay Đổi Về API & Logic Backend

1. **Xóa Controller/Service cũ:**
   - Xóa hoàn toàn `InstructorRequestController` và `InstructorRequestService`.
2. **Logic Nâng cấp Role Tự động (Auto-upgrade):**
   - Không cần API xin cấp quyền.
   - Khi một `Course` của user được Admin chuyển trạng thái sang `APPROVED` lần đầu tiên, Backend tự động kích hoạt một event để `UPDATE User SET role = 'INSTRUCTOR'`.
3. **Logic Chặn Spam:**
   - API Tạo khóa học mới (`POST /api/v1/courses`) sẽ kiểm tra: Nếu User bị ban/spam, hoặc chưa Verify Email, trả về HTTP 403 Forbidden.
4. **API Cho Admin (Kiểm duyệt nội dung):**
   - Sửa API duyệt User thành API Duyệt Khóa Học: `GET /api/v1/admin/courses?status=PENDING_REVIEW`.
   - Cung cấp API để Admin đổi trạng thái khóa học: `PUT /api/v1/admin/courses/{id}/status`.

---

## 4. Chi Tiết Thay Đổi Về Giao Diện (Frontend Screens)

### 4.1. Global Header / Navigation
- **Hiện tại:** Chỉ kiểm tra `user.role === 'INSTRUCTOR'` mới render nút "Instructor Dashboard" hoặc menu bên trái.
- **Mới:** Menu **"📹 Kênh quản lý của tôi"** sẽ luôn hiển thị cho tất cả mọi người dùng (dù role là STUDENT).

### 4.2. Workspace Quản Lý (Giữ nguyên 100%)
- Mọi trang dashboard, danh sách khóa học, upload video, tạo Quiz, Flashcard, Sơ đồ tư duy, Gradebook **không bị ảnh hưởng gì**, tiếp tục dùng code hiện tại.

### 4.3. Màn Hình Tạo Khóa Học (Cải tiến nhỏ)
- Khi bấm nút **"Tạo khóa học mới"**, Frontend gọi API check điều kiện.
- Nếu tài khoản chưa xác thực 2FA/Email, hiển thị một **Modal (Popup) Cảnh báo**: *"Để đảm bảo chất lượng nền tảng, vui lòng xác minh Email và bật 2FA trước khi khởi tạo khóa học đầu tiên."* kèm nút chuyển hướng đến trang Cài đặt cá nhân.

### 4.4. Màn Hình Dành Cho Admin
- Xóa bỏ trang "Duyệt yêu cầu Giảng viên".
- Tạo mới trang **"Kiểm Duyệt Nội Dung" (Content Moderation)**.
  - **Tab 1:** Các khóa học đang chờ duyệt (`PENDING_REVIEW`). Admin có thể xem thumbnail, tiêu đề, và video bài học đầu tiên trước khi bấm Xanh (Duyệt) hoặc Đỏ (Từ chối kèm lý do).
  - **Tab 2:** Các báo cáo vi phạm (`ContentReport`) từ cộng đồng để Admin xem xét gỡ khóa học hoặc khóa tài khoản user vi phạm.

---

## 5. Luồng Nghiệp Vụ (Flowchart) Tóm Tắt

```mermaid
flowchart TD
    A[Bất kỳ User nào] -->|Click Menu| B[Vào 'Kênh quản lý của tôi']
    B --> C[Giao diện Instructor Workspace (Giữ nguyên)]
    C --> D{Bấm 'Tạo khóa học'}
    D -->|Chưa xác minh| E[Hiện Modal bắt buộc xác minh Email/2FA]
    D -->|Đã xác minh| F[Tạo khóa học - trạng thái DRAFT]
    F --> G[Upload Video, Tạo Bài Giảng]
    G --> H[Bấm Xuất Bản -> PENDING_REVIEW]
    H --> I{Hệ thống / Admin duyệt}
    I -->|Từ chối| J[Trả về DRAFT, báo lỗi]
    I -->|Duyệt| K[Khóa học APPROVED - Public]
    K --> L{Check: User đã là INSTRUCTOR chưa?}
    L -->|Chưa| M[Tự động nâng cấp Role = INSTRUCTOR]
    L -->|Đã là INSTRUCTOR| N[Hoàn tất]
```
