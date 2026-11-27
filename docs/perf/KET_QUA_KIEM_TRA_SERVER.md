# Kết quả kiểm tra server LinguaLearn (trả lời KIEM_TRA_SERVER.md)

> **Ngày đo:** 25/11/2026, bằng các lệnh chỉ đọc trong `KIEM_TRA_SERVER.md`. Không có giá trị bí mật nào được ghi lại.
> **Kết luận ngắn:** lúc đo, server **chưa đủ chỗ để chạy thường trực Prometheus + Grafana** (RAM đã dùng swap, ổ đĩa
> chỉ trống 4,3 GB, sau khi dọn lên 12 GB). Cùng ngày, sau khi chuyển backend sang bản production, RAM available lên
> ~1,7 GB nên **đạt ngưỡng tối thiểu nhưng còn sát** (swap vẫn đang dùng ~1,9 GB). Chi tiết và đề xuất ở cuối file.

### 1. Tài nguyên máy
- RAM: tổng 3,7 GB, available **0,58 GB** (đo ngay sau khi khởi động lại backend; trước đó là 1,2 GB). Swap 4 GB, đã dùng 1,7–2,1 GB, swappiness 60.
- CPU: 2 nhân, load average 0,20 / 0,77 / 0,85 (nhẹ).
- Ổ đĩa `/`: tổng 38 GB, còn trống **12 GB** (69% đã dùng). Trước khi dọn dẹp hôm nay chỉ trống 4,3 GB (88%).
- Hệ điều hành: Ubuntu 22.04.5 LTS, uptime 32 ngày.

Nhận xét: CPU dư, **RAM thiếu** (đang phải dùng swap), ổ đĩa đã đỡ hơn sau khi dọn.

### 2. Container
| Container | RAM đang dùng | Ghi chú |
| --- | --- | --- |
| lms_backend | 1,67 GB (ngay sau khởi động; bình thường ~0,6 GB) | Spring Boot chạy bằng Maven, không giới hạn RAM |
| lms_ai_worker | 151 MB | Celery |
| lms_mysql | 108 MB | |
| roadmap-worker-1 | 44 MB / 200 MB | duy nhất có giới hạn RAM |
| lms_caddy, lms_mailpit, lms_ai_api, lms_ai_beat, lms_redis, lms_adminer | 1–21 MB mỗi cái | |

- 10 container, tất cả đang chạy, MySQL/Redis/Backend ở trạng thái healthy.
- Docker chiếm: image 10,2 GB, volume 0,87 GB, build cache 1,9 GB.

Nhận xét: không container nào bị giới hạn RAM ngoài roadmap-worker; backend là container ngốn RAM nhất.

### 3. Chế độ backend
- `SPRING_PROFILES_ACTIVE` = **dev**
- Image/lệnh chạy: `be-backend`, `mvn -B spring-boot:run` + devtools restart (từ `Dockerfile.dev`)
- Thời gian khởi động: ~50 giây (từ lúc container chạy đến lúc nhận request)
- Số dòng "Hibernate:" trong 10 phút: **139** (log mọi câu SQL)
- Thư mục compose: `/var/lms/be` (mã nguồn được mount thẳng vào container)

Nhận xét: **đang chạy chế độ phát triển trên môi trường thật**. Tốn RAM hơn và log nhiều hơn bản production.

> **Cập nhật cùng ngày (25/11/2026):** sau khi đo, backend đã được chuyển sang image production (`lms-backend:prod`,
> profile `prod`, chạy bằng file jar). Đo lại sau khi chuyển: backend dùng ~634 MB (so với 1,67 GB ngay sau khi khởi động
> ở chế độ dev), RAM available của máy tăng từ ~0,6 GB lên ~1,7 GB, log không còn in câu SQL, 0 lỗi khi khởi động,
> `healthy`. Quy trình phát hành mới: `docs/perf/QUY_TRINH_PHAT_HANH_BACKEND.md`.

### 4. Cổng mở ra ngoài
Chỉ 22 (SSH), 80, 443, 8443 (Caddy). Các cổng 8080 (backend), 8000 (AI), 8081 (Adminer), 8025/1025 (Mailpit) chỉ nghe ở localhost.
SSH chỉ cho đăng nhập bằng khoá, tắt mật khẩu, tắt root; fail2ban đang chạy.

Nhận xét: gọn, không có cổng thừa.

### 5. MySQL / Redis
- MySQL 8.0.46, buffer pool **128 MB**, max_connections 151, Max_used_connections **21**, slow_query_log **tắt** (4 truy vấn chậm từ lúc khởi động), uptime 29,9 ngày.
- 10 bảng lớn nhất: lesson_progress 13,1 MB (57.815 dòng), user_learning_days 2,0 MB, lessons 1,7 MB, payments 0,9 MB, enrollments 0,7 MB, course_reviews 0,5 MB, transcript_segments 0,4 MB, instructor_ledger_entries 0,4 MB, certificates 0,3 MB, courses 0,3 MB.
- Redis: **chưa đo được used/maxmemory/policy** — mật khẩu Redis không nằm trong biến môi trường của container (truyền qua `--requirepass` trong compose) nên lệnh trong file bị từ chối, và theo hướng dẫn của file tôi không lấy mật khẩu ra. Container Redis đang dùng ~7 MB.

Nhận xét: toàn bộ dữ liệu chỉ vài chục MB nên buffer pool 128 MB là đủ, không cần tăng. Nên bật slow_query_log.

### 6. Log, swap, OOM
- Log Docker (ước lượng qua `docker logs`, vì đọc file log cần sudo): backend **302 MB** (trước khi giới hạn), roadmap-worker 9,1 MB, caddy 2,1 MB, các container còn lại dưới 1 MB.
- Đã xử lý hôm nay: backend giới hạn 3 file × 20 MB. Còn thiếu giới hạn cho mysql, redis, caddy, adminer, mailpit (log nhỏ, chưa gấp).
- Swap: 4 GB, đã dùng ~1,7–2,1 GB; swappiness 60.
- OOMKilled / restart: **không container nào** (OOMKilled=false, Restarts=0). Chưa đọc được `dmesg` (cần sudo).

Nhận xét: chưa có dấu hiệu bị OOM, nhưng việc đang dùng swap cho thấy RAM đã sát giới hạn.

### 7. Phiên bản, triển khai
- Java trong container backend: **17** (openjdk 17.0.20.1; `pom.xml` cũng đặt Java 17)
- Docker Compose: v5.6.1
- Code chạy: nhánh `feat/additional-features` tại `/var/lms/be`; cập nhật bằng `git` rồi tạo lại container
- Frontend chạy ở: **Vercel** (không chạy trên server này)
- Gói VPS: **Hetzner Cloud, gói CX23** — 2 vCPU, 4 GB RAM, 40 GB đĩa local, lưu lượng ra 20 TB/tháng, giá **€5,49/tháng**,
  trung tâm dữ liệu Falkenstein (Đức, `fsn1`). Hetzner tính tiền theo giờ và có chức năng **Rescale** để đổi gói (nâng cấp phải tắt máy vài phút).
- Người dùng/ngày ước lượng: ___ *(chủ server điền)*

## Trả lời bảng quyết định
| Câu hỏi | Kết luận |
| --- | --- |
| Đủ chỗ cho Prometheus + Grafana? | **Lúc đo: chưa** (RAM available 0,6–1,2 GB, ổ đĩa 4,3 GB). **Sau khi chuyển backend sang production và dọn đĩa:** RAM available ~1,7 GB, ổ đĩa trống ~9 GB → đạt ngưỡng (≥ 1 GB và ≥ 5 GB) nhưng sát; nên giới hạn mỗi container giám sát 256 MB hoặc dùng Grafana Cloud để nhẹ hơn. |
| Cần đặt giới hạn RAM từng container? | Nên đặt cho backend (đang không giới hạn, có lúc lên 1,7 GB). Tổng RAM container nằm quanh 30–50% RAM máy tuỳ thời điểm. |
| MySQL có cần chỉnh? | Chưa cần: dữ liệu nhỏ hơn nhiều so với buffer pool 128 MB, kết nối tối đa dùng 21/151. Nên bật slow_query_log. |
| Cần xoay vòng log? | Đã làm cho backend (nguồn log lớn nhất). Các container khác log nhỏ, chưa bắt buộc. |
| Nâng Java 21 được ngay không? | Hiện cả `pom.xml` và Dockerfile.dev đều dùng Java 17, đổi image/triển khai cần thử riêng, chưa làm. |
| Backend có đang chạy chế độ dev không? | **Lúc đo: có.** Đã chuyển sang jar + JRE, profile prod trong cùng ngày (xem ghi chú ở mục 3). |

## Đề xuất
1. **Chạy Prometheus + Grafana thường trực trên server này chỉ nên làm với giới hạn RAM chặt** (mỗi container ≤ 256 MB, giữ dữ liệu ngắn ngày); nếu không muốn rủi ro thì dùng phương án 2 bên dưới.
2. Hai hướng thay thế rẻ: (a) dùng Grafana Cloud bản miễn phí, server chỉ chạy một agent nhỏ (~100 MB), hoặc (b) chỉ bật giám sát lúc cần đo tải hoặc demo, giữ dữ liệu 1–2 ngày.
3. Hướng bền vững: nâng VPS lên 8 GB RAM. Bảng Rescale của Hetzner (cùng dòng CX, đĩa vẫn 40 GB):

   | Gói | vCPU | RAM | Đĩa | Giá |
   | --- | --- | --- | --- | --- |
   | CX23 (đang dùng) | 2 | 4 GB | 40 GB | €5,49/tháng (€0,009/giờ) |
   | **CX33** | 4 | 8 GB | 40 GB | **€8,49/tháng (€0,014/giờ)** |
   | CX43 | 8 | 16 GB | 40 GB | €15,99/tháng |
   | CX53 | 16 | 32 GB | 40 GB | €29,49/tháng |

   Lên CX33 chỉ tốn thêm ~€3/tháng và đủ RAM để chạy Prometheus + Grafana ngay trên server này. Backend đã được chuyển
   sang bản production nên không còn tốn RAM của Maven.
4. Để thu số liệu, backend cần bật endpoint số liệu (Spring Actuator + Micrometer) — thay đổi code nhỏ, cần bàn riêng.
