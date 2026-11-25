package com.lms.payment.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.DisputeStatus;
import com.lms.common.enums.InstructorDecision;
import com.lms.common.enums.Role;
import com.lms.common.service.NotificationService;
import com.lms.payment.entity.PaymentDispute;
import com.lms.payment.repository.PaymentDisputeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đánh dấu NO_RESPONSE cho khiếu nại mà giảng viên không phản hồi đúng hạn (11/11/2026, Giai đoạn B) và
 * nhắc admin. Chạy mỗi giờ, mỗi lần chỉ chạm các dòng quá hạn chưa xử lý (có index) → rất nhẹ. Idempotent:
 * sau khi đánh dấu, dòng không còn thỏa điều kiện nên không bị nhắc lại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DisputeDeadlineJob {

    private final PaymentDisputeRepository disputeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 5 * * * *")
    @SchedulerLock(name = "DisputeDeadlineJob.markExpired", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    @Transactional
    public void markExpiredInstructorResponses() {
        markExpired(LocalDateTime.now());
    }

    /** Tách ra để test được với "bây giờ" cố định. Trả số khiếu nại vừa đánh dấu. */
    @Transactional
    public int markExpired(LocalDateTime now) {
        List<PaymentDispute> expired = disputeRepository
                .findByInstructorDecisionIsNullAndInstructorDeadlineAtBeforeAndStatusIn(
                        now, Set.of(DisputeStatus.OPEN, DisputeStatus.REOPENED));
        if (expired.isEmpty()) {
            return 0;
        }
        List<com.lms.auth.entity.User> admins = userRepository.findByRole(Role.ADMIN);
        for (PaymentDispute d : expired) {
            d.setInstructorDecision(InstructorDecision.NO_RESPONSE);
            disputeRepository.save(d);
            admins.forEach(a -> notificationService.notify(a.getId(), "DISPUTE_INSTRUCTOR_NO_RESPONSE",
                    "Giảng viên không phản hồi khiếu nại",
                    "Giao dịch " + d.getPayment().getTxnRef() + " đã quá hạn phản hồi của giảng viên, có thể xử lý.",
                    "/admin/transactions/disputes"));
        }
        log.info("Đánh dấu {} khiếu nại quá hạn phản hồi của giảng viên", expired.size());
        return expired.size();
    }
}
