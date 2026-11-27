# Bật giám sát Prometheus + Grafana (27/11/2026)

## Đã làm
1. Phát hành backend có Micrometer (`/actuator/prometheus`) — 1212 test đạt, sao lưu DB trước, giữ `lms-backend:prev` để quay lại. Web ngừng ~70 s.
2. Bật đúng 3 dịch vụ giám sát bằng `docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d --no-deps prometheus grafana node-exporter` (**không** dùng lệnh `up -d` trơn vì sẽ tạo lại cả MySQL/Redis/backend).

## Kết quả kiểm tra
| Hạng mục | Kết quả |
| --- | --- |
| Nguồn số liệu Prometheus | `backend`, `node`, `prometheus`: cả 3 **up**, mỗi lần thu < 0,05 s |
| Grafana | `/api/health` ok (v12.1.1); chỉ có cảnh báo vô hại lần đầu (di trú CSDL, thiếu thư mục `alerting` vì chưa có luật cảnh báo) |
| Truy vấn thật | request/giây của backend, RAM còn dùng được (1190 MB), đĩa trống (5,5 GB) đều có số liệu |
| Cổng mở | chỉ `127.0.0.1:3001` (Grafana); Prometheus và node-exporter không mở cổng |
| Internet | `/actuator/prometheus` → 404 (Caddy chặn) |
| Dịch vụ cũ | không bị tạo lại; 0 container không `Up`; 0 OOM; web 200 |

## Tài nguyên sau khi bật (so với trần)
Prometheus 73 / 256 MB; **Grafana 135 / 192 MB (70% trần — theo dõi)**; node-exporter 17 / 48 MB. Đĩa trống 6,6 → 5,2 GB (3 image + dữ liệu); số liệu giữ 7 ngày hoặc 1 GB (`PROMETHEUS_RETENTION*` trong `.env`). Swap 598 → 849 MB.

## Cách xem Grafana
```bash
# trên máy cá nhân: mở đường hầm SSH rồi vào http://localhost:3001
ssh -L 3001:127.0.0.1:3001 dev-thien@46.224.175.90
# tài khoản: admin; mật khẩu: lấy từ be/.env (khóa GRAFANA_ADMIN_PASSWORD), không gửi qua chat
```
Dashboard "LinguaLearn — Tổng quan" tự nạp (13 panel: request/giây, độ trễ p50/p95/p99, 5xx và 429, 10 endpoint chậm nhất, heap JVM, kết nối DB, CPU, RAM/swap, đĩa).

## Tắt / bật lại phần giám sát (không đụng web)
```bash
cd /var/lms/be
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml stop prometheus grafana node-exporter
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d --no-deps prometheus grafana node-exporter
```
Dừng bằng tay thì **không tự bật lại sau reboot**; bình thường (không dừng tay) 3 container tự lên cùng máy.

## Còn thiếu
- Chưa có luật cảnh báo (backend sập, đĩa > 80%, RAM thấp, sao lưu quá 26 giờ) và chưa có kênh nhận cảnh báo.
- Chưa thu số liệu MySQL, Redis, AI worker (chỉ backend + máy chủ).
- Chưa thu chỉ số sao lưu (`/home/dev-thien/ops/metrics/*.prom`): cần gắn thư mục này vào node-exporter (textfile collector).
