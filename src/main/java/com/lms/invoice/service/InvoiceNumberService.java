package com.lms.invoice.service;

import com.lms.invoice.entity.InvoiceSequence;
import com.lms.invoice.entity.InvoiceType;
import com.lms.invoice.repository.InvoiceSequenceRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cấp số chứng từ dạng {@code LOẠI-YYYYMM-STT} (vd {@code RCP-202611-000001}). Dòng bộ đếm bị khóa ghi cho tới hết transaction phát hành
 * nên hai chứng từ cùng loại/tháng không bao giờ trùng số; nếu transaction phát hành lỗi và hoàn tác thì số cũng được hoàn lại (không đứt quãng).
 * Tháng lấy theo ngày của chứng từ (ngày giao dịch), nên chứng từ phát bù cho giao dịch cũ nằm đúng tháng giao dịch.
 */
@Service
@RequiredArgsConstructor
public class InvoiceNumberService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final InvoiceSequenceRepository sequenceRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public String next(InvoiceType type, LocalDateTime documentDate) {
        String key = type.prefix() + "-" + documentDate.format(MONTH);
        InvoiceSequence seq = sequenceRepository.lockByKey(key).orElseGet(() -> {
            sequenceRepository.createIfAbsent(key);
            return sequenceRepository.lockByKey(key)
                    .orElseThrow(() -> new IllegalStateException("Không tạo được bộ đếm chứng từ " + key));
        });
        long value = seq.getSeqValue() + 1;
        seq.setSeqValue(value);
        sequenceRepository.save(seq);
        return key + "-" + String.format("%06d", value);
    }
}
