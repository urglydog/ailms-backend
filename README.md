# ailms-backend — Core Web Service

Backend lõi của **AI-Powered LMS**. Java 17 · Spring Boot 3 · Maven · MySQL 8.0 · Redis.

> **Chạy hệ thống:** xem **[RUNNING.md](./RUNNING.md)**.
> `docker-compose.yml` điều phối cả 3 service nằm trong repo này.

## Vai trò trong kiến trúc

Đây là **Service 1** của kiến trúc Dịch vụ kép: xử lý tác vụ I/O-bound, cần giao dịch
ACID và bảo mật cao.

- Sở hữu **toàn bộ MySQL** — AI Worker không bao giờ ghi trực tiếp vào cơ sở dữ liệu.
- Là producer đẩy job vào Redis (`lms:dubbing:jobs`) cho AI Worker.
- Là nơi duy nhất phát WebSocket tới frontend.

## Cấu trúc

```
src/main/java/com/lms/
├─ LmsApplication.java
├─ common/
│  ├─ config/        SecurityConfig, DevDataSeeder
│  ├─ entity/        BaseEntity (id, createdAt, updatedAt)
│  ├─ enums/         11 enum
│  └─ exception/     DomainException + 7 lớp con, GlobalExceptionHandler
├─ auth/             User, InstructorRequest
├─ notification/     Notification
├─ catalog/          Category, Course, Chapter, Lesson, LessonDocument
├─ enrollment/       Enrollment, LessonProgress, CourseReview
├─ payment/          Payment
├─ dubbing/          AiJob, AiJobChunk, Transcript, TranscriptSegment,
│                    AudioTrack, AudioChunk, VoiceMapping
├─ material/         MaterialGeneration, Mindmap, FlashcardDeck, Flashcard,
│                    FlashcardReview, Quiz, QuizQuestion, QuizOption,
│                    QuizAttempt, QuizAnswer
└─ chat/             ChatSession, ChatMessage
```

Chia package theo **module nghiệp vụ**, mỗi module có `entity/` và `repository/`.
Tổng **31 entity** — đủ toàn bộ mô hình miền ngay từ Giai đoạn 0.

## Ba quy ước không được vi phạm

1. **Không dùng `@Data` trên entity.** Lombok `@Data` sinh `equals`/`hashCode` trên
   mọi field kể cả quan hệ LAZY → `LazyInitializationException` và so sánh sai.
   Chỉ dùng `@Getter @Setter`; `equals`/`hashCode` đã viết trong `BaseEntity` dựa
   trên `id`.
2. **Mọi quan hệ `FetchType.LAZY`.** Cần nạp kèm thì dùng `@EntityGraph` tại đúng
   query cần, không đổi mapping sang `EAGER`.
3. **Không trả entity ra controller.** Dùng Java `record` DTO.

## Schema

`src/main/resources/db/migration/V1__init_schema.sql` được **sinh tự động từ các
entity Java** — không sửa tay. Hibernate chạy `ddl-auto: validate` nên lệch một cột
là app fail ngay khi khởi động.

Seed dữ liệu tham chiếu (danh mục, giọng đọc) ở `db/dev/V100__seed_dev_reference.sql`,
chỉ chạy ở profile `dev`. Tài khoản mẫu do `DevDataSeeder` tạo để mật khẩu được băm
bằng `PasswordEncoder` thật thay vì nhúng chuỗi hash không kiểm chứng được vào SQL.

## Đặc tả nghiệp vụ

Trước khi viết code, tra skill trong workspace cha:

| Cần gì | Đọc |
| --- | --- |
| Chức năng nào chạm entity/rule nào | `Skills/CodeSkills/05_AIPoweredLMS/skills/lms-usecase-map/` |
| Thuộc tính, ràng buộc entity | `.../reference/entities.md` |
| 63 quy tắc nghiệp vụ | `.../reference/business-rules.md` |
| Quy chuẩn code backend | `Skills/CodeSkills/04_TechStack/rules/lms/lms-springboot-backend.md` |

Kế hoạch 11 giai đoạn: `doc/DEVELOPMENT_PLAN.md`.
