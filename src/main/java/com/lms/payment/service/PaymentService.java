package com.lms.payment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.service.EnrollmentService;
import com.lms.payment.dto.PaymentDto;
import com.lms.payment.entity.Payment;
import com.lms.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkItem;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentService enrollmentService;
    private final PayOS payOS;

    @Value("${payment.vnpay.tmnCode:}")
    private String vnpTmnCode;

    @Value("${payment.vnpay.hashSecret:}")
    private String vnpHashSecret;

    @Value("${payment.vnpay.url:}")
    private String vnpUrl;

    @Value("${payment.vnpay.returnUrl:}")
    private String vnpReturnUrl;

    @Value("${payment.payos.returnUrl:http://localhost:3000/payments/callback}")
    private String payosReturnUrl;

    @Value("${payment.payos.cancelUrl:http://localhost:3000/payments/callback}")
    private String payosCancelUrl;

    @Transactional
    public PaymentDto.PaymentUrlRes createPayment(String email, PaymentDto.CreateReq req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
        Course course = courseRepository.findById(req.courseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", req.courseId()));

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessRuleViolationException("BR-PAY-01: Chỉ có thể thanh toán khóa học PUBLISHED.");
        }
        if (Boolean.TRUE.equals(course.getIsFree())) {
            throw new BusinessRuleViolationException("BR-PAY-01: Khóa học miễn phí, vui lòng dùng chức năng ghi danh miễn phí.");
        }
        if (enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId())) {
            throw new BusinessRuleViolationException("BR-ENROLL-01: Bạn đã sở hữu khóa học này.");
        }

        // BR-PAY-02: Lấy giá từ server
        BigDecimal amount = course.getPrice();

        Payment payment = new Payment();
        // Giới hạn txnRef 8 kí tự để test dễ nhìn hơn, thực tế nên dùng UUID đầy đủ hoặc logic format hóa đơn
        String txnRef = UUID.randomUUID().toString().substring(0, 8);
        payment.setTxnRef(txnRef);
        payment.setAmount(amount);
        payment.setPaymentMethod(req.paymentMethod());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setUser(user);
        payment.setCourse(course);
        payment.setBillingName(req.billingName());
        payment.setBillingPhone(req.billingPhone());

        paymentRepository.save(payment);

        if ("PAYOS".equalsIgnoreCase(req.paymentMethod())) {
            // Generate unique orderCode (number) for PayOS
            long orderCode = System.currentTimeMillis() % 1000000000L;
            payment.setTxnRef(String.valueOf(orderCode));
            paymentRepository.save(payment);

            PaymentLinkItem item = PaymentLinkItem.builder()
                    .name("Khóa học: " + course.getTitle())
                    .price(amount.longValue())
                    .quantity(1)
                    .build();
            String checkoutUrl = buildPayOsCheckoutUrl(orderCode, amount, "Thanh toan don " + orderCode, List.of(item));
            return new PaymentDto.PaymentUrlRes(checkoutUrl);
        }

        if ("VNPAY".equalsIgnoreCase(req.paymentMethod())) {
            String orderInfo = "Thanh toan khoa hoc " + course.getId();
            return new PaymentDto.PaymentUrlRes(buildVnpayUrl(txnRef, amount, orderInfo));
        }

        // Fallback for Momo / Others
        String mockPaymentUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=" + payment.getTxnRef();
        return new PaymentDto.PaymentUrlRes(mockPaymentUrl);
    }

    /**
     * Giỏ hàng (06/09/2026) — TÍNH NĂNG MỞ RỘNG, không nằm trong 49 use case đặc tả gốc.
     * Gộp thanh toán NHIỀU khóa học học viên tự chọn (checkbox) trong 1 lần "Proceed to
     * Checkout" từ giỏ hàng, qua ĐÚNG 1 giao dịch cổng thanh toán cho TỔNG số tiền — khác
     * {@link #createPayment} (luôn đúng 1 khóa). Mỗi khóa vẫn có 1 dòng {@link Payment}
     * RIÊNG (giữ đúng BR-PAY-05: tách hoa hồng giảng viên theo từng khóa), các dòng này
     * chia sẻ chung {@code orderGroupRef} để {@link #processIpn} xác nhận CẢ NHÓM cùng lúc
     * khi cổng thanh toán gọi lại đúng 1 lần cho tổng giao dịch.
     *
     * <p>Xác thực TOÀN BỘ khóa học trước khi tạo bất kỳ {@link Payment} nào (tất cả-hoặc-
     * không-gì) — hệ thống không có cơ chế hoàn tiền/huỷ một phần (BR-PAY-04).
     */
    @Transactional
    public PaymentDto.PaymentUrlRes createBatchPayment(String email, PaymentDto.CreateBatchReq req) {
        if (req.courseIds() == null || req.courseIds().isEmpty()) {
            throw new BusinessRuleViolationException("Chọn ít nhất 1 khóa học để thanh toán.");
        }
        List<Long> distinctIds = req.courseIds().stream().distinct().toList();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        List<Course> courses = distinctIds.stream()
                .map(id -> courseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Course", id)))
                .toList();
        for (Course course : courses) {
            if (course.getStatus() != CourseStatus.PUBLISHED) {
                throw new BusinessRuleViolationException("BR-PAY-01: Khóa học \"" + course.getTitle() + "\" chưa được xuất bản.");
            }
            if (Boolean.TRUE.equals(course.getIsFree())) {
                throw new BusinessRuleViolationException("BR-PAY-01: Khóa học \"" + course.getTitle() + "\" miễn phí, vui lòng dùng chức năng ghi danh miễn phí.");
            }
            if (enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId())) {
                throw new BusinessRuleViolationException("BR-ENROLL-01: Bạn đã sở hữu khóa học \"" + course.getTitle() + "\".");
            }
        }

        String orderGroupRef = String.valueOf(System.currentTimeMillis() % 1000000000L);
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Course course : courses) {
            Payment payment = new Payment();
            payment.setTxnRef(UUID.randomUUID().toString().substring(0, 8));
            payment.setOrderGroupRef(orderGroupRef);
            payment.setAmount(course.getPrice());
            payment.setPaymentMethod(req.paymentMethod());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setUser(user);
            payment.setCourse(course);
            payment.setBillingName(req.billingName());
            payment.setBillingPhone(req.billingPhone());
            paymentRepository.save(payment);
            totalAmount = totalAmount.add(course.getPrice());
        }

        if ("PAYOS".equalsIgnoreCase(req.paymentMethod())) {
            long orderCode = Long.parseLong(orderGroupRef);
            List<PaymentLinkItem> items = courses.stream()
                    .map(c -> PaymentLinkItem.builder().name("Khóa học: " + c.getTitle()).price(c.getPrice().longValue()).quantity(1).build())
                    .toList();
            String checkoutUrl = buildPayOsCheckoutUrl(orderCode, totalAmount, "Thanh toan don " + orderCode, items);
            return new PaymentDto.PaymentUrlRes(checkoutUrl);
        }

        if ("VNPAY".equalsIgnoreCase(req.paymentMethod())) {
            String orderInfo = courses.size() == 1
                    ? "Thanh toan khoa hoc " + courses.get(0).getId()
                    : "Thanh toan " + courses.size() + " khoa hoc";
            return new PaymentDto.PaymentUrlRes(buildVnpayUrl(orderGroupRef, totalAmount, orderInfo));
        }

        // Fallback for Momo / Others
        return new PaymentDto.PaymentUrlRes("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=" + orderGroupRef);
    }

    /** Tạo link thanh toán PayOS thật — dùng chung bởi cả {@link #createPayment} (1 khóa)
     * lẫn {@link #createBatchPayment} (nhiều khóa, mỗi khóa 1 {@link PaymentLinkItem}). */
    private String buildPayOsCheckoutUrl(long orderCode, BigDecimal totalAmount, String description, List<PaymentLinkItem> items) {
        try {
            String returnUrl = payosReturnUrl + "?status=success&orderCode=" + orderCode;
            String cancelUrl = payosCancelUrl + "?status=cancel&orderCode=" + orderCode;

            CreatePaymentLinkRequest paymentData = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(totalAmount.longValue())
                    .description(description)
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .items(items)
                    .build();

            CreatePaymentLinkResponse data = payOS.paymentRequests().create(paymentData);
            return data.getCheckoutUrl();
        } catch (Exception e) {
            log.error("PayOS error", e);
            throw new BusinessRuleViolationException("Lỗi khởi tạo thanh toán PayOS");
        }
    }

    /** Dựng URL thanh toán VNPAY Sandbox thật — dùng chung bởi cả {@link #createPayment} (1
     * khóa) lẫn {@link #createBatchPayment} (nhiều khóa, `vnpTxnRef`/`amount` là của CẢ
     * nhóm). Logic giữ NGUYÊN VẸN so với bản gốc trước khi tách hàm — chỉ tham số hoá
     * `vnpTxnRef`/`amount`/`orderInfo` thay vì đọc thẳng biến cục bộ. */
    private String buildVnpayUrl(String vnpTxnRef, BigDecimal amount, String orderInfo) {
        String vnp_Version = "2.1.0";
        String vnp_Command = "pay";
        String vnp_OrderInfo = orderInfo;
        String orderType = "other";
        String vnp_IpAddr = "127.0.0.1";
        String vnp_TmnCode = vnpTmnCode;

        int amountParam = amount.intValue() * 100;
        java.util.Map<String, String> vnp_Params = new java.util.HashMap<>();
        vnp_Params.put("vnp_Version", vnp_Version);
        vnp_Params.put("vnp_Command", vnp_Command);
        vnp_Params.put("vnp_TmnCode", vnp_TmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amountParam));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_BankCode", "NCB"); // Hardcode bank NCB để test Sandbox
        vnp_Params.put("vnp_TxnRef", vnpTxnRef);
        vnp_Params.put("vnp_OrderInfo", vnp_OrderInfo);
        vnp_Params.put("vnp_OrderType", orderType);
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnpReturnUrl);
        vnp_Params.put("vnp_IpAddr", vnp_IpAddr);

        java.util.Calendar cld = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        cld.add(java.util.Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        java.util.Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                hashData.append(fieldName);
                hashData.append('=');
                try {
                    hashData.append(java.net.URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                    query.append(java.net.URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                    query.append('=');
                    query.append(java.net.URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String queryUrl = query.toString();
        String vnp_SecureHash = com.lms.payment.config.VnpayConfig.hmacSHA512(vnpHashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;
        return vnpUrl + "?" + queryUrl;
    }

    /**
     * Giả lập xử lý IPN từ VNPAY. Thực tế sẽ cần truyền map parameters và verify checksum.
     *
     * <p>Giỏ hàng (06/09/2026) — {@code reference} có thể là {@code txnRef} của 1 Payment
     * đơn lẻ (luồng cũ) HOẶC {@code orderGroupRef} dùng chung bởi nhiều Payment tạo cùng 1
     * lần gộp thanh toán (luồng giỏ hàng) — thử tìm theo NHÓM trước, không thấy nhóm nào
     * mới rơi về tra theo {@code txnRef} đơn lẻ như trước đây (không đổi hành vi cũ).
     */
    @Transactional
    public void processIpn(String reference, String gatewayTxnNo, boolean isSuccess) {
        List<Payment> group = paymentRepository.findByOrderGroupRef(reference);
        List<Payment> targets = !group.isEmpty()
                ? group
                : List.of(paymentRepository.findByTxnRef(reference)
                        .orElseThrow(() -> new ResourceNotFoundException("Payment", reference)));

        for (Payment payment : targets) {
            applyOutcome(payment, gatewayTxnNo, isSuccess);
        }
    }

    private void applyOutcome(Payment payment, String gatewayTxnNo, boolean isSuccess) {
        // BR-PAY-03: Idempotent - Nếu đã xử lý thì bỏ qua
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} already processed. Status: {}", payment.getTxnRef(), payment.getStatus());
            return;
        }

        if (isSuccess) {
            payment.setStatus(PaymentStatus.PAID);
            payment.setGatewayTxnNo(gatewayTxnNo);
            payment.setPaidAt(LocalDateTime.now());

            // BR-PAY-05: Chốt cứng fee
            BigDecimal platformFee = payment.getAmount().multiply(new BigDecimal("0.30"));
            BigDecimal instructorEarning = payment.getAmount().subtract(platformFee);
            payment.setPlatformFee(platformFee);
            payment.setInstructorEarning(instructorEarning);

            paymentRepository.save(payment);

            // Phụ thuộc F3.1
            enrollmentService.createFromPayment(payment);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }
    }

    @Scheduled(fixedDelay = 60000) // Chạy mỗi phút
    @Transactional
    public void cancelExpiredPayments() {
        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(15);
        // Chuyển PENDING sang EXPIRED nếu quá 15 phút
        var expiredPayments = paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING, expiryTime);
        for (Payment p : expiredPayments) {
            p.setStatus(PaymentStatus.EXPIRED);
            paymentRepository.save(p);
        }
        if (!expiredPayments.isEmpty()) {
            log.info("Expired {} pending payments", expiredPayments.size());
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<PaymentDto.Res> getMyPayments(String email) {
        return paymentRepository.findByUser_EmailOrderByCreatedAtDesc(email).stream()
                .map(p -> new PaymentDto.Res(
                        p.getTxnRef(),
                        p.getAmount(),
                        p.getPaymentMethod(),
                        p.getStatus(),
                        p.getPaidAt(),
                        p.getCourse().getTitle(),
                        p.getGatewayTxnNo(),
                        p.getBillingName(),
                        p.getBillingPhone()
                )).toList();
    }

    @Transactional(readOnly = true)
    public java.util.List<PaymentDto.AdminRes> getAllPayments() {
        return paymentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(p -> new PaymentDto.AdminRes(
                        p.getTxnRef(),
                        p.getAmount(),
                        p.getPlatformFee(),
                        p.getInstructorEarning(),
                        p.getPaymentMethod(),
                        p.getStatus(),
                        p.getPaidAt(),
                        p.getCourse().getTitle(),
                        p.getGatewayTxnNo(),
                        p.getUser().getEmail(),
                        p.getBillingName(),
                        p.getBillingPhone()
                )).toList();
    }
}
