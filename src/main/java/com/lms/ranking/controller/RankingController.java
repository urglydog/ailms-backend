package com.lms.ranking.controller;

import com.lms.ranking.dto.RankingDto.LeaderboardEntry;
import com.lms.ranking.dto.RankingDto.MeRes;
import com.lms.ranking.service.RankingService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ranking")
@RequiredArgsConstructor
public class RankingController {

    private static final int MAX_LEADERBOARD_LIMIT = 50;

    private final RankingService rankingService;

    /** Banner "Bảng xếp hạng cộng đồng" ở trang chủ — công khai, xem được không cần đăng nhập
     * (đăng ký ở {@code SecurityConfig.PUBLIC_GET_ENDPOINTS}). */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard(
            @RequestParam(defaultValue = "5") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LEADERBOARD_LIMIT));
        return ResponseEntity.ok(rankingService.getLeaderboard(safeLimit));
    }

    /** Hạng + XP của chính người đang đăng nhập — để tự biết vị trí khi không nằm trong Top N. */
    @GetMapping("/me")
    public ResponseEntity<MeRes> getMyRanking(Principal principal) {
        return ResponseEntity.ok(rankingService.getMyRanking(principal.getName()));
    }
}
