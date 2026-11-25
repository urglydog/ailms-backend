# Kiểm tra server trước khi thêm giám sát (Prometheus + Grafana)

> **Dành cho:** người quản lý server `46.224.175.90` (backend LinguaLearn chạy sau Caddy tại
> `https://46.224.175.90.nip.io`).
> **Cách dùng:** đưa nguyên file này cho AI (Claude, ChatGPT…) đang có quyền chạy lệnh trên server, hoặc tự
> chạy từng lệnh. Tất cả lệnh đều **chỉ đọc**, không sửa gì trên server.
> **Gửi lại:** phần "Mẫu kết quả gửi lại" ở cuối file.

---

## Hướng dẫn cho AI đọc file này

Bạn đang giúp nhóm LinguaLearn kiểm tra tài nguyên server. Yêu cầu:

1. Chỉ chạy các lệnh trong mục 1–4. **Không** sửa file, restart container, cài gói hay xóa dữ liệu.
2. **Không** in giá trị bí mật (mật khẩu, khóa API, token). Nếu lệnh nào in ra biến môi trường, chỉ ghi
   **tên** biến, thay giá trị bằng `***`.
3. Điền kết quả vào "Mẫu kết quả gửi lại", kèm nhận xét ngắn ở mỗi mục (đủ / thiếu / bất thường).

---

## 1. Tài nguyên máy

```bash
free -h            # RAM tổng, đã dùng, còn trống (available)
nproc              # số nhân CPU
df -h /            # dung lượng ổ đĩa gốc
uptime             # tải CPU trung bình 1/5/15 phút
cat /etc/os-release | head -3
```

## 2. Docker đang chạy gì, tốn bao nhiêu

```bash
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
docker stats --no-stream --format 'table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}'
docker system df   # dung lượng image, volume, cache của Docker
```

## 3. Backend đang chạy chế độ nào (quan trọng)

Repo có `Dockerfile.dev` chạy `mvn spring-boot:run` với profile `dev` (log DEBUG + in mọi câu SQL, khởi động
~3 phút, tốn RAM cho cả Maven). Cần biết server đang dùng chế độ này hay một bản build production.

```bash
# Profile Spring đang bật (chỉ in tên biến và giá trị của đúng biến này)
docker inspect lms_backend --format '{{range .Config.Env}}{{println .}}{{end}}' | grep -E '^SPRING_PROFILES_ACTIVE='
# Lệnh khởi động container backend
docker inspect lms_backend --format '{{.Config.Image}} | {{json .Config.Cmd}} | {{json .Config.Entrypoint}}'
# Thời gian khởi động gần nhất và có log SQL không
docker logs lms_backend 2>&1 | grep -m1 'Started LmsApplication'
docker logs --since 10m lms_backend 2>&1 | grep -c 'Hibernate:'
# Thư mục chứa docker-compose đang dùng
docker inspect lms_backend --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}'
```

Nếu tên container khác `lms_backend`, thay bằng tên backend thấy ở mục 2.

## 4. Các cổng đang mở ra Internet

```bash
ss -tlnp | grep -vE '127\.0\.0\.1|::1' | head -20
```

---

## Mẫu kết quả gửi lại

```
### 1. Tài nguyên máy
- RAM: tổng ___ GB, available ___ GB
- CPU: ___ nhân, load average ___ / ___ / ___
- Ổ đĩa /: tổng ___ GB, còn trống ___ GB
- Hệ điều hành: ___
Nhận xét:

### 2. Container
(dán bảng docker ps và docker stats)
- Docker chiếm: image ___ GB, volume ___ GB, build cache ___ GB
Nhận xét:

### 3. Chế độ backend
- SPRING_PROFILES_ACTIVE = ___
- Image / lệnh chạy: ___
- Thời gian khởi động: ___ giây
- Số dòng "Hibernate:" trong 10 phút: ___
- Thư mục compose: ___
Nhận xét:

### 4. Cổng mở ra ngoài
(dán kết quả)
Nhận xét:
```

---

## Nhóm sẽ dùng kết quả để quyết định

| Câu hỏi | Ngưỡng |
| --- | --- |
| Có đủ chỗ cho Prometheus + Grafana không? | RAM available ≥ 1 GB và ổ đĩa trống ≥ 5 GB (lưu số liệu 15 ngày, giới hạn mỗi container 256 MB) |
| Có cần đặt giới hạn RAM cho từng container không? | Nên đặt nếu tổng RAM container > 70% RAM máy |
| Backend có đang chạy chế độ dev không? | Nếu profile là `dev` hoặc lệnh chạy là `mvn spring-boot:run` → nên chuyển sang bản build production (jar + JRE, profile prod) |
