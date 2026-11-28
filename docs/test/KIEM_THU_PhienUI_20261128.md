# Kiểm thử backend (28/11/2026)

## Unit/integration test
Chạy offline trong container `maven:3.9-eclipse-temurin-17` dùng volume `be_backend_maven_cache`, trên bản sao source (không đụng backend đang chạy):
```bash
docker run --rm -v <copy>:/w -v be_backend_maven_cache:/root/.m2 -w /w maven:3.9-eclipse-temurin-17 mvn -o -q test
```
Kết quả: **1271/1271 đạt**.

Test thêm hoặc sửa:
- `RateLimitFilterTest`: bỏ qua XFF từ IP public, tin XFF từ loopback.
- `LessonProgressServiceTest`: không tính lại % khóa khi bài chưa hoàn thành.
- `StompAuthInterceptorTest`: topic bài học cần quyền học; khách bị từ chối.
- `TutorInsightAnalyzerTest`: nhãn 4 âm tiết; gộp chủ đề trùng nhãn.

## Test tải (k6)
- HTTP, luồng học viên: 200 và 500 người dùng ảo (kết quả trong `../perf/DOT9_PhienUI_ToiUu_20261128.md`).
- WebSocket: STOMP thô qua `ws://localhost:8080/ws/websocket` với JWT trong frame CONNECT. Kịch bản tăng dần 100 → 300 → 500 người cùng subscribe `/topic/lesson/9/chat`. Một người dùng ảo khác gửi `/app/chat/9` mỗi 3 giây, nội dung kèm timestamp để đo độ trễ nhận tin. Dữ liệu test đã được xoá sau khi chạy.

## Test E2E trên trình duyệt (web local qua cổng 3005)
| Luồng | Kết quả |
| --- | --- |
| Đăng ký, OTP (Mailpit), đăng nhập | Đạt |
| Giỏ hàng, mã PYDEMO30 | Đạt sau khi sửa phần hiển thị ở FE |
| Thanh toán PayOS | **Lỗi 422** ở máy local (chưa có cấu hình sandbox) |
| Học, lưu tiến độ, vào học tiếp đúng chỗ (YouTube) | Đạt sau khi sửa |
| Lồng tiếng AI, gia sư AI (gợi ý và trả lời stream), ghi chú, Hỏi đáp | Đạt (key Gemini mới) |
| Đăng ký giảng viên mới (CCCD mẫu), tạo khóa 161, gửi duyệt, admin duyệt | Đạt |
| Ghi danh, quiz đạt 10/10, tiến độ 100%, chứng chỉ (trang, `/verify`, PDF) | Đạt |
| Hỏi đáp realtime giữa 2 tab | Đạt |
| Insight AI khóa 9 (tóm tắt mới sau khoảng 40 giây) | Đạt |

Dữ liệu test để lại trong DB local:
- khóa 161 "[E2E] Khoá test duyệt";
- chứng chỉ `LL-2026-PBU5DXM4` của student2;
- tài khoản `e2e.instr.0001@lms.local` và `e2e.student.1128a@lms.local`.
