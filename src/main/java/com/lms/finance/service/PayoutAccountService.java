package com.lms.finance.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.service.EmailService;
import com.lms.common.enums.NameMatchStatus;
import com.lms.common.enums.PayoutAccountStatus;
import com.lms.common.enums.PayoutStatus;
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
import com.lms.finance.repository.InstructorPayoutRepository;
import com.lms.instructor.repository.InstructorVerificationRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tài khoản nhận tiền của giảng viên (11/11/2026, Epic hoàn tiền – chi trả, Giai đoạn C). Chỉ giảng viên đã xác minh
 * (có hồ sơ CCCD) mới liên kết được. Mỗi giảng viên có tối đa {@link #MAX_ACCOUNTS} tài khoản, 1 tài khoản mặc định dùng để rút.
 * Thêm tài khoản, hoặc sửa NGÂN HÀNG/SỐ tài khoản của tài khoản đang dùng, phải bấm link xác nhận trong email (token dùng 1 lần, 30 phút);
 * sửa riêng tên chủ tài khoản, đổi mặc định, gỡ tài khoản thì làm trực tiếp. KHÔNG khóa rút sau khi đổi (quyết định 14/11/2026).
 * Số tài khoản mã hóa AES-GCM, chỉ trả về 4 số cuối; mọi thay đổi ghi nhật ký kiểm toán (không bao giờ ghi số tài khoản).
 */
@Service
@RequiredArgsConstructor
public class PayoutAccountService {

    static final int MAX_ACCOUNTS = 5;
    private static final Set<PayoutStatus> OPEN_PAYOUT_STATUSES =
            Set.of(PayoutStatus.PENDING_EMAIL, PayoutStatus.REQUESTED, PayoutStatus.APPROVED);

    private final InstructorPayoutAccountRepository accountRepository;
    private final UserRepository userRepository;
    private final InstructorVerificationRepository verificationRepository;
    private final PayoutCrypto crypto;
    private final FinanceConfirmationService confirmations;
    private final FinanceAuditService auditService;
    private final EmailService emailService;
    private final FinanceRateLimit rateLimit;
    private final NotificationService notificationService;
    private final InstructorPayoutRepository payoutRepository;

    public List<PayoutDto.BankRes> banks() {
        return BankDirectory.all().stream().map(b -> new PayoutDto.BankRes(b.code(), b.name())).toList();
    }

    /** Tài khoản mặc định (dùng để rút); chưa có thì tài khoản đang chờ xác nhận; không có thì null. */
    @Transactional(readOnly = true)
    public PayoutDto.AccountRes getCurrent(String email) {
        User user = requireUser(email);
        return accountRepository.findFirstByUser_IdAndStatusAndIsDefaultTrue(user.getId(), PayoutAccountStatus.ACTIVE)
                .or(() -> accountRepository.findFirstByUser_IdAndStatus(user.getId(), PayoutAccountStatus.ACTIVE))
                .or(() -> accountRepository.findFirstByUser_IdAndStatus(user.getId(), PayoutAccountStatus.PENDING_CONFIRM))
                .map(PayoutAccountService::toRes)
                .orElse(null);
    }

    /** Mọi tài khoản đang dùng hoặc đang chờ xác nhận, tài khoản mặc định lên đầu. */
    @Transactional(readOnly = true)
    public List<PayoutDto.AccountRes> list(String email) {
        User user = requireUser(email);
        return accountRepository.findByUser_IdAndStatusInOrderByIdAsc(user.getId(),
                        List.of(PayoutAccountStatus.ACTIVE, PayoutAccountStatus.PENDING_CONFIRM)).stream()
                .sorted((x, y) -> Boolean.compare(Boolean.TRUE.equals(y.getIsDefault()), Boolean.TRUE.equals(x.getIsDefault())))
                .map(PayoutAccountService::toRes)
                .toList();
    }

    /** Gửi yêu cầu liên kết tài khoản mới: tạo bản PENDING_CONFIRM + email xác nhận. Chưa có hiệu lực tới khi xác nhận. */
    @Transactional
    public PayoutDto.AccountRes link(String email, PayoutDto.LinkAccountReq req) {
        User user = requireLinkableUser(email);
        String number = normalizeNumber(req.accountNumber());
        // Giới hạn tính theo tài khoản đang dùng (ACTIVE); bản chờ xác nhận cũ bị thay bên dưới nên không tính.
        if (accountRepository.countByUser_IdAndStatus(user.getId(), PayoutAccountStatus.ACTIVE) >= MAX_ACCOUNTS) {
            throw new BusinessRuleViolationException("Bạn đã liên kết tối đa " + MAX_ACCOUNTS + " tài khoản. Hãy gỡ bớt trước khi thêm mới.");
        }
        if (accountRepository.existsByUser_IdAndAccountHashAndStatus(user.getId(), crypto.hash(number), PayoutAccountStatus.ACTIVE)) {
            throw new BusinessRuleViolationException("Số tài khoản này đã được liên kết");
        }
        // Chỉ giữ 1 bản "thêm mới" chờ xác nhận: bản cũ (nếu có) bị thay thế.
        for (InstructorPayoutAccount old : accountRepository.findByUser_IdAndStatus(user.getId(), PayoutAccountStatus.PENDING_CONFIRM)) {
            if (old.getReplacesAccountId() == null) {
                supersede(old);
            }
        }
        return createPending(user, email, req.bankCode(), number, req.holderName(), null, "PAYOUT_ACCOUNT_LINK_REQUESTED");
    }

    /**
     * Sửa tài khoản. Đang chờ xác nhận → sửa tại chỗ và gửi lại email. Đang ACTIVE: chỉ đổi tên chủ tài khoản → áp dụng ngay
     * (không email); đổi ngân hàng/số tài khoản → tạo bản chờ xác nhận qua email, bản cũ vẫn dùng được cho tới khi xác nhận.
     */
    @Transactional
    public PayoutDto.AccountRes edit(String email, Long accountId, PayoutDto.EditAccountReq req) {
        User user = requireLinkableUser(email);
        InstructorPayoutAccount account = ownedOpen(user, accountId);
        var bank = BankDirectory.find(req.bankCode()).orElseThrow(() -> new InvalidRequestException("Ngân hàng không hợp lệ"));
        String holder = NameNormalizer.normalize(req.holderName());
        if (holder.isEmpty()) {
            throw new InvalidRequestException("Tên chủ tài khoản không hợp lệ");
        }
        boolean keepNumber = req.accountNumber() == null || req.accountNumber().isBlank();
        String number = keepNumber ? crypto.decrypt(account.getAccountNumberEnc()) : normalizeNumber(req.accountNumber());
        boolean sameBankAndNumber = bank.code().equals(account.getBankCode()) && crypto.hash(number).equals(account.getAccountHash());

        if (account.getStatus() == PayoutAccountStatus.ACTIVE && sameBankAndNumber) {
            Map<String, Object> before = Map.of("holder", account.getHolderName());
            account.setHolderName(holder);
            account.setNameMatchStatus(NameNormalizer.matches(holder, user.getFullName()) ? NameMatchStatus.MATCHED : NameMatchStatus.MISMATCH);
            InstructorPayoutAccount saved = accountRepository.save(account);
            auditService.record(email, "PAYOUT_ACCOUNT_HOLDER_EDITED", "payout_account", String.valueOf(saved.getId()), before,
                    Map.of("holder", saved.getHolderName(), "nameMatch", saved.getNameMatchStatus().name()));
            return toRes(saved);
        }
        if (account.getStatus() == PayoutAccountStatus.PENDING_CONFIRM) {
            Long replaces = account.getReplacesAccountId();
            supersede(account);
            return createPending(user, email, req.bankCode(), number, req.holderName(), replaces, "PAYOUT_ACCOUNT_LINK_REQUESTED");
        }
        if (!sameBankAndNumber && accountRepository.existsByUser_IdAndAccountHashAndStatus(user.getId(), crypto.hash(number), PayoutAccountStatus.ACTIVE)) {
            throw new BusinessRuleViolationException("Số tài khoản này đã được liên kết");
        }
        // Mỗi tài khoản chỉ có 1 bản sửa chờ xác nhận: bản sửa cũ (nếu có) bị thay.
        for (InstructorPayoutAccount old : accountRepository.findByUser_IdAndStatus(user.getId(), PayoutAccountStatus.PENDING_CONFIRM)) {
            if (accountId.equals(old.getReplacesAccountId())) {
                supersede(old);
            }
        }
        return createPending(user, email, req.bankCode(), number, req.holderName(), accountId, "PAYOUT_ACCOUNT_EDIT_REQUESTED");
    }

    /** Đặt làm tài khoản mặc định (chỉ giữa các tài khoản đã xác nhận, không cần email). */
    @Transactional
    public PayoutDto.AccountRes setPrimary(String email, Long accountId) {
        User user = requireUser(email);
        InstructorPayoutAccount target = accountRepository.findByIdAndUser_Id(accountId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PayoutAccount", accountId));
        if (target.getStatus() != PayoutAccountStatus.ACTIVE) {
            throw new BusinessRuleViolationException("Chỉ đặt mặc định được cho tài khoản đã xác nhận");
        }
        for (InstructorPayoutAccount a : accountRepository.findByUser_IdAndStatus(user.getId(), PayoutAccountStatus.ACTIVE)) {
            boolean should = a.getId().equals(target.getId());
            if (Boolean.TRUE.equals(a.getIsDefault()) != should) {
                a.setIsDefault(should);
                accountRepository.save(a);
            }
        }
        auditService.record(email, "PAYOUT_ACCOUNT_SET_DEFAULT", "payout_account", String.valueOf(target.getId()), null,
                Map.of("last4", target.getAccountLast4()));
        return toRes(target);
    }

    /** Gỡ tài khoản. Không gỡ được khi có yêu cầu rút còn mở đang dùng tài khoản đó. */
    @Transactional
    public void remove(String email, Long accountId) {
        User user = requireUser(email);
        InstructorPayoutAccount account = ownedOpen(user, accountId);
        if (payoutRepository.existsByAccount_IdAndStatusIn(accountId, OPEN_PAYOUT_STATUSES)) {
            throw new BusinessRuleViolationException("Tài khoản này đang được dùng cho một yêu cầu rút chưa hoàn tất. Hãy đổi tài khoản của yêu cầu đó hoặc hủy yêu cầu trước.");
        }
        boolean wasDefault = Boolean.TRUE.equals(account.getIsDefault());
        account.setStatus(PayoutAccountStatus.REMOVED);
        account.setIsDefault(false);
        account.setReplacedAt(LocalDateTime.now());
        accountRepository.save(account);
        // Bản sửa đang chờ xác nhận của tài khoản này không còn đối tượng để thay → hủy luôn.
        for (InstructorPayoutAccount pending : accountRepository.findByUser_IdAndStatus(user.getId(), PayoutAccountStatus.PENDING_CONFIRM)) {
            if (accountId.equals(pending.getReplacesAccountId())) {
                supersede(pending);
            }
        }
        if (wasDefault) {
            accountRepository.findByUser_IdAndStatus(user.getId(), PayoutAccountStatus.ACTIVE).stream().findFirst().ifPresent(next -> {
                next.setIsDefault(true);
                accountRepository.save(next);
            });
        }
        auditService.record(email, "PAYOUT_ACCOUNT_REMOVED", "payout_account", String.valueOf(accountId),
                Map.of("last4", account.getAccountLast4()), null);
    }

    private User requireLinkableUser(String email) {
        User user = requireUser(email);
        if (user.getRole() != Role.INSTRUCTOR) {
            throw new AccessDeniedDomainException("Chỉ giảng viên mới liên kết được tài khoản nhận tiền");
        }
        rateLimit.check("account-link", user.getId(), 8);
        crypto.requireConfigured();
        if (!verificationRepository.existsByUser_Id(user.getId())) {
            throw new BusinessRuleViolationException("Bạn cần hoàn tất xác minh giảng viên (CCCD) trước khi liên kết tài khoản nhận tiền");
        }
        return user;
    }

    private InstructorPayoutAccount ownedOpen(User user, Long accountId) {
        InstructorPayoutAccount account = accountRepository.findByIdAndUser_Id(accountId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("PayoutAccount", accountId));
        if (account.getStatus() != PayoutAccountStatus.ACTIVE && account.getStatus() != PayoutAccountStatus.PENDING_CONFIRM) {
            throw new BusinessRuleViolationException("Tài khoản này không còn hiệu lực");
        }
        return account;
    }

    private static String normalizeNumber(String raw) {
        String number = raw == null ? "" : raw.replaceAll("\\s", "");
        if (!number.matches("[0-9]{6,20}")) {
            throw new InvalidRequestException("Số tài khoản chỉ gồm chữ số (6–20 số)");
        }
        return number;
    }

    private void supersede(InstructorPayoutAccount old) {
        old.setStatus(PayoutAccountStatus.REPLACED);
        old.setReplacedAt(LocalDateTime.now());
        accountRepository.save(old);
    }

    private PayoutDto.AccountRes createPending(User user, String email, String bankCode, String number, String holderRaw,
                                               Long replacesId, String auditAction) {
        var bank = BankDirectory.find(bankCode).orElseThrow(() -> new InvalidRequestException("Ngân hàng không hợp lệ"));
        String holder = NameNormalizer.normalize(holderRaw);
        if (holder.isEmpty()) {
            throw new InvalidRequestException("Tên chủ tài khoản không hợp lệ");
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
        account.setReplacesAccountId(replacesId);
        account.setNameMatchStatus(NameNormalizer.matches(holder, user.getFullName())
                ? NameMatchStatus.MATCHED : NameMatchStatus.MISMATCH);
        InstructorPayoutAccount saved = accountRepository.save(account);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("bank", saved.getBankCode());
        after.put("last4", saved.getAccountLast4());
        after.put("holder", saved.getHolderName());
        after.put("nameMatch", saved.getNameMatchStatus().name());
        if (replacesId != null) {
            after.put("replacesAccountId", replacesId);
        }
        auditService.record(email, auditAction, "payout_account", String.valueOf(saved.getId()), null, after);

        String token = confirmations.issue(FinanceConfirmationService.TYPE_ACCOUNT, saved.getId(), user.getId());
        // Email xác nhận BẮT BUỘC gửi được: lỗi thì ném, transaction rollback (không để lại bản chờ xác nhận mồ côi).
        emailService.sendPayoutAccountConfirmEmail(user.getEmail(), saved.getBankName(), saved.getAccountLast4(), token);
        return toRes(saved);
    }

    /** Xác nhận qua link email. Bản SỬA thay tài khoản cũ (kế thừa cờ mặc định); bản THÊM MỚI cùng tồn tại với các tài khoản khác. */
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
        List<InstructorPayoutAccount> actives = accountRepository.findByUser_IdAndStatus(userId, PayoutAccountStatus.ACTIVE);
        InstructorPayoutAccount previous = account.getReplacesAccountId() == null ? null : actives.stream()
                .filter(a -> a.getId().equals(account.getReplacesAccountId())).findFirst().orElse(null);
        if (previous == null && actives.size() >= MAX_ACCOUNTS) {
            throw new BusinessRuleViolationException("Bạn đã liên kết tối đa " + MAX_ACCOUNTS + " tài khoản");
        }
        boolean makeDefault;
        if (previous != null) {
            makeDefault = Boolean.TRUE.equals(previous.getIsDefault());
            previous.setStatus(PayoutAccountStatus.REPLACED);
            previous.setIsDefault(false);
            previous.setReplacedAt(now);
            accountRepository.save(previous);
        } else {
            makeDefault = actives.stream().noneMatch(a -> Boolean.TRUE.equals(a.getIsDefault()));
        }
        account.setStatus(PayoutAccountStatus.ACTIVE);
        account.setConfirmedAt(now);
        account.setUsableFrom(now);
        account.setIsDefault(makeDefault);
        InstructorPayoutAccount saved = accountRepository.save(account);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("bank", saved.getBankCode());
        after.put("last4", saved.getAccountLast4());
        after.put("default", saved.getIsDefault());
        auditService.record(saved.getUser().getEmail(), "PAYOUT_ACCOUNT_CONFIRMED", "payout_account", String.valueOf(saved.getId()),
                previous == null ? null : Map.<String, Object>of("replacedLast4", previous.getAccountLast4()), after);

        if (previous != null) {
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
                a.getHolderName(), a.getStatus(), a.getNameMatchStatus(), a.getUsableFrom(), a.getConfirmedAt(),
                Boolean.TRUE.equals(a.getIsDefault()), a.getReplacesAccountId());
    }
}
