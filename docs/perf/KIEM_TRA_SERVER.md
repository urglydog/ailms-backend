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

## 5. Cấu hình MySQL và Redis

Máy local đang để mặc định `innodb_buffer_pool_size` = 128 MB, quá nhỏ cho production. Cần số liệu thật để
chỉnh theo RAM máy.

```bash
# Mật khẩu root MySQL lấy từ file .env cạnh docker-compose — KHÔNG in ra màn hình
docker exec lms_mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -e "
  SELECT @@version, @@innodb_buffer_pool_size/1024/1024 AS buffer_mb, @@max_connections, @@slow_query_log;
  SHOW GLOBAL STATUS WHERE Variable_name IN (\"Max_used_connections\",\"Threads_connected\",\"Slow_queries\",\"Uptime\",\"Questions\");
  SELECT table_name, ROUND((data_length+index_length)/1024/1024,1) AS mb, table_rows
    FROM information_schema.tables WHERE table_schema=DATABASE() OR table_schema=\"lms_db\"
    ORDER BY (data_length+index_length) DESC LIMIT 10;"'
# Redis: bộ nhớ đang dùng và giới hạn (mật khẩu đọc từ biến môi trường, không in ra)
docker exec lms_redis sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning INFO memory | grep -E "used_memory_human|maxmemory_human|maxmemory_policy"' 2>/dev/null \
  || echo "Redis không có REDIS_PASSWORD trong env container — ghi lại để nhóm biết"
```

## 6. Log và swap (nguy cơ đầy ổ đĩa)

Trong `docker-compose.yml` chỉ 3 service AI có xoay vòng log; backend, MySQL, Redis, Caddy, frontend thì log
Docker tăng mãi.

```bash
# Dung lượng file log Docker của từng container
for c in $(docker ps --format '{{.Names}}'); do
  f=$(docker inspect --format '{{.LogPath}}' $c); echo "$c $(sudo du -h "$f" 2>/dev/null | cut -f1)"; done
swapon --show; cat /proc/sys/vm/swappiness
# Container nào từng bị hệ điều hành giết vì thiếu RAM (OOM)
docker inspect --format '{{.Name}} OOMKilled={{.State.OOMKilled}} Restarts={{.RestartCount}}' $(docker ps -q)
sudo dmesg 2>/dev/null | grep -iE 'out of memory|oom-kill' | tail -5
```

## 7. Phiên bản và cách triển khai

```bash
docker exec lms_backend java -version 2>&1 | head -1
docker compose version
# Code trên server cập nhật bằng cách nào? (git pull trong thư mục compose, CI, copy tay…)
cd "$(docker inspect lms_backend --format '{{index .Config.Labels "com.docker.compose.project.working_dir"}}')" \
  && git log --oneline -1 && git branch --show-current
```

Câu hỏi trả lời bằng chữ (không cần lệnh):
- Frontend (Next.js) đang chạy ở đâu: Vercel, hay cũng trên server này?
- Có bao nhiêu người dùng thật/ngày (ước lượng), có kế hoạch demo đông người không?
- Gói VPS là gì (nhà cung cấp, tên gói), có nâng cấp được không?

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

### 5. MySQL / Redis
- MySQL version ___, buffer pool ___ MB, max_connections ___, Max_used_connections ___, slow_query_log ___
- 10 bảng lớn nhất: (dán)
- Redis used ___, maxmemory ___, policy ___
Nhận xét:

### 6. Log, swap, OOM
- Log Docker từng container: (dán)
- Swap: ___, swappiness ___
- Container từng OOMKilled / restart nhiều: ___
Nhận xét:

### 7. Phiên bản, triển khai
- Java trong container backend: ___
- Docker compose: ___
- Commit/nhánh đang chạy: ___
- Frontend chạy ở: ___
- Người dùng/ngày ước lượng: ___; gói VPS: ___
```

---

## Nhóm sẽ dùng kết quả để quyết định

| Câu hỏi | Ngưỡng |
| --- | --- |
| Có đủ chỗ cho Prometheus + Grafana không? | RAM available ≥ 1 GB và ổ đĩa trống ≥ 5 GB (lưu số liệu 15 ngày, giới hạn mỗi container 256 MB) |
| Có cần đặt giới hạn RAM cho từng container không? | Nên đặt nếu tổng RAM container > 70% RAM máy |
| MySQL có cần chỉnh không? | `innodb_buffer_pool_size` nên ≈ 25–40% RAM máy (khi MySQL chung máy với web); `Max_used_connections` gần `max_connections` thì phải tăng |
| Có cần xoay vòng log không? | File log container nào > 500 MB, hoặc ổ đĩa trống < 20% → bắt buộc |
| Có nâng Java 21 được ngay không? | Biết Java hiện tại và cách deploy để đổi image an toàn |
| Backend có đang chạy chế độ dev không? | Nếu profile là `dev` hoặc lệnh chạy là `mvn spring-boot:run` → nên chuyển sang bản build production (jar + JRE, profile prod) |
