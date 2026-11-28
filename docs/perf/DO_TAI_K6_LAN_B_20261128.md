# Đo tải k6 lần B — bản đang chạy production (28/11/2026)

## Bối cảnh
- Server: Hetzner CX23 (2 vCPU, 3,8 GB RAM, đĩa 38 GB), Docker Compose một máy; backend chạy image `lms-backend:prod` build từ `develop` (đợt 9, 35 commit hiệu năng/bảo mật/tính năng) cộng sửa quyền flashcard và lộ trình học AI.
- Thời điểm: **19:16:21 → 19:20:53 giờ Việt Nam** (12:16:21 → 12:20:53 UTC), 4 phút 30 giây.
- Kịch bản: `loadtest/k6-public.js` — chỉ gọi API đọc công khai (danh sách khoá học, danh mục, banner, chi tiết khoá, tìm kiếm có từ khoá), người dùng ảo nghỉ 1–3 giây giữa các lượt. Mỗi người dùng ảo gửi một IP riêng trong `X-Forwarded-For` nên rate limit tính như 50 người khác nhau. k6 chạy trong mạng Docker, gọi thẳng `backend:8080` (không qua Caddy/Internet).
- Cấu hình: 50 người dùng ảo, tăng dần 1 phút, giữ đỉnh 3 phút, giảm dần 30 giây. k6 giới hạn 256 MB RAM.
- An toàn: có "cầu dao" tự dừng k6 nếu RAM khả dụng < 300 MB, swap > 3,5 GB, hoặc backend không `healthy` hai lần liên tiếp. **Cầu dao không kích hoạt.**
- Trước đó đã chạy thử 2 người dùng ảo (1 phút 35 giây): 276 request, 0 lỗi, p95 danh sách 57 ms.

## Kết quả
| Chỉ số | Giá trị | Ngưỡng |
|---|---|---|
| Số request | 27.456 | — |
| Request/giây | **101,4** | — |
| Lỗi (`http_req_failed`) | **0,00 %** (0/27.456) | < 1 % |
| Kiểm tra (`checks`) | 100 % (16.473/16.473) | — |
| p95 danh sách khoá học / danh mục / banner | **41,2 ms** | < 800 ms |
| p95 chi tiết khoá học | **40,3 ms** | < 800 ms |
| Độ trễ mọi request: trung bình / p50 / p95 / p99 / lớn nhất | 13,7 / 8,7 / 40,9 / 73,4 / 297,9 ms | — |
| Vòng lặp hoàn chỉnh | 5.491 (20,3/giây) | — |
| Dữ liệu nhận | 96 MB | — |

Cả ba ngưỡng đạt.

## Tài nguyên máy chủ trong lúc đo (22 mẫu, 10 giây/mẫu)
| Chỉ số | Giá trị |
|---|---|
| RAM khả dụng thấp nhất / cao nhất | **982 MB** / 1.075 MB |
| Swap dùng | 883 MB (không tăng thêm trong lúc đo) |
| Backend: RAM lớn nhất / CPU lớn nhất / CPU trung bình | 822 MB (trần 1,5 GB) / 125 % (trên 2 vCPU) / 70 % |
| MySQL: RAM lớn nhất / CPU lớn nhất | 154 MB / 31 % |
| Redis: RAM lớn nhất | 9 MB |

Sau đo: backend `healthy`, 0 lần tự khởi động lại, không có dòng `ERROR` trong log, không có yêu cầu bị chặn 429.

## Nhận xét
- Với 50 người dùng đồng thời trên API đọc công khai, server giữ được ~100 request/giây, độ trễ p95 ~41 ms, không lỗi — cao hơn nhiều so với mục tiêu đặt ra (p95 < 800 ms).
- Nút cổ chai quan sát được là **CPU của backend** (đỉnh 125 % trên 2 vCPU), không phải RAM (RAM khả dụng không xuống dưới 982 MB, swap không tăng).
- **Giới hạn của phép đo:** chỉ API đọc công khai, không qua Caddy/Internet, không có đăng nhập/ghi DB/thanh toán/WebSocket/AI. Chưa phản ánh tải học viên thật (nộp bài thi, chat, tiến độ video 15 giây/lần). Chưa đo 100 người dùng ảo (tuỳ chọn lần C).
- Về cảnh báo: luật Grafana "RAM máy chủ thấp" (< 300 MB, 5 phút) và "API chậm" (p95 > 2 giây, 10 phút) không bị chạm tới trong lần đo này, nên không có tin Telegram nào — đúng kỳ vọng.

## Tệp số liệu gốc
- `docs/perf/k6/k6-B-20261128.txt` — bản tóm tắt k6 (đã bỏ các dòng tiến trình từng giây).
- `docs/perf/k6/ram-B-20261128.csv` — RAM máy chủ và từng container mỗi 10 giây.
- Ảnh Grafana (`LinguaLearn — Tổng quan`) và UptimeRobot trong khung 19:10–19:25 do chủ dự án chụp bổ sung.
