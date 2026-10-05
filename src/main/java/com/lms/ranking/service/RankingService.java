package com.lms.ranking.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.ranking.dto.RankingDto.LeaderboardEntry;
import com.lms.ranking.dto.RankingDto.MeRes;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ranking cộng đồng theo XP (UpComming_Plan.md, Epic "Ranking cộng đồng") — leaderboard TOÀN
 * HỆ THỐNG (không tách theo khóa học), đọc trực tiếp cột {@code users.total_xp} (xem
 * {@code XpService}), không cần bảng tổng hợp/cron riêng ở quy mô hiện tại.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingService {

    private final UserRepository userRepository;

    public List<LeaderboardEntry> getLeaderboard(int limit) {
        List<User> topUsers = userRepository.findByOrderByTotalXpDesc(PageRequest.of(0, limit));
        List<LeaderboardEntry> result = new java.util.ArrayList<>(topUsers.size());
        int rank = 1;
        for (User u : topUsers) {
            result.add(new LeaderboardEntry(rank++, u.getId(), u.getFullName(), u.getAvatarUrl(), u.getTotalXp()));
        }
        return result;
    }

    public MeRes getMyRanking(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("User not found"));
        long myXp = user.getTotalXp() == null ? 0L : user.getTotalXp();
        int rank = (int) userRepository.countByTotalXpGreaterThan(myXp) + 1;
        return new MeRes(rank, myXp);
    }
}
