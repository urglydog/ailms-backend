package com.lms.payment.service;

import com.lms.payment.entity.Payment;
import com.lms.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCronJob {

    private final PaymentRepository paymentRepository;

    /**
     * Chạy mỗi phút 1 lần.
     * Tìm các Payment ở trạng thái PENDING mà đã được tạo quá 15 phút (BR-PAY-03)
     * và chuyển sang trạng thái FAILED/EXPIRED.
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expirePendingPayments() {
        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(15);
        List<Payment> pendingPayments = paymentRepository.findByStatusAndCreatedAtBefore(com.lms.common.enums.PaymentStatus.PENDING, expiryTime);
        
        if (!pendingPayments.isEmpty()) {
            for (Payment payment : pendingPayments) {
                payment.setStatus(com.lms.common.enums.PaymentStatus.FAILED);
            }
            paymentRepository.saveAll(pendingPayments);
            log.info("Đã đánh dấu huỷ {} giao dịch quá hạn", pendingPayments.size());
        }
    }
}
