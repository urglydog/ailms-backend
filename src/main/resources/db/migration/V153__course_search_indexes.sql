-- Index cho tìm kiếm công khai ở Kho khóa học (UC09) — 08/11/2026.
-- Truy vấn luôn lọc status = PUBLISHED và visibility = PUBLIC, rồi sắp xếp theo created_at (Mới nhất,
-- mặc định) hoặc avg_rating (Đánh giá cao / lọc "từ X sao"). Đặt 2 cột lọc cố định đứng đầu để MySQL
-- vừa lọc vừa đọc sẵn đúng thứ tự, khỏi sắp xếp lại (filesort) khi số khóa học tăng lên.
CREATE INDEX idx_courses_public_newest ON courses (status, visibility, created_at);
CREATE INDEX idx_courses_public_rating ON courses (status, visibility, avg_rating);

-- "Đánh giá nhiều" đếm đánh giá CHƯA ẨN của từng khóa trong subquery: index (course_id, is_hidden)
-- cho phép đếm thẳng trên index thay vì đọc từng dòng đánh giá.
CREATE INDEX idx_course_reviews_course_hidden ON course_reviews (course_id, is_hidden);
