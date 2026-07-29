-- =====================================================================
-- AI-Powered LMS - Schema khoi tao (31 bang)
--
-- File nay duoc SINH TU DONG tu cac entity Java, khong sua tay.
-- Hibernate chay voi ddl-auto=validate se doi chieu lai schema nay voi
-- 31 entity khi app khoi dong: lech mot cot la app fail ngay.
--
-- Dac ta day du: Skills/CodeSkills/05_AIPoweredLMS/reference/entities.md
-- =====================================================================

-- ---------------------------------------------------------------------
-- users  (entity: User, module: auth)
-- ---------------------------------------------------------------------
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255),
    full_name VARCHAR(100) NOT NULL,
    avatar_url VARCHAR(500),
    role VARCHAR(20) NOT NULL DEFAULT 'STUDENT',
    auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    preferred_language VARCHAR(10) NOT NULL DEFAULT 'vi-VN',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_users_role ON users (role);

-- ---------------------------------------------------------------------
-- categories  (entity: Category, module: catalog)
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_slug UNIQUE (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- voice_mappings  (entity: VoiceMapping, module: dubbing)
-- ---------------------------------------------------------------------
CREATE TABLE voice_mappings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    language VARCHAR(10) NOT NULL,
    voice_name VARCHAR(100) NOT NULL,
    gender VARCHAR(10) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_voice_lang_name UNIQUE (language, voice_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- instructor_requests  (entity: InstructorRequest, module: auth)
-- ---------------------------------------------------------------------
CREATE TABLE instructor_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    motivation TEXT,
    credential_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reject_reason TEXT,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_instructor_requests_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- notifications  (entity: Notification, module: notification)
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    link_url VARCHAR(500),
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read);

-- ---------------------------------------------------------------------
-- courses  (entity: Course, module: catalog)
-- ---------------------------------------------------------------------
CREATE TABLE courses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(280) NOT NULL,
    description TEXT,
    thumbnail_url VARCHAR(500),
    level VARCHAR(20) NOT NULL DEFAULT 'BEGINNER',
    price DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    is_free BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    reject_reason TEXT,
    resubmit_count INT NOT NULL DEFAULT 0,
    avg_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    total_lessons INT NOT NULL DEFAULT 0,
    category_id BIGINT NOT NULL,
    instructor_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_courses_slug UNIQUE (slug),
    CONSTRAINT fk_courses_category_id FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_courses_instructor_id FOREIGN KEY (instructor_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_courses_status_category ON courses (status, category_id);
CREATE INDEX idx_courses_instructor ON courses (instructor_id);
CREATE INDEX idx_courses_level ON courses (level);
CREATE INDEX idx_courses_is_free ON courses (is_free);

-- ---------------------------------------------------------------------
-- chapters  (entity: Chapter, module: catalog)
-- ---------------------------------------------------------------------
CREATE TABLE chapters (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    course_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chapters_course_id FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_chapters_course_order ON chapters (course_id, display_order);

-- ---------------------------------------------------------------------
-- lessons  (entity: Lesson, module: catalog)
-- ---------------------------------------------------------------------
CREATE TABLE lessons (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    video_source VARCHAR(20) NOT NULL,
    video_url VARCHAR(1000) NOT NULL,
    youtube_id VARCHAR(50),
    duration_sec INT NOT NULL DEFAULT 0,
    source_language VARCHAR(10),
    display_order INT NOT NULL DEFAULT 0,
    is_preview BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    chapter_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lessons_chapter_id FOREIGN KEY (chapter_id) REFERENCES chapters (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_lessons_chapter_order ON lessons (chapter_id, display_order);
CREATE INDEX idx_lessons_status ON lessons (status);

-- ---------------------------------------------------------------------
-- lesson_documents  (entity: LessonDocument, module: catalog)
-- ---------------------------------------------------------------------
CREATE TABLE lesson_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    file_size BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_lesson_documents_lesson_id FOREIGN KEY (lesson_id) REFERENCES lessons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- enrollments  (entity: Enrollment, module: enrollment)
-- BR-ENROLL-01: so huu vinh vien, KHONG co thao tac xoa/huy ghi danh
-- ---------------------------------------------------------------------
CREATE TABLE enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    progress_pct DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    enrolled_at DATETIME NOT NULL,
    completed_at DATETIME,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_enrollment_user_course UNIQUE (user_id, course_id),
    CONSTRAINT fk_enrollments_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_enrollments_course_id FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_enrollments_user ON enrollments (user_id);
CREATE INDEX idx_enrollments_course ON enrollments (course_id);

-- ---------------------------------------------------------------------
-- lesson_progress  (entity: LessonProgress, module: enrollment)
-- BR-PROGRESS-01: watched_sec khong cong doan tua nhanh; is_completed mot chieu
-- ---------------------------------------------------------------------
CREATE TABLE lesson_progress (
    id BIGINT NOT NULL AUTO_INCREMENT,
    last_position_sec INT NOT NULL DEFAULT 0,
    watched_sec INT NOT NULL DEFAULT 0,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_progress_user_lesson UNIQUE (user_id, lesson_id),
    CONSTRAINT fk_lesson_progress_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_lesson_progress_lesson_id FOREIGN KEY (lesson_id) REFERENCES lessons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_progress_user ON lesson_progress (user_id);

-- ---------------------------------------------------------------------
-- course_reviews  (entity: CourseReview, module: enrollment)
-- ---------------------------------------------------------------------
CREATE TABLE course_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    rating INT NOT NULL,
    comment TEXT,
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_user_course UNIQUE (user_id, course_id),
    CONSTRAINT fk_course_reviews_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_course_reviews_course_id FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- payments  (entity: Payment, module: payment)
-- BR-PAY-02/03: amount lay tu server; txn_ref la khoa idempotency cua callback IPN
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    txn_ref VARCHAR(100) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    platform_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    instructor_earning DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    payment_method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    gateway_txn_no VARCHAR(100),
    paid_at DATETIME,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_txn_ref UNIQUE (txn_ref),
    CONSTRAINT fk_payments_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_payments_course_id FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_payments_user_created ON payments (user_id, created_at);
CREATE INDEX idx_payments_status_created ON payments (status, created_at);
CREATE INDEX idx_payments_method ON payments (payment_method);

-- ---------------------------------------------------------------------
-- ai_jobs  (entity: AiJob, module: dubbing)
-- BR-DUB-05: active_flag = 1 khi dang chay, NULL khi ket thuc (MySQL khong co partial unique index)
-- ---------------------------------------------------------------------
CREATE TABLE ai_jobs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    job_type VARCHAR(30) NOT NULL DEFAULT 'DUBBING',
    target_language VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    active_flag INT DEFAULT 1,
    total_chunks INT NOT NULL DEFAULT 0,
    done_chunks INT NOT NULL DEFAULT 0,
    celery_task_id VARCHAR(100),
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at DATETIME,
    finished_at DATETIME,
    lesson_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_aijob_lesson_lang_active UNIQUE (lesson_id, target_language, active_flag),
    CONSTRAINT fk_ai_jobs_lesson_id FOREIGN KEY (lesson_id) REFERENCES lessons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_aijobs_status_created ON ai_jobs (status, created_at);

-- ---------------------------------------------------------------------
-- ai_job_chunks  (entity: AiJobChunk, module: dubbing)
-- ---------------------------------------------------------------------
CREATE TABLE ai_job_chunks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chunk_index INT NOT NULL,
    start_sec INT NOT NULL,
    end_sec INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    ai_job_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_chunk_job_index UNIQUE (ai_job_id, chunk_index),
    CONSTRAINT fk_ai_job_chunks_ai_job_id FOREIGN KEY (ai_job_id) REFERENCES ai_jobs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- transcripts  (entity: Transcript, module: dubbing)
-- ---------------------------------------------------------------------
CREATE TABLE transcripts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    language VARCHAR(10) NOT NULL,
    is_source BOOLEAN NOT NULL DEFAULT FALSE,
    full_text LONGTEXT,
    lesson_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transcript_lesson_lang UNIQUE (lesson_id, language),
    CONSTRAINT fk_transcripts_lesson_id FOREIGN KEY (lesson_id) REFERENCES lessons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- transcript_segments  (entity: TranscriptSegment, module: dubbing)
-- ---------------------------------------------------------------------
CREATE TABLE transcript_segments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seq INT NOT NULL,
    start_sec DECIMAL(10,3) NOT NULL,
    end_sec DECIMAL(10,3) NOT NULL,
    text TEXT NOT NULL,
    speech_rate DECIMAL(5,2),
    was_summarized BOOLEAN NOT NULL DEFAULT FALSE,
    transcript_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_segment_transcript_seq UNIQUE (transcript_id, seq),
    CONSTRAINT fk_transcript_segments_transcript_id FOREIGN KEY (transcript_id) REFERENCES transcripts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- audio_tracks  (entity: AudioTrack, module: dubbing)
-- BR-DUB-04: UNIQUE(lesson_id, language) => moi cap chi sinh audio MOT lan, tai su dung cho moi hoc vien
-- ---------------------------------------------------------------------
CREATE TABLE audio_tracks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    language VARCHAR(10) NOT NULL,
    voice_name VARCHAR(100) NOT NULL,
    final_url VARCHAR(1000),
    duration_sec INT NOT NULL DEFAULT 0,
    file_size BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    play_count BIGINT NOT NULL DEFAULT 0,
    lesson_id BIGINT NOT NULL,
    voice_mapping_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_track_lesson_lang UNIQUE (lesson_id, language),
    CONSTRAINT fk_audio_tracks_lesson_id FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT fk_audio_tracks_voice_mapping_id FOREIGN KEY (voice_mapping_id) REFERENCES voice_mappings (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_tracks_status_playcount ON audio_tracks (status, play_count);

-- ---------------------------------------------------------------------
-- audio_chunks  (entity: AudioChunk, module: dubbing)
-- ---------------------------------------------------------------------
CREATE TABLE audio_chunks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chunk_index INT NOT NULL,
    start_sec INT NOT NULL,
    end_sec INT NOT NULL,
    file_url VARCHAR(1000) NOT NULL,
    audio_track_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_audiochunk_track_index UNIQUE (audio_track_id, chunk_index),
    CONSTRAINT fk_audio_chunks_audio_track_id FOREIGN KEY (audio_track_id) REFERENCES audio_tracks (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- material_generations  (entity: MaterialGeneration, module: material)
-- BR-MAT-01: gan course_id, KHONG co lesson_id (hoc lieu o cap khoa hoc)
-- ---------------------------------------------------------------------
CREATE TABLE material_generations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    material_type VARCHAR(20) NOT NULL,
    language VARCHAR(10) NOT NULL,
    scope_type VARCHAR(30) NOT NULL,
    scope_ref_id BIGINT,
    quantity_level VARCHAR(20),
    difficulty_level VARCHAR(20),
    version_no INT NOT NULL DEFAULT 1,
    title VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    celery_task_id VARCHAR(100),
    error_message TEXT,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_material_generations_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_material_generations_course_id FOREIGN KEY (course_id) REFERENCES courses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_matgen_user_course ON material_generations (user_id, course_id);

-- ---------------------------------------------------------------------
-- mindmaps  (entity: Mindmap, module: material)
-- ---------------------------------------------------------------------
CREATE TABLE mindmaps (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mermaid_code LONGTEXT NOT NULL,
    node_count INT NOT NULL DEFAULT 0,
    material_generation_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_mindmaps_material_generation_id UNIQUE (material_generation_id),
    CONSTRAINT fk_mindmaps_material_generation_id FOREIGN KEY (material_generation_id) REFERENCES material_generations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- flashcard_decks  (entity: FlashcardDeck, module: material)
-- ---------------------------------------------------------------------
CREATE TABLE flashcard_decks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    card_count INT NOT NULL DEFAULT 0,
    material_generation_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_flashcard_decks_material_generation_id UNIQUE (material_generation_id),
    CONSTRAINT fk_flashcard_decks_material_generation_id FOREIGN KEY (material_generation_id) REFERENCES material_generations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- flashcards  (entity: Flashcard, module: material)
-- ---------------------------------------------------------------------
CREATE TABLE flashcards (
    id BIGINT NOT NULL AUTO_INCREMENT,
    front_text TEXT NOT NULL,
    back_text TEXT NOT NULL,
    flashcard_deck_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_flashcards_flashcard_deck_id FOREIGN KEY (flashcard_deck_id) REFERENCES flashcard_decks (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- flashcard_reviews  (entity: FlashcardReview, module: material)
-- ---------------------------------------------------------------------
CREATE TABLE flashcard_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    easiness DECIMAL(4,2) NOT NULL DEFAULT 2.50,
    interval_days INT NOT NULL DEFAULT 0,
    repetitions INT NOT NULL DEFAULT 0,
    next_review_at DATE NOT NULL,
    user_id BIGINT NOT NULL,
    flashcard_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_user_card UNIQUE (user_id, flashcard_id),
    CONSTRAINT fk_flashcard_reviews_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_flashcard_reviews_flashcard_id FOREIGN KEY (flashcard_id) REFERENCES flashcards (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_cardreview_user_next ON flashcard_reviews (user_id, next_review_at);

-- ---------------------------------------------------------------------
-- quizzes  (entity: Quiz, module: material)
-- ---------------------------------------------------------------------
CREATE TABLE quizzes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_count INT NOT NULL DEFAULT 0,
    material_generation_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quizzes_material_generation_id UNIQUE (material_generation_id),
    CONSTRAINT fk_quizzes_material_generation_id FOREIGN KEY (material_generation_id) REFERENCES material_generations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- quiz_questions  (entity: QuizQuestion, module: material)
-- BR-QUIZ-01/02: CO Y khong co cot explanation va timestamp_sec
-- ---------------------------------------------------------------------
CREATE TABLE quiz_questions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content TEXT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    quiz_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_questions_quiz_id FOREIGN KEY (quiz_id) REFERENCES quizzes (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- quiz_options  (entity: QuizOption, module: material)
-- ---------------------------------------------------------------------
CREATE TABLE quiz_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    quiz_question_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_options_quiz_question_id FOREIGN KEY (quiz_question_id) REFERENCES quiz_questions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- quiz_attempts  (entity: QuizAttempt, module: material)
-- BR-QUIZ-01: lam lai khong gioi han => KHONG dat UNIQUE(user_id, quiz_id)
-- ---------------------------------------------------------------------
CREATE TABLE quiz_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    score DECIMAL(4,2) NOT NULL,
    total_questions INT NOT NULL,
    correct_count INT NOT NULL,
    submitted_at DATETIME NOT NULL,
    quiz_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_attempts_quiz_id FOREIGN KEY (quiz_id) REFERENCES quizzes (id),
    CONSTRAINT fk_quiz_attempts_user_id FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_attempts_user_quiz ON quiz_attempts (user_id, quiz_id);

-- ---------------------------------------------------------------------
-- quiz_answers  (entity: QuizAnswer, module: material)
-- ---------------------------------------------------------------------
CREATE TABLE quiz_answers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    selected_option_id BIGINT,
    quiz_attempt_id BIGINT NOT NULL,
    quiz_question_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_quiz_answers_selected_option_id FOREIGN KEY (selected_option_id) REFERENCES quiz_options (id),
    CONSTRAINT fk_quiz_answers_quiz_attempt_id FOREIGN KEY (quiz_attempt_id) REFERENCES quiz_attempts (id),
    CONSTRAINT fk_quiz_answers_quiz_question_id FOREIGN KEY (quiz_question_id) REFERENCES quiz_questions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- chat_sessions  (entity: ChatSession, module: chat)
-- ---------------------------------------------------------------------
CREATE TABLE chat_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_sessions_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_chat_sessions_lesson_id FOREIGN KEY (lesson_id) REFERENCES lessons (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- chat_messages  (entity: ChatMessage, module: chat)
-- ---------------------------------------------------------------------
CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sender VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    cited_timestamps TEXT,
    token_used INT,
    chat_session_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_chat_messages_chat_session_id FOREIGN KEY (chat_session_id) REFERENCES chat_sessions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_chatmsg_session ON chat_messages (chat_session_id);
