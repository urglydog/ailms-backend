# Chạy môi trường phát triển AI-Powered LMS

Hướng dẫn khởi động toàn bộ hệ thống và kiểm thử từng service.

---

## 1. Yêu cầu

**Chỉ cần Docker Desktop.** Không cần cài Java, Maven, Node hay Python trên máy —
mọi thứ chạy trong container.

| Thành phần | Yêu cầu |
| --- | --- |
| Docker Desktop | Đã bật, WSL2 backend nếu dùng Windows |
| RAM trống | ≥ 8 GB (khuyến nghị 16 GB) |
| Đĩa trống | ≥ 15 GB cho image và volume |

### Bố cục thư mục — quan trọng

Ba repo phải được clone **ngang hàng** trong cùng một thư mục cha:

```
project/
├─ be/            ← ailms-backend    (chạy lệnh docker compose TỪ ĐÂY)
├─ fe/            ← ailms-web-client
└─ ai-worker/     ← ailms-ai-worker
```

`docker-compose.yml` nằm trong `be/` và trỏ build context ra `../fe`, `../ai-worker`.
Thiếu một repo hoặc đặt sai chỗ thì `docker compose` báo lỗi không tìm thấy context.

```bash
mkdir project && cd project
git clone https://github.com/urglydog/ailms-backend.git     be
git clone https://github.com/urglydog/ailms-web-client.git   fe
git clone https://github.com/urglydog/ailms-ai-worker.git    ai-worker
```

---

## 2. Cấu hình biến môi trường

```bash
cd be
cp .env.example .env
```

Mở `.env` và điền **một biến bắt buộc**:

```bash
# Sinh chuỗi ngẫu nhiên (chạy trong container node nếu máy chưa có Node):
docker run --rm node:24-alpine node -e "console.log(require('crypto').randomBytes(48).toString('base64url'))"
```

Dán kết quả vào `JWT_SECRET=`.

| Biến | Giai đoạn 0 | Ghi chú |
| --- | --- | --- |
| `JWT_SECRET` | **Bắt buộc** | Container `backend` không khởi động nếu thiếu |
| `MYSQL_*` | Dùng mặc định được | `lms_user` / `lms_pass` / `lms_db` |
| `GROQ_API_KEY`, `GEMINI_API_KEY` | Để trống | Cần từ Giai đoạn 5 |
| `B2_*` | Để trống | Cần từ Giai đoạn 4 |
| `MOMO_*`, `ZALOPAY_*`, `VNPAY_*` | Để trống | Cần từ Giai đoạn 3 |
| `SUPABASE_VECTOR_*` | Để trống | Cần từ Giai đoạn 8 |

> `.env` đã nằm trong `.gitignore`. **Không bao giờ commit file này** — nó chứa
> `JWT_SECRET` và các khoá API.

---

## 3. Khởi động

```bash
cd be
docker compose up -d
```

Lần đầu **mất 10–20 phút**: phải tải image MySQL, Redis, Maven, Node, Python
(khoảng 2–3 GB), rồi Maven tải dependency và npm cài `node_modules`.

Theo dõi tiến trình:

```bash
docker compose logs -f backend    # chờ dòng "Started LmsApplication"
docker compose ps                 # xem trạng thái health của từng service
```

Thứ tự khởi động do healthcheck quyết định:
`mysql` + `redis` khoẻ → `backend` → `frontend`; `ai-api`/`ai-worker`/`ai-beat` chỉ chờ `redis`.

### Bảng cổng

| Service | URL | Dùng để |
| --- | --- | --- |
| Frontend | http://localhost:3000 | Giao diện web |
| Backend API | http://localhost:8080 | REST API |
| AI Worker API | http://localhost:8000 | Socratic Tutor, Course Discovery |
| Adminer | http://localhost:8081 | Xem cơ sở dữ liệu |
| Mailpit | http://localhost:8025 | Đọc email OTP khi test |
| MySQL | localhost:3306 | Kết nối bằng DBeaver/Workbench nếu muốn |
| Redis | localhost:6379 | |

---

## 4. Kiểm thử Backend

### 4.1 Health check

```bash
curl http://localhost:8080/actuator/health
# Mong đợi: {"status":"UP",...}
```

### 4.2 Đếm đủ 31 bảng

Vào Adminer http://localhost:8081 — đăng nhập:

| Trường | Giá trị |
| --- | --- |
| System | MySQL |
| Server | `mysql` |
| Username | `lms_user` |
| Password | `lms_pass` (hoặc giá trị bạn đặt) |
| Database | `lms_db` |

Hoặc dùng dòng lệnh:

```bash
docker compose exec mysql mysql -ulms_user -plms_pass lms_db \
  -e "SELECT COUNT(*) AS so_bang FROM information_schema.tables WHERE table_schema='lms_db' AND table_name NOT LIKE 'flyway%';"
# Mong đợi: 31
```

### 4.3 Xác nhận Flyway đã chạy

```bash
docker compose exec mysql mysql -ulms_user -plms_pass lms_db \
  -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Mong đợi 2 dòng: `1 / init schema / 1` và `100 / seed dev reference / 1`.

### 4.4 Kiểm `ddl-auto: validate` — phép thử quan trọng nhất

Backend chạy Hibernate ở chế độ `validate`: nó **đối chiếu 31 entity Java với
schema thật** lúc khởi động. Nếu log không có lỗi
`SchemaManagementException` và thấy dòng `Started LmsApplication`, nghĩa là entity
và migration khớp nhau hoàn toàn.

```bash
docker compose logs backend | grep -i "schema\|Started LmsApplication"
```

### 4.5 Dữ liệu seed (chỉ profile `dev`)

```bash
docker compose exec mysql mysql -ulms_user -plms_pass lms_db \
  -e "SELECT email, role FROM users; SELECT COUNT(*) AS so_giong FROM voice_mappings WHERE is_active=1;"
```

Mong đợi 4 tài khoản và 10 giọng đọc (5 ngôn ngữ × 2 giọng).

| Tài khoản | Vai trò |
| --- | --- |
| `admin@lms.local` | ADMIN |
| `instructor@lms.local` | INSTRUCTOR |
| `student1@lms.local`, `student2@lms.local` | STUDENT |

Mật khẩu chung: `Password123!` — chỉ dùng khi phát triển. Chưa đăng nhập được ở
Giai đoạn 0 vì API xác thực thuộc Giai đoạn 1.

---

## 5. Kiểm thử Frontend

Mở http://localhost:3000 và đi qua 4 màn:

| Đường dẫn | Kiểm gì |
| --- | --- |
| `/` | Hero chia đôi, dải 5 danh mục, 6 thẻ khoá học, mục "Cách hoạt động" 3 bước. Khối bên phải hero có dấu chấm nhấp nháy ở dòng 日本語 |
| `/courses` | Ô tìm kiếm + 4 nhóm bộ lọc. Chọn "Miễn phí" còn 2 khoá; gõ từ khoá vô nghĩa phải hiện trạng thái rỗng kèm nút "Xoá toàn bộ bộ lọc" |
| `/courses/khoa-hoc-1` | Accordion 3 chương (chương 1 mở sẵn), 2 bài có nhãn "Học thử", các bài khác hiện icon khoá 🔒, cột giá sticky khi cuộn |
| `/learn/101` | Bấm 🇯🇵 hoặc 🇰🇷 (có dấu chấm cảnh báo) → panel **"Chưa có lồng tiếng"**. Bấm "⚡ Kích hoạt lồng tiếng AI" → panel **"ĐANG XỬ LÝ LỒNG TIẾNG AI"** với 3 bước pipeline. Bấm 🇺🇸 (ngôn ngữ gốc) → bị vô hiệu hoá, có nhãn "(gốc)" |

Kiểm chất lượng nhanh:

```bash
docker compose exec frontend npm run typecheck   # phải sạch, zero any
docker compose exec frontend npm run lint
```

> Toàn bộ dữ liệu ở Giai đoạn 0 là **mock** trong `fe/lib/mock/courses.ts`.
> Các giai đoạn sau chỉ thay hàm trong file đó bằng React Query gọi API thật,
> không phải sửa component.

---

## 6. Kiểm thử AI Worker

```bash
# 6.1 API đồng bộ (UC30 Tutor, UC49 Discovery)
curl http://localhost:8000/health
# Mong đợi: {"status":"ok","service":"ai-worker","asr_backend":"groq",...}

# 6.2 Tài liệu API tự sinh
# Mở http://localhost:8000/docs

# 6.3 Celery worker đã nối Redis
docker compose logs ai-worker | grep -i "ready\|connected"

# 6.4 Ping worker
docker compose exec ai-worker celery -A app.celery_app inspect ping

# 6.5 Celery beat đã nạp 4 tác vụ định kỳ
docker compose logs ai-beat | grep -i "beat\|schedule"
```

Endpoint `/api/v1/tutor/ask` và `/api/v1/discovery/chat` hiện trả
`NotImplementedError` — đúng như thiết kế, chúng thuộc Giai đoạn 8.

---

## 7. Xử lý sự cố

### Cổng bị chiếm

```
Error: bind: address already in use
```

Đổi cổng trong `.env` (ví dụ `BACKEND_PORT=18080`) rồi `docker compose up -d` lại.
Trên Windows tìm tiến trình đang giữ cổng:

```powershell
netstat -ano | findstr :8080
```

### `backend` khởi động rồi tắt ngay

```bash
docker compose logs backend | tail -50
```

Ba nguyên nhân thường gặp:

| Log chứa | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `JWT_SECRET chua duoc dat` | Chưa điền `JWT_SECRET` trong `.env` | Xem mục 2 |
| `Communications link failure` | MySQL chưa kịp khoẻ | Chờ thêm, hoặc `docker compose restart backend` |
| `SchemaManagementException` | Entity lệch schema | Báo lỗi kèm log — đây là lỗi cần sửa code, không phải lỗi môi trường |

### Hot-reload không ăn (Windows/WSL2)

Bind mount trên Windows không phát sinh inotify event đáng tin. Đã cấu hình sẵn
`WATCHPACK_POLLING=true` cho frontend và `poll-interval: 2s` cho Spring DevTools.
Nếu vẫn không ăn, đặt code trong hệ thống tệp WSL2 (`\\wsl$\...`) thay vì ổ `D:`.

### Maven tải lại dependency mỗi lần khởi động

Volume `backend_maven_cache` bị mất. Kiểm tra:

```bash
docker volume ls | grep maven
```

### Reset sạch toàn bộ

```bash
docker compose down -v      # -v XOÁ LUÔN dữ liệu MySQL và Redis
docker compose up -d --build
```

⚠️ `-v` xoá vĩnh viễn nội dung cơ sở dữ liệu. Bỏ `-v` nếu muốn giữ dữ liệu.

### Xem lại toàn bộ cấu hình đã resolve

```bash
docker compose config
```

Hữu ích khi nghi biến môi trường không được truyền vào container.

---

## 8. Lệnh hay dùng

| Việc | Lệnh |
| --- | --- |
| Dừng, giữ dữ liệu | `docker compose stop` |
| Dừng, xoá container (giữ volume) | `docker compose down` |
| Xem log 1 service | `docker compose logs -f <service>` |
| Vào shell container | `docker compose exec backend bash` |
| Build lại 1 service | `docker compose up -d --build backend` |
| Xem tài nguyên đang dùng | `docker stats` |

---

## 9. Bước tiếp theo

Giai đoạn 0 chỉ dựng khung sườn: chưa có API nghiệp vụ nào, frontend dùng mock, AI
Worker mới có hợp đồng hàm. Xem `doc/DEVELOPMENT_PLAN.md` để biết Giai đoạn 1 làm gì
(UC01–UC08 xác thực và tài khoản) và tiêu chí hoàn thành từng giai đoạn.
