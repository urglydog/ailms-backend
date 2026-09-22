-- Dọn các bản ghi material_assignments bị trùng lặp do bug versioning-overwrite
-- (mỗi lần kéo đè học liệu đã gán sẵn lại tạo thêm 1 assignment trùng cùng đích),
-- giữ lại bản ghi cũ nhất (id nhỏ nhất) mỗi tổ hợp (material_id, lesson_id, chapter_id).
DELETE ma FROM material_assignments ma
    JOIN (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY material_id, COALESCE(lesson_id, -1), COALESCE(chapter_id, -1)
                   ORDER BY id ASC
               ) AS rn
        FROM material_assignments
    ) dup ON ma.id = dup.id
    WHERE dup.rn > 1;
