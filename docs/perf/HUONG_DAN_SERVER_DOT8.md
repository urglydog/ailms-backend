# Hướng dẫn server đợt 8: phát hành → đo tải → bật giám sát

> **Một file duy nhất, làm từ đầu tới cuối.** Dành cho người quản lý server `46.224.175.90` hoặc AI có quyền chạy
> lệnh trên server. Gồm: phát hành backend bản mới trên `develop` (đợt 8), **đo tải thật bằng k6** (vì chưa có
> người dùng thật nên không chờ số liệu tự nhiên), quyết định có đủ RAM không, rồi bật Prometheus + Grafana
> (nhánh `feat/monitoring`).
> Tài liệu liên quan: [DOT8_SanSangGiamSat.md](DOT8_SanSangGiamSat.md) (đợt 8 đổi gì),
> [QUY_TRINH_PHAT_HANH_BACKEND.md](QUY_TRINH_PHAT_HANH_BACKEND.md) (quy trình phát hành gốc).

---

## Quy tắc cho AI thực hiện

1. Làm **đúng thứ tự** các phần 1 → 7. Mỗi bước có "Kết quả mong đợi"; **sai thì dừng**, ghi lại lệnh và lỗi vào
   báo cáo (phần 8), không tự sửa code hay đổi cấu hình ngoài những gì file này ghi.
2. **Không in giá trị bí mật** (mật khẩu, khóa API, token). Chỉ ghi tên biến.
3. Không xóa dữ liệu, volume, sao lưu. Không `git push --force`, không sửa lịch sử git.
4. Phần 7 (merge `feat/monitoring`) chỉ làm khi phần 6 kết luận **"Đủ RAM"**.
5. Bài đo tải chỉ gọi API **đọc** công khai và chạy trong mạng Docker, không đi qua Internet hay Caddy.
6. Cuối cùng điền **phần 8** và gửi lại cho nhóm.

Đường dẫn trên server (theo quy trình phát hành hiện tại):
- `/var/lms/be`: thư mục chạy `docker compose` (container `lms_*`).
- `/home/dev-thien/work/be-work`: worktree để build jar, không có Java/Maven trên máy nên build bằng container.

---

## 1. Ghi lại trạng thái hiện tại

```bash
cd /var/lms/be
git branch --show-current; git log --oneline -1
git status --short                  # file đang sửa tay trên server
git diff --stat
docker images lms-backend --format '{{.Tag}} {{.ID}} {{.CreatedSince}}'
docker inspect lms_backend --format '{{.Config.Image}}'
docker inspect lms_backend --format '{{range .Config.Env}}{{println .}}{{end}}' | grep '^SPRING_PROFILES_ACTIVE='
free -h; df -h /
```

**Kết quả mong đợi:** backend đang chạy image `lms-backend:prod`, profile `prod`. Ghi lại mọi file trong
`git status --short` (dùng ở bước 3.2).

## 2. Đo lần A: bản đang chạy (trước khi phát hành)

### 2.1 Lấy kịch bản đo

```bash
cd /home/dev-thien/work/be-work
git fetch origin
git checkout --detach origin/develop
ls loadtest/k6-public.js loadtest/ghi-ram.sh    # phải có 2 file
```

### 2.2 Chạy đo (khoảng 5 phút)

Mở **hai cửa sổ** SSH (hoặc chạy ghi RAM ở nền):

```bash
# Cửa sổ 1: ghi RAM mỗi 10 giây
cd /home/dev-thien/work/be-work
bash loadtest/ghi-ram.sh /tmp/ram-A.csv &
RAM_PID=$!

# Cửa sổ 1 (tiếp): chạy k6 với 50 người dùng ảo, giữ đỉnh 3 phút
NET=$(docker inspect lms_backend --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}')
docker run --rm -m 256m --network "$NET" -v "$PWD/loadtest:/scripts:ro" \
  -e PEAK_VUS=50 -e HOLD=3m grafana/k6:1.2.3 run /scripts/k6-public.js 2>&1 | tee /tmp/k6-A.txt
kill $RAM_PID
```

Kịch bản gọi danh sách khóa học, danh mục, banner, chi tiết khóa học và **tìm kiếm có từ khóa** (đường nặng nhất),
mỗi người dùng ảo nghỉ 1–3 giây giữa các lượt như người thật. Mỗi người dùng ảo gửi một IP riêng trong
`X-Forwarded-For`, nên rate limit tính như 50 người khác nhau, không phải 1 IP bị chặn 429.

### 2.3 Tóm tắt kết quả

```bash
grep -E "✓|✗|http_req_duration|http_req_failed|http_reqs" /tmp/k6-A.txt
# RAM thấp nhất và swap cao nhất trong lúc đo
awk -F, 'NR>1 {if(min==""||$2<min)min=$2; if($3>max)max=$3} END {print "RAM available thấp nhất:",min,"MB; swap cao nhất:",max,"MB"}' /tmp/ram-A.csv
grep lms_backend /tmp/ram-A.csv | awk -F, '{print $5}' | sort -h | tail -1   # RAM backend cao nhất
```

**Kết quả mong đợi:** k6 in bảng kết quả; ghi lại `http_reqs` (request/giây), `p(95)`, `http_req_failed`.

## 3. Phát hành bản mới (develop, đợt 8)

### 3.1 Test, build jar và image backend

Theo [QUY_TRINH_PHAT_HANH_BACKEND.md](QUY_TRINH_PHAT_HANH_BACKEND.md), với phiên bản là `origin/develop`:

```bash
cd /home/dev-thien/work/be-work        # đang ở origin/develop từ bước 2.1

# Test đầy đủ (~10–15 phút). Kết quả mong đợi: BUILD SUCCESS, Failures: 0, Errors: 0
docker run --rm -m 1200m --memory-swap 2200m -e MAVEN_OPTS="-Xmx600m -XX:+UseSerialGC" \
  -v "$PWD":/app -v lms-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-17 \
  sh -c "mvn -B clean verify; rc=\$?; chown -R $(id -u):$(id -g) /app; exit \$rc" 2>&1 | tail -20

# Build jar
docker run --rm -m 1200m --memory-swap 2200m -e MAVEN_OPTS="-Xmx600m -XX:+UseSerialGC" \
  -v "$PWD":/app -v lms-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-17 \
  sh -c "mvn -B -DskipTests clean package; rc=\$?; chown -R $(id -u):$(id -g) /app; exit \$rc" 2>&1 | tail -5

# Giữ image cũ để quay lại, build image mới
docker tag lms-backend:prod lms-backend:prev
docker build -t lms-backend:prod .
```

Đợt 8 **không có migration mới**, nên không bắt buộc sao lưu DB. Nếu `git log` có thêm file `V…sql` mới so với bản
đang chạy thì sao lưu theo QUY_TRINH trước bước 3.3.

### 3.2 Cập nhật file compose ở `/var/lms/be`

Đợt 8 sửa `docker-compose.yml` (xoay vòng log cho MySQL/Redis/Adminer/Mailpit, `stop_grace_period` cho backend).
Thư mục này đang ở nhánh `feat/additional-features` và có thể có file sửa tay (bước 1).

```bash
cd /var/lms/be
git fetch origin
git diff                                       # phần sửa tay so với nhánh đang đứng
git diff HEAD origin/develop --stat            # develop khác nhánh hiện tại ở những file nào
# Với từng dòng sửa tay, kiểm develop đã có chưa, vd. giới hạn log backend:
git show origin/develop:docker-compose.yml | grep -n -A3 'logging:' | head -20
```

- Nếu phần sửa tay **đã có trong develop** (vd. giới hạn log backend 3 × 20 MB, `mem_limit`): bỏ bản sửa tay và
  chuyển nhánh:
  ```bash
  git checkout -- docker-compose.yml        # chỉ các file đã kiểm ở trên
  git checkout develop && git pull --ff-only origin develop
  ```
- Nếu có sửa tay **chưa có trong develop**: **dừng**, chép `git diff` vào báo cáo để nhóm đưa vào develop trước.

### 3.3 Tạo lại container

```bash
cd /var/lms/be
docker compose config -q && echo compose-hop-le
docker compose up -d
```

`up -d` chỉ tạo lại container có cấu hình đổi: backend (image mới + `stop_grace_period`), MySQL, Redis, Adminer,
Mailpit (thêm xoay vòng log). MySQL/Redis khởi động lại mất khoảng 10–30 giây; dữ liệu nằm trong volume nên
không mất.

## 4. Kiểm tra sau phát hành

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}'                    # mọi container Up, backend (healthy)
docker inspect lms_backend --format '{{.State.Health.Status}}'          # healthy (chờ tới 2 phút)
curl -s http://127.0.0.1:8080/actuator/health                          # {"status":"UP"...}
docker logs lms_backend 2>&1 | grep -c ' ERROR '                         # 0
docker logs lms_backend 2>&1 | grep -m1 'Started LmsApplication'        # thời gian khởi động
# Endpoint số liệu mới (đợt 8): phải ra số liệu, không phải 404
docker exec lms_backend curl -s localhost:8080/actuator/prometheus | grep -c '^http_server_requests_seconds_bucket'
# Từ Internet phải bị Caddy chặn (404):
curl -s -o /dev/null -w '%{http_code}\n' https://46.224.175.90.nip.io/actuator/prometheus
# Xoay vòng log đã áp dụng:
for c in lms_mysql lms_redis lms_backend; do docker inspect $c --format '{{.Name}} {{.HostConfig.LogConfig.Config}}'; done
docker inspect lms_backend --format 'StopTimeout={{.Config.StopTimeout}}'   # 35
```

**Nếu backend không healthy hoặc có ERROR:** quay lại bản cũ ngay rồi ghi lỗi vào báo cáo:

```bash
docker tag lms-backend:prev lms-backend:prod
cd /var/lms/be && docker compose up -d --no-deps backend
```

## 5. Đo lần B: bản mới

Chờ backend chạy ổn định **5 phút** sau khi healthy, rồi lặp đúng bước 2.2–2.3 với tên file `B`:

```bash
cd /home/dev-thien/work/be-work
bash loadtest/ghi-ram.sh /tmp/ram-B.csv & RAM_PID=$!
NET=$(docker inspect lms_backend --format '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}')
docker run --rm -m 256m --network "$NET" -v "$PWD/loadtest:/scripts:ro" \
  -e PEAK_VUS=50 -e HOLD=3m grafana/k6:1.2.3 run /scripts/k6-public.js 2>&1 | tee /tmp/k6-B.txt
kill $RAM_PID
grep -E "✓|✗|http_req_duration|http_req_failed|http_reqs" /tmp/k6-B.txt
awk -F, 'NR>1 {if(min==""||$2<min)min=$2; if($3>max)max=$3} END {print "RAM available thấp nhất:",min,"MB; swap cao nhất:",max,"MB"}' /tmp/ram-B.csv
```

**Tùy chọn, đo sức chịu đựng:** chạy thêm một lần với `-e PEAK_VUS=100` (file `C`) để xem độ trễ khi gấp đôi tải.
Bỏ qua nếu lần B đã có `http_req_failed` > 1% hoặc RAM available < 800 MB.

## 6. Quyết định: có đủ RAM cho giám sát không

Prometheus + Grafana + node-exporter có trần **~0,5 GB** (đo ở local thực tế ~130 MB).

| Điều kiện (lấy từ lần B) | Đạt khi |
| --- | --- |
| RAM available **thấp nhất** trong lúc đo | ≥ **1.500 MB** |
| Swap cao nhất so với lúc bắt đầu đo | không tăng quá 200 MB |
| k6 `http_req_failed` | < 1% |
| k6 `p(95)` danh sách / chi tiết | < 800 ms |

- **Đạt cả 4** → kết luận **"Đủ RAM"**, làm tiếp phần 7.
- **Không đạt** → kết luận **"Thiếu RAM"**, **dừng ở đây**. Ghi số liệu vào báo cáo. Nhóm sẽ chọn nâng VPS
  (Hetzner CX23 → CX33: 8 GB, ~€8,49/tháng) hoặc dùng Grafana Cloud bản miễn phí.

## 7. Bật giám sát (chỉ khi "Đủ RAM")

### 7.1 Merge nhánh `feat/monitoring` vào `develop`

Nhánh `feat/monitoring` chỉ thêm file cấu hình (`docker-compose.monitoring.yml`, thư mục `monitoring/`), **không
đổi code Java**, nên **không cần** build lại backend.

Tạo pull request `feat/monitoring` → `develop` trên GitHub (repo `urglydog/ailms-backend`) rồi merge; hoặc nếu có
quyền push:

```bash
cd /home/dev-thien/work/be-work
git fetch origin
git checkout -B develop origin/develop
git merge --no-ff origin/feat/monitoring -m "Merge feat/monitoring: Prometheus + Grafana"
git push origin develop
```

Nếu merge báo **xung đột**: dừng, không tự giải, ghi vào báo cáo.

### 7.2 Chạy 3 container giám sát

```bash
cd /var/lms/be
git pull --ff-only origin develop
ls docker-compose.monitoring.yml monitoring/prometheus.yml    # phải có

# Mật khẩu admin Grafana trong be/.env (KHÔNG commit, không in ra). Tạo ngẫu nhiên nếu chưa có:
grep -q '^GRAFANA_ADMIN_PASSWORD=.\+' .env || echo "GRAFANA_ADMIN_PASSWORD=$(openssl rand -base64 18)" >> .env

docker compose -f docker-compose.yml -f docker-compose.monitoring.yml up -d prometheus grafana node-exporter
docker ps --format '{{.Names}}\t{{.Status}}' | grep -E 'prometheus|grafana|node_exporter'
```

Lệnh chỉ tạo 3 container mới; backend, MySQL, Redis, AI không bị tạo lại.

| Container | Vai trò | Trần RAM |
| --- | --- | --- |
| `lms_prometheus` | Đọc số liệu 15 s/lần, giữ tối đa 15 ngày hoặc 2 GB | 256 MB |
| `lms_grafana` | Dashboard, chỉ nghe `127.0.0.1:3001` | 192 MB |
| `lms_node_exporter` | RAM, CPU, ổ đĩa, swap của máy chủ | 48 MB |

### 7.3 Kiểm tra

```bash
# 3 nguồn số liệu phải "up"
docker exec lms_prometheus wget -qO- http://localhost:9090/api/v1/targets | grep -o '"health":"[a-z]*"' | sort | uniq -c
# Grafana đã nạp dashboard (đọc mật khẩu từ .env, không in ra)
P=$(grep '^GRAFANA_ADMIN_PASSWORD=' .env | cut -d= -f2-)
curl -s -u "admin:$P" http://127.0.0.1:3001/api/dashboards/uid/lms-overview | grep -o '"title":"LinguaLearn[^"]*"'
# Không mở cổng nào ra Internet: chỉ thấy 127.0.0.1:3001
ss -tlnp | grep -E ':3001|:9090|:9100'
free -h
```

**Kết quả mong đợi:** `3 "health":"up"`; tiêu đề `LinguaLearn — Tổng quan`; cổng 3001 chỉ ở `127.0.0.1`,
không có 9090/9100 trên máy chủ.

### 7.4 Đo lần D: bản mới + giám sát

Lặp lại bước 5 với tên file `D` để biết RAM còn lại khi giám sát đang chạy. Trong lúc đo, có thể mở Grafana
(mục 7.5) để xem biểu đồ cập nhật trực tiếp, hợp để chụp ảnh cho báo cáo.

Nếu RAM available thấp nhất lần D < **1.000 MB** hoặc swap tăng mạnh: tắt giám sát (lệnh ở 7.6) và ghi vào báo cáo.

### 7.5 Xem dashboard

Grafana không mở ra Internet. Từ máy cá nhân, mở SSH tunnel rồi vào trình duyệt:

```bash
ssh -L 3001:127.0.0.1:3001 <user>@46.224.175.90
# giữ cửa sổ này mở, vào http://localhost:3001 — đăng nhập admin / GRAFANA_ADMIN_PASSWORD trong be/.env
```

Dashboard **LinguaLearn — Tổng quan** (trang chủ):
- **Backend — request:** request/giây theo mã trạng thái; độ trễ p50/p95/p99; lỗi 5xx và bị chặn 429; 10 endpoint chậm nhất.
- **Backend — JVM và DB:** heap JVM; kết nối HikariCP (đang dùng / chờ / tối đa 20); CPU tiến trình.
- **Máy chủ:** RAM available và swap; CPU; ổ đĩa `/` còn trống.

### 7.6 Tắt / gỡ giám sát

```bash
cd /var/lms/be
# Tạm tắt (giữ số liệu):
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml stop prometheus grafana node-exporter
# Gỡ hẳn (xóa cả số liệu — chỉ làm khi nhóm đồng ý):
docker compose -f docker-compose.yml -f docker-compose.monitoring.yml rm -sf prometheus grafana node-exporter
```

### Bảo mật của phần giám sát

- Không container nào mở cổng ra Internet; Grafana chỉ ở `127.0.0.1`, xem qua SSH tunnel.
- `/actuator/prometheus` không cần JWT (Prometheus không đăng nhập được) và được bảo vệ bởi `Caddyfile` chặn
  `/actuator/*` từ Internet (bước 4 đã kiểm tra trả 404). Đổi reverse proxy thì phải giữ luật này.
- Grafana tắt đăng ký tài khoản và gửi thống kê; thiếu `GRAFANA_ADMIN_PASSWORD` thì compose từ chối chạy.

## Dọn dẹp sau khi xong

```bash
rm -rf /home/dev-thien/work/be-work/target
docker image prune -f
rm -f /tmp/k6-*.txt /tmp/ram-*.csv      # sau khi đã chép kết quả vào báo cáo
```

---

## 8. Mẫu báo cáo gửi lại

```
# Kết quả đợt 8 trên server (ngày ___)

## 1. Trạng thái trước
- Nhánh /var/lms/be: ___ ; file sửa tay: ___
- Image backend: ___ ; profile: ___
- free -h: available ___ ; swap used ___

## 2–5. Đo tải (50 người dùng ảo, giữ đỉnh 3 phút)
| | Lần A (bản cũ) | Lần B (bản mới) | Lần D (mới + giám sát) |
|---|---|---|---|
| Request/giây (http_reqs) | | | |
| p95 danh sách (ms) | | | |
| p95 chi tiết (ms) | | | |
| p99 tổng (ms) | | | |
| Tỉ lệ lỗi (http_req_failed) | | | |
| RAM backend cao nhất | | | |
| RAM available thấp nhất (MB) | | | |
| Swap cao nhất (MB) | | | |
(Tùy chọn lần C, 100 người dùng ảo: ___)

## 3–4. Phát hành
- Test: ___ test, Failures ___, Errors ___
- Xử lý file sửa tay ở /var/lms/be: ___
- Backend healthy: ___ ; số dòng ERROR: ___ ; khởi động ___ giây
- /actuator/prometheus trong container: ___ dòng bucket ; từ Internet: mã ___
- Log rotation mysql/redis/backend: ___ ; StopTimeout: ___

## 6. Kết luận RAM
- Đủ RAM / Thiếu RAM — lý do: ___

## 7. Giám sát (nếu làm)
- Merge feat/monitoring: ___ (PR/commit ___ ; xung đột: có/không)
- Targets up: ___/3 ; dashboard nạp: ___ ; cổng: ___
- RAM 3 container giám sát (docker stats): prometheus ___ , grafana ___ , node-exporter ___

## Lỗi / điểm bất thường
- ___
```
