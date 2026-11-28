# Tổng kết tối ưu hiệu năng và độ ổn định hệ thống LinguaLearn

> Tài liệu này tóm tắt **5 đợt tối ưu** đã làm trên nhánh `feat/performance` (backend `project/be` và
> frontend `project/fe`). Mỗi đợt ghi rõ: hệ thống trước đó làm thế nào, vấn đề là gì, kỹ thuật mới được dùng,
> vì sao chọn kỹ thuật đó, kết quả đo và cách kiểm chứng.
> Nhật ký đo chi tiết nằm ở [PLAN_HieuNang_HeThong.md](PLAN_HieuNang_HeThong.md).

---

## 0. Tổng quan nhanh

| Đợt | Chủ đề | Vấn đề trước đây | Kỹ thuật mới | Kết quả chính |
| --- | --- | --- | --- | --- |
| Tiền đề | Trang Kho khóa học | Phân trang `Page` đếm `COUNT(*)` mỗi lần; tải hết rồi lọc | `Slice` + index DB + cuộn vô hạn, debounce, hủy request cũ | Không còn câu `COUNT(*)`; mỗi lần tải 24 khóa |
| 1 | "Khóa học của tôi" | N+1: **476 câu SQL**/request | `@EntityGraph` + truy vấn theo lô `IN (…)` + projection | 476 → **13** SQL; 16 → **~280** req/s; p50 2,5 s → **~170 ms** |
| 2 | Tìm kiếm khóa học | N+1: **121 câu SQL**/request | `@EntityGraph` + `GROUP BY` theo lô + tính giá coupon theo lô | 121 → **15** SQL; 76 → **~510** req/s; p50 639 → **~115 ms** |
| 3 | Job nền và danh sách admin | Nạp **toàn bộ bảng** vào RAM | Đếm/lọc trong DB, xử lý theo lô 500, phân trang phía server | Bộ nhớ không còn tăng theo tổng dữ liệu |
| 4 | Ổn định production | Log DEBUG ở mọi môi trường, không giới hạn request, pool chờ 30 s | Rate limit bằng Redis, log INFO, chỉnh Hikari | Vượt ngưỡng nhận 429 ngay; hết kết nối báo lỗi sau 5 s |
| 5 | Chat realtime | Polling 5 s và 15 s; topic WebSocket không kiểm quyền | Đẩy qua STOMP/WebSocket + `ChannelInterceptor` kiểm quyền | ~16 → ~2 request/phút mỗi người; vá lỗ hổng nghe lén |
| 6 | Ổn định nhiều người dùng/nhiều backend | 19 job chung 1 luồng; chạy 2 backend thì job chạy trùng; deploy cắt ngang request; JSON không nén | Pool scheduler 6 luồng, ShedLock + Redis, graceful shutdown, gzip | Chi tiết: [DOT6_OnDinh_NhieuNguoiDung.md](DOT6_OnDinh_NhieuNguoiDung.md) |
| 7 | Tải trang và giảm tải | Thư viện nặng (mermaid, mindmap, recharts) tải ngay khi mở trang; banner thông báo mỗi lần gọi là 1 câu SQL | `import()` động / `next/dynamic`; cache Redis 30 s + evict khi admin sửa | Chi tiết: [DOT7_TaiTrang_GiamTai.md](DOT7_TaiTrang_GiamTai.md) |
| 8 | Sẵn sàng giám sát | Server chạy bản dev (1,6 GB RAM, log mọi SQL); chưa có số liệu để giám sát | Endpoint Prometheus + histogram độ trễ, xoay vòng log còn thiếu, `stop_grace_period`; nhánh `feat/monitoring` có Prometheus + Grafana | Bản prod 0,9 GB RAM (−45%); chi tiết [DOT8_SanSangGiamSat.md](DOT8_SanSangGiamSat.md) |
| 9 | Tiến độ học và trang học bài | Mỗi lần gửi tiến độ (15 s/lần/người) đều tính lại tiến độ cả khóa; trang học bài tải sẵn mọi tab | Chỉ tính lại khi bài vừa hoàn thành hoặc mở lần đầu; tải lười 9 tab/panel | ~15 → ~11 câu MySQL/lần gửi; trang học bài 328 → 247 kB; chi tiết [DOT9_TienDo_TrangHocBai.md](DOT9_TienDo_TrangHocBai.md) |

Tổng số test sau cùng: **491 test backend** và **564 test frontend**, tất cả đều qua.

---

## 1. Cách đo (để hiểu các con số)

- **Môi trường:** máy local, Docker gồm backend Spring Boot, MySQL 8, Redis. Dữ liệu demo gồm 121 người dùng,
  60 khóa học, 3.288 lượt ghi danh, 37.518 bản ghi tiến độ học.
- **Số câu SQL mỗi request:** lấy hiệu của biến `Questions` (`SHOW GLOBAL STATUS`) trước và sau đúng 1 request.
  Đây là cách đơn giản để phát hiện **N+1** mà không cần cài thêm công cụ.
- **Chịu tải:** `npx autocannon -c 50`, tức 50 kết nối đồng thời trong 10–15 giây. Lấy 3 lần đo cho mỗi trường hợp.
  - `req/s`: số request xử lý được mỗi giây (càng cao càng tốt).
  - `p50`, `p99`: 50% và 99% request nhanh hơn mức thời gian này (càng thấp càng tốt).
- **Giới hạn:** mọi thứ chạy chung một máy, nên con số tuyệt đối chỉ dùng để **so trước/sau**, không phải năng
  lực thật của máy chủ.

### Khái niệm N+1 là gì?

Khi lấy danh sách N đối tượng, code chạy **1 câu** lấy danh sách, rồi với **mỗi phần tử** lại chạy thêm vài câu
để lấy dữ liệu liên quan (danh mục, giảng viên, số đánh giá…). Kết quả là 1 + N×k câu SQL. Với 45 khóa học và
khoảng 10 câu mỗi khóa thì thành gần 500 câu cho **một** lần mở trang. Lỗi này khó thấy khi dữ liệu ít, nhưng
càng nhiều dữ liệu càng chậm tuyến tính.

---

## 2. Tiền đề: Trang Kho khóa học (làm trước 5 đợt)

**Trước đây**
- Backend trả `Page<Course>`. Spring Data luôn chạy thêm một câu `SELECT COUNT(*)` để biết tổng số trang.
- Frontend hiện số trang và số lượng khóa; mỗi lần gõ ô tìm kiếm là gọi API ngay.

**Kỹ thuật mới**
- **`Slice` thay cho `Page`:** `Slice` chỉ lấy thêm 1 dòng để biết "còn trang sau không", không cần đếm tổng.
  Phù hợp với cuộn vô hạn, vốn không cần biết tổng số trang.
- **DTO riêng `SearchSliceRes`:** cache Redis dùng Jackson, mà Jackson không dựng lại được `SliceImpl` của Spring.
  Vì vậy kết quả được đóng gói vào một record đơn giản (`content`, `page`, `size`, `last`).
- **Index DB (Flyway V153):** thêm index cho các cột lọc và sắp xếp hay dùng, để MySQL không phải quét cả bảng.
- **Frontend:**
  - `useInfiniteQuery` của React Query, tải 24 khóa mỗi lần khi cuộn tới cuối (IntersectionObserver).
  - `useDebounce` 300 ms: chỉ gọi API khi người dùng ngừng gõ.
  - Truyền `AbortSignal` để **hủy request cũ** khi bộ lọc đổi, tránh kết quả cũ ghi đè kết quả mới.
  - `keepPreviousData`: giữ danh sách cũ, làm mờ trong lúc tải, không nhấp nháy về trạng thái rỗng.

**Vì sao chọn:** đây là các kỹ thuật chuẩn của React Query và Spring Data, không phải thêm thư viện mới.

---

## 3. Đợt 1: "Khóa học của tôi" (`GET /api/v1/enrollments/mine`)

**Trước đây:** `EnrollmentService.getMyEnrollments` lấy danh sách ghi danh, rồi **trong vòng lặp** với từng khóa:
lấy khóa học, danh mục, giảng viên, đánh giá của học viên, chứng chỉ, danh sách bài học, tiến độ từng bài.
Với học viên có 45 khóa, một request tốn **476 câu SQL**.

**Kỹ thuật mới**
1. **`@EntityGraph`** trên `findWithCourseByUser_Email`: báo Hibernate lấy luôn `course`, `course.category`,
   `course.instructor` bằng JOIN trong cùng một câu, thay vì lazy-load từng cái.
2. **Truy vấn theo lô với `WHERE course_id IN (...)`:** 4 câu cho cả danh sách thay vì 4×N câu.
   - `findReviewRefsByUserAndCourseIds`: đánh giá
   - `findCertificateRefsByStudentAndCourseIds`: chứng chỉ
   - `findLessonRefsByCourseIds`: danh sách bài, đã sắp thứ tự
   - `findProgressRefsByUserAndCourseIds`: tiến độ
   Kết quả được gom vào `Map<courseId, …>` để tra cứu O(1) khi dựng response.
3. **Projection bằng record** (`CourseLessonRef`, `CourseProgressRef`, …): JPQL `select new ...` chỉ lấy đúng
   các cột cần, không dựng cả entity, nên nhẹ hơn và tránh lazy-load ngầm.
4. "Học tiếp" và "lần truy cập gần nhất" được tính từ bản ghi tiến độ mới nhất có sẵn trong bộ nhớ.

**Vì sao chọn:** `@EntityGraph` dùng cho quan hệ **một-một/nhiều-một** (mỗi ghi danh có đúng 1 khóa). Truy vấn
theo lô dùng cho quan hệ **một-nhiều** (một khóa có nhiều bài, nhiều tiến độ). Nếu JOIN FETCH cả các quan hệ
một-nhiều trong một câu sẽ tạo tích Descartes (số dòng nhân lên rất lớn). Chia thành vài câu theo lô là cân bằng
tốt nhất: số câu SQL **cố định**, không phụ thuộc số khóa học.

**Kết quả:** 476 → **13** SQL/request; 16 → **~280** req/s; p50 2.469 → **~170 ms**; p99 4.590 → **~360 ms**.

---

## 4. Đợt 2: Tìm kiếm khóa học (`GET /api/v1/courses?keyword=…`)

**Trước đây:** mỗi khóa trong trang kết quả lại gọi riêng: lấy danh mục, giảng viên, đếm đánh giá, đếm số bài,
tổng thời lượng, tính giá sau coupon (bản thân hàm này cũng query coupon và số lượt dùng). Kết quả là
**121 câu SQL** cho một lần tìm. Tìm có từ khóa không dùng được cache nên đây là điểm nghẽn thật.

**Kỹ thuật mới**
1. **`@EntityGraph(category, instructor)`** cho cả 3 truy vấn tìm kiếm: `searchPublic`, `searchPublicByReviews`,
   `searchPublicByRelevance`.
2. **Đếm bằng `GROUP BY` cho cả trang:**
   - `countVisibleByCourseIds` → `CourseReviewCount(courseId, count)`
   - `findLessonStatsByCourseIds` → `CourseLessonStats(courseId, lessonCount, totalDurationSec)`
3. **Tính giá theo lô** `CouponService.getDisplayPrices(Collection<Course>)`:
   - Nạp coupon tự động **một lần**.
   - Kiểm số lượt dùng **một lần cho mỗi coupon**.
   - Nạp phạm vi áp dụng cho mỗi coupon.
   - Áp đúng các quy tắc của hàm cũ `getDisplayPrice` (có test đối chiếu).
4. Gom tất cả trong `mapSummaries(List<Course>)` và bỏ hàm map từng khóa cũ.
5. **Sắp xếp theo whitelist:** chỉ nhận các giá trị sort cho phép, tránh lỗi và tránh chèn tham số lạ.

**Vì sao chọn:** cùng nguyên lý với đợt 1. Số câu SQL không còn tăng theo số khóa trong trang. Phần tính giá được
tách thành hàm theo lô thay vì sửa hàm cũ, để các nơi khác đang dùng `getDisplayPrice` không bị ảnh hưởng.

**Kết quả:** 121 → **15** SQL/request; 76 → **~510** req/s; p50 639 → **~115 ms**; p99 993 → **~300 ms**.

---

## 5. Đợt 3: Job nền và danh sách lớn

Vấn đề chung: code dùng `findAll()` để nạp **toàn bộ bảng** vào RAM rồi lọc bằng Java. Khi dữ liệu lớn, cách này
vừa chậm vừa có thể làm hết bộ nhớ (OutOfMemory).

| Chỗ sửa | Trước đây | Sau khi sửa | Vì sao |
| --- | --- | --- | --- |
| `FlashcardNotificationJob` (8h sáng mỗi ngày) | Nạp mọi `flashcard_reviews`, lọc và đếm bằng Java | `countDueByUser(now)`: DB tự `GROUP BY` và trả `FlashcardDueCount(email, count)`, dùng sẵn index `(user_id, next_review_at)` | DB đếm nhanh hơn nhiều và chỉ trả về vài dòng |
| `StreakReminderJob` (mỗi giờ) | Nạp mọi `user_streaks` mỗi giờ | `findReminderCandidates(minActivityDate, Pageable)`: chỉ lấy streak còn chuỗi và có học trong 2 ngày gần đây, theo lô 500 bằng `Slice` | Lọc sớm trong DB; xử lý theo lô giữ bộ nhớ ổn định |
| Gửi thông báo hệ thống "Tất cả" | Nạp mọi User để lấy ID | `findIdsForAudience(role, Pageable)` chỉ lấy cột ID, lô 500 (`RECIPIENT_BATCH`) | Chỉ đọc đúng cột cần, theo lô |
| Trang admin Quản lý người dùng | `GET /users` tải **tất cả** người dùng về trình duyệt rồi lọc | `GET /users/admin`: tìm, lọc, sắp xếp, phân trang phía server; `GET /users/admin/counts`: đếm theo vai trò bằng `GROUP BY` | Trình duyệt chỉ nhận 1 trang. Kích thước trang tối đa 100, cột sắp xếp theo whitelist |

Frontend trang admin dùng `useDebounce` cho ô tìm kiếm và nhận `Page<AdminUser>` từ server.

**Kết quả:** bộ nhớ và số dòng đọc giờ chỉ tăng theo **số bản ghi cần xử lý** hoặc **một trang**, không còn theo
tổng dữ liệu. Đợt này không đo req/s vì là job nền; được kiểm bằng 15 test mới.

---

## 6. Đợt 4: Ổn định production

### 6.1 Mức log
- **Trước:** `application.yml` đặt `com.lms: DEBUG` cho **mọi** môi trường. Log DEBUG ghi rất nhiều, tốn I/O và
  dung lượng đĩa, có thể lộ dữ liệu nhạy cảm.
- **Sau:** mặc định `INFO`. DEBUG (kèm log SQL của Hibernate) chỉ bật trong `application-dev.yml`.
- **Lưu ý:** docker-compose đang mặc định chạy profile `dev`. Khi deploy thật cần đặt `SPRING_PROFILES_ACTIVE` khác `dev`.

### 6.2 Giới hạn số request (rate limit)
- **Trước:** không giới hạn. Một IP có thể dò mật khẩu, spam OTP hay email quên mật khẩu không giới hạn, hoặc
  gửi dồn request làm nghẽn server. Chỉ các tính năng AI có hạn mức riêng.
- **Kỹ thuật mới:** `RateLimitFilter` + `RateLimiter` (thư mục `common/ratelimit/`) dùng thuật toán **cửa sổ cố
  định 1 phút** trên Redis:
  - Mỗi request thực hiện `INCR rl:{nhóm}:{ip}:{phút}`. Lần đầu trong phút thì đặt `EXPIRE` 65 giây để khóa tự xóa.
  - Nếu bộ đếm vượt ngưỡng thì trả **HTTP 429** kèm header `Retry-After` (số giây còn lại) và thông báo tiếng Việt.

  | Nhóm | Ngưỡng / phút / IP |
  | --- | --- |
  | `auth`: POST `/api/v1/auth/**` (đăng nhập, đăng ký, OTP, quên mật khẩu) | 20 |
  | `search`: GET `/api/v1/courses` | 120 |
  | `api`: các API còn lại | 600 |

  Không áp dụng cho `/api/internal/**` (AI worker gọi về), `/actuator` và request `OPTIONS`.
  Ngưỡng chỉnh được qua `app.rate-limit.*` mà không cần sửa code.
- **Vì sao chọn Redis + cửa sổ cố định:**
  - Redis đã có sẵn trong hệ thống. Không thêm thư viện mới như Bucket4j (Maven đang chạy offline).
  - Bộ đếm nằm ở Redis nên **dùng chung được khi chạy nhiều backend**, khác bộ đếm trong RAM.
  - Cửa sổ cố định đơn giản, chỉ 1–2 lệnh Redis mỗi request. Nhược điểm là có thể vọt gấp đôi ở ranh giới hai
    phút, nhưng chấp nhận được cho mục đích chống lạm dụng.
- **Các chi tiết an toàn:**
  - **IP lấy phần tử *cuối* của `X-Forwarded-For`.** Caddy nối IP thật vào cuối header, còn phần đầu client tự
    gửi được. Nếu lấy phần tử đầu, kẻ tấn công đổi header là lách được giới hạn.
  - **Fail-open:** Redis lỗi thì cho request đi qua và ghi cảnh báo, để Redis hỏng không kéo sập cả hệ thống.
  - Bộ lọc gắn **trong chuỗi Spring Security, sau CORS**. Nhờ vậy response 429 vẫn có header CORS và trình duyệt
    đọc được lỗi. Đăng ký servlet tự động đã được tắt để bộ lọc không chạy 2 lần.
- **Kiểm thật:** 20 lần đăng nhập sai đầu tiên được xử lý bình thường, từ lần 21 nhận 429 với `Retry-After: 44`.
  Tìm khóa học vẫn trả 200.

### 6.3 Pool kết nối DB (HikariCP)

| Tham số | Trước | Sau | Ý nghĩa |
| --- | --- | --- | --- |
| `maximum-pool-size` | 20 | `${DB_POOL_MAX:20}` | Giữ 20 (đủ cho khoảng 500 req/s đã đo vì truy vấn giờ đã ngắn), nhưng chỉnh được qua biến môi trường |
| `connection-timeout` | 30.000 ms | 5.000 ms | Hết kết nối thì báo lỗi sau 5 s thay vì treo người dùng 30 s (fail fast) |
| `idle-timeout` | mặc định | 300.000 ms | Đóng kết nối nhàn rỗi sau 5 phút |
| `max-lifetime` | mặc định | 1.500.000 ms | Thay kết nối sau 25 phút, trước khi MySQL tự cắt (`wait_timeout`) |
| `leak-detection-threshold` | không | 20.000 ms | Cảnh báo trong log nếu code giữ kết nối quá 20 s (dấu hiệu rò kết nối) |

**Vì sao không tăng pool:** pool lớn hơn không làm DB nhanh hơn, chỉ khiến MySQL phải xử lý song song nhiều hơn.
Sau đợt 1–2 mỗi request chỉ giữ kết nối rất ngắn, nên 20 là đủ.

---

## 7. Đợt 5: Chat realtime qua WebSocket

**Trước đây**
- Khung chat mở gọi `GET /messages/conversations/{id}` **5 giây/lần**, danh sách hội thoại gọi **15 giây/lần**.
  Mỗi người mở chat tạo khoảng **16 request/phút** dù không ai nhắn gì, và tin mới có thể đến trễ tới 5 giây.
- Backend **đã** đẩy tin qua STOMP (`/topic/conversations/{id}`) nhưng frontend không dùng.
- **Lỗ hổng bảo mật:** WebSocket không kiểm quyền khi subscribe. Ai biết id hội thoại hay id người dùng cũng nghe
  được tin nhắn riêng và thông báo của người khác.

**Kỹ thuật mới**
1. **Backend: `StompAuthInterceptor`** (một `ChannelInterceptor` gắn vào kênh nhận từ client):
   - Frame **CONNECT:** đọc `Authorization: Bearer <JWT>`. Nếu hợp lệ thì lưu userId và email vào phiên WebSocket.
     Không có token vẫn kết nối được, để nghe các kênh công khai như chat bài học.
   - Frame **SUBSCRIBE:**
     - `/topic/notifications/{x}`: chỉ chính chủ (x là id hoặc email của mình).
     - `/topic/conversations/{id}`: chỉ 2 người trong hội thoại, kiểm bằng `ConversationRepository.isParticipant`
       (một câu `count` nhỏ).
     - Sai quyền thì ném `MessageDeliveryException`, client nhận frame ERROR.
2. **Frontend:**
   - `useConversationMessages` mở kết nối STOMP vào `/topic/conversations/{id}`. Có tin mới thì **invalidate**
     query để React Query tải lại.
     - Không chèn thẳng payload vì field `mine` trong đó tính theo người gửi.
     - Tải lại còn giúp đánh dấu tin đã đọc.
   - `NotificationProvider` (đã có sẵn socket thông báo) nhận `NEW_MESSAGE` thì làm mới danh sách hội thoại.
   - Polling giảm còn **60 giây**, chỉ làm lưới an toàn khi socket rớt.

**Vì sao chọn WebSocket/STOMP:** hạ tầng đã có sẵn (Spring STOMP, `@stomp/stompjs`, SockJS) và đang dùng cho
lồng tiếng, thông báo, chat bài học. Server chủ động đẩy khi có sự kiện nên không tốn request lúc rảnh, và tin đến
gần như tức thì. Kiểm quyền ở `ChannelInterceptor` là cách chuẩn của Spring, vì bước bắt tay SockJS là HTTP
public, không chặn JWT ở đó được.

**Kết quả:** khoảng 16 → 2 request/phút mỗi người mở chat, tin đến tức thì. Thử với kết nối ẩn danh: subscribe
hội thoại và thông báo bị từ chối, chat bài học vẫn nghe được.

---

## 8. Những gì chưa làm (đề xuất tiếp)

| Việc | Lý do để sau |
| --- | --- |
| Broker ngoài (RabbitMQ/Redis relay) cho WebSocket | Simple broker hiện nằm trong RAM một backend. Chỉ cần khi chạy **nhiều** instance |
| Đợt 6: giám sát (Spring Actuator + Prometheus/Grafana) và kịch bản k6 nhiều người dùng | Để đo năng lực thật trên server và theo dõi lâu dài |
| Phân trang tin nhắn trong một hội thoại, danh sách khiếu nại và hỗ trợ | Dữ liệu tăng chậm, chưa thành nghẽn |
| Lỗi cũ: `FlashcardNotificationJob` gửi tới `/topic/notifications/{email}` trong khi frontend nghe theo user id | Nhắc ôn flashcard có thể chưa từng đến người dùng; cần đổi sang id |

---

## 9. Commit liên quan (nhánh `feat/performance`, chưa push)

| Repo | Commit | Nội dung |
| --- | --- | --- |
| backend | `adef6a4` (trên `feat/UI`) | Slice cho tìm kiếm, index V153 |
| backend | `941ceee` | Đợt 1 |
| backend | `1fcee4c` | Đợt 2 |
| backend | `d646948` | Đợt 3 |
| backend | `73a7a7c` | Đợt 4 |
| backend | `2c38480` | Đợt 5 |
| frontend | `9b875b2` | Đợt 3 (trang admin người dùng) |
| frontend | `7140f28` | Đợt 5 (chat qua WebSocket) |

## 10. Nguyên tắc chung rút ra

1. **Đo trước, sửa sau:** đếm số câu SQL mỗi request là cách rẻ nhất để tìm N+1.
2. **Số câu SQL phải cố định**, không tăng theo số phần tử: dùng JOIN cho quan hệ nhiều-một, truy vấn `IN (…)`
   theo lô cho quan hệ một-nhiều.
3. **Để DB làm việc của DB:** đếm, lọc, phân trang trong SQL thay vì `findAll()` rồi xử lý bằng Java.
4. **Fail fast và fail-open đúng chỗ:** pool DB báo lỗi nhanh; rate limit bỏ qua khi Redis lỗi.
5. **Đẩy thay vì hỏi:** dùng WebSocket cho dữ liệu realtime, polling chỉ làm dự phòng.
6. **Mỗi thay đổi có test:** viết test trước (TDD), chạy toàn bộ test và kiểm thật trên hệ thống đang chạy trước
   khi commit.
