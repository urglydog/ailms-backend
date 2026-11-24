# Đợt 6: Ổn định khi nhiều người dùng và nhiều backend (nhóm A)

> Nhánh `perf` (backend `project/be`). Đây là đợt tiếp theo của 5 đợt trong
> [TONG_KET_ToiUu_HeThong.md](TONG_KET_ToiUu_HeThong.md). Đợt này chỉ sửa **cấu hình và hạ tầng**,
> không đổi logic nghiệp vụ.

## Tổng quan

| Mục | Vấn đề trước đây | Cách sửa | Biến môi trường |
| --- | --- | --- | --- |
| A1. Pool luồng cho job nền | 19 job `@Scheduled` chạy chung **1 luồng** (mặc định của Spring), job dài làm trễ các job khác | `spring.task.scheduling.pool.size = 6` | `SCHEDULER_POOL_SIZE` |
| A2. Khóa phân tán cho job | Chạy 2 backend thì mỗi job chạy **2 lần**: gửi trùng email, xuất trùng hóa đơn, đối soát 2 lần | ShedLock dùng Redis, gắn `@SchedulerLock` cho 16 job | — |
| A3. Tắt server an toàn | Deploy hoặc restart cắt ngang request và job đang chạy | `server.shutdown: graceful`, chờ tối đa 30 s | `SHUTDOWN_TIMEOUT` |
| A4. Nén response | JSON trả về không nén | Bật gzip cho JSON/text từ 1 KB | `HTTP_COMPRESSION` |
| A5. Virtual threads | — | **Chưa làm:** dự án biên dịch Java 17, virtual threads cần Java 21 | — |

## A1. Pool luồng cho job nền

**Trước đây:** Spring Boot mặc định tạo `ThreadPoolTaskScheduler` chỉ có **1 luồng**. Cả 19 job xếp hàng
trên luồng đó. Ví dụ: job phát thông báo hệ thống (mỗi 3 s) gửi cho hàng nghìn người mất vài chục giây;
trong lúc đó job hủy thanh toán quá hạn (mỗi 60 s) và job kết thúc buổi live (mỗi 15 s) phải chờ.

**Sau khi sửa:** pool 6 luồng, tên luồng `lms-sched-*` để dễ đọc log và thread dump. Khi tắt server, pool
chờ tối đa 30 s cho job đang chạy hoàn tất.

**Vì sao 6:** phần lớn job chạy vài giây mỗi vài phút, chỉ khoảng 3–4 job có thể trùng giờ chạy. 6 luồng đủ
dư mà không tranh pool kết nối DB (20 kết nối) với request của người dùng.

## A2. Khóa phân tán cho job (ShedLock + Redis)

**Trước đây:** không có cơ chế nào ngăn một job chạy cùng lúc trên nhiều instance. Với 1 backend thì không
sao, nhưng khi tăng lên 2 backend để chịu tải thì mọi job tài chính, hóa đơn, nhắc nhở đều chạy 2 lần.

**Kỹ thuật:** [ShedLock](https://github.com/lukas-krecan/ShedLock) (`shedlock-spring` +
`shedlock-provider-redis-spring` 6.3.1), cấu hình ở `common/config/SchedulerLockConfig.java`.
- Trước khi chạy, job đặt khóa `SET job-lock:lms:<tên> NX PX <lockAtMostFor>` trên Redis. Instance nào đặt
  được khóa thì chạy, các instance khác bỏ qua lượt đó.
- `lockAtMostFor`: thời gian giữ khóa tối đa, phòng khi instance giữ khóa bị sập (khóa tự hết hạn).
  Job 5–10 phút: `PT10M`; job hằng ngày/hằng giờ: `PT30M`; job 15–60 s: `PT1M`/`PT5M`.
- `lockAtLeastFor`: giữ khóa thêm ít nhất một khoảng (`PT1M` cho job cron, `PT5–10S` cho job lặp), để
  instance có đồng hồ lệch vài giây không chạy lại cùng một lượt.
- Dùng Redis vì hạ tầng đã có sẵn (cache, rate limit), không cần thêm bảng DB.

**Job được khóa (16):** `StreakReminderJob`, `DebtService` (2 job), `PayosPayoutService`,
`FinanceMaintenanceJob` (2 job), `InvoiceIssueJob`, `InvoiceTokenBackfillJob`, `MonthlyStatementService`,
`LiveSessionCronJob`, `FlashcardNotificationJob`, `DisputeDeadlineJob`, `PaymentCronJob`, `PaymentService`,
`RefundRateFlagJob`, `FinanceReportJob`.

**Cố ý không khóa (3):**
- `TutorSecurityService.refreshPatterns`: nạp danh sách pattern bảo mật vào **RAM của từng instance**, nên
  instance nào cũng phải tự chạy.
- `SystemAnnouncementService.consumeQueue`, `WishlistPriceDropService.consumeQueue`: lấy việc bằng `RPOP`
  của Redis, vốn nguyên tử, nên nhiều instance cùng chạy vẫn không trùng việc mà còn chia tải.

**Chốt chặn:** `SchedulerLockCoverageTest` quét mọi method `@Scheduled`; job mới thêm mà quên
`@SchedulerLock` (và không nằm trong danh sách cố ý bỏ) thì test fail. Test cũng kiểm tên khóa không trùng.

## A3. Tắt server an toàn (graceful shutdown)

**Trước đây:** khi `docker compose up` bản mới hoặc restart, Tomcat dừng ngay: request đang xử lý (thanh
toán, nộp bài kiểm tra) trả lỗi, job đang chạy dừng giữa chừng.

**Sau khi sửa:** `server.shutdown: graceful` — ngừng nhận request mới, chờ request cũ xong, tối đa
`SHUTDOWN_TIMEOUT` (30 s). Lưu ý `stop_grace_period` của Docker mặc định 10 s; muốn tận dụng đủ 30 s thì đặt
`stop_grace_period: 35s` cho service backend trong docker-compose khi deploy.

## A4. Nén response (gzip)

**Trước đây:** JSON danh sách khóa học, kết quả tìm kiếm, tài liệu học trả về dạng thô.

**Sau khi sửa:** Tomcat nén gzip các response JSON/text từ 1 KB trở lên khi trình duyệt gửi
`Accept-Encoding: gzip` (mọi trình duyệt hiện đại). JSON thường nhỏ đi 70–80%, giảm thời gian tải trên mạng
di động. Nếu sau này đặt Nginx/Cloudflare phía trước đã nén sẵn thì có thể tắt bằng `HTTP_COMPRESSION=false`.

## A5. Virtual threads — chưa làm

`spring.threads.virtual.enabled` cần Java 21, còn `pom.xml` đang biên dịch cho Java 17. Khi nâng lên Java 21
thì bật cờ này để request chờ I/O (Gemini, B2, PayOS) không chiếm luồng Tomcat; khi đó cần đo lại vì pool
DB 20 kết nối sẽ trở thành giới hạn chính.

## Kiểm chứng

- `mvn test`: toàn bộ test backend qua, gồm `SchedulerLockCoverageTest` mới.
- Kiểm nén: `curl -s -H "Accept-Encoding: gzip" -D - -o /dev/null http://localhost:8080/api/v1/courses`
  phải thấy `Content-Encoding: gzip`.
- Kiểm khóa job: khi backend chạy, `redis-cli KEYS 'job-lock:lms:*'` thấy khóa xuất hiện lúc job chạy.

## Kết quả chạy thử (24/11/2026, máy local, Docker)

- Backend khởi động bình thường, health `UP`.
- **Gzip:** `GET /api/v1/courses?size=24` trả `Content-Encoding: gzip`, **9.895 → 2.407 byte (−76%)**.
- **ShedLock:** quét Redis trong 20 s thấy các khóa `job-lock:lms:LiveSessionCronJob.endDisconnected`,
  `job-lock:lms:PaymentCronJob.expirePending`, `job-lock:lms:PaymentService.cancelExpired`,
  `job-lock:lms:DisputeDeadlineJob.markExpired` xuất hiện rồi tự hết hạn đúng như cấu hình.
