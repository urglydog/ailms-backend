CREATE TABLE student_study_plans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    target_date DATE NOT NULL,
    hours_per_week INT NOT NULL,
    plan_data JSON NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_course_plan (user_id, course_id),
    CONSTRAINT fk_study_plan_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_study_plan_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE
);
