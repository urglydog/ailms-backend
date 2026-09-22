-- Nhật ký hoạt động giảng viên trên khóa học (panel "Hoạt động gần đây" kiểu GakuNin RDM).
CREATE TABLE IF NOT EXISTS course_activity_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    course_id BIGINT NOT NULL,
    actor_id BIGINT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
    FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_course_activity_logs_course_created ON course_activity_logs(course_id, created_at DESC);
