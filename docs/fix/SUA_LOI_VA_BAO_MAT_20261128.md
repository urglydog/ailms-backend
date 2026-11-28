# Sửa lỗi và bảo mật backend (28/11/2026)

Nhánh: `fix/post-merge-polish`.

| Commit | Lỗi | Nguyên nhân | Cách sửa |
| --- | --- | --- | --- |
| `936a531` | Có thể giả IP để lách bộ giới hạn tốc độ | `RateLimitFilter.clientIp` tin `X-Forwarded-For` của mọi request | Chỉ tin header này khi kết nối đến từ loopback hoặc mạng riêng (Caddy, mạng Docker). IP public gọi thẳng thì dùng IP thật của kết nối |
| `d54be96` | `GET /api/v1/streak/me` trả 500 trên mọi trang với người chưa có streak | `UserStreak.version = 0` cộng với ID gán tay (`@MapsId`) khiến Spring Data `merge()` thay vì `persist()`: lệnh UPDATE trúng 0 dòng nên ném OptimisticLock | Để `version` mặc định là null |
| `d54be96` | Gọi sai method (vd. `GET /lessons/{id}/progress`) trả 500 | Lưới an toàn `Exception.class` nuốt cả lỗi 4xx của Spring | `GlobalExceptionHandler` giữ đúng mã 4xx (405, 400, 404, 415…); body JSON sai trả 400 |
| `b9a5112` | Bất kỳ ai cũng nghe lén hoặc gửi được vào Hỏi đáp của mọi bài qua WebSocket | `StompAuthInterceptor` không kiểm tra topic `/topic/lesson/{id}/**` | SUBSCRIBE `/topic/lesson/{id}/**` và SEND `/app/chat/{id}` phải qua `EnrollmentSecurity.canAccessLesson(email, id, true)` (đã ghi danh, là chủ khóa, hoặc bài xem thử) |

Các lỗi đã biết, chưa sửa (tìm thấy khi test E2E):
- API tiến độ tin luôn `watchedSec` do client gửi lên, nên gửi một request là bài được tính hoàn thành.
- Danh sách kiểm duyệt của admin ghi "0 bài".
- Video YouTube dưới 60 giây bị chặn vì thời lượng làm tròn xuống 0 phút.
- PayOS trả 422 khi tạo thanh toán ở máy local.
