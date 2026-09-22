# Database & Models — AI-Powered LMS

## Tổng quan

- **DB**: MySQL 8, charset `utf8mb4_unicode_ci`
- **Migration**: Flyway (`V1__` → `V122__`), `ddl-auto=validate` (sai 1 cột là fail boot)
- **BaseEntity**: `created_at` + `updated_at` cho tất cả entity

---

## Nhóm bảng theo domain

### AUTH
| Bảng | Mô tả |
|---|---|
| `users` | User chung: role (`STUDENT`/`INSTRUCTOR`/`ADMIN`), auth_provider (`LOCAL`/`GOOGLE`), `headline`, `bio` (V111), `is_active` |
| `instructor_requests` | *(bảng cũ, đã DROP tại V110)* |
| `instructor_verifications` | *(bảng cũ, đã DROP tại V110 — unified sang users.role)* |

> **V110**: Hợp nhất Instructor/Student thành 1 bảng `users` duy nhất với `role` enum.

---

### CATALOG
| Bảng | Mô tả |
|---|---|
| `categories` | Danh mục khóa học (name, slug) |
| `courses` | Khóa học: `status` (`DRAFT/PENDING/PUBLISHED/REJECTED/ARCHIVED`), `visibility` (`PUBLIC/PRIVATE_INVITE/PRIVATE_PASSWORD`), `referral_code` (V122), `reject_reason`, `resubmit_count` (max 5) |
| `chapters` | Chương học, thuộc course, có `display_order` |
| `lessons` | Bài học: `video_source` (`YOUTUBE/UPLOAD`), `youtube_id`, `is_preview`, `status` (`DRAFT/PUBLISHED`) |
| `lesson_documents` | File đính kèm bài học (PDF/...), tối đa 5 file/lesson, 50MB/file |
| `course_invites` | Danh sách email được mời vào khóa private (V119) |

**Quan trọng:**
- `courses.referral_code` (V122): sinh ngẫu nhiên 1 lần, dùng link `?ref={code}` → quyết định `revenue_source` lúc tạo đơn
- `courses.visibility` (V119): `PUBLIC` / `PRIVATE_INVITE` / `PRIVATE_PASSWORD`

---

### ENROLLMENT
| Bảng | Mô tả |
|---|---|
| `enrollments` | Ghi danh vĩnh viễn (UNIQUE user+course, không xóa — BR-ENROLL-01) |
| `lesson_progress` | Tiến độ xem video: `watched_sec`, `last_position_sec`, `is_completed` (một chiều, không tụt — BR-PROGRESS-01) |
| `course_reviews` | Đánh giá khóa học (1 user/course, `rating`, `is_hidden`) |

---

### MATERIAL (Học liệu AI)
| Bảng | Mô tả |
|---|---|
| `material_generations` | Master record: `material_type` (`MINDMAP/FLASHCARD/QUIZ`), `scope_type`, `status` (`PENDING/IN_PROGRESS/COMPLETED/FAILED`), `folder_id`, `is_archived`, `lesson_id`, `chapter_id` (V103/V105/V112) |
| `material_folders` | Cây thư mục học liệu (hỗ trợ đa cấp, `parent_id` tự tham chiếu), thuộc `course_id` |
| `material_assignments` | Phân phối học liệu đến course/chapter/lesson (V112) |
| `mindmaps` | Lưu `mermaid_code` (1-1 với material_generation) |
| `flashcard_decks` | Bộ thẻ (1-1 với material_generation) |
| `flashcards` | Từng thẻ: `front_text`, `back_text` |
| `flashcard_reviews` | SRS SM-2: `easiness` (EF), `interval_days`, `repetitions`, `next_review_at` (UNIQUE user+card) |
| `quizzes` | Quiz header (1-1 với material_generation), `is_official` (V8), `quiz_type` (`OFFICIAL_EXAM/LECTURE_QUIZ`) (V103) |
| `quiz_questions` | Câu hỏi, có `display_order` |
| `quiz_options` | 4 đáp án/câu, `is_correct` |
| `quiz_attempts` | Lần làm bài (không giới hạn retry, không UNIQUE — BR-QUIZ-01) |
| `quiz_answers` | Chi tiết đáp án từng lần làm |
| `course_resources` | File tĩnh (PDF/ZIP/PPT) đính kèm course/chapter/lesson (V105), có soft delete `is_deleted`, `deleted_at` (V106/V113-V115) |

---

### DUBBING (Lồng tiếng AI)
| Bảng | Mô tả |
|---|---|
| `ai_jobs` | Job lồng tiếng/transcript: `job_type`, `status` (`PENDING/IN_PROGRESS/COMPLETED/FAILED`), `active_flag` (partial unique — BR-DUB-05), `celery_task_id` |
| `ai_job_chunks` | Chunk của job (từng đoạn video) |
| `transcripts` | Transcript bài học: `language`, `is_source`, `full_text` (UNIQUE lesson+language) |
| `transcript_segments` | Từng đoạn: `seq`, `start_sec`, `end_sec`, `text`, `speech_rate` |
| `audio_tracks` | File audio đã dub: `language`, `voice_name`, `final_url`, `status` (UNIQUE lesson+language — BR-DUB-04) |
| `audio_chunks` | Chunk audio tạm (ghép thành final) |
| `voice_mappings` | Cấu hình giọng TTS: `language`, `voice_name`, `gender`, `is_default` |

---

### PAYMENT
| Bảng | Mô tả |
|---|---|
| `payments` | Đơn thanh toán: `txn_ref` (idempotency UNIQUE), `payment_method` (`VNPAY/MOMO/ZALOPAY/PAYOS`), `status` (`PENDING/PAID/FAILED`), `platform_fee`, `instructor_earning`, `revenue_source` (`ORGANIC/INSTRUCTOR_REFERRAL`) (V122), `paid_at` |
| `cart_items` | Giỏ hàng tạm (xóa sau khi thanh toán) |
| `coupons` | Mã giảm giá: `discount_type` (`PERCENT/FIXED`), `scope_type` (`ALL_COURSES/SPECIFIC_COURSES`), `auto_apply`, thời hạn hiệu lực |
| `coupon_courses` | Áp dụng coupon cho course cụ thể |
| `wishlist_items` | Wishlist |

---

### LIVE CLASSROOM
| Bảng | Mô tả |
|---|---|
| `live_sessions` | Phiên học trực tiếp: `status` (`SCHEDULED/LIVE/ENDED`), `room_name` (LiveKit), `visibility`, `scheduled_at`, `started_at`, `ended_at`, `instructor_disconnected_at` |
| `live_language_tracks` | Track ngôn ngữ đang phát trong live |

---

### CHAT & COMMUNITY
| Bảng | Mô tả |
|---|---|
| `chat_sessions` | Phiên AI Tutor (per user+lesson) |
| `chat_messages` | Tin nhắn: `sender` (`USER/AI`), `cited_timestamps`, `token_used` |
| `chat_message_attachments` | Ảnh đính kèm vào chat |
| `lesson_chats` | Q&A cộng đồng per bài học |

---

### COMMUNICATION (V118)
| Bảng | Mô tả |
|---|---|
| `announcements` | Thông báo khóa học từ giảng viên |
| `conversations` | Hộp thư 1-1 giảng viên–học viên (UNIQUE student+instructor) |
| `messages` | Tin nhắn trong conversation |
| `course_assignments` | Bài tập giảng viên giao (gắn lesson) |
| `assignment_submissions` | Nộp bài của học viên (UNIQUE assignment+student) |

---

### COMMON
| Bảng | Mô tả |
|---|---|
| `notifications` | Thông báo hệ thống, TTL 90 ngày, `is_read` |
| `ai_usage_log` | Log sử dụng token AI (audit/billing) |

---

## Quy tắc dữ liệu quan trọng

| Quy tắc | Mô tả |
|---|---|
| BR-ENROLL-01 | Enrollment không bao giờ xóa — vĩnh viễn |
| BR-PROGRESS-01 | `is_completed` chỉ lên, không xuống. `watched_sec` không cộng đoạn tua |
| BR-DUB-04 | 1 cặp (lesson_id, language) → 1 AudioTrack duy nhất |
| BR-DUB-05 | `active_flag=1` khi job đang chạy, `NULL` khi xong → partial UNIQUE |
| BR-QUIZ-01 | Làm quiz không giới hạn số lần, không UNIQUE(user, quiz) |
| BR-PAY-05 | `platform_fee` + `instructor_earning` chốt cứng lúc PAID, không tính lại |
| Soft Delete | `course_resources` có `is_deleted`, `deleted_at` — không hard delete |
