package com.lms.ranking.dto;

public class RankingDto {

    /** 1 dòng trong bảng xếp hạng cộng đồng (trang chủ) — KHÔNG lộ email, chỉ những gì vốn đã
     * công khai ở hồ sơ công khai (tên, avatar). */
    public record LeaderboardEntry(
            int rank,
            Long userId,
            String fullName,
            String avatarUrl,
            long totalXp
    ) {}

    /** Hạng + XP của người đang đăng nhập — dùng để tự biết vị trí của mình khi không nằm
     * trong Top N hiển thị ở trang chủ. {@code ranked=false} khi chưa có XP nào (chưa làm gì
     * để tính điểm) — "hạng" không có ý nghĩa trong trường hợp đó, FE không nên hiện số hạng. */
    public record MeRes(
            boolean ranked,
            int rank,
            long totalXp
    ) {}
}
