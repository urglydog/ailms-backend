# Đợt 8: Hoàn thiện chạy production và sẵn sàng giám sát

> Nhánh `perf` (backend). Làm sau khi nhận kết quả [KIEM_TRA_SERVER.md](KIEM_TRA_SERVER.md) ngày 25/11/2026.
> Phần Prometheus + Grafana nằm ở nhánh riêng `feat/monitoring`, xem
> [GIAM_SAT_PROMETHEUS_GRAFANA.md](GIAM_SAT_PROMETHEUS_GRAFANA.md) (có trên nhánh đó).

## Bối cảnh: kết quả kiểm tra server

| Chỉ số | Giá trị | Ý nghĩa |
| --- | --- | --- |
| RAM | 3,7 GB, còn 0,58–1,2 GB, đang dùng swap 1,7–2,1 GB | **Điểm nghẽn chính** |
| CPU | 2 vCPU, load 0,2–0,85 | Dư |
| Ổ đĩa | 38 GB, còn 12 GB sau khi dọn | Đủ |
| Backend | profile `dev`, `mvn spring-boot:run`, có lúc 1,67 GB RAM, 139 dòng log SQL/10 phút | Chạy chế độ phát triển trên môi trường thật |
| MySQL | dữ liệu vài chục MB, buffer pool 128 MB, dùng tối đa 21/151 kết nối | Không cần chỉnh |

## Việc đã có sẵn trên develop (PR #240, nhóm làm ngày 25–26/11)

Kế hoạch 5 việc ban đầu có 4 việc đã được làm trong PR #240, nên đợt này **không làm lại**:

| Việc trong kế hoạch | Đã có ở develop |
| --- | --- |
| 1. Bản build production | `Dockerfile` (jar + `eclipse-temurin:17-jre`, user không phải root), profile `prod` (`application-prod.yml` giữ `db/dev` cho Flyway) — xem [QUY_TRINH_PHAT_HANH_BACKEND.md](QUY_TRINH_PHAT_HANH_BACKEND.md) |
| 2. Giới hạn RAM | `mem_limit` mọi container, JVM `-Xmx896m -XX:+UseSerialGC` — xem [GIOI_HAN_RAM.md](GIOI_HAN_RAM.md) |
| 3. Xoay vòng log | backend, 3 service AI, Caddy |
| 4. Slow query log | MySQL `--slow_query_log=1` |

## Việc đợt 8 bổ sung

| Việc | Trước | Sau |
| --- | --- | --- |
| Xoay vòng log cho MySQL, Redis, Adminer, Mailpit | Log tăng mãi | Mẫu chung `x-logging` (3 file × 10 MB) trong `docker-compose.yml` |
| Chờ tắt an toàn | Docker chờ 10 s rồi kill, trong khi backend cần tới 30 s (`server.shutdown: graceful`, đợt 6) | `stop_grace_period: 35s` cho backend |
| Endpoint số liệu | Actuator chỉ có `health`, `info` | Thêm `micrometer-registry-prometheus`, mở `/actuator/prometheus` |
| Độ trễ p95/p99 | Micrometer mặc định không có histogram | `percentiles-histogram.http.server.requests: true` |
| Nhãn số liệu | — | Mọi số liệu có nhãn `application=ai-powered-lms` |

### Vì sao `/actuator/prometheus` không cần cổng riêng

- `Caddyfile` đã chặn `/actuator/*` (trừ `health`) từ Internet và trả 404 (PR #239), nên người ngoài không đọc được.
- Cổng 8080 của backend chỉ mở ở `127.0.0.1` trên máy chủ.
- Prometheus đọc `http://backend:8080/actuator/prometheus` qua mạng nội bộ Docker.
- Trong `SecurityConfig`, endpoint này được cho phép không cần JWT, vì Prometheus không đăng nhập được.
  Lớp bảo vệ là Caddy cùng mạng Docker. Nếu sau này đổi reverse proxy thì **phải giữ luật chặn `/actuator/*`**.

### Chi phí của endpoint số liệu

Micrometer chỉ giữ bộ đếm trong RAM, khoảng vài MB, và chỉ tính toán khi có người đọc endpoint (Prometheus đọc
15 s/lần). Không chạy Prometheus thì endpoint này vẫn tồn tại nhưng gần như không tốn gì.

## Kiểm chứng

- `mvn package`: **1212/1212** test qua, gồm test chốt chặn endpoint GET công khai.
- `docker compose config`: hợp lệ; MySQL/Redis/Adminer/Mailpit nhận đúng `logging` 10 MB × 3.
- Kết quả chạy thử bản production ở local: xem mục dưới.

## Chạy thử bản production ở local (26/11/2026)

Chạy image production (`Dockerfile`, profile `prod`, `--memory 1536m` như server) cạnh backend dev, cùng MySQL và
Redis. Gửi cùng 300 request `GET /api/v1/courses` vào mỗi bản rồi đo:

| | Bản dev (server đang chạy) | Bản production |
| --- | --- | --- |
| RAM sau 300 request | **1,62 GB** | **0,90 GB** (−45%) |
| Số dòng log trong 2 phút | 28.508 | 146 |
| Câu SQL in ra log | 264 | 0 |
| Khởi động | ~3 phút (biên dịch Maven) | 23 giây |

`/actuator/prometheus` trả đủ số liệu `http_server_requests_seconds_bucket`, `jvm_memory_used_bytes`,
`hikaricp_connections_active`, kèm nhãn `application="ai-powered-lms"`.

**Ước lượng cho server 3,7 GB:** chuyển backend sang bản production giải phóng khoảng **0,7 GB RAM**. Theo số đo
ngày 25/11, RAM available sẽ lên khoảng 1,3–1,9 GB, đủ cho Prometheus + Grafana + node-exporter (trần ~0,5 GB).
**Cần đo lại trên server** sau khi deploy bản production rồi mới bật giám sát.
