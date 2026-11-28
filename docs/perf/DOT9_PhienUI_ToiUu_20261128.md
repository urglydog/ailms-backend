# Đợt 9 – Tối ưu backend cho nhiều người dùng đồng thời (27–28/11/2026)

Nhánh: `fix/post-merge-polish`. Đo bằng k6 (`grafana/k6` trong Docker); mỗi người dùng ảo gửi một `X-Forwarded-For` riêng để không bị chặn vì vượt giới hạn tốc độ.

## Kết quả tổng
| Kịch bản | Trước | Sau |
| --- | --- | --- |
| 200 người dùng ảo (luồng học viên) | 68 req/s, lỗi 4,4% | 455 req/s, lỗi 0% |
| 500 người dùng ảo | 92 req/s | 531 req/s, lỗi 0,01% |
| WebSocket: 500 người cùng nghe 1 bài | — | kết nối 100%, độ trễ nhận tin p95 37 ms, không rớt kết nối, không mất tin |

## Các thay đổi
| Commit | Vấn đề gốc | Cách sửa |
| --- | --- | --- |
| `289764e` | Mở trình phát bài học chạy khoảng 150 câu truy vấn (N+1: lấy track, transcript từng ngôn ngữ và tiến độ từng bài riêng lẻ) | Gộp thành vài truy vấn theo lô |
| `69a77de` | API gợi ý câu hỏi gia sư giữ 2 kết nối DB cùng lúc, nên hết pool khi tải cao | Bỏ việc giữ kết nối lồng nhau; cache phần quét câu hỏi phổ biến 60 giây |
| `981a2d3` | Insight AI quét lại tới 5000 dòng chat mỗi lần mở | Dùng lại kết quả quét của từng khóa trong 60 giây |
| `93e1f4c` | Danh sách Hỏi đáp chạy 1 truy vấn cho mỗi tác giả | Lấy tác giả cùng lúc với danh sách |
| `936a531` | API gửi tiến độ học (bị gọi 15 giây/lần với mọi người đang xem) lần nào cũng tính lại % cả khóa: 4–5 truy vấn và 1 lệnh UPDATE | Chỉ tính lại khi có bài **vừa** hoàn thành (`LessonProgressService.recordProgress`) |

## WebSocket (chỉ đo, chưa đổi cấu hình)
Đi qua 3 bậc 100 → 300 → 500 người, mỗi bậc giữ 40 giây (bậc 500 giữ 2 phút). CPU backend dao động 17–57%, đỉnh 160% trong vài giây; RAM ổn định khoảng 1,15 GiB.

Khi cần chịu tải cao hơn thì làm tiếp những việc sau (`WebSocketConfig.java`):
- thêm heartbeat;
- tăng thread pool cho kênh outbound;
- đặt `sendTimeLimit` / `sendBufferSizeLimit`;
- khi chạy nhiều instance thì chuyển sang broker relay.

Script test lưu tạm trong scratchpad của phiên làm việc. Nếu cần chạy lại, viết lại theo mô tả trong `../test/KIEM_THU_PhienUI_20261128.md`.
