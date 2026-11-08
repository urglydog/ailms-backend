package com.lms.auth.service;

import com.lms.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ranking cộng đồng (UpComming_Plan.md, Epic "Ranking cộng đồng") — quy đổi đơn giản theo số
 * lần hành động, KHÔNG theo độ khó/thời lượng. Mỗi điểm gọi {@link #award} đều ĐÃ được đảm bảo
 * idempotent bởi chính nơi gọi (vd "lesson vừa hoàn thành lần đầu", "learning day vừa ghi nhận
 * thành công", "enrollment vừa đạt 100% lần đầu") — service này không tự kiểm tra lại, chỉ cộng
 * XP vô điều kiện khi được gọi.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class XpService {

    /** Hoàn thành 1 bài học (video) lần đầu — {@code LessonProgressService.recordProgress}. */
    public static final long LESSON_COMPLETED_XP = 10;
    /** Pass 1 quiz chính thức lần đầu (điểm ≥ ngưỡng đạt) — {@code QuizService.submitAttempt}. */
    public static final long OFFICIAL_QUIZ_PASSED_XP = 20;
    /** Hoàn thành 100% 1 khóa học lần đầu — cùng lúc với cấp Certificate. */
    public static final long COURSE_COMPLETED_XP = 100;
    /** Giữ streak thêm 1 ngày (1 learning day mới) — {@code StreakService.recordActivity}. */
    public static final long STREAK_DAY_XP = 5;

    private final UserRepository userRepository;

    /**
     * BUG THẬT (07/11/2026) — trước đây {@code REQUIRES_NEW}: khi gọi từ trong 1 transaction đã
     * chạm bảng {@code users} (vd nộp bài thi → StreakService insert {@code user_learning_days}
     * giữ khoá S lên dòng user qua FK), transaction mới xin khoá X để {@code UPDATE users} và chờ
     * transaction cha — cha lại đợi con → tự khoá cứng (self-deadlock) tới hết
     * {@code innodb_lock_wait_timeout} (50s), học viên thấy nút "Đang nộp..." treo gần 1 phút.
     * Giờ THAM GIA transaction hiện có (cùng connection, không tranh khoá với chính mình).
     */
    @Transactional
    public void award(Long userId, long amount, String reason) {
        userRepository.addXp(userId, amount);
        log.info("Cong {} XP cho user {} (ly do: {})", amount, userId, reason);
    }
}
