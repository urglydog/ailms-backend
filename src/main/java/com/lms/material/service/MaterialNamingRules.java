package com.lms.material.service;

import com.lms.common.enums.MaterialType;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.material.entity.MaterialGeneration;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quy tắc trùng tên (title + materialType, trong cùng thư mục) cho học liệu.
 *
 * <p>Tạo mới/đổi tên/di chuyển: chặn hẳn (người dùng chọn đích danh 1 tên/1 đích, tự động đổi tên
 * hộ có thể che mất việc gõ nhầm hoặc ý định gộp 2 học liệu). Nhân bản (copy-paste): tự thêm hậu
 * tố "(1)", "(2)"... như File Explorer — đây là hành động "tạo mới", tự thêm hậu tố không mất gì.
 */
public final class MaterialNamingRules {

    private MaterialNamingRules() {
    }

    public static void assertNoCollision(List<MaterialGeneration> siblingsInDestination, String title, MaterialType type, Long excludeId) {
        String normalized = title == null ? "" : title.trim();
        boolean collides = siblingsInDestination.stream().anyMatch(m ->
                !m.getId().equals(excludeId) && m.getMaterialType() == type &&
                        m.getTitle() != null && m.getTitle().trim().equalsIgnoreCase(normalized));
        // excludeId == null nghĩa là "không loại trừ bản nào" (dùng khi tạo mới — chưa có id).
        if (collides) {
            throw new BusinessRuleViolationException("DUPLICATE_MATERIAL_NAME",
                    "Đã tồn tại học liệu \"" + normalized + "\" cùng loại trong thư mục này. Vui lòng chọn tên khác.");
        }
    }

    public static String nextAvailableCopyName(List<MaterialGeneration> siblingsInDestination, String baseTitle, MaterialType type) {
        Set<String> existing = siblingsInDestination.stream()
                .filter(m -> m.getMaterialType() == type)
                .map(m -> m.getTitle() == null ? "" : m.getTitle().trim().toLowerCase())
                .collect(Collectors.toSet());
        String base = baseTitle == null ? "" : baseTitle.trim();
        if (!existing.contains(base.toLowerCase())) {
            return base;
        }
        int n = 1;
        while (existing.contains((base + " (" + n + ")").toLowerCase())) {
            n++;
        }
        return base + " (" + n + ")";
    }
}
