package com.lms.auth.security;

import com.lms.auth.entity.User;
import com.lms.common.exception.AccessDeniedDomainException;
import org.springframework.stereotype.Service;

/**
 * Điểm DUY NHẤT kiểm tra hạn chế tài khoản (AI, viết đánh giá...) — thay cho việc mỗi service
 * tự viết lại {@code if (Boolean.TRUE.equals(user.getIsAiLocked())) throw ...} rải rác, không
 * nhất quán (23/10/2026, phát hiện khi test: {@code TutorService}/{@code DiscoveryController}
 * hoàn toàn không check {@code isAiLocked}, chỉ {@code MaterialGenerationService}/
 * {@code QuizService} có check — mỗi nơi 1 kiểu).
 *
 * <p>3 cấp chồng nhau (cascade set ở {@code UserService.updateUser}/{@code AdminController}, KHÔNG
 * phải ở đây): khóa AI (độc lập) &lt; đánh dấu spam (kéo theo khóa AI + chặn viết đánh giá) &lt; khóa
 * tài khoản (kéo theo cả 2 + chặn toàn bộ qua {@link AccountLockRegistry}).
 */
@Service
public class AccountRestrictionService {

    public void requireAiAllowed(User user) {
        if (Boolean.TRUE.equals(user.getIsAiLocked())) {
            throw new AccessDeniedDomainException("Tài khoản của bạn đã bị khóa tính năng AI do vi phạm chính sách sử dụng.");
        }
    }

    public void requireReviewAllowed(User user) {
        if (Boolean.TRUE.equals(user.getIsSpamFlagged())) {
            throw new AccessDeniedDomainException("Tài khoản của bạn đang bị hạn chế do vi phạm chính sách, không thể viết đánh giá.");
        }
    }
}
