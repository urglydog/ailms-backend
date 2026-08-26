package com.lms.payment.controller;

import com.lms.payment.dto.PaymentDto;
import com.lms.payment.service.PaymentService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.payos.PayOS;
import vn.payos.model.webhooks.WebhookData;
import vn.payos.model.webhooks.Webhook;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final PayOS payOS;

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<PaymentDto.PaymentUrlRes> createPayment(
            Principal principal,
            @Valid @RequestBody PaymentDto.CreateReq req) {
        return ResponseEntity.ok(paymentService.createPayment(principal.getName(), req));
    }

    /**
     * Endpoint IPN giả lập để test.
     * Trong thực tế, VNPAY sẽ gọi endpoint này dưới dạng GET với param đầy đủ,
     * hoặc Momo gọi dưới dạng POST.
     */
    @PostMapping("/ipn-mock")
    public ResponseEntity<java.util.Map<String, String>> mockIpn(
            @RequestParam String txnRef,
            @RequestParam String gatewayTxnNo,
            @RequestParam boolean isSuccess) {
        paymentService.processIpn(txnRef, gatewayTxnNo, isSuccess);
        return ResponseEntity.ok(java.util.Map.of("status", "ok"));
    }

    /**
     * Webhook chính thức của PayOS. PayOS sẽ gọi vào đây khi có cập nhật giao dịch.
     * Cần cấu hình URL webhook này trên Dashboard của PayOS (ví dụ: https://domain.com/api/v1/payments/payos/webhook).
     */
    @PostMapping("/payos/webhook")
    public ResponseEntity<java.util.Map<String, Object>> payosWebhook(@RequestBody Webhook webhookBody) {
        try {
            // Verify signature với checksumKey để đảm bảo request gửi từ PayOS
            WebhookData data = payOS.webhooks().verify(webhookBody);
            
            // Xử lý IPN (lấy orderCode làm txnRef)
            String txnRef = String.valueOf(data.getOrderCode());
            String gatewayTxnNo = String.valueOf(data.getPaymentLinkId());
            
            // Theo tài liệu PayOS, khi data hợp lệ thì giao dịch thường đã thanh toán thành công,
            // nhưng ta cũng có thể check data.getCode() nếu có (tuỳ version SDK).
            // Mặc định gọi true vì verifyPaymentWebhookData đã lọc hợp lệ.
            paymentService.processIpn(txnRef, gatewayTxnNo, true);
            
            return ResponseEntity.ok(java.util.Map.of("success", true));
        } catch (com.lms.common.exception.ResourceNotFoundException e) {
            log.warn("Webhook test từ PayOS hoặc giao dịch không tồn tại: {}", e.getMessage());
            // Trả về 200 OK để PayOS lưu được Webhook URL (bỏ qua lỗi không tìm thấy đơn hàng test)
            return ResponseEntity.ok(java.util.Map.of("success", true, "message", "Test webhook OK"));
        } catch (Exception e) {
            log.error("Lỗi xác thực hoặc xử lý PayOS Webhook", e);
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "success", false,
                    "message", "Invalid webhook data"
            ));
        }
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
    public ResponseEntity<java.util.List<PaymentDto.Res>> getMyPayments(Principal principal) {
        return ResponseEntity.ok(paymentService.getMyPayments(principal.getName()));
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<java.util.List<PaymentDto.AdminRes>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }
}
