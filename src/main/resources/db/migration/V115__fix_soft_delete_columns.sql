-- (19/09/2026, sửa lỗi) — cùng nguyên nhân V113/V114: rà lại TOÀN BỘ entity có field
-- `isDeleted`/`deletedAt` trong dự án (`grep -rl "isDeleted\|deletedAt" entity/*.java`) để vá
-- 1 lần duy nhất, tránh crash-loop lặp lại từng bảng/từng cột một như V106 để lại.
ALTER TABLE material_generations ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE material_generations ADD COLUMN deleted_at DATETIME NULL;

ALTER TABLE quizzes ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE quizzes ADD COLUMN deleted_at DATETIME NULL;
