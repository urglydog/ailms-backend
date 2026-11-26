# Giám sát bằng Prometheus + Grafana

> Nhánh `feat/monitoring`. Cần nhánh `perf` (đợt 8, [DOT8_SanSangGiamSat.md](DOT8_SanSangGiamSat.md)) đã được
> merge và **phát hành** trước, vì backend phải có endpoint `/actuator/prometheus`.

## Gồm những gì

| Thành phần | Vai trò | Trần RAM | RAM đo ở local |
| --- | --- | --- | --- |
| `lms_prometheus` (`prom/prometheus:v3.5.0`) | Đọc số liệu 15 s/lần, lưu tối đa 15 ngày hoặc 2 GB | 256 MB | 29 MB |
| `lms_grafana` (`grafana/grafana-oss:12.1.1`) | Vẽ dashboard | 192 MB | 95 MB |
| `lms_node_exporter` (`prom/node-exporter:v1.9.1`) | RAM, CPU, ổ đĩa, swap của máy chủ | 48 MB | 8 MB |

Tổng trần ~0,5 GB; thực tế đo được ~130 MB.

File trong repo:
- `docker-compose.monitoring.yml`: 3 container trên, chạy kèm `docker-compose.yml`.
- `monitoring/prometheus.yml`: danh sách nơi đọc số liệu (`backend:8080`, `node-exporter:9100`).
- `monitoring/grafana/provisioning/`: tự khai nguồn dữ liệu Prometheus và nạp dashboard, không phải bấm tay.
- `monitoring/grafana/dashboards/lms-overview.json`: dashboard **LinguaLearn — Tổng quan**.

## Dashboard có gì

| Nhóm | Biểu đồ |
| --- | --- |
| Backend — request | Request/giây theo mã trạng thái; độ trễ p50/p95/p99; lỗi 5xx và request bị chặn 429; 10 endpoint chậm nhất |
| Backend — JVM và DB | Heap JVM (đang dùng / `-Xmx`); kết nối HikariCP (đang dùng / đang chờ / tối đa 20); CPU tiến trình |
| Máy chủ | RAM available và swap đã dùng; CPU máy; ổ đĩa `/` còn trống (đỏ < 15%, cam < 30%) |

## Điều kiện trước khi bật

1. Backend đang chạy **image production** (`lms-backend:prod`) có đợt 8 — kiểm tra:
   `docker exec lms_backend curl -s localhost:8080/actuator/prometheus | head -3` phải ra số liệu, không phải 404.
2. Đo RAM sau khi backend chạy ổn định vài giờ: `free -h`. **Cần `available` ≥ 1,5 GB** (giữ ~1 GB dư sau khi
   bật giám sát). Nếu thấp hơn: nâng VPS (vd. Hetzner CX23 → CX33, 8 GB) hoặc dùng Grafana Cloud bản miễn phí.

## Bật trên server

```bash
cd /var/lms/be
git fetch origin && git checkout feat/monitoring   # hoặc develop sau khi đã merge nhánh này

# Mật khẩu admin Grafana: thêm vào be/.env (KHÔNG commit). Chọn mật khẩu mạnh.
echo 'GRAFANA_ADMIN_PASSWORD=<mat-khau-manh>' >> .env

docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d prometheus grafana node-exporter
docker ps --format '{{.Names}}\t{{.Status}}' | grep -E 'prometheus|grafana|node_exporter'
```

Lệnh trên chỉ tạo 3 container giám sát, **không** tạo lại backend, MySQL, Redis hay AI.

Kiểm tra Prometheus đọc được backend:

```bash
docker exec lms_prometheus wget -qO- http://localhost:9090/api/v1/targets | grep -o '"health":"[a-z]*"'
# phải ra 3 dòng "health":"up"
```

## Xem dashboard (qua SSH tunnel, không mở cổng ra Internet)

Grafana chỉ nghe `127.0.0.1:3001` trên server. Từ máy của mình:

```bash
ssh -L 3001:127.0.0.1:3001 <user>@46.224.175.90
```

Giữ cửa sổ SSH mở, rồi mở `http://localhost:3001` trên trình duyệt, đăng nhập `admin` / mật khẩu đã đặt.
Dashboard **LinguaLearn — Tổng quan** là trang chủ.

## Tắt / gỡ

```bash
# Tạm tắt (giữ số liệu):
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml stop prometheus grafana node-exporter
# Gỡ hẳn và xóa số liệu:
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml rm -sf prometheus grafana node-exporter
docker volume rm be_prometheus_data be_grafana_data
```

## Bảo mật

- Không container nào mở cổng ra Internet. Grafana chỉ ở `127.0.0.1`; Prometheus và node-exporter không có cổng
  trên máy chủ.
- `/actuator/prometheus` không cần JWT (Prometheus không đăng nhập được), được bảo vệ bởi `Caddyfile` chặn
  `/actuator/*` từ Internet. **Đổi reverse proxy thì phải giữ luật này.**
- Grafana tắt đăng ký tài khoản và gửi thống kê; mật khẩu admin bắt buộc có trong `.env` (thiếu thì compose báo lỗi).

## Đã thử ở local (26/11/2026)

- 3 target `backend`, `node`, `prometheus` đều `up`.
- Dashboard tự nạp vào thư mục **LinguaLearn**; 16/18 truy vấn có dữ liệu ngay. 2 truy vấn còn lại:
  - lỗi 5xx: không có lỗi 5xx nào trong lúc thử (đúng);
  - ổ đĩa `/`: Docker Desktop trên Windows không có phân vùng `/` dạng ext4; trên server Ubuntu có.
- Số liệu 429 lên ~1 request/giây khi gửi request liên tục, đúng như rate limit đợt 4.
- `node-exporter` dùng `/:/host:ro,rslave` — chuẩn trên Linux; Docker Desktop không hỗ trợ `rslave` nên khi thử
  local phải bỏ.
