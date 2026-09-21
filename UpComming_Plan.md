# Kế Hoạch Triển Khai (Implementation Plan) - Sửa Lỗi & Tối Ưu UI/UX LMS

Bản kế hoạch này tổng hợp lại toàn bộ các vấn đề cốt lõi bạn đang gặp phải, đi thẳng vào nguyên nhân gốc rễ và đề xuất phương án giải quyết dứt điểm mà không cần can thiệp quá sâu vào cấu trúc Backend hiện tại.

## 1. Sửa Lỗi Tính Năng (Core Bug Fixes)

### 1.1 Lỗi Nút Loa (TTS) Bị Ẩn Trên Flashcard Official Của Học Viên
- **Vấn đề**: Thẻ do học viên tạo có loa, thẻ giảng viên (Official) xem ở phía học viên lại mất loa. Bạn từ chối cơ chế fallback ngầm (vì ngôn ngữ đã có sẵn từ transcript).
- **Nguyên nhân**: Khi học viên truy cập thẻ từ tab Official (thông qua `(student)/materials/[id]/page.tsx`), thuộc tính `language` có thể đang không được truyền đúng xuống component `FlashcardViewer`, hoặc API lấy chi tiết thẻ Official đang trả về thiếu trường `language` so với API của Kho cá nhân.
- **Giải pháp**: 
  - [MODIFY] `app/(student)/materials/[id]/page.tsx` & `MaterialManager.tsx`: Đảm bảo API trả về đúng trường `language` và truyền nguyên vẹn giá trị này vào `<FlashcardViewer language={material.language} />`. Tuyệt đối tôn trọng ngôn ngữ gốc của thẻ.

### 1.2 Lỗi Kéo Thả Phân Bổ (Assign) Học Liệu Không Hiện Phía Học Viên
- **Vấn đề**: Kéo thả học liệu vào Bài học (Lesson) hoặc Chương (Chapter), phía giảng viên thấy đổi nhưng phía học viên lại không thấy hiện lên trong Kho Official.
- **Nguyên nhân**: API lấy danh sách học liệu của học viên (ví dụ `/api/v1/instructor/materials/courses/{courseId}` hoặc `/api/v1/materials`) có thể đang thiếu logic lọc theo `lessonId` / `chapterId`, hoặc frontend của học viên đang không hiển thị các học liệu được assign theo `chapterId` (hiện tại UI học viên chỉ query theo `lessonId`).
- **Giải pháp**:
  - [MODIFY] Backend API (phần query học liệu cho học viên): Đảm bảo các học liệu được gán vào `lessonId` HOẶC `chapterId` hiện tại đều được trả về đầy đủ.
  - Định hướng UI: Học liệu gán vào Chương (Chapter) sẽ được hiển thị ở phần "Học liệu chung của chương" trên màn hình học tập của sinh viên.

### 1.3 Lỗi Menu "Đánh Dấu Official / Bỏ Official"
- **Vấn đề**: Báo lỗi và không hoạt động.
- **Giải pháp**: 
  - [MODIFY] `MaterialFolderTree.tsx` & UI: **Xóa bỏ hoàn toàn nút này khỏi menu chuột phải**. Trạng thái "Official" nay sẽ được quyết định ngầm tự động thông qua việc giảng viên có "Phân phối (Assign)" học liệu đó vào khóa học hay không.

### 1.4 Lỗi Không Thể Xóa/Di Chuyển Thư Mục
- **Vấn đề**: Chuyển học liệu vào thư mục bị lỗi, xóa thư mục trống báo lỗi ràng buộc.
- **Giải pháp**:
  - [MODIFY] `InstructorMaterialController.java`: Fix lỗi `ClassCastException` do dữ liệu `folderId` gửi lên bị Jackson ép kiểu ngầm thành `Integer`. (Thay đổi nhận `@RequestBody Map<String, Object>`).
  - [MODIFY] `MaterialFolderService.java`: Sửa logic `deleteFolder`, tự động gỡ khóa ngoại (set `folder_id = null` cho các học liệu bên trong) trước khi xóa thư mục để vượt qua lỗi ràng buộc DB.

---

## 2. Nâng Cấp Trải Nghiệm Người Dùng (UI/UX)

### 2.1 Trạng Thái Loading (Spinner / Overlay)
- **Giải pháp**:
  - [MODIFY] Các component thao tác (kéo thả, xóa, chuyển thư mục): Thêm lớp phủ mờ (Disabled overlay) hoặc Spinner xoay vòng lên thẻ học liệu đang được xử lý. 
  - **Mục đích**: Chặn người dùng double-click gây ra nhiều request trùng lặp (duplicate requests) làm crash backend.

### 2.2 Thông Báo Lỗi Thân Thiện (Toast Notifications)
- **Giải pháp**: 
  - [MODIFY] Bắt toàn bộ các lỗi từ API (xóa thất bại, di chuyển thất bại) thay vì `console.log`.
  - Sử dụng thư viện Toast (Sonner) hiển thị thông báo tiếng Việt rõ nghĩa ở góc màn hình. Ví dụ: *"Không thể xóa thư mục vì vẫn còn học liệu bên trong"*.

### 2.3 Cải Tổ Bố Cục Giảng Viên (Instructor Workspace)
- **Master-Detail Layout**: Chia màn hình thành 2 cột (Trái: Cây thư mục (1/4) | Phải: Workspace (3/4)).
- **Phân bổ học liệu lộn xộn**: Quy hoạch lại cách hiển thị danh sách học liệu đã gán để dễ nhìn hơn.
- **View Toggle & Search Bar**: Thêm thanh công cụ phía trên Workspace để chuyển đổi giữa **Grid View** (thẻ) và **List View** (bảng ngang chi tiết), kèm thanh tìm kiếm và lọc.
- **Modern Material Cards (Neumorphism)**: Làm mới thiết kế thẻ học liệu (bo tròn, đổ bóng, màu sắc phân biệt Quiz/Flashcard/Mindmap). Thêm hiệu ứng fade-in cho các nút thao tác nhanh khi hover.
- **Folder Tree Modal**: Bấm di chuyển sẽ hiện Popup Modal hiển thị Cây Thư Mục có icon folder trực quan để người dùng chọn, thay vì nhập ID.

### 2.4 Cải Tổ Giao Diện Học Viên (Student View)
- **Tabs Official/Cá nhân**: Thiết kế lại tab "Kho Học Liệu Official" và "Kho Học Liệu Cá Nhân" thành dạng Pill-Tab hiện đại
- **Giao diện Ôn tập Flashcard (SRS)**: 
  - Các nút `Hard / Good / Easy` được thêm hiệu ứng hover màu sắc nổi bật.
  - Tính toán và hiển thị rõ thời gian "Next Review Date" kết hợp icon lịch 🗓️ để dễ hình dung hơn con số `<10m` tĩnh.

---

## Bạn Cần Phê Duyệt (Review Required)

> Bản kế hoạch này đã bám sát chính xác các vấn đề bạn liệt kê (Từ chối Fallback tiếng Việt, Fix lỗi hiển thị Official, Phân bổ học liệu, Xóa folder, Loading Overlay, Toast Tiếng Việt).
> Nếu bạn đồng ý với hướng tiếp cận gọn gàng này (chủ yếu tập trung sửa lỗi và làm đẹp UI, không đụng chạm phá vỡ cấu trúc Backend), vui lòng bấm **Xác nhận (Approve)** để tôi bắt đầu thực hiện ngay phần code!
