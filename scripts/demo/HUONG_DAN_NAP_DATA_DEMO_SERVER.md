# Hướng dẫn nạp dữ liệu demo lên server (dành cho người/AI chạy trên VPS)

> Có thể copy nguyên file này cho Claude/AI trên máy chủ và bảo nó làm theo từng bước.
> Quy tắc: **chỉ THÊM dữ liệu, không xóa, không `TRUNCATE`, không `docker compose down -v`, không `FLUSHDB` Redis.** Dừng và hỏi người dùng nếu gặp điều gì lạ.

## 1. Bối cảnh

- Nhánh `feat/UI` của repo `ailms-backend` và `ailms-web-client` đã được merge vào `develop`. Backend có thêm bộ nạp dữ liệu demo `com.lms.demo.DemoDataLoader`, dữ liệu ở `be/src/main/resources/demo/courses.json`.
- Bộ nạp thêm khoảng **53 khóa mới** (+12 khóa đã có trong dữ liệu demo cũ), 55 giảng viên, 80 học viên, ghi danh, thanh toán PAID giả lập, đánh giá, chứng chỉ, XP và streak. Mọi tài khoản demo có email **`@example.com`**, dùng chung một mật khẩu.
- DB chính là **MySQL 8** (container `lms_mysql`). Supabase chỉ dùng làm kho vector cho AI worker, **không** phải nơi chứa dữ liệu khóa học.
- Bộ nạp **idempotent**: chạy lại không nhân đôi. Nó chỉ tạo khóa chưa có (theo `slug`), tài khoản chưa có (theo `email`), danh mục chưa có (theo `slug`).
  - Với khóa demo *đã tồn tại*, nó cập nhật tên, mô tả, ảnh bìa, tên chương, tên/mô tả bài; với giảng viên demo, cập nhật ảnh/tiêu đề/giới thiệu. Không đụng giá, trạng thái, ghi danh, thanh toán, hay khóa/tài khoản khác.

## 2. Kiểm tra trước khi chạy (bắt buộc)

Trong thư mục `be` trên server (nơi có `docker-compose.yml` và `.env`):

```bash
git status && git log --oneline -3          # đảm bảo đang ở develop đã có bộ nạp demo
ls src/main/resources/demo/courses.json     # file dữ liệu phải tồn tại
grep -E '^SPRING_PROFILES_ACTIVE' .env      # ghi nhận profile đang dùng (dev/prod)
```

**Kiểm tra trùng slug với khóa thật** (nếu có dòng trả về, DỪNG và báo người dùng — khóa đó sẽ bị ghi đè nội dung hiển thị):

```bash
SLUGS=$(node -e "const d=require('./src/main/resources/demo/courses.json');console.log(d.courses.map(c=>\"'\"+c.slug+\"'\").join(','))")
docker exec lms_mysql sh -c "mysql -uroot -p\"\$MYSQL_ROOT_PASSWORD\" \"\$MYSQL_DATABASE\" -e \"SELECT slug,title,created_at FROM courses WHERE slug IN ($SLUGS);\""
```

(Nếu máy chủ không có Node, mở `courses.json` và lấy các trường `slug` bằng cách khác. Kết quả mong đợi: không có dòng nào, hoặc chỉ là các khóa demo đã nạp trước đó.)

## 3. Sao lưu DB

```bash
mkdir -p ~/backups && docker exec lms_mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction "$MYSQL_DATABASE"' > ~/backups/lms_before_demo_$(date +%Y%m%d_%H%M).sql
ls -la ~/backups | tail -2     # file phải có dung lượng > 0
```

## 4. Bật bộ nạp

Thêm vào cuối `be/.env` (file không được commit). Mật khẩu chung cho mọi tài khoản demo do người dùng báo riêng cho bạn — **hỏi người dùng nếu chưa có, không tự đặt, không ghi mật khẩu vào log/chat/commit**:

```
APP_DEMO_SEED_ENABLED=true
APP_DEMO_SEED_PASSWORD=<mật khẩu người dùng cung cấp>
```

Build lại và chạy backend (Flyway sẽ tự chạy các migration mới của `develop` trước):

```bash
docker compose up -d --build backend
```

## 5. Theo dõi tiến trình

Backend khởi động mất vài phút, nạp thêm khoảng 3–10 phút tùy máy. Chờ tới khi thấy dòng thành công:

```bash
docker logs -f lms_backend 2>&1 | grep -E "Nap du lieu demo|APPLICATION FAILED|Caused by"
```

Kết quả mong đợi: `Nap du lieu demo: 53 khoa moi, <vài nghìn> ghi danh (...)`. Nếu `APPLICATION FAILED`, dừng lại, đọc lỗi và báo người dùng. Không sửa dữ liệu bằng tay.

## 6. Tắt cờ nạp

```bash
sed -i 's/^APP_DEMO_SEED_ENABLED=true/APP_DEMO_SEED_ENABLED=false/' .env
docker compose up -d backend
```

## 7. Xóa cache Redis (nếu bỏ bước này, trang chủ vẫn hiện danh mục/khóa cũ)

Chỉ xóa đúng 3 nhóm khóa cache, **không dùng FLUSHDB** (sẽ làm mọi người bị đăng xuất):

```bash
PW=$(grep '^REDIS_PASSWORD=' .env | cut -d= -f2-)
for p in 'categories::*' 'publicCourseSearch*' 'courseDetails::*'; do
  docker exec lms_redis redis-cli -a "$PW" --no-auth-warning --scan --pattern "$p" \
    | while IFS= read -r k; do docker exec lms_redis redis-cli -a "$PW" --no-auth-warning del "$k" >/dev/null; done
done
```

## 8. Kiểm tra kết quả

```bash
docker exec lms_mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "
SELECT (SELECT COUNT(*) FROM courses WHERE status=\"PUBLISHED\") published,
       (SELECT COUNT(*) FROM categories) categories,
       (SELECT COUNT(*) FROM users WHERE email LIKE \"%@example.com\") demo_users,
       (SELECT COUNT(*) FROM enrollments) enrollments;"'
curl -s https://46.224.175.90.nip.io/api/v1/categories | head -c 400
```

Mong đợi: khoảng 14 danh mục (thêm 8 mới), vài chục khóa đã xuất bản, khoảng 130 tài khoản `@example.com`. Mở trang chủ, `/community`, một trang chi tiết khóa; đăng nhập thử một tài khoản demo (ví dụ giảng viên `codevolution@example.com`).

## 9. Việc tùy chọn: đánh index embedding cho Course Discovery (AI)

Các khóa mới chưa có vector trên Supabase nên Course Discovery bằng AI chưa tìm thấy theo ngữ nghĩa (lọc/tìm kiếm thường vẫn chạy). Để index hàng loạt (tốn quota Gemini, làm sau khi người dùng đồng ý):

```bash
curl -X POST https://46.224.175.90.nip.io/api/internal/courses/embeddings/backfill -H "X-Internal-Token: <INTERNAL_API_TOKEN trong .env>"
```

## 10. Lưu ý

- Doanh thu, học viên, thanh toán demo (`txn_ref` bắt đầu bằng `DEMO-`) sẽ hiện trên trang admin/giảng viên như dữ liệu của hệ thống. Người dùng đã đồng ý điều này, không cần chạy riêng trên staging.
- Mọi tài khoản demo (`@example.com`) dùng chung một mật khẩu (giá trị bạn đã đặt ở bước 4); đăng nhập thử bằng tài khoản giảng viên/học viên demo như bước 8.
- Ảnh là đường dẫn ngoài (YouTube `i.ytimg.com`, `yt3.googleusercontent.com`, `randomuser.me`, `dicebear.com`, Unsplash), không lưu trên server. Frontend cần bản `develop` mới (đã khai báo `images.unsplash.com` trong `next.config.ts`).
- Script `be/scripts/demo/cleanup-local-courses.sql` **chỉ dành cho máy local**, không chạy trên server.
- Rút lui: khôi phục từ file sao lưu ở bước 3. (Chưa có script gỡ riêng cho dữ liệu demo; có thể nhờ viết thêm nếu cần.)
