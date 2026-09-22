-- Dữ liệu lịch sử (trước các lần sửa versioning trong phiên làm việc này) để lại nhiều
-- bản ghi is_archived=0 (đang dùng) trong CÙNG một dòng version (root_generation_id) —
-- ví dụ một nhánh mồ côi song song với nhánh thật đang được tiếp tục. Chỉ giữ đúng 1 bản
-- active mỗi dòng: bản có id lớn nhất (tạo gần nhất), các bản còn lại chuyển is_archived=1.
UPDATE material_generations mg
    JOIN (
        SELECT root_generation_id, MAX(id) AS keep_id
        FROM material_generations
        WHERE root_generation_id IS NOT NULL AND is_archived = 0
        GROUP BY root_generation_id
    ) t ON mg.root_generation_id = t.root_generation_id
    SET mg.is_archived = 1
    WHERE mg.is_archived = 0 AND mg.id <> t.keep_id;
