package com.lms.material.dto;

/**
 * 1 dòng trong danh sách ngôn ngữ FE cho học viên/giảng viên chọn lúc sinh học liệu — CÙNG nguồn
 * {@code voice_mappings.is_active} (BR-DUB-07) với danh sách ngôn ngữ lồng tiếng, để 2 nơi luôn
 * khớp nhau (đúng yêu cầu "chọn full ngôn ngữ giống dubbing").
 *
 * <p>{@code available} ở đây có nghĩa KHÁC {@code DubLanguage.available} bên lồng tiếng: bên đó
 * là "đã có audio lồng tiếng xong", còn ở đây là "đã có SẴN bản dịch transcript cho ngôn ngữ này"
 * (do lồng tiếng hoặc lần sinh học liệu trước tạo ra) — chỉ là GỢI Ý hiển thị (dấu tích/dấu chấm),
 * KHÔNG hạn chế lựa chọn: BR-MAT-01 cho chọn ngôn ngữ tự do, {@code false} chỉ có nghĩa lần này sẽ
 * tốn thêm bước dịch trước khi sinh.
 */
public record LanguageAvailabilityRes(String code, String label, boolean available) {
}
