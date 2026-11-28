# Đợt 9: API ghi tiến độ học và trang học bài

> Nhánh `perf` ở cả `project/be` và `project/fe`, làm trên `develop` ngày 28/11/2026 (sau khi `feat/UI` đã merge).
> Tiếp theo [đợt 8](DOT8_SanSangGiamSat.md).

## Tổng quan

| Mục | Repo | Vấn đề trước đây | Cách sửa |
| --- | --- | --- | --- |
| Ghi tiến độ học | be | Mỗi lần trình phát gửi tiến độ (15 s/lần cho mỗi người đang xem) đều tính lại tiến độ cả khóa | Chỉ tính lại khi bài **vừa hoàn thành** hoặc học viên mở bài **lần đầu** |
| Trang học bài `/learn/[lessonId]` | fe | Trang nặng nhất của học viên (328 kB); tải sẵn mọi tab dù chỉ xem video | Tải lười 9 tab/panel không mặc định |

## 1. API ghi tiến độ học (`POST /api/v1/lessons/{id}/progress`)

**Trước đây:** `LessonProgressService.recordProgress` lưu vị trí xem rồi **luôn** gọi
`recalculateEnrollmentProgress`:
1. tìm enrollment,
2. đếm số bài READY của khóa,
3. đếm số bài học viên đã hoàn thành,
4. tìm quiz Official của khóa,
5. (nếu có quiz) tìm các lượt làm quiz,
6. lưu enrollment.

Nhưng `progressPct` của khóa chỉ phụ thuộc **số bài đã hoàn thành** và quiz Official. Phần quiz đã tự tính lại ở
`QuizService` khi nộp bài. Đa số lần gửi chỉ đổi vị trí xem, nên 5–6 câu SQL trên là vô ích. Đây lại là API
được gọi nhiều nhất hệ thống: 200 người đang học cùng lúc tương đương khoảng 13 request/giây.

**Sau khi sửa:** chỉ tính lại tiến độ khóa khi:
- bài **vừa chuyển sang hoàn thành** trong lần gửi này (vượt ngưỡng `completeThresholdPercent`), hoặc
- đây là lần đầu học viên mở bài này (chưa có bản ghi `lesson_progress`), để `progressPct` bắt kịp nếu giảng
  viên đã thêm hoặc bớt bài kể từ lần tính trước.

**Vì sao an toàn:**
- Cấp chứng chỉ và XP "hoàn thành khóa" vẫn nằm trong `recalculateEnrollmentProgress`, và vẫn được gọi đúng
  lúc bài cuối cùng vừa hoàn thành.
- Bỏ các lần `enrollmentRepository.save` không đổi gì: `Enrollment.updatedAt` không được dùng ở đâu. "Lần truy
  cập gần nhất" và "học tiếp" lấy từ `lesson_progress.updatedAt`, mà bản ghi này vẫn được lưu mỗi lần gửi.
- **Đánh đổi chấp nhận được:** nếu giảng viên thêm bài mới, `progressPct` cập nhật vào lần kế tiếp học viên mở
  một bài mới hoặc hoàn thành một bài, thay vì ngay lần gửi tiếp theo.

**Test (TDD):** thêm 3 test vào `LessonProgressServiceTest`. Chạy trên code cũ thì 2 test "không tính lại" fail
(RED), sau khi sửa thì qua (GREEN):
- `pingBinhThuong_chuaHoanThanh_khongTinhLaiTienDoKhoa`: không đếm bài, không đọc/ghi enrollment.
- `pingVuotNguong_vuaHoanThanh_tinhLaiTienDoKhoa`: vẫn tính lại, `progressPct` = 50% khi xong 2/4 bài.
- `xemLaiBaiDaHoanThanh_khongTinhLaiTienDoKhoa`: xem lại bài đã xong không tính lại.

Các test cũ (bài mới mở lần đầu, hoàn thành hết khóa thì có `completedAt`, đã hoàn thành thì không ghi đè, quiz
70/30) vẫn qua vì đều đi qua nhánh "lần đầu" hoặc "vừa hoàn thành".

## 2. Trang học bài (`app/(learn)/learn/[lessonId]/page.tsx`)

**Trước đây:** trang import tĩnh mọi tab. Học viên vào xem video vẫn phải tải code của bảng điểm, kế hoạch học,
tài liệu, đánh giá, quản lý học liệu, ghi chú, chat trực tiếp và Tutor AI (kèm `react-markdown`).

**Sau khi sửa:** 9 component chỉ hiện khi bấm đúng tab được chuyển sang `next/dynamic(..., { ssr: false })`:
`CourseGradebookTab`, `CourseStudyPlanTab`, `LessonAssignmentsList`, `CourseResourcesTab`, `ReviewsSection`,
`MaterialManager`, `TutorEmbedded`, `NotesPanel`, `LiveChatPanel`. Các tab này vốn render có điều kiện theo
`mainTab`/`sidebarTab`, nên chunk chỉ tải khi tab được mở. Tab mặc định (Tổng quan, Transcript), trình phát
`DualPlayer` và thanh lồng tiếng giữ import tĩnh để video hiện ngay.

## Kết quả

| | Trước | Sau |
| --- | --- | --- |
| Câu lệnh MySQL mỗi lần gửi tiến độ (bài chưa hoàn thành) | ~15 | **~11** (−27%) |
| First Load JS `/learn/[lessonId]` (`next build`) | 328 kB | **247 kB** (−25%) |
| Kích thước riêng của trang | 49,4 kB | 32,2 kB |

## Kiểm chứng

- fe: `tsc --noEmit` sạch; eslint sạch; `vitest run` **679/679** test (141 file) qua; `next build` thành công.
- be: `mvn test` **1267/1267** test qua.

## Đo số câu SQL (28/11/2026, máy local)

Chạy 2 image production cạnh nhau, cùng MySQL/Redis: bản `develop` cũ và bản đã sửa. Đăng nhập `student1`, gửi 20 lần
tiến độ cho bài 3464 (718 s, đang xem ở giây 290–310, chưa tới ngưỡng hoàn thành), lấy chênh lệch bộ đếm
`Questions` của MySQL rồi trừ phần nền (job nền chạy trong 6 s không gửi request). Lặp 2 lượt:

| | Lượt 1 | Lượt 2 |
| --- | --- | --- |
| Bản cũ | ~15 câu/lần | ~15 câu/lần |
| Bản mới | ~11 câu/lần | ~11 câu/lần |

`Questions` đếm mọi lệnh gửi tới MySQL, kể cả lệnh giao dịch (`SET autocommit`, `COMMIT`), nên chỉ dùng để so
trước/sau. Giảm thấp hơn ước lượng ban đầu (~50%) vì phần còn lại chủ yếu là kiểm quyền truy cập bài học
(`EnrollmentSecurity.canAccessLesson`) và ghi điểm mốc xem cho biểu đồ giữ chân người xem (`recordWatchCheckpoints`),
cả hai đều cần thiết ở mỗi lần gửi. Với 200 người đang học cùng lúc (~13 request/giây) là bớt khoảng **50 câu lệnh
MySQL mỗi giây**.
