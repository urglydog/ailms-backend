# Core Features & Logic — AI-Powered LMS

## Danh sách tính năng hiện có

### ✅ Authentication & User
- Đăng ký/đăng nhập Local (email+password) + Google OAuth2
- JWT access/refresh token rotation
- Phân quyền: `STUDENT`, `INSTRUCTOR`, `ADMIN`
- Profile bắt buộc cho Instructor: `headline` + `bio` (validation backend block)
- Onboarding giảng viên: kiểm tra profile đầy đủ trước khi tạo khóa học

### ✅ Catalog & Curriculum
- CRUD khóa học (course → chapter → lesson hierarchy)
- Lesson hỗ trợ 2 nguồn video: `YOUTUBE` (youtube_id) + `UPLOAD` (B2)
- YouTube metadata validation qua YouTube Data API v3 (đã bỏ yt-dlp vì bị chặn bot)
- Upload document/PDF đính kèm bài học (max 5 file, 50MB/file)
- Course visibility: `PUBLIC` / `PRIVATE_INVITE` / `PRIVATE_PASSWORD` (V119)
- Course referral link `?ref={code}` → ảnh hưởng tỷ lệ doanh thu (V122)

### ✅ Course Moderation (Admin)
- Lifecycle: `DRAFT → PENDING → PUBLISHED` hoặc `→ REJECTED`
- Course bị PENDING/PUBLISHED: curriculum read-only (không sửa được)
- Từ chối có lý do (min 20 ký tự), nộp lại tối đa 5 lần
- Admin có thể ẩn review, archive/reactivate course (V120)

### ✅ Enrollment & Progress
- Ghi danh vĩnh viễn (không hủy — BR-ENROLL-01)
- Tiến độ tự động theo `watched_sec ≥ 90%` duration → `is_completed = true`
- Không tụt tiến độ khi giảng viên xóa bài quiz (soft delete)
- Shopping cart + thanh toán đa cổng: VNPay, MoMo, ZaloPay, PayOS
- Coupon: `PERCENT`/`FIXED`, `ALL_COURSES`/`SPECIFIC_COURSES`, `auto_apply`
- Mã giới thiệu giảng viên: earning 97% vs 37% organic (V122)

### ✅ Dubbing Pipeline (UC19)
- Giảng viên yêu cầu lồng tiếng → Celery worker xử lý bất đồng bộ
- WhisperX STT (transcript gốc) → Gemini dịch → TTS → merge audio → B2
- Lưu transcript (Supabase Vector) để tái sử dụng cho AI Tutor + sinh học liệu
- Redis lock tránh job trùng (30 phút TTL — BR-DUB-05)
- Student chọn ngôn ngữ audio khi xem bài (quota 15 lần/ngày)

### ✅ Transcript Extraction
- Tách riêng thành `transcript_extraction.py` task
- Tự động extract transcript khi upload/link video lesson
- Transcript lưu vào DB + đánh index Supabase Vector

### ✅ AI Material Generation (UC25)
- 3 loại học liệu: **Mindmap** (Mermaid flowchart), **Flashcard** (JSON), **Quiz** (JSON 4 đáp án)
- Phạm vi sinh: `COURSE` / `CHAPTER` / `LESSON` (scope_type + scope_ref_id)
- Ngôn ngữ đầu ra tùy chọn (dịch tự động qua Gemini nếu chưa có transcript ngôn ngữ đó)
- Validate output LLM: retry tối đa 2 lần, fail → `FAILED` status
- Quota: 6 lần/ngày/user
- Giảng viên có thể CRUD thủ công câu hỏi sau khi AI sinh (add/edit/delete)
- Quiz phân loại: `OFFICIAL_EXAM` (thi chính thức) / `LECTURE_QUIZ` (câu hỏi nhanh trong bài)

### ✅ Instructor Workspace (Material Manager)
- Cây thư mục đa cấp (`material_folders`, parent_id self-ref)
- Kéo thả phân phối học liệu vào course/chapter/lesson (`material_assignments`)
- Grid/List view toggle, search/filter
- Tình trạng "Official" = học liệu đã được assign (không cần cờ riêng — assignment-based)
- Soft delete + archive học liệu (`is_archived`)

### ✅ Student Learning Experience
- Video player với audio track đa ngôn ngữ
- Tab học liệu: **Official** (do instructor assign) vs **Cá nhân** (student tự tạo)
- Flashcard SRS (SM-2 algorithm): nút Hard/Good/Easy, hiển thị "Next Review" ngày
- Quiz làm lại không giới hạn, lưu lịch sử điểm
- Quiz attempt: cảnh báo khi bỏ trống câu (không block cứng)

### ✅ AI Tutor Chat (UC30)
- Chat per lesson, lịch sử session lưu DB
- Context-aware: RAG từ Supabase Vector (transcript segment liên quan)
- Streaming SSE real-time
- Đính kèm ảnh tối đa 3 file/lượt, 8MB/file
- Quota 30 tin/ngày
- Discovery mode (guest không cần đăng nhập): 15 lần/giờ

### ✅ Live Classroom (UC50)
- LiveKit Cloud WebRTC (không self-hosted)
- Lifecycle: `SCHEDULED → LIVE → ENDED`
- Auto-end sau 60s instructor disconnect (Cron job 15s — BR-LIVE-09)
- Đa ngôn ngữ live track
- Subtitle trực tiếp (original subtitle listener count)

### ✅ Communication (Udemy-style, V118)
- **Announcements**: Giảng viên gửi thông báo đến học viên trong khóa
- **Direct Messages**: 1-1 giảng viên ↔ học viên (per course context)
- **Course Assignments**: Giảng viên giao bài tập, học viên nộp file/text, giảng viên chấm điểm + feedback
- **Q&A Community** (lesson_chats): thảo luận public per bài học

### ✅ Admin Dashboard
- Duyệt/từ chối khóa học, quản lý user
- Thống kê doanh thu, AI usage
- Quản lý voice mapping cho TTS

---

## Logic nghiệp vụ quan trọng

### Revenue Share (V122)
```
Payment.revenue_source chốt lúc TẠO đơn (không tính lại khi PAID):
  - ORGANIC           → instructor 37%, platform 63%
  - INSTRUCTOR_REFERRAL → instructor 97%, platform 3%
Referral code: /courses/{slug}?ref={code}
```

### Course Readiness Checklist (trước khi SUBMIT)
- Phải có ít nhất 1 chapter + lesson
- Instructor profile phải có headline + bio
- Không check vật liệu học liệu (optional)

### SRS Flashcard (SM-2)
```
next_interval = current_interval * easiness_factor
EF_new = EF + (0.1 - (5-q)*(0.08+(5-q)*0.02))  [q: 0=Hard, 3=Good, 5=Easy]
Nhắc qua notification hàng ngày lúc 07:00 (Celery beat)
```

### Material Visibility Logic
```
Student thấy học liệu "Official" khi:
  material_assignments tồn tại với (material_id, course_id/chapter_id/lesson_id phù hợp)
  VÀ course đã PUBLISHED + student đã enrolled
  KHÔNG dùng cờ is_official riêng (đã bỏ)
```

### Dubbing Lock (BR-DUB-05)
```
Redis key: lock:dub:{lessonId}:{language}  TTL 30 phút
AiJob: UNIQUE(lesson_id, target_language, active_flag=1)
  → active_flag NULL khi job kết thúc → cho phép tạo job mới
```

---

## Lỗi đang tồn tại / Điểm cần xử lý

### 🔴 Bug đã biết

| # | Vấn đề | Nguyên nhân | File liên quan |
|---|---|---|---|
| 1 | Nút TTS (loa) bị ẩn trên Flashcard Official của học viên | Thiếu trường `language` trong API response hoặc không truyền xuống FlashcardViewer | `app/(student)/materials/[id]/page.tsx` |
| 2 | Học liệu assign vào Chapter không hiện phía học viên | Student API chỉ query theo `lessonId`, thiếu `chapterId` filter | Backend query endpoint materials |
| 3 | Menu "Đánh dấu Official / Bỏ Official" báo lỗi | Cơ chế cũ (is_official flag) đã bị thay bởi assignment-based | `MaterialFolderTree.tsx` — cần XÓA nút này |
| 4 | `ClassCastException` khi move material vào folder | Jackson ép `folderId` thành `Integer` thay vì `Long` | `InstructorMaterialController.java` |
| 5 | Xóa thư mục không trống báo FK constraint | `deleteFolder` không gỡ FK trước khi xóa | `MaterialFolderService.java` |
| 6 | YouTube yt-dlp bị chặn trên VPS | YouTube bot-detection chặn IP datacenter | ✅ ĐÃ FIX — chuyển sang YouTube Data API v3 |

### 🟡 Tính năng chưa hoàn thiện / TODO

| # | Hạng mục | Ghi chú |
|---|---|---|
| 1 | Chứng chỉ hoàn thành (Certificate PDF) | Chưa implement — cần khi progress = 100% |
| 2 | Import quiz/flashcard từ CSV/Excel | CurrentPlan.md đề xuất, chưa làm |
| 3 | Concurrent stream detection (heartbeat Redis) | CurrentPlan.md Task 10 — chưa implement |
| 4 | Device/session management | CurrentPlan.md Task 10 — chưa implement |
| 5 | Thanh progress bar tích hợp vào Learning Player header | CurrentPlan.md Task 1 |
| 6 | Loading overlay / spinner trên thao tác kéo thả | UpComming_Plan.md §2.1 |
| 7 | Toast notifications tiếng Việt thân thiện | UpComming_Plan.md §2.2 |
| 8 | Admin rate-limit AI usage dashboard | CurrentPlan.md Task 11A |
| 9 | Multi-choice quiz scoring | Đã có schema, cần verify logic tính điểm |

### 🔵 Quy tắc quan trọng khi tiếp tục phát triển

- **TUYỆT ĐỐI KHÔNG** thay `ddl-auto=validate` thành `update/create` — chỉ dùng Flyway migration
- **TUYỆT ĐỐI KHÔNG** hardcode secret trong code/yml — đọc từ env
- **KHÔNG** commit/push khi chưa confirm
- **KHÔNG** tự động build/lint FE khi đang sửa — hỏi trước
- **Soft delete** `course_resources`: luôn filter `is_deleted = false` trong query
- Mọi endpoint AI Worker → gọi qua Backend (FE không gọi trực tiếp `ai-api:8000`)
