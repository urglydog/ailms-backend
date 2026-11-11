package com.lms.finance.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.common.enums.NameMatchStatus;
import com.lms.common.enums.PayoutAccountStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.AccessDeniedDomainException;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.NotificationService;
import com.lms.finance.BankDirectory;
import com.lms.finance.NameNormalizer;
import com.lms.finance.dto.PayoutDto;
import com.lms.finance.entity.InstructorPayoutAccount;
import com.lms.finance.repository.InstructorPayoutAccountRepository;
import com.lms.instructor.repository.InstructorVerificationRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tài khoản nhận tiền của giảng viên (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn C). Chỉ giảng viên đã xác minh
 * (có hồ sơ CCCD) mới liên kết được. Thêm/đổi tài khoản phải bấm link xác nhận trong email (token dùng 1 lần, 30 phút);
 * đổi tài khoản thì tài khoản MỚI bị khóa rút {@link #COOLDOWN_DAYS} ngày (chống kẻ chiếm tài khoản rút trộm). Số tài khoản
 * mã hóa AES-GCM, chỉ trả về 4 số cuối; mọi thay đổi ghi nhật ký kiểm toán (không bao giờ ghi số tài khoản).
 */
@Service
@RequiredArgsConstructor
public class PayoutAccountService {

    static final int COOLDOWN_DAYS = 3;

    private final InstructorPayoutAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final InstructorVerificationRepository verificationRepository;
    private final PayoutCrypto crypto;
    private final FinanceConfirmationService confirmations;
    private final FinanceAuditService auditService;
    private final EmailService emailService;
    private final FinanceRateLimit rateLimit;
    private final NotificationService notificationService;

    public List<PayoutDto.BankRes> banks() {
        return BankDirectory.all().stream().map(b -> new PayoutDto.BankRes(b.code(), b.name())).toList();
    }

    @Transactional(readOnly = true)
    public PayoutDto.AccountRes getCurrent(String email) {
        User user = requireUser(email);
        return accountRepository.findFirstByUser_IdAndStatus(user.getId(), PayoutAccountStatus.ACTIVE)
                .or(() -> accountRepository.findFirstByUser_IdAndStatus(user.getId(), PayoutAccountStatus.PENDING_CONFIRM))
                .map(PayoutAccountService::toRes)
                .orElse(null);
    }

    /** Gửi yêu cầu liên kết tài khoản mới: tạo bản PENDING_CONFIRM + email xác nhận. Chưa có hiệu lực tới khi xác nhận. */
    @Transactional
    public PayoutDto.AccountRes link(String email, PayoutDto.LinkAccountReq req) {
        User user = requireUser(email);
        if (user.getRole() != Role.INSTRUCTOR) {
            throw new AccessDeniedDomainException("Chỉ giảng viên mới liên kết được tài khoản nhận tiền");
        }
        rateLimit.check("account-link", user.getId(), 5);
        crypto.requireConfigured();
        if (!verificationRepository.existsByUser_Id(user.getId())) {
            throw new BusinessRuleViolationException("Bạn cần hoàn tất xác minh giảng viên (CCCD) trước khi liên kết tài khoản nhận tiền");
        }
        var bank = BankDirectory.find(req.bankCode())
                .orElseThrow(() -> new InvalidRequestException("Ngân hàng không hợp lệ"));
        String number = req.accountNumber().replaceAll("\\s", "");
        if (!number.matches("[0-9]{6,20}")) {
            throw new InvalidRequestException("Số tài khoản chỉ gồm chữ số (6–20 số)");
        }
        String holder = NameNormalizer.normalize(req.holderName());
        if (holder.isEmpty()) {
            throw new InvalidRequestException("Tên chủ tài khoản không hợp lệ");
        }

        // Chỉ giữ 1 bản chờ xác nhận: bản cũ (nếu có) bị thay thế.
        for (InstructorPayoutAccount old : accountRepository.findByUser_IdAndStatus(user.getId(), PayoutAccountStatus.PENDING_CONFIRM)) {
            old.setStatus(PayoutAccountStatus.REPLACED);
            old.setReplacedAt(LocalDateTime.now());
            accountRepository.save(old);
        }

        InstructorPayoutAccount account = new InstructorPayoutAccount();
        account.setUser(user);
        account.setBankCode(bank.code());
        account.setBankBin(bank.bin());
        account.setBankName(bank.name());
        account.setAccountNumberEnc(crypto.encrypt(number));
        account.setAccountLast4(number.substring(number.length() - 4));
        account.setAccountHash(crypto.hash(number));
        account.setHolderName(holder);
        account.setStatus(PayoutAccountStatus.PENDING_CONFIRM);
        account.setNameMatchStatus(NameNormalizer.matches(holder, user.getFullName())
                ? NameMatchStatus.MATCHED : NameMatchStatus.MISMATCH);
        InstructorPayoutAccount saved = accountRepository.save(account);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("bank", saved.getBankCode());
        after.put("last4", saved.getAccountLast4());
        after.put("holder", saved.getHolderName());
        after.put("nameMatch", saved.getNameMatchStatus().name());
        auditService.record(email, "PAYOUT_ACCOUNT_LINK_REQUESTED", "payout_account", String.valueOf(saved.getId()), null, after);

        String token = confirmations.issue(FinanceConfirmationService.TYPE_ACCOUNT, saved.getId(), user.getId());
        // Email xác nhận BẮT BUỘC gửi được: lỗi thì ném, transaction rollback (không để lại bản chờ xác nhận mồ côi).
        emailService.sendPayoutAccountConfirmEmail(user.getEmail(), saved.getBankName(), saved.getAccountLast4(), token);
        return toRes(saved);
    }

    /** Xác nhận qua link email. Tài khoản cũ (nếu có) bị thay thế; tài khoản mới bị khóa rút COOLDOWN_DAYS ngày nếu là lần ĐỔI. */
    @Transactional
    public PayoutDto.AccountRes confirm(Long userId, Long accountId) {
        InstructorPayoutAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("PayoutAccount", accountId));
        if (!account.getUser().getId().equals(userId)) {
            throw new AccessDeniedDomainException("Liên kết xác nhận này không dành cho tài khoản của bạn");
        }
        if (account.getStatus() != PayoutAccountStatus.PENDING_CONFIRM) {
            throw new BusinessRuleViolationException("Tài khoản này đã được xác nhận hoặc đã bị thay thế");
        }
        LocalDateTime now = LocalDateTime.now();
        var previous = accountRepository.findFirstByUser_IdAndStatus(userId, PayoutAccountStatus.ACTIVE);
        previous.ifPresent(p -> {
            p.setStatus(PayoutAccountStatus.REPLACED);
            p.setReplacedAt(now);
            accountRepository.save(p);
        });
        account.setStatus(PayoutAccountStatus.ACTIVE);
        account.setConfirmedAt(now);
        account.setUsableFrom(previous.isPresent() ? now.plusDays(COOLDOWN_DAYS) : now);
        InstructorPayoutAccount saved = accountRepository.save(account);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("bank", saved.getBankCode());
        after.put("last4", saved.getAccountLast4());
        after.put("usableFrom", String.valueOf(saved.getUsableFrom()));
        auditService.record(saved.getUser().getEmail(), "PAYOUT_ACCOUNT_CONFIRMED", "payout_account", String.valueOf(saved.getId()),
                previous.map(p -> Map.<String, Object>of("replacedLast4", p.getAccountLast4())).orElse(null), after);

        if (previous.isPresent()) {
            emailService.sendPayoutAccountChangedEmail(saved.getUser().getEmail(), saved.getBankName(),
                    saved.getAccountLast4(), saved.getUsableFrom());
        }
        // Cảnh báo admin nếu cùng số tài khoản đang được giảng viên khác dùng.
        if (accountRepository.existsByAccountHashAndStatusAndUser_IdNot(saved.getAccountHash(), PayoutAccountStatus.ACTIVE, userId)) {
            userRepository.findByRole(Role.ADMIN).forEach(a -> notificationService.notify(a.getId(),
                    "PAYOUT_ACCOUNT_DUPLICATE", "Trùng số tài khoản nhận tiền",
                    "Một số tài khoản " + saved.getBankName().toUpperCase(Locale.ROOT) + " •••• " + saved.getAccountLast4()
                            + " đang được nhiều giảng viên liên kết, cần kiểm tra.",
                    "/admin/transactions/payouts"));
        }
        return toRes(saved);
    }

    /** Admin xem SỐ ĐẦY ĐỦ của tài khoản trong 1 yêu cầu rút (snapshot) — mỗi lần xem ghi nhật ký. */
    public String decryptForAdmin(String encryptedNumber) {
        return crypto.decrypt(encryptedNumber);
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    static PayoutDto.AccountRes toRes(InstructorPayoutAccount a) {
        return new PayoutDto.AccountRes(a.getId(), a.getBankCode(), a.getBankName(), PayoutCrypto.mask(a.getAccountLast4()),
                a.getHolderName(), a.getStatus(), a.getNameMatchStatus(), a.getUsableFrom(), a.getConfirmedAt());
    }
}
