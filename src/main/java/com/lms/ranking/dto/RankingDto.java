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
     * trong Top N hiển thị ở trang chủ. */
    public record MeRes(
            int rank,
            long totalXp
    ) {}
}
