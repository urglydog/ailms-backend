-- Dọn các khóa học mẫu cũ (video/ảnh ngẫu nhiên) ở DB LOCAL trước khi nạp dữ liệu demo thật.
-- KHÔNG chạy trên server. Giữ lại: khóa có bản lồng tiếng (khoa-hoc-dang-soan-thao), khóa chờ duyệt/nháp
-- do người dùng tự tạo (khoa-hoc-cho-duyet, unity, kkkk) và nhap-mon-tri-tue-nhan-tao (đã có học liệu AI sinh).
-- Luôn sao lưu trước:  mysqldump -uroot -p lms_db > backup.sql

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS tmp_old_courses;
CREATE TEMPORARY TABLE tmp_old_courses (id BIGINT PRIMARY KEY) AS
SELECT id FROM courses WHERE slug IN (
  'lap-trinh-web-voi-react-nextjs', 'phan-tich-du-lieu-voi-python-pandas', 'thiet-ke-giao-dien-uiux-co-ban',
  'tieng-anh-giao-tiep-cho-it', 'html-css-tu-con-so-0', 'javascript-hien-dai-cho-nguoi-moi', 'typescript-thuc-chien',
  'xay-dung-rest-api-voi-spring-boot', 'sql-cho-phan-tich-du-lieu', 'truc-quan-hoa-du-lieu-voi-power-bi',
  'thong-ke-ung-dung-voi-python', 'prompt-engineering-cho-cong-viec', 'machine-learning-voi-scikit-learn',
  'xay-dung-chatbot-rag', 'figma-cho-nguoi-moi-bat-dau', 'nghien-cuu-nguoi-dung-va-ux-writing',
  'viet-email-tieng-anh-cong-so', 'tieng-anh-thuyet-trinh-ky-thuat');

DROP TEMPORARY TABLE IF EXISTS tmp_old_lessons;
CREATE TEMPORARY TABLE tmp_old_lessons (id BIGINT PRIMARY KEY) AS
SELECT l.id FROM lessons l JOIN chapters ch ON ch.id = l.chapter_id WHERE ch.course_id IN (SELECT id FROM tmp_old_courses);

-- Phụ thuộc của bài học
DELETE FROM lesson_progress WHERE lesson_id IN (SELECT id FROM tmp_old_lessons);
DELETE FROM lesson_watch_checkpoints WHERE lesson_id IN (SELECT id FROM tmp_old_lessons);
DELETE FROM lesson_documents WHERE lesson_id IN (SELECT id FROM tmp_old_lessons);
DELETE FROM lesson_chats WHERE lesson_id IN (SELECT id FROM tmp_old_lessons) AND parent_id IS NOT NULL;
DELETE FROM lesson_chats WHERE lesson_id IN (SELECT id FROM tmp_old_lessons);
DELETE FROM course_resources WHERE course_id IN (SELECT id FROM tmp_old_courses) OR lesson_id IN (SELECT id FROM tmp_old_lessons);

-- Phụ thuộc của khóa học
DELETE FROM live_language_tracks WHERE live_session_id IN (SELECT id FROM live_sessions WHERE course_id IN (SELECT id FROM tmp_old_courses));
DELETE FROM live_sessions WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM announcements WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM cart_items WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM wishlist_items WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM certificates WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM course_reviews WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM payments WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM enrollments WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM course_activity_logs WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM coupon_courses WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM course_bundle_items WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM course_invites WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM student_study_plans WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM tutor_security_flags WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM chat_messages WHERE chat_session_id IN (SELECT id FROM chat_sessions WHERE course_id IN (SELECT id FROM tmp_old_courses));
DELETE FROM chat_sessions WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM messages WHERE conversation_id IN (SELECT id FROM conversations WHERE course_id IN (SELECT id FROM tmp_old_courses));
DELETE FROM conversations WHERE course_id IN (SELECT id FROM tmp_old_courses);

DELETE FROM lessons WHERE id IN (SELECT id FROM tmp_old_lessons);
DELETE FROM chapters WHERE course_id IN (SELECT id FROM tmp_old_courses);
DELETE FROM courses WHERE id IN (SELECT id FROM tmp_old_courses);

COMMIT;
