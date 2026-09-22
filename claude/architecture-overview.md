# Architecture Overview — AI-Powered LMS

## Tổng quan hệ thống

```
┌──────────────────────────────────────────────────────────────┐
│                    FRONTEND (Next.js 14)                     │
│  Port 3000 | App Router | TypeScript | TailwindCSS           │
│                                                              │
│  Routes:                                                     │
│  /(public)     — trang công khai, landing, catalog           │
│  /(authenticated) — auth flow (login, register, oauth)      │
│  /(student)    — dashboard học viên, materials, progress     │
│  /(learn)      — learning player (video + tutor + quiz)      │
│  /instructor   — workspace giảng viên                        │
│  /teaching     — quản lý khóa học, curriculum editor        │
│  /admin        — trang admin                                 │
└────────────────────────┬─────────────────────────────────────┘
                         │ REST API + SSE
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                  BACKEND (Spring Boot 3)                     │
│  Port 8080 | Java 21 | Maven | Hibernate/JPA | Flyway        │
│                                                              │
│  Modules (package com.lms.*):                                │
│  auth, catalog, enrollment, material, dubbing,               │
│  chat (AI tutor), live, payment, coupon, community,          │
│  communication, instructor, notification, wishlist, common   │
│                                                              │
│  Security: JWT (access 15m / refresh 7d) + Google OAuth2    │
│  DB: MySQL 8 (ddl-auto=validate, Flyway migration)          │
│  Cache/Queue: Redis                                          │
│  Storage: Backblaze B2                                       │
└──────────┬──────────────────────────┬────────────────────────┘
           │ Internal HTTP            │ Redis LPUSH/BRPOP
           │ (INTERNAL_API_TOKEN)     │ (job queue)
           ▼                          ▼
┌─────────────────────┐   ┌──────────────────────────────────┐
│  AI-WORKER (FastAPI │   │  Celery Worker + Celery Beat     │
│  + Celery, Python)  │   │                                  │
│  Port 8000          │   │  Tasks:                          │
│                     │   │  · dubbing.py  — UC19 lồng tiếng │
│  Providers:         │   │  · material.py — UC25 sinh học   │
│  · Gemini (LLM)     │   │    liệu (Mindmap/Flash/Quiz)     │
│  · Supabase Vector  │   │  · transcript_extraction.py      │
│  · WhisperX (STT)   │   │  · maintenance.py (beat/cron)    │
│                     │   │                                  │
│  Services:          │   │  Scheduler:                      │
│  · dubbing_service  │   │  · cleanup temp files (hourly)   │
│  · tutor_service    │   │  · SRS flashcard remind (07:00)  │
│  · translation      │   │  · unused audio report (Mon)     │
│  · tutor_indexing   │   │  · old notifications cleanup     │
└─────────────────────┘   └──────────────────────────────────┘
```

## Luồng dữ liệu chính

### 1. Lồng tiếng video (UC19 — Dubbing Pipeline)
```
FE → POST /api/v1/dubbing/lessons/{id}/dub
  → BE acquires Redis lock (lock:dub:{lessonId}:{lang})
  → BE tạo AiJob (PENDING) + LPUSH vào lms:dubbing:jobs
  → AI-Worker Celery nhận task → WhisperX STT → dịch Gemini
  → sinh audio (TTS) chunk từng đoạn → merge → upload B2
  → callback POST /internal/dubbing/complete về BE
  → BE cập nhật AudioTrack.status = READY
  → FE poll GET /api/v1/dubbing/lessons/{id}/status
```

### 2. Sinh học liệu AI (UC25 — Material Generation)
```
FE → POST /api/v1/materials/generate
  → BE tạo MaterialGeneration (PENDING) → trả 202 + generationId
  → BE LPUSH vào Celery queue
  → AI-Worker lấy transcript → dịch nếu cần → gọi Gemini LLM
  → parse & validate JSON/Mermaid (retry tối đa 2 lần)
  → callback POST /internal/materials/{id}/complete về BE
  → FE poll GET /api/v1/materials/generations/{id}
```

### 3. AI Tutor Chat (UC30)
```
FE → POST /api/v1/tutor/sessions/{id}/messages
  → BE chuyển tiếp → AI-Worker /chat/completions (streaming SSE)
  → AI-Worker: Supabase Vector tìm ngữ cảnh transcript liên quan
  → Gemini sinh câu trả lời streaming
  → FE nhận SSE stream hiển thị real-time
  Quota: 30 tin/ngày/user (Redis counter)
```

### 4. Live Classroom (UC50 — LiveKit)
```
FE → POST /api/v1/live/sessions → BE tạo LiveSession (SCHEDULED)
FE → GET  /api/v1/live/sessions/{id}/token → BE gọi LiveKit Cloud API → trả JWT token
FE → LiveKit SDK kết nối WebRTC trực tiếp với LiveKit Cloud
LiveKit Webhook → BE /internal/livekit/webhook → cập nhật status
  (SCHEDULED → LIVE → ENDED, BR-LIVE-09: auto-end sau 60s disconnect)
```

### 5. Thanh toán (UC40)
```
FE → POST /api/v1/payments/create → BE tạo Payment (PENDING), txn_ref là idempotency key
  → BE redirect URL VNPay/MoMo/ZaloPay/PayOS
  → Gateway callback IPN → BE verify signature → Payment.status = PAID
  → BE tạo Enrollment, tính instructor_earning theo revenue_source:
      ORGANIC: 37% instructor / 63% platform
      INSTRUCTOR_REFERRAL: 97% instructor / 3% platform
  → FE polling GET /api/v1/payments/{txnRef}/status
```

## Công nghệ cốt lõi

| Layer | Tech |
|---|---|
| Frontend | Next.js 14 (App Router), TypeScript, TailwindCSS |
| Backend | Spring Boot 3, Java 21, Hibernate JPA, Flyway |
| AI Worker | FastAPI, Celery, Python, WhisperX, Gemini API |
| Database | MySQL 8 (lms_db), Flyway versioned migration |
| Cache/Queue | Redis (job queue, rate limiting, session locks) |
| Storage | Backblaze B2 (video chunks, audio tracks, tài liệu PDF) |
| AI/LLM | Google Gemini (chat, STT dịch, sinh học liệu) |
| Vector DB | Supabase pgvector (AI Tutor context retrieval) |
| Live Stream | LiveKit Cloud (WebRTC, JWT token) |
| Auth | JWT (HS256) + Google OAuth2 |
| Payment | VNPay, MoMo, ZaloPay, PayOS |
| Deploy | Docker Compose (be, fe, ai-worker, ai-beat, mysql, redis) |

## Quy tắc bảo mật & hạn ngạch (từ application.yml)

- JWT: access token 15 phút, refresh token 7 ngày
- Rate limit (Redis):
  - Student lồng tiếng: 15 lần/ngày
  - Instructor lồng tiếng: 30 lần/ngày
  - Sinh học liệu: 6 lần/ngày
  - AI Tutor: 30 tin/ngày
  - Discovery (guest): 15 lần/giờ
- Dubbing lock TTL: 30 phút (tránh job trùng lặp)
- Login fail lock: Redis key `login_fail:{email}`
- Upload tối đa: video 2GB, document 50MB/file, 5 file/lesson
