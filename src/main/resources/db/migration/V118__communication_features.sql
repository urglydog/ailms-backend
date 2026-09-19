-- (19/09/2026) — tính năng "Giao tiếp" kiểu Udemy: Thông báo (Announcements), Nhắn tin
-- (Messages), Bài tập nộp bài (Assignments). Q&A tái dùng bảng `lesson_chats` có sẵn (chỉ thêm
-- endpoint/service, không cần bảng mới).

CREATE TABLE IF NOT EXISTS announcements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_announcements_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    instructor_id BIGINT NOT NULL,
    course_id BIGINT NULL,
    last_message_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_conversations_student_instructor UNIQUE (student_id, instructor_id),
    CONSTRAINT fk_conversations_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_conversations_instructor FOREIGN KEY (instructor_id) REFERENCES users (id),
    CONSTRAINT fk_conversations_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS course_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lesson_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    instructions TEXT NULL,
    due_date DATETIME NULL,
    max_score INT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_course_assignments_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS assignment_submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    text_content TEXT NULL,
    file_url VARCHAR(500) NULL,
    file_name VARCHAR(255) NULL,
    submitted_at DATETIME NOT NULL,
    score INT NULL,
    feedback TEXT NULL,
    graded_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_assignment_submissions_assignment_student UNIQUE (assignment_id, student_id),
    CONSTRAINT fk_assignment_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES course_assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_submissions_student FOREIGN KEY (student_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
