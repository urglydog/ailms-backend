-- F2.1: Lesson được tạo trước với metadata (title) rồi mới nạp video ở Giai đoạn 4
-- (UC33 tách khỏi UC34 theo doc/FEATURE_ASSIGNMENT.md). video_source/video_url vì vậy
-- phải cho phép NULL cho tới khi Giai đoạn 4 gán giá trị qua endpoint upload video.
ALTER TABLE lessons MODIFY COLUMN video_source VARCHAR(20) NULL;
ALTER TABLE lessons MODIFY COLUMN video_url VARCHAR(1000) NULL;
