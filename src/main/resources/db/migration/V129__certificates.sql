-- ---------------------------------------------------------------------
-- Chứng chỉ hoàn thành khóa học (BR-CERT-01..10, doc/DacTa_ChucNangChungChi.md) — thay thế hoàn
-- toàn cơ chế tạm ở V127 (PDF sinh on-the-fly từ Enrollment, không lưu bản ghi riêng). Mỗi cặp
-- (student, course) chỉ có ĐÚNG 1 chứng chỉ (BR-CERT-02, idempotent). Dữ liệu hiển thị SNAPSHOT
-- tại thời điểm cấp (BR-CERT-04) — không tham chiếu động tới users/courses, nên đổi tên sau này
-- không ảnh hưởng chứng chỉ đã phát hành.
-- ---------------------------------------------------------------------
CREATE TABLE certificates (
    id BIGINT NOT NULL AUTO_INCREMENT,
    -- Mã công khai in trên chứng chỉ + dùng ở URL xác thực — KHÔNG đoán được (BR-CERT-03),
    -- dạng LL-{năm}-{8 ký tự random}, sinh ở CertificateService.
    certificate_code VARCHAR(20) NOT NULL,
    student_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    student_name_snapshot VARCHAR(255) NOT NULL,
    course_name_snapshot VARCHAR(500) NOT NULL,
    course_hours_snapshot DECIMAL(6,2) NOT NULL,
    instructor_name_snapshot VARCHAR(255) NOT NULL,
    completed_at DATETIME NOT NULL,
    issued_at DATETIME NOT NULL,
    -- BR-CERT-07: Admin có thể thu hồi (gian lận/vi phạm chính sách) — chưa có UI revoke ở v1,
    -- nhưng cột + logic kiểm tra ở trang xác thực công khai phải có sẵn.
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    -- BR-CERT-09: null cho tới lần tải PDF đầu tiên (render on-demand rồi cache lên B2).
    pdf_url VARCHAR(500) NULL,
    -- Đổi thiết kế chứng chỉ sau này thì tăng số này để BUỘC render lại PDF, bỏ cache cũ.
    template_version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_certificates_code UNIQUE (certificate_code),
    CONSTRAINT uk_certificates_student_course UNIQUE (student_id, course_id),
    CONSTRAINT fk_certificates_student FOREIGN KEY (student_id) REFERENCES users (id),
    CONSTRAINT fk_certificates_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT fk_certificates_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- V127 lưu mã UUID ngay trên Enrollment cho cơ chế PDF tạm thời — bảng certificates ở trên giờ
-- là nguồn sự thật duy nhất, cột này không còn được đọc/ghi ở đâu nữa.
ALTER TABLE enrollments DROP COLUMN certificate_code;
