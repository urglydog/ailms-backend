-- (19/09/2026, sửa lỗi) — cùng nguyên nhân V113/V114: rà lại TOÀN BỘ entity có field
-- `isDeleted`/`deletedAt` trong dự án (`grep -rl "isDeleted\|deletedAt" entity/*.java`) để vá
-- 1 lần duy nhất, tránh crash-loop lặp lại từng bảng/từng cột một như V106 để lại.
SELECT 1;
