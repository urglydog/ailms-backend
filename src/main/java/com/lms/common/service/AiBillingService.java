package com.lms.common.service;

import com.lms.auth.repository.UserRepository;
import com.lms.common.entity.AiBillingEntry;
import com.lms.common.exception.InvalidRequestException;
import com.lms.common.repository.AiBillingEntryRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nhập và xem hóa đơn thực tế của nhà cung cấp AI để đối soát (không sửa/xóa: nhập sai thì nhập khoảng khác và báo kế toán; không chồng lấn khoảng ngày). */
@Service
@RequiredArgsConstructor
public class AiBillingService {

    static final long MAX_AMOUNT_VND = 5_000_000_000L;
    static final long MAX_SPAN_DAYS = 400;

    private final AiBillingEntryRepository repository;
    private final UserRepository userRepository;

    public record BillingRes(Long id, LocalDate periodStart, LocalDate periodEnd, long amountVnd, String source, String note) {}

    public record CreateReq(LocalDate periodStart, LocalDate periodEnd, Long amountVnd, String source, String note) {}

    @Transactional(readOnly = true)
    public List<BillingRes> list() {
        return repository.findAllByOrderByPeriodStartDesc().stream().map(AiBillingService::toRes).toList();
    }

    private static BillingRes toRes(AiBillingEntry e) {
        return new BillingRes(e.getId(), e.getPeriodStart(), e.getPeriodEnd(), e.getAmountVnd(), e.getSource(), e.getNote());
    }

    @Transactional
    public BillingRes create(String adminEmail, CreateReq req) {
        if (req == null || req.periodStart() == null || req.periodEnd() == null) {
            throw new InvalidRequestException("Vui lòng chọn đủ ngày bắt đầu và ngày kết thúc của kỳ hóa đơn.");
        }
        if (req.periodEnd().isBefore(req.periodStart())) {
            throw new InvalidRequestException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        if (ChronoUnit.DAYS.between(req.periodStart(), req.periodEnd()) > MAX_SPAN_DAYS) {
            throw new InvalidRequestException("Mỗi hóa đơn chỉ nên bao quát tối đa " + MAX_SPAN_DAYS + " ngày.");
        }
        if (req.periodEnd().isAfter(LocalDate.now().plusDays(1))) {
            throw new InvalidRequestException("Kỳ hóa đơn không được nằm ở tương lai.");
        }
        if (req.amountVnd() == null || req.amountVnd() < 0 || req.amountVnd() > MAX_AMOUNT_VND) {
            throw new InvalidRequestException("Số tiền phải là số nguyên không âm (đơn vị đồng), tối đa " + MAX_AMOUNT_VND + ".");
        }
        String source = req.source() == null ? "" : req.source().trim();
        if (source.isEmpty() || source.length() > 100) {
            throw new InvalidRequestException("Nguồn hóa đơn không được để trống và tối đa 100 ký tự.");
        }
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        if (note != null && note.length() > 300) {
            throw new InvalidRequestException("Ghi chú tối đa 300 ký tự.");
        }
        if (repository.countOverlapping(req.periodStart(), req.periodEnd()) > 0) {
            throw new InvalidRequestException("Khoảng ngày này trùng với một hóa đơn đã nhập. Hãy chọn khoảng khác để tránh tính trùng.");
        }
        AiBillingEntry e = new AiBillingEntry();
        e.setPeriodStart(req.periodStart());
        e.setPeriodEnd(req.periodEnd());
        e.setAmountVnd(req.amountVnd());
        e.setSource(source);
        e.setNote(note);
        e.setCreatedBy(adminEmail == null ? null : userRepository.findByEmail(adminEmail).map(u -> u.getId()).orElse(null));
        return toRes(repository.save(e));
    }
}
