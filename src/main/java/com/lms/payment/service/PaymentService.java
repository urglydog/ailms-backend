package com.lms.payment.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Course;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.service.CourseAccessService;
import com.lms.common.enums.CourseStatus;
import com.lms.common.enums.PaymentStatus;
import com.lms.common.enums.RevenueSource;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.coupon.entity.Coupon;
import com.lms.coupon.service.CouponService;
import com.lms.bundle.repository.CourseBundleRepository;
import com.lms.bundle.entity.CourseBundle;
import com.lms.enrollment.repository.EnrollmentRepository;
import com.lms.enrollment.service.EnrollmentService;
import com.lms.payment.dto.PaymentDto;
import com.lms.payment.entity.Payment;
import com.lms.payment.repository.PaymentRepository;
import com.lms.payment.repository.CartItemRepository;
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
    private final CouponService couponService;
    private final CourseAccessService courseAccessService;
    private final CourseBundleRepository bundleRepository;
    private final CartItemRepository cartItemRepository;
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

        courseAccessService.verifyCanEnroll(course, email, req.courseAccessPassword());

        if (course.getStatus() != CourseStatus.PUBLISHED) {
            throw new BusinessRuleViolationException("BR-PAY-01: Chỉ có thể thanh toán khóa học PUBLISHED.");
        }
        if (Boolean.TRUE.equals(course.getIsFree())) {
            throw new BusinessRuleViolationException("BR-PAY-01: Khóa học miễn phí, vui lòng dùng chức năng ghi danh miễn phí.");
        }
        if (enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId())) {
            throw new BusinessRuleViolationException("BR-ENROLL-01: Bạn đã sở hữu khóa học này.");
        }
        // BUG THẬT (25/09/2026) — xem docblock nhánh fallback bên dưới: validate NGAY từ đầu
        // (trước khi tạo dòng Payment PENDING) thay vì để tới cuối hàm.
        if (!"PAYOS".equalsIgnoreCase(req.paymentMethod()) && !"VNPAY".equalsIgnoreCase(req.paymentMethod())) {
            throw new BusinessRuleViolationException("PAYMENT_METHOD_UNAVAILABLE",
                    "Phương thức thanh toán \"" + req.paymentMethod() + "\" hiện chưa được hỗ trợ. Vui lòng chọn VNPAY hoặc PayOS.");
        }

        // BR-PAY-02: Lấy giá từ server. UC57 mở rộng (15/09/2026) — áp coupon tốt nhất
        // (autoApply + mã tự nhập nếu có, BR-COUPON-01 không cộng dồn) TRƯỚC khi chốt amount,
        // để BR-PAY-05 (30/70) tính đúng trên giá THỰC THU.
        CouponService.PricingResult pricing = couponService.resolveBestPrice(course, user, req.couponCode());
        BigDecimal amount = pricing.finalPrice();
        if (pricing.appliedCoupon() != null) {
            // BUG THẬT (03/10/2026): phải tái kiểm tra hạn mức CÓ LOCK ngay trước khi lưu
            // Payment, không chỉ dựa vào resolveBestPrice (chỉ đọc, không chống race).
            couponService.reserveUsage(pricing.appliedCoupon(), user);
        }

        Payment payment = new Payment();
        // Giới hạn txnRef 8 kí tự để test dễ nhìn hơn, thực tế nên dùng UUID đầy đủ hoặc logic format hóa đơn
        String txnRef = UUID.randomUUID().toString().substring(0, 8);
        payment.setTxnRef(txnRef);
        payment.setAmount(amount);
        applyCouponToPayment(payment, pricing);
        payment.setPaymentMethod(req.paymentMethod());
        payment.setStatus(PaymentStatus.PENDING);
        payment.setUser(user);
        payment.setCourse(course);
        payment.setBillingName(req.billingName());
        payment.setBillingPhone(req.billingPhone());
        payment.setRevenueSource(resolveRevenueSource(course, req.referralCode()));

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
            return new PaymentDto.PaymentUrlRes(checkoutUrl, payment.getTxnRef());
        }

        if ("VNPAY".equalsIgnoreCase(req.paymentMethod())) {
            String orderInfo = "Thanh toan khoa hoc " + course.getId();
            return new PaymentDto.PaymentUrlRes(buildVnpayUrl(txnRef, amount, orderInfo), txnRef);
        }

        // Không thể tới đây nữa — đã validate paymentMethod ngay đầu hàm (trước đây MOMO/ZALOPAY
        // và bất kỳ giá trị lạ nào rơi vào đây, ÂM THẦM trả URL sandbox VNPAY sai — bug thật,
        // 25/09/2026).
        throw new IllegalStateException("Unreachable: paymentMethod đã được validate ở đầu hàm");
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
        if ((req.courseIds() == null || req.courseIds().isEmpty()) && (req.bundleIds() == null || req.bundleIds().isEmpty())) {
            throw new BusinessRuleViolationException("Chọn ít nhất 1 khóa học hoặc gói khóa học để thanh toán.");
        }
        
        if (!"PAYOS".equalsIgnoreCase(req.paymentMethod()) && !"VNPAY".equalsIgnoreCase(req.paymentMethod())) {
            throw new BusinessRuleViolationException("PAYMENT_METHOD_UNAVAILABLE",
                    "Phương thức thanh toán \"" + req.paymentMethod() + "\" hiện chưa được hỗ trợ. Vui lòng chọn VNPAY hoặc PayOS.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        // 1. Thu thập tất cả các khóa học từ Course lẻ và Bundle
        java.util.Set<Long> standaloneCourseIds = new java.util.HashSet<>();
        if (req.courseIds() != null) {
            standaloneCourseIds.addAll(req.courseIds());
        }

        java.util.List<CourseBundle> activeBundles = new ArrayList<>();
        if (req.bundleIds() != null && !req.bundleIds().isEmpty()) {
            activeBundles = bundleRepository.findAllById(req.bundleIds());
            for (CourseBundle bundle : activeBundles) {
                if (!bundle.getIsActive()) {
                    throw new BusinessRuleViolationException("Gói khóa học " + bundle.getTitle() + " hiện không hoạt động.");
                }
            }
        }

        // 2. Tách khóa học thành các nhóm (Bundle Items và Standalone Items)
        // Để làm tròn chính xác, ta sẽ tạo một class tạm để chứa thông tin thanh toán cho từng khóa học
        class CheckoutItem {
            Course course;
            CourseBundle bundle; // null if standalone
            BigDecimal originalPrice;
            BigDecimal finalPrice;
            BigDecimal discountAmount;
            Coupon coupon; // null nếu là bundle hoặc không áp mã
        }

        java.util.List<CheckoutItem> checkoutItems = new ArrayList<>();
        java.util.Set<Long> processedCourseIds = new java.util.HashSet<>();

        // Xử lý Bundle trước (De-duplicate: Nếu có trong bundle thì bỏ qua ở standalone)
        for (CourseBundle bundle : activeBundles) {
            List<Course> bundleCourses = bundle.getCourses();
            List<Course> validBundleCourses = new ArrayList<>();

            for (Course course : bundleCourses) {
                if (processedCourseIds.contains(course.getId())) continue;
                if (enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId())) continue; // Pro-rated: Khách đã sở hữu
                
                validBundleCourses.add(course);
                processedCourseIds.add(course.getId());
                standaloneCourseIds.remove(course.getId()); // Loại khỏi danh sách lẻ nếu bị trùng
            }

            if (validBundleCourses.isEmpty()) continue;

            // Tính tiền cho Bundle (Rounding Absorbtion)
            BigDecimal bundleOriginalSum = BigDecimal.ZERO;
            for (Course c : validBundleCourses) {
                bundleOriginalSum = bundleOriginalSum.add(c.getPrice());
            }

            BigDecimal discountRatio = new BigDecimal(bundle.getDiscountPercent()).divide(new BigDecimal(100));
            BigDecimal totalDiscountAmount = bundleOriginalSum.multiply(discountRatio).setScale(0, java.math.RoundingMode.HALF_UP);
            BigDecimal bundleFinalSum = bundleOriginalSum.subtract(totalDiscountAmount);

            BigDecimal currentSum = BigDecimal.ZERO;
            for (int i = 0; i < validBundleCourses.size(); i++) {
                Course c = validBundleCourses.get(i);
                CheckoutItem item = new CheckoutItem();
                item.course = c;
                item.bundle = bundle;
                item.originalPrice = c.getPrice();

                if (i == validBundleCourses.size() - 1) {
                    // Item cuối cùng: Hấp thụ sai số làm tròn
                    item.finalPrice = bundleFinalSum.subtract(currentSum);
                    item.discountAmount = item.originalPrice.subtract(item.finalPrice);
                } else {
                    // Các item đầu: Tính theo tỷ lệ
                    BigDecimal itemDiscount = c.getPrice().multiply(discountRatio).setScale(0, java.math.RoundingMode.HALF_UP);
                    item.finalPrice = c.getPrice().subtract(itemDiscount);
                    item.discountAmount = itemDiscount;
                    currentSum = currentSum.add(item.finalPrice);
                }
                checkoutItems.add(item);
            }
        }

        // Xử lý Standalone Courses
        for (Long courseId : standaloneCourseIds) {
            if (processedCourseIds.contains(courseId)) continue;
            Course course = courseRepository.findById(courseId).orElseThrow(() -> new ResourceNotFoundException("Course", courseId));
            
            courseAccessService.verifyCanAddToCart(course, email);
            if (course.getStatus() != CourseStatus.PUBLISHED) {
                throw new BusinessRuleViolationException("BR-PAY-01: Khóa học \"" + course.getTitle() + "\" chưa được xuất bản.");
            }
            if (Boolean.TRUE.equals(course.getIsFree())) {
                throw new BusinessRuleViolationException("BR-PAY-01: Khóa học \"" + course.getTitle() + "\" miễn phí, vui lòng dùng chức năng ghi danh miễn phí.");
            }
            if (enrollmentRepository.existsByUser_IdAndCourse_Id(user.getId(), course.getId())) {
                throw new BusinessRuleViolationException("BR-ENROLL-01: Bạn đã sở hữu khóa học \"" + course.getTitle() + "\".");
            }

            // Coupon CHỈ áp dụng cho khóa lẻ
            CouponService.PricingResult pricing = couponService.resolveBestPrice(course, user, req.couponCode());
            if (pricing.appliedCoupon() != null) {
                // BUG THẬT (03/10/2026): tái kiểm tra hạn mức CÓ LOCK ngay khi quyết định áp
                // dụng coupon, tránh race condition giống createPayment (xem reserveUsage).
                couponService.reserveUsage(pricing.appliedCoupon(), user);
            }

            CheckoutItem item = new CheckoutItem();
            item.course = course;
            item.bundle = null;
            item.originalPrice = pricing.originalPrice();
            item.finalPrice = pricing.finalPrice();
            item.discountAmount = pricing.originalPrice().subtract(pricing.finalPrice());
            item.coupon = pricing.appliedCoupon();
            checkoutItems.add(item);
            
            processedCourseIds.add(courseId);
        }

        if (checkoutItems.isEmpty()) {
            throw new BusinessRuleViolationException("Bạn đã sở hữu tất cả khóa học trong danh sách thanh toán.");
        }

        String orderGroupRef = String.valueOf(System.currentTimeMillis() % 1000000000L);
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        for (CheckoutItem item : checkoutItems) {
            Payment payment = new Payment();
            payment.setTxnRef(UUID.randomUUID().toString().substring(0, 8));
            payment.setOrderGroupRef(orderGroupRef);
            payment.setAmount(item.finalPrice);
            payment.setOriginalAmount(item.originalPrice);
            payment.setDiscountAmount(item.discountAmount);
            payment.setBundle(item.bundle);
            
            // Nếu là khóa lẻ thì mới cho áp coupon — dùng lại coupon đã resolveBestPrice +
            // reserveUsage ở bước tạo CheckoutItem phía trên, KHÔNG gọi lại resolveBestPrice
            // (BUG THẬT 03/10/2026: gọi lại ở đây sẽ đọc lại hạn mức mà KHÔNG có lock, mất hết
            // tác dụng của reserveUsage phía trên).
            if (item.coupon != null) {
                payment.setCoupon(item.coupon);
            }

            payment.setPaymentMethod(req.paymentMethod());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setUser(user);
            payment.setCourse(item.course);
            payment.setBillingName(req.billingName());
            payment.setBillingPhone(req.billingPhone());
            
            String referralCode = req.referralCodes() != null ? req.referralCodes().get(item.course.getId()) : null;
            payment.setRevenueSource(resolveRevenueSource(item.course, referralCode));
            
            paymentRepository.save(payment);
            totalAmount = totalAmount.add(item.finalPrice);
        }

        // Zero-dollar logic
        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            // Thay vì gọi qua API, chúng ta cập nhật trực tiếp Payment thành PAID và enroll
            // Giả lập callback thành công nội bộ
            for (Payment p : paymentRepository.findByOrderGroupRef(orderGroupRef)) {
                p.setStatus(PaymentStatus.PAID);
                p.setPaidAt(LocalDateTime.now());
                enrollmentService.createFromPayment(p);
            }
            // Trả về một URL giả để FE xử lý success
            return new PaymentDto.PaymentUrlRes("/payments/callback?status=success&orderCode=" + orderGroupRef, orderGroupRef);
        }

        if ("PAYOS".equalsIgnoreCase(req.paymentMethod())) {
            long orderCode = Long.parseLong(orderGroupRef);
            List<PaymentLinkItem> items = new ArrayList<>();
            for (CheckoutItem item : checkoutItems) {
                items.add(PaymentLinkItem.builder()
                        .name("Khóa học: " + item.course.getTitle())
                        .price(item.finalPrice.longValue())
                        .quantity(1)
                        .build());
            }
            String checkoutUrl = buildPayOsCheckoutUrl(orderCode, totalAmount, "Thanh toan don " + orderCode, items);
            return new PaymentDto.PaymentUrlRes(checkoutUrl, orderGroupRef);
        }

        if ("VNPAY".equalsIgnoreCase(req.paymentMethod())) {
            String orderInfo = checkoutItems.size() == 1
                    ? "Thanh toan khoa hoc " + checkoutItems.get(0).course.getId()
                    : "Thanh toan " + checkoutItems.size() + " khoa hoc";
            return new PaymentDto.PaymentUrlRes(buildVnpayUrl(orderGroupRef, totalAmount, orderInfo), orderGroupRef);
        }

        throw new IllegalStateException("Unreachable: paymentMethod đã được validate ở đầu hàm");
    }

    private RevenueSource resolveRevenueSource(Course course, String referralCode) {
        if (referralCode != null && !referralCode.isBlank()
                && referralCode.trim().equalsIgnoreCase(course.getReferralCode())) {
            return RevenueSource.INSTRUCTOR_REFERRAL;
        }
        return RevenueSource.ORGANIC;
    }

    /** Ghi lại coupon đã áp dụng (nếu có) vào 1 dòng {@link Payment} — dùng chung bởi cả
     * {@link #createPayment} và {@link #createBatchPayment} (15/09/2026, mở rộng). */
    private void applyCouponToPayment(Payment payment, CouponService.PricingResult pricing) {
        if (pricing.appliedCoupon() != null) {
            Coupon coupon = pricing.appliedCoupon();
            payment.setCoupon(coupon);
            payment.setOriginalAmount(pricing.originalPrice());
            payment.setDiscountAmount(pricing.originalPrice().subtract(pricing.finalPrice()));
        }
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

            // BR-PAY-05 (chia doanh thu 2 mức, 20/09/2026): tỷ lệ theo revenueSource đã chốt
            // lúc TẠO đơn (resolveRevenueSource) — ORGANIC 63% nền tảng, INSTRUCTOR_REFERRAL
            // 3% nền tảng. Chốt cứng fee lúc PAID, không tính lại lúc hiển thị.
            BigDecimal platformRate = payment.getRevenueSource() == RevenueSource.INSTRUCTOR_REFERRAL
                    ? new BigDecimal("0.03")
                    : new BigDecimal("0.63");
            BigDecimal platformFee = payment.getAmount().multiply(platformRate);
            BigDecimal instructorEarning = payment.getAmount().subtract(platformFee);
            payment.setPlatformFee(platformFee);
            payment.setInstructorEarning(instructorEarning);

            paymentRepository.save(payment);

            // Phụ thuộc F3.1
            enrollmentService.createFromPayment(payment);
            
            // Dọn dẹp Giỏ hàng (Cart Cleanup) sau khi thanh toán thành công
            cartItemRepository.deleteByUser_IdAndCourse_Id(payment.getUser().getId(), payment.getCourse().getId());
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
                        p.getBillingPhone(),
                        p.getOriginalAmount(),
                        p.getDiscountAmount(),
                        p.getCoupon() != null ? p.getCoupon().getCode() : null
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
                        p.getBillingPhone(),
                        p.getOriginalAmount(),
                        p.getDiscountAmount(),
                        p.getCoupon() != null ? p.getCoupon().getCode() : null,
                        p.getRevenueSource()
                )).toList();
    }
}
