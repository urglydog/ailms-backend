-- Nhóm các bản version của cùng một học liệu thành một "dòng" (lineage) để
-- tính đúng số version tiếp theo và liệt kê lịch sử theo đúng dòng, thay vì
-- theo toàn bộ user+course như trước. Bản gốc (V1) tự trỏ vào chính nó.
ALTER TABLE material_generations
    ADD COLUMN root_generation_id BIGINT NULL AFTER parent_generation_id;

UPDATE material_generations SET root_generation_id = id WHERE parent_generation_id IS NULL;

-- Lặp lại vài lần để lan truyền đúng cho các chuỗi version sâu nhiều cấp
-- (V1 -> V2 -> V3 -> ...), vì UPDATE...JOIN không đảm bảo lan truyền đa cấp trong 1 lần.
UPDATE material_generations child
    JOIN material_generations parent ON child.parent_generation_id = parent.id
    SET child.root_generation_id = parent.root_generation_id
    WHERE child.parent_generation_id IS NOT NULL AND parent.root_generation_id IS NOT NULL;

UPDATE material_generations child
    JOIN material_generations parent ON child.parent_generation_id = parent.id
    SET child.root_generation_id = parent.root_generation_id
    WHERE child.parent_generation_id IS NOT NULL AND parent.root_generation_id IS NOT NULL;

UPDATE material_generations child
    JOIN material_generations parent ON child.parent_generation_id = parent.id
    SET child.root_generation_id = parent.root_generation_id
    WHERE child.parent_generation_id IS NOT NULL AND parent.root_generation_id IS NOT NULL;

ALTER TABLE material_generations
    ADD CONSTRAINT fk_material_generations_root
        FOREIGN KEY (root_generation_id) REFERENCES material_generations(id) ON DELETE SET NULL;

CREATE INDEX idx_material_generations_root ON material_generations(root_generation_id);
