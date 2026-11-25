# Giới hạn RAM từng container (26/11/2026)

Server Hetzner CX23: 3,7 GB RAM, đã có swap 4 GB. Trần dưới đây chỉ là **giới hạn trên** (không chiếm sẵn RAM), đặt để một container lỗi
hoặc quá tải không kéo cả máy xuống. Cấu hình nằm ở `mem_limit` trong `docker-compose.yml`.

| Container | Trần RAM | Đo lúc đặt | Ghi chú |
| --- | --- | --- | --- |
| lms_backend | 1536 MB | ~790 MB | Java heap tối đa 896 MB (+ metaspace 256 MB) |
| lms_mysql | 768 MB | ~400 MB | buffer pool 128 MB, dữ liệu rất nhỏ |
| lms_ai_worker | 1280 MB | ~290 MB | chạy 8 tác vụ song song (lồng tiếng/ffmpeg); khi máy cạn RAM bị hệ điều hành dừng **đầu tiên** (`oom_score_adj: 500`) |
| lms_ai_api | 384 MB | ~130 MB | |
| lms_redis | 320 MB | ~9 MB | chừa chỗ cho ghi lại file AOF |
| lms_ai_beat | 192 MB | ~70 MB | |
| lms_caddy / lms_mailpit / lms_adminer | 128 / 96 / 64 MB | 12–24 MB | |

Tổng trần lớn hơn RAM máy là **chủ ý**: các container không đạt trần cùng lúc; mức dùng thực tế khoảng 1,8 GB.

## Khi một container bị dừng vì vượt trần (OOM)
```bash
docker inspect <tên> --format '{{.State.OOMKilled}} {{.RestartCount}}'   # true = bị dừng vì hết RAM
docker stats --no-stream                                                # xem mức dùng thật
```
Container tự chạy lại nhờ `restart: unless-stopped`. Nếu lặp lại: tăng trần của đúng container đó (sửa `mem_limit`, rồi
`docker update --memory <mới> --memory-swap <gấp đôi> <tên>` để áp dụng ngay không cần khởi động lại), hoặc giảm tải (vd `--concurrency` của ai-worker).

## Áp dụng
`docker update` đã áp dụng trên container đang chạy (không gián đoạn). `oom_score_adj` chỉ có hiệu lực khi container được tạo lại
(lần `docker compose up -d` kế tiếp có thay đổi cấu hình, hoặc khi phát hành).
