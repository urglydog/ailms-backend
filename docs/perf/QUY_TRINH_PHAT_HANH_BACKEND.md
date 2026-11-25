# Quy trình phát hành backend (image production)

> Áp dụng từ 25/11/2026. Backend không còn tự biên dịch code trong `/var/lms/be`: container `lms_backend`
> chạy image `lms-backend:prod` (file jar đã build sẵn, profile `prod`). Sửa code trong `/var/lms/be` hoặc
> `git pull` ở đó **không** làm bản đang chạy thay đổi — phải phát hành theo các bước dưới đây.
> Chế độ cũ (hot-reload, `Dockerfile.dev`) vẫn còn trong repo nếu cần quay lại.

## Phát hành một phiên bản mới
Làm trong worktree `/home/dev-thien/work/be-work` (không có Java/Maven trên máy chủ nên dùng container Maven có giới hạn RAM).

```bash
# 1. Lấy đúng phiên bản cần phát hành
cd /home/dev-thien/work/be-work
git fetch origin && git checkout --detach origin/feat/additional-features   # hoặc một mã commit cụ thể

# 2. Chạy đầy đủ test (bắt buộc trước khi phát hành; bỏ qua nếu chỉ phát hành lại đúng bản đã test)
docker run --rm -m 1200m --memory-swap 2200m -e MAVEN_OPTS="-Xmx600m -XX:+UseSerialGC" \
  -v "$PWD":/app -v lms-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-17 \
  sh -c "mvn -B clean verify; rc=\$?; chown -R $(id -u):$(id -g) /app; exit \$rc"

# 3. Build file jar (bỏ test vì đã chạy ở bước 2)
docker run --rm -m 1200m --memory-swap 2200m -e MAVEN_OPTS="-Xmx600m -XX:+UseSerialGC" \
  -v "$PWD":/app -v lms-m2:/root/.m2 -w /app maven:3.9-eclipse-temurin-17 \
  sh -c "mvn -B -DskipTests clean package; rc=\$?; chown -R $(id -u):$(id -g) /app; exit \$rc"

# 4. Giữ lại image đang chạy để quay lại, rồi build image mới
docker tag lms-backend:prod lms-backend:prev
docker build -t lms-backend:prod .

# 5. Tạo lại đúng container backend (ngừng khoảng 1 phút; MySQL/Redis/AI không bị ảnh hưởng)
cd /var/lms/be && docker compose up -d --no-deps backend

# 6. Kiểm tra: chờ healthy, không có ERROR khi khởi động
docker inspect lms_backend --format '{{.State.Health.Status}}'
curl -s http://127.0.0.1:8080/actuator/health
docker logs lms_backend 2>&1 | grep -c ' ERROR '

# 7. Dọn dung lượng (đĩa chỉ 38 GB)
rm -rf /home/dev-thien/work/be-work/target
docker image prune -f
```

Nếu bản phát hành có **migration mới** (file `V…sql` mới trong `src/main/resources/db/migration`), sao lưu DB trước bước 5:
```bash
docker exec lms_mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction lms_db' \
  | gzip > /home/dev-thien/backups/lms_db_pre_V<số>_$(date +%Y%m%d_%H%M).sql.gz
```

## Quay lại bản trước
```bash
docker tag lms-backend:prev lms-backend:prod
cd /var/lms/be && docker compose up -d --no-deps backend
```
Chỉ quay lại được khi bản mới chưa áp dụng migration làm đổi schema; nếu đã có migration thì phải khôi phục từ file sao lưu ở trên.

## Đổi bộ nhớ Java
Mặc định `-Xmx896m`. Đổi bằng biến `BACKEND_JAVA_OPTS` trong `be/.env` (không cần build lại image), rồi `docker compose up -d --no-deps backend`.

## Lưu ý
- Profile `prod` giữ thư mục migration `db/dev` (V100, V101) vì DB thật đã áp dụng hai migration này. Không xoá thư mục `db/dev`.
- `SPRING_PROFILES_ACTIVE` được đặt cứng `prod` trong `docker-compose.yml` (giá trị `dev` trong `.env` không còn tác dụng với backend).
- Sau mỗi lần phát hành, image cũ không còn tag là image "dangling"; bước 7 dọn chúng.
